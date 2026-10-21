// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.system

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import lorix.noslackalarm.common.constants.Constants
import lorix.noslackalarm.common.util.WakeCheckOutcome
import lorix.noslackalarm.common.util.isActiveBucket
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 「起床验证」的传感器采样器（全部零权限）。
 *
 * 时间轴：关闭闹钟后先进入 [Constants.WAKE_CHECK_SETTLE_MS] 的沉降期（丢弃「把手机放回
 * 床头柜」这次动作），随后在 [Constants.WAKE_CHECK_SAMPLE_MS] 内按
 * [Constants.WAKE_CHECK_BUCKET_MS] 切桶统计运动量，结束后回调一次 [WakeCheckOutcome]。
 *
 * 传感器选择：
 * - 运动量优先用 [Sensor.TYPE_LINEAR_ACCELERATION]（系统已去掉重力分量），
 *   机型没有该虚拟传感器时回退 [Sensor.TYPE_ACCELEROMETER] 并用「模长 − g」近似动态分量；
 * - [Sensor.TYPE_SIGNIFICANT_MOTION] 作为硬件级「确实动过」的一次性触发，几乎零功耗。
 *
 * 失败语义：拿不到传感器、注册失败、或样本数不足
 * （[Constants.WAKE_CHECK_MIN_SAMPLES]）时回调 `null`，调用方必须放弃验证、绝不复响。
 *
 * 线程：所有方法都必须主线程调用，回调也在主线程。
 */
