// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.ringing.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import lorix.noslackalarm.domain.repository.AlarmRepository
import lorix.noslackalarm.system.AlarmScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AlarmReceiverEntryPoint {
        fun alarmRepository(): AlarmRepository
        fun alarmScheduler(): AlarmScheduler
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra("alarm_id", -1L)
        if (alarmId == -1L) return

        val isSnooze = intent.getBooleanExtra("is_snooze", false)
        val mode = intent.getStringExtra("alarm_mode") ?: "BOTH"

        // 立即启动前台服务（必须在 goAsync 之前完成，不阻塞主线程）
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("alarm_id", alarmId)
            putExtra("is_snooze", isSnooze)
            putExtra("alarm_mode", mode)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // 重复闹钟：重排下次触发（仅一次闹钟的禁用由 RingingViewModel 在解题完成时处理）
        if (!isSnooze) {
            val pendingResult = goAsync()
            val entryPoint = EntryPointAccessors.fromApplication(
                context,
                AlarmReceiverEntryPoint::class.java
            )
            val alarmRepository = entryPoint.alarmRepository()
            val alarmScheduler = entryPoint.alarmScheduler()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val alarm = alarmRepository.getAlarmById(alarmId)
                    if (alarm != null && alarm.repeatDays != 0) {
                        alarmScheduler.scheduleAlarm(alarm)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
