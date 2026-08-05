// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.ringing.domain

import android.content.Context
import android.content.Intent
import android.widget.Toast
import lorix.noslackalarm.common.SettingsDefaults
import lorix.noslackalarm.common.constants.Constants
import lorix.noslackalarm.domain.repository.AlarmRepository
import lorix.noslackalarm.system.AlarmScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnoozeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmScheduler: AlarmScheduler,
    private val alarmRepository: AlarmRepository
) {

    fun triggerSnooze(alarmId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            val alarm = alarmRepository.getAlarmById(alarmId)
            if (alarm == null) {
                return@launch
            }

            val newCount = alarm.snoozeCount + 1
            if (newCount > SettingsDefaults.MAX_SNOOZE_COUNT) {
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        context,
                        "已达到最大贪睡次数，请答题关闭闹钟",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@launch
            }

            alarmRepository.updateAlarm(alarm.copy(snoozeCount = newCount))

            // Stop current ringing
            val stopIntent = Intent(context, lorix.noslackalarm.ringing.service.AlarmService::class.java).apply {
                action = Constants.ACTION_SNOOZE
            }
            context.startService(stopIntent)

            // Schedule next alarm
            alarmScheduler.scheduleSnooze(alarmId, Constants.DEFAULT_SNOOZE_MINUTES, alarm.mode.name)

            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(
                    context,
                    "${Constants.DEFAULT_SNOOZE_MINUTES}分钟后再次响起",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