@Singleton
class WakeCheckSampler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val powerManager: PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private var running = false
    private var delivered = false
    private var callback: ((WakeCheckOutcome?) -> Unit)? = null

    private var accelerometerListener: SensorEventListener? = null
    private var motionTriggerListener: TriggerEventListener? = null

    /** 布防时拿到的显著运动传感器实例，注销时复用，避免二次查询返回 null 导致漏注销。 */
    private var motionTriggerSensor: Sensor? = null

    /** 当前 1 秒桶内的动态加速度模长样本（约 50 个/桶）。 */
    private val bucketSamples = ArrayList<Float>(BUCKET_SAMPLE_CAPACITY)
    private var bucketStartUptimeMs = 0L

    private var activeBuckets = 0
    private var totalBuckets = 0
    private var maxPeak = 0f
    private var sampleCount = 0
    private var significantMotionFired = false

    /**
     * 开始一次验证。
     *
     * @param onResult 结果回调（主线程，且保证只回调一次）；参数为 `null` 表示放弃验证。
     */
    fun start(onResult: (WakeCheckOutcome?) -> Unit) {
        cancel()
        callback = onResult
        running = true
        delivered = false
        handler.postDelayed({ beginSampling() }, Constants.WAKE_CHECK_SETTLE_MS)
    }

    /** 取消验证：注销全部传感器监听且不回调。幂等，可在服务销毁时无条件调用。 */
    fun cancel() {
        running = false
        delivered = true
        callback = null
        handler.removeCallbacksAndMessages(null)
        releaseSensors()
        resetState()
    }

    private fun beginSampling() {
        if (!running) return
        val manager = sensorManager
        if (manager == null) {
            finish(null, "no SensorManager")
            return
        }

        val linear = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val raw = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val sensor = linear ?: raw
        if (sensor == null) {
            finish(null, "no accelerometer")
            return
        }
        // 有线性加速度就用它（已去重力），否则用原始加速度并在回调里减掉重力
        val useLinear = linear != null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!running) return
                val magnitude = magnitudeOf(event.values, useLinear)
                addSample(magnitude)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        accelerometerListener = listener

        val registered = runCatching {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }.getOrDefault(false)
        if (!registered) {
            finish(null, "registerListener failed")
            return
        }

        // 显著运动：一次性触发，硬件已过滤桌面微振，触发即说明人带着手机动过
        manager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)?.let { motionSensor ->
            val trigger = object : TriggerEventListener() {
                override fun onTrigger(event: TriggerEvent?) {
                    significantMotionFired = true
                    Log.d(TAG, "significant motion fired")
                }
            }
            motionTriggerListener = trigger
            runCatching { manager.requestTriggerSensor(trigger, motionSensor) }
                .onSuccess { motionTriggerSensor = motionSensor }
                .onFailure { Log.d(TAG, "requestTriggerSensor failed: ${it.message}") }
        }

        bucketStartUptimeMs = SystemClock.elapsedRealtime()
        Log.d(TAG, "sampling started (linear=$useLinear)")
        handler.postDelayed({ finishSampling() }, Constants.WAKE_CHECK_SAMPLE_MS)
    }

    private fun magnitudeOf(values: FloatArray, useLinear: Boolean): Float {
        val x = values.getOrElse(0) { 0f }
        val y = values.getOrElse(1) { 0f }
        val z = values.getOrElse(2) { 0f }
        val magnitude = sqrt(x * x + y * y + z * z)
        // 原始加速度含重力分量：减去 1g 得到近似动态分量；线性加速度本身已去重力
        return if (useLinear) magnitude else abs(magnitude - STANDARD_GRAVITY)
    }

    private fun addSample(magnitude: Float) {
        sampleCount++
        bucketSamples.add(magnitude)
        val now = SystemClock.elapsedRealtime()
        if (now - bucketStartUptimeMs >= Constants.WAKE_CHECK_BUCKET_MS) {
            closeBucket()
            bucketStartUptimeMs = now
        }
    }

    private fun closeBucket() {
        if (bucketSamples.isEmpty()) return
        var sum = 0.0
        var peak = 0f
        for (value in bucketSamples) {
            sum += value.toDouble()
            peak = max(peak, abs(value))
        }
        val mean = sum / bucketSamples.size
        var variance = 0.0
        for (value in bucketSamples) {
            val delta = value - mean
            variance += delta * delta
        }
        val std = sqrt(variance / bucketSamples.size).toFloat()
        val active = isActiveBucket(std, peak)
        totalBuckets++
        if (active) activeBuckets++
        maxPeak = max(maxPeak, peak)
        Log.d(TAG, "bucket#$totalBuckets std=$std peak=$peak active=$active")
        bucketSamples.clear()
    }

    private fun finishSampling() {
        if (!running) return
        closeBucket()
        if (sampleCount < Constants.WAKE_CHECK_MIN_SAMPLES) {
            finish(null, "insufficient samples: $sampleCount")
            return
        }
        val interactive = powerManager?.isInteractive ?: true
        val outcome = WakeCheckOutcome(
            activeBuckets = activeBuckets,
            totalBuckets = totalBuckets,
            maxPeak = maxPeak,
            significantMotionFired = significantMotionFired,
            screenInteractiveAtEnd = interactive,
        )
        Log.d(
            TAG,
            "finished active=$activeBuckets/$totalBuckets maxPeak=$maxPeak " +
                "significant=$significantMotionFired interactive=$interactive",
        )
        finish(outcome, reason = null)
    }

    private fun finish(outcome: WakeCheckOutcome?, reason: String?) {
        if (delivered) return
        delivered = true
        running = false
        reason?.let { Log.d(TAG, "abandon: $it") }
        handler.removeCallbacksAndMessages(null)
        releaseSensors()
        val pending = callback
        callback = null
        // 先复位内部状态再回调：回调里若再次 start() 不会被本次复位清掉
        resetState()
        pending?.invoke(outcome)
    }

    private fun releaseSensors() {
        val manager = sensorManager
        if (manager != null) {
            accelerometerListener?.let { listener ->
                runCatching { manager.unregisterListener(listener) }
            }
            motionTriggerListener?.let { trigger ->
                motionTriggerSensor?.let { motionSensor ->
                    runCatching { manager.cancelTriggerSensor(trigger, motionSensor) }
                }
            }
        }
        accelerometerListener = null
        motionTriggerListener = null
        motionTriggerSensor = null
    }

    private fun resetState() {
        bucketSamples.clear()
        bucketStartUptimeMs = 0L
        activeBuckets = 0
        totalBuckets = 0
        maxPeak = 0f
        sampleCount = 0
        significantMotionFired = false
    }

    private companion object {
        const val TAG = "WakeCheckSampler"
        const val STANDARD_GRAVITY = 9.80665f

        /** 50Hz 下一个 1 秒桶约 50 个样本，预留少量余量避免扩容。 */
        const val BUCKET_SAMPLE_CAPACITY = 64
    }
}
