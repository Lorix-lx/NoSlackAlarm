// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.ringing.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lorix.noslackalarm.common.SettingsDefaults
import lorix.noslackalarm.common.constants.Constants
import lorix.noslackalarm.common.constants.Constants.RingingPhase
import lorix.noslackalarm.common.util.WakeCheckOutcome
import lorix.noslackalarm.common.util.isSustainedMovement
import lorix.noslackalarm.system.WakeCheckSampler
import javax.inject.Inject

/**
 * 防赖床检测的验证服务。
 *
 * 答题关闭闹钟后由 [AlarmService] 拉起：静默观察约 30 秒（8 秒沉降 + 22 秒采样），
 * 判定「用户回睡」时再次拉起 [AlarmService] 复响该闹钟（`is_wake_check = true`）。
 *
 * 之所以独立成服务而不是寄生在 [AlarmService]：后者是 `mediaPlayback` 类型的前台服务，
 * 关闭后长时间存活却无实际播放与该类型语义不符；本服务使用 `specialUse` 类型，职责更单一，
 * 生命周期严格限制在一次验证内（含看门狗兜底）。
 *
 * 任何异常情况（传感器不可用、样本不足、超时）都只会「放弃验证」，绝不复响。
 */
@AndroidEntryPoint
class WakeCheckService : Service() {

    @Inject
    lateinit var sampler: WakeCheckSampler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var watchdogJob: Job? = null
    private var countdownJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false
    private var reRang = false

    /** 本轮验证开始时刻（elapsedRealtime），用于通知里的剩余秒数倒计时。 */
    private var verificationStartedAt = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 已在验证中：忽略重复启动（同一晚不会有第二个验证）
        if (started) return START_NOT_STICKY

        val alarmId = intent?.getLongExtra(AlarmService.EXTRA_ALARM_ID, -1L) ?: -1L
        val mode = intent?.getStringExtra(AlarmService.EXTRA_ALARM_MODE) ?: SettingsDefaults.DEFAULT_MODE

        // 服务可能被系统重建后收到空 intent：直接退出，不做任何事
        if (alarmId == -1L) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 验证会跨多轮复响持续进行，直到检测到「持续移动」。这里只做两道防跑飞兜底：
        // 轮次上限与总时长上限（手机被遗留在家、传感器异常时不会无限循环）。
        val round = RingingStateStore.wakeCheckRound(this)
        val startedAt = RingingStateStore.wakeCheckStartedAt(this)
        val timedOut = startedAt > 0 &&
            System.currentTimeMillis() - startedAt >= Constants.WAKE_CHECK_SESSION_MAX_MS
        if (round >= Constants.WAKE_CHECK_MAX_ROUNDS || timedOut) {
            Log.d(TAG, "wake-check limit reached (round=$round, timedOut=$timedOut), stop")
            RingingStateStore.clearWakeCheckSession(this)
            stopSelf()
            return START_NOT_STICKY
        }
        RingingStateStore.startWakeCheckSessionIfNeeded(this)

        started = true
        verificationStartedAt = SystemClock.elapsedRealtime()
        val foregroundStarted = runCatching { startAsForeground() }
            .onFailure { Log.w(TAG, "startForeground failed: ${it.message}") }
            .isSuccess
        if (!foregroundStarted) {
            // 前台服务起不来（权限被拒 / 系统限制）：放弃验证，绝不复响
            stopSelf()
            return START_NOT_STICKY
        }
        acquireWakeLock()
        startWatchdog()
        startCountdown()

        Log.d(TAG, "verification started (alarmId=$alarmId)")
        sampler.start { outcome -> onSampled(alarmId, mode, outcome) }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onSampled(alarmId: Long, mode: String, outcome: WakeCheckOutcome?) {
        watchdogJob?.cancel()
        watchdogJob = null
        // 先停倒计时，避免它把「即将复响」的通知再改回倒计时文案
        countdownJob?.cancel()
        countdownJob = null

        if (outcome == null) {
            // 传感器不可用 / 样本不足：放弃验证并结束本轮统计
            RingingStateStore.clearWakeCheckSession(this)
            stopSelf()
            return
        }

        if (isSustainedMovement(outcome)) {
            // 检测到持续移动：真正起床，结束本轮验证
            Log.d(
                TAG,
                "sustained movement detected (${outcome.activeBuckets}/${outcome.totalBuckets}), verification passed",
            )
            RingingStateStore.clearWakeCheckSession(this)
        } else {
            Log.d(
                TAG,
                "not enough movement (${outcome.activeBuckets}/${outcome.totalBuckets}), re-ringing alarm $alarmId",
            )
            RingingStateStore.incrementWakeCheckRound(this)
            startRingingAgain(alarmId, mode)
        }
        stopSelf()
    }

