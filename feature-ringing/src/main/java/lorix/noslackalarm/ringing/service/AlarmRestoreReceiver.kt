// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.ringing.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import lorix.noslackalarm.domain.model.Alarm
import lorix.noslackalarm.domain.repository.AlarmRepository
import lorix.noslackalarm.system.AlarmScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AlarmRestoreReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AlarmRestoreEntryPoint {
        fun alarmRepository(): AlarmRepository
        fun alarmScheduler(): AlarmScheduler
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            val entryPoint = EntryPointAccessors.fromApplication(
                context,
                AlarmRestoreEntryPoint::class.java
            )
            val alarmRepository = entryPoint.alarmRepository()
            val alarmScheduler = entryPoint.alarmScheduler()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val alarms = alarmRepository.getEnabledAlarms().first()
                    alarms.forEach { alarm: Alarm ->
                        alarmScheduler.scheduleAlarm(alarm)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
