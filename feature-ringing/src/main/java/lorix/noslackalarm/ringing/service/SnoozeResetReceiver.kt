// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.ringing.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import lorix.noslackalarm.domain.repository.AlarmRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class SnoozeResetReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SnoozeResetEntryPoint {
        fun alarmRepository(): AlarmRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            SnoozeResetEntryPoint::class.java
        )
        val alarmRepository = entryPoint.alarmRepository()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                alarmRepository.resetAllSnoozeCounts()
                scheduleNextReset(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val RESET_REQUEST_CODE = 999999

        fun scheduleNextReset(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, SnoozeResetReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                RESET_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }
}