    /**
     * 复响：重新拉起 [AlarmService]，并打上 `is_wake_check` 标记。
     *
     * 标记只用于界面提示与通知文案；用户再次关闭后仍会重新进入验证，
     * 直到 [isSustainedMovement] 成立或触发轮次/时长兜底。
     */
    private fun startRingingAgain(alarmId: Long, mode: String) {
        reRang = true
        val intent = Intent(this, AlarmService::class.java).apply {
            putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmService.EXTRA_ALARM_MODE, mode)
            putExtra(AlarmService.EXTRA_IS_SNOOZE, false)
            putExtra(AlarmService.EXTRA_IS_WAKE_CHECK, true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun startAsForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            Constants.WAKE_CHECK_NOTIFICATION_ID,
            buildNotification(remainingSeconds()),
            type,
        )
    }

    /**
     * 通知栏倒计时：每秒刷新一次「剩余 xx 秒」，让用户清楚还要等多久。
     *
     * 与采样共用 [verificationStartedAt] 这个时间基准，避免采样提前/延后时文案与真实进度脱节。
     */
    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (true) {
                val remaining = remainingSeconds()
                updateNotification(remaining)
                if (remaining <= 0) break
                delay(COUNTDOWN_TICK_MS)
            }
        }
    }

    private fun remainingSeconds(): Int {
        val elapsed = SystemClock.elapsedRealtime() - verificationStartedAt
        return ((TOTAL_MS - elapsed) / 1000L).toInt().coerceIn(0, TOTAL_SECONDS)
    }

    private fun updateNotification(remainingSeconds: Int) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            manager.notify(
                Constants.WAKE_CHECK_NOTIFICATION_ID,
                buildNotification(remainingSeconds),
            )
        }
    }

    private fun buildNotification(remainingSeconds: Int = TOTAL_SECONDS): Notification {
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        // 倒计时走完（采样可能因 ROM 调度稍有延后）时改为「判定中」，避免一直停在 0 秒
        val statusText = if (remainingSeconds > 0) {
            "正在检测是否赖床，剩余${remainingSeconds}秒"
        } else {
            "正在判定是否赖床…"
        }
        return NotificationCompat.Builder(this, Constants.WAKE_CHECK_CHANNEL_ID)
            .setContentTitle("起床验证中")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            Constants.WAKE_CHECK_CHANNEL_ID,
            "起床验证",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "关闭闹钟后的起床验证（不发声、不振动）"
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "noslackalarm:WakeCheck",
        ).apply {
            setReferenceCounted(false)
            acquire(Constants.WAKE_CHECK_WAKE_LOCK_MS)
        }
    }

    /**
     * 看门狗：采样回调因 ROM 限制未按时到达时，强制结束验证并注销传感器，避免长时间占用。
     */
    private fun startWatchdog() {
        watchdogJob = scope.launch {
            delay(Constants.WAKE_CHECK_WATCHDOG_MS)
            Log.w(TAG, "watchdog timeout, abandoning verification")
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sampler.cancel()
        watchdogJob?.cancel()
        watchdogJob = null
        countdownJob?.cancel()
        countdownJob = null
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
        // 部分 ROM 不会随服务销毁自动移除前台通知，显式清理避免残留「起床验证中」
        stopForeground(STOP_FOREGROUND_REMOVE)
        // 未经复响的验证结束：把会话置回空闲并清空轮次统计（覆盖看门狗等异常退出路径）；
        // 已复响时不要动相位，那一轮由 AlarmService 自己写 RINGING
        //（这里清掉会让「关屏=贪睡」在复响期间失效）。
        if (!reRang) {
            if (RingingStateStore.phase(this) == RingingPhase.VERIFYING) {
                RingingStateStore.setIdle(this)
            }
            RingingStateStore.clearWakeCheckSession(this)
        }
        scope.cancel()
    }

    companion object {
        private const val TAG = "WakeCheckService"

        /** 验证总时长（沉降 + 采样），通知倒计时以它为准。 */
        private const val TOTAL_MS =
            Constants.WAKE_CHECK_SETTLE_MS + Constants.WAKE_CHECK_SAMPLE_MS

        /** 倒计时初始秒数（30 秒）。 */
        private val TOTAL_SECONDS = (TOTAL_MS / 1000L).toInt()

        /** 通知倒计时刷新间隔。 */
        private const val COUNTDOWN_TICK_MS = 1_000L
    }
}
