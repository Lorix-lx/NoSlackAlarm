// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.ringing.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import lorix.noslackalarm.common.SettingsDefaults
import lorix.noslackalarm.common.constants.Constants
import lorix.noslackalarm.common.constants.Constants.RingingPhase
import lorix.noslackalarm.domain.repository.SettingsRepository
import lorix.noslackalarm.ringing.presentation.RingingActivity
import lorix.noslackalarm.system.WakeLockManager
import javax.inject.Inject

@AndroidEntryPoint
class AlarmService : Service() {

    @Inject
    lateinit var ringtonePlayer: RingtonePlayer

    @Inject
    lateinit var vibrationController: VibrationController

    @Inject
    lateinit var wakeLockManager: WakeLockManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private var alarmId: Long = -1L
    private var screenStateReceiver: ScreenStateReceiver? = null

    /** 关闭动作只处理一次，避免 Activity 重发 ACTION_DISMISS 时重复拉起验证。 */
    private var dismissHandled = false

    /**
     * 响铃会话令牌：每次正常响铃自增。关闭流程里要读 DataStore（挂起），
     * 若期间新闹钟已经开始响铃，旧流程必须整体放弃，不能把新会话的相位覆盖成 VERIFYING。
     */
    private var sessionToken = 0

    /**
     * 本次停止是否由「按电源键暂停响铃」触发。
     *
     * 暂停只停声音、不结束会话：相位保持 RINGING，这样用户稍后仍可在响铃界面上答题关闭，
     * 并照常走防赖床检测。onDestroy 据此跳过「相位归空闲」。
     */
    private var snoozeKeepsPhase = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerScreenStateReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            // 暂停响铃：只停声音，不结束会话 —— 相位保持 RINGING，
            // 用户之后仍可在响铃界面答题关闭，并照常进入防赖床检测。
            // 10 分钟后由 AlarmScheduler 重新拉起本服务（is_snooze=true）。
            Constants.ACTION_SNOOZE -> {
                stopAlarmInternal()
                RingingStateStore.clearWakeCheckSession(this)
                snoozeKeepsPhase = true
                stopSelf()
                return START_NOT_STICKY
            }
            // 答题关闭：停响铃，按开关决定是否进入「起床验证」
            Constants.ACTION_DISMISS -> {
                handleDismiss(intent, startId)
                return START_NOT_STICKY
            }
        }

        alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
        if (alarmId == -1L) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 在发送前台通知前先读取模式，以便选择不带振动的通知渠道
        val mode = intent?.getStringExtra(EXTRA_ALARM_MODE) ?: SettingsDefaults.DEFAULT_MODE
        val isSnooze = intent?.getBooleanExtra(EXTRA_IS_SNOOZE, false) ?: false
        val isWakeCheck = intent?.getBooleanExtra(EXTRA_IS_WAKE_CHECK, false) ?: false

        // 新的响铃开始：丢弃可能仍在跑的起床验证（旧闹钟不再复响）
        stopService(Intent(this, WakeCheckService::class.java))

        dismissHandled = false
        sessionToken++
        startForeground(Constants.ALARM_NOTIFICATION_ID, buildNotification(mode, isWakeCheck))

        // 获取 60 秒唤醒锁，确保响铃完整执行
        wakeLockManager.acquire(60_000L)

        // 记录会话状态：供 ScreenStateReceiver 判断「关屏是否等于贪睡」，也供关闭时判断是否为复响
        RingingStateStore.setRinging(this, alarmId, mode, isWakeCheck)

        when (mode) {
            "RINGTONE_ONLY" -> ringtonePlayer.play()
            "VIBRATE_ONLY" -> vibrationController.start()
            else -> {
                ringtonePlayer.play()
                vibrationController.start()
            }
        }

        // 直接启动响铃 Activity（屏幕唤醒由 RingingActivity 的 turnScreenOn/setTurnScreenOn 处理）
        val ringingIntent = Intent(this, RingingActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_IS_SNOOZE, isSnooze)
            putExtra(EXTRA_IS_WAKE_CHECK, isWakeCheck)
        }
        startActivity(ringingIntent)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 处理「答题关闭闹钟」。
     *
     * 原先响铃页直接 `stopService`，这里改为先停铃声再决定是否拉起 [WakeCheckService]：
     * - 本轮已是防赖床复响（`is_wake_check`），或用户关闭了开关 → 直接结束；
     * - 否则进入验证相位并启动验证服务。
     *
     * 关闭开关需要读 DataStore（挂起），因此放到协程里完成后再 `stopSelf()`，
     * 保证「先启动新服务、再停止自己」的顺序。
     */
    private fun handleDismiss(intent: Intent?, startId: Int) {
        if (dismissHandled) {
            stopSelf(startId)
            return
        }
        dismissHandled = true

        // 只接受「正在响铃」的关闭：Activity 重建后重发的 DISMISS、或验证已在进行时，直接忽略。
        // 这也顺带保证每轮响铃最多只拉起一次验证。
        if (RingingStateStore.phase(this) != RingingPhase.RINGING) {
            stopSelf(startId)
            return
        }

        stopAlarmInternal()

        val closedAlarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L)?.takeIf { it != -1L }
            ?: RingingStateStore.currentAlarmId(this)
        val mode = intent?.getStringExtra(EXTRA_ALARM_MODE)
            ?: RingingStateStore.currentMode(this)
            ?: SettingsDefaults.DEFAULT_MODE
        val tokenAtDismiss = sessionToken

        CoroutineScope(Dispatchers.Main.immediate).launch {
            // 复响会话同样要再验证一次：验证会一轮接一轮持续，直到检测到持续移动
            val enabled = runCatching { settingsRepository.wakeCheckEnabled.first() }
                .getOrDefault(false)

            // 读配置期间可能已有新闹钟开始响铃：整轮关闭流程作废，且不要 stopSelf 掉新会话
            if (tokenAtDismiss != sessionToken) return@launch

            if (enabled && closedAlarmId != -1L) {
                RingingStateStore.setVerifying(this@AlarmService)
                startWakeCheck(closedAlarmId, mode)
            } else {
                RingingStateStore.setIdle(this@AlarmService)
            }
            stopSelf(startId)
        }
    }

    private fun startWakeCheck(closedAlarmId: Long, mode: String) {
        val intent = Intent(this, WakeCheckService::class.java).apply {
            putExtra(EXTRA_ALARM_ID, closedAlarmId)
            putExtra(EXTRA_ALARM_MODE, mode)
        }
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }.isSuccess
        // 起不来就当作没有验证：绝不复响
        if (!started) RingingStateStore.setIdle(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterScreenStateReceiver()
        stopAlarmInternal()
        wakeLockManager.release()
        // 验证期间、以及「按电源键暂停响铃」后都要保留相位：
        // 前者关屏不是贪睡；后者用户回到界面答题仍属于同一轮响铃会话。
        if (!snoozeKeepsPhase && RingingStateStore.phase(this) != RingingPhase.VERIFYING) {
            RingingStateStore.setIdle(this)
        }
    }

    private fun registerScreenStateReceiver() {
        screenStateReceiver = ScreenStateReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenStateReceiver, filter)
    }

    private fun unregisterScreenStateReceiver() {
        screenStateReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // 未注册
            }
            screenStateReceiver = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibChannel = NotificationChannel(
                Constants.ALARM_CHANNEL_ID,
                "闹钟服务（含振动）",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "闹钟响铃通知（含振动）"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 300, 500)
            }

            val noVibChannel = NotificationChannel(
                Constants.ALARM_CHANNEL_ID_NO_VIBRATE,
                "闹钟服务（仅铃声）",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "闹钟响铃通知（仅铃声）"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(false)
            }

            getSystemService(NotificationManager::class.java)?.apply {
                createNotificationChannel(vibChannel)
                createNotificationChannel(noVibChannel)
            }
        }
    }

    private fun buildNotification(mode: String = SettingsDefaults.DEFAULT_MODE, isWakeCheck: Boolean = false): Notification {
        val contentIntent = Intent(this, RingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_IS_WAKE_CHECK, isWakeCheck)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (mode == "RINGTONE_ONLY") {
            Constants.ALARM_CHANNEL_ID_NO_VIBRATE
        } else {
            Constants.ALARM_CHANNEL_ID
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(if (isWakeCheck) "闹钟响起中（防赖床检测）" else "闹钟响起中")
            .setContentText("解答题目以关闭闹钟")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    fun stopAlarm() {
        stopAlarmInternal()
        stopForeground(STOP_FOREGROUND_REMOVE)
        wakeLockManager.release()
        stopSelf()
    }

    private fun stopAlarmInternal() {
        ringtonePlayer.stop()
        vibrationController.stop()
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_ALARM_MODE = "alarm_mode"
        const val EXTRA_IS_SNOOZE = "is_snooze"
        const val EXTRA_IS_WAKE_CHECK = "is_wake_check"
    }
}
