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
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import lorix.noslackalarm.common.constants.Constants
import lorix.noslackalarm.ringing.presentation.RingingActivity
import lorix.noslackalarm.system.WakeLockManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AlarmService : Service() {

    @Inject
    lateinit var ringtonePlayer: RingtonePlayer

    @Inject
    lateinit var vibrationController: VibrationController

    @Inject
    lateinit var wakeLockManager: WakeLockManager

    private var alarmId: Long = -1L
    private var screenStateReceiver: ScreenStateReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerScreenStateReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == Constants.ACTION_SNOOZE) {
            stopAlarmInternal()
            stopSelf()
            return START_NOT_STICKY
        }

        alarmId = intent?.getLongExtra("alarm_id", -1L) ?: -1L
        if (alarmId == -1L) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 在发送前台通知前先读取模式，以便选择不带振动的通知渠道
        val mode = intent?.getStringExtra("alarm_mode") ?: "BOTH"
        val isSnooze = intent?.getBooleanExtra("is_snooze", false) ?: false

        startForeground(Constants.ALARM_NOTIFICATION_ID, buildNotification(mode))

        // 获取 60 秒唤醒锁，确保响铃完整执行
        wakeLockManager.acquire(60_000L)

        // 保存当前闹钟 id，供屏幕状态广播接收器使用
        getSharedPreferences("ringing_state", Context.MODE_PRIVATE)
            .edit()
            .putLong("current_alarm_id", alarmId)
            .apply()

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
            putExtra("alarm_id", alarmId)
            putExtra("is_snooze", isSnooze)
        }
        startActivity(ringingIntent)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        unregisterScreenStateReceiver()
        stopAlarmInternal()
        wakeLockManager.release()
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

    private fun buildNotification(mode: String = "BOTH"): Notification {
        val contentIntent = Intent(this, RingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("alarm_id", alarmId)
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
            .setContentTitle("闹钟响起中")
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
}
