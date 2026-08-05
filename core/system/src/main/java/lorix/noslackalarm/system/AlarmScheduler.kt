// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.system

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import lorix.noslackalarm.domain.model.Alarm
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager
) {

    companion object {
        private const val SNOOZE_REQUEST_CODE_OFFSET = 100000
    }

    /**
     * 使用 AlarmManager.setAlarmClock() 安排闹钟。
     *
     */
    fun scheduleAlarm(alarm: Alarm) {
        // 仅一次闹钟：预设日期已过则跳过调度
        // 解决锁屏响铃后无法禁用闹钟，导致下次/开机后被重新调度的问题
        if (alarm.repeatDays == 0 && alarm.scheduledDate.isNotEmpty()) {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            if (alarm.scheduledDate < today) {
                return
            }
        }

        val triggerTime = calculateNextAlarmTime(alarm)

        // fireIntent: 闹钟触发时发送广播启动 AlarmService
        val fireIntent = Intent("lorix.noslackalarm.ALARM_TRIGGER").apply {
            setPackage(context.packageName)
            putExtra("alarm_id", alarm.id)
            putExtra("alarm_mode", alarm.mode.name)
        }
        val firePendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            fireIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // showIntent: 用户点击状态栏闹钟图标时打开应用
        val showIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("alarm_id", alarm.id)
                putExtra("alarm_mode", alarm.mode.name)
            }
        val showPendingIntent = showIntent?.let {
            PendingIntent.getActivity(
                context,
                alarm.id.toInt() + 200000,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        alarmManager.setAlarmClock(
            AlarmClockInfo(triggerTime, showPendingIntent),
            firePendingIntent
        )
    }

    fun cancelAlarm(alarmId: Long) {
        val intent = Intent("lorix.noslackalarm.ALARM_TRIGGER").apply {
            setPackage(context.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()

        
        val snoozeIntent = Intent("lorix.noslackalarm.ALARM_TRIGGER").apply {
            setPackage(context.packageName)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt() + SNOOZE_REQUEST_CODE_OFFSET,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(snoozePendingIntent)
        snoozePendingIntent.cancel()

        
        val showIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val showPendingIntent = showIntent?.let {
            PendingIntent.getActivity(
                context,
                alarmId.toInt() + 200000,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        showPendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    fun cancelSnooze(alarmId: Long) {
        val snoozeIntent = Intent("lorix.noslackalarm.ALARM_TRIGGER").apply {
            setPackage(context.packageName)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt() + SNOOZE_REQUEST_CODE_OFFSET,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(snoozePendingIntent)
        snoozePendingIntent.cancel()
    }

    fun scheduleSnooze(alarmId: Long, minutes: Int, mode: String) {
        val fireIntent = Intent("lorix.noslackalarm.ALARM_TRIGGER").apply {
            setPackage(context.packageName)
            putExtra("alarm_id", alarmId)
            putExtra("is_snooze", true)
            putExtra("alarm_mode", mode)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt() + SNOOZE_REQUEST_CODE_OFFSET,
            fireIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + minutes * 60 * 1000

        // 贪睡也用 setAlarmClock，确保准时触发
        val showIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("alarm_id", alarmId)
                putExtra("is_snooze", true)
                putExtra("alarm_mode", mode)
            }
        val showPendingIntent = showIntent?.let {
            PendingIntent.getActivity(
                context,
                alarmId.toInt() + SNOOZE_REQUEST_CODE_OFFSET + 200000,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        alarmManager.setAlarmClock(
            AlarmClockInfo(triggerTime, showPendingIntent),
            pendingIntent
        )
    }

    private fun calculateNextAlarmTime(alarm: Alarm): Long {
        // 复用公共工具，与 UI 倒计时共享同一份「下次响铃」算法（单一真相源）
        return lorix.noslackalarm.common.util.nextRingTimeMillis(alarm)
    }
}
