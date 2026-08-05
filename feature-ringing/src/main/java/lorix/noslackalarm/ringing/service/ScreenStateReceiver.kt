// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.ringing.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import lorix.noslackalarm.ringing.domain.SnoozeManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class ScreenStateReceiver : BroadcastReceiver() {

    // 防止短时间内重复触发贪睡
    private var lastSnoozeTime: Long = 0L

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ScreenStateEntryPoint {
        fun snoozeManager(): SnoozeManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_OFF) {
            val alarmId = getCurrentAlarmId(context)
            if (alarmId != -1L) {
                val now = System.currentTimeMillis()
                if (now - lastSnoozeTime > 1000L) {
                    lastSnoozeTime = now
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context,
                        ScreenStateEntryPoint::class.java
                    )
                    entryPoint.snoozeManager().triggerSnooze(alarmId)
                }
            }
        }
    }

    private fun getCurrentAlarmId(context: Context): Long {
        val prefs = context.getSharedPreferences("ringing_state", Context.MODE_PRIVATE)
        return prefs.getLong("current_alarm_id", -1L)
    }
}
