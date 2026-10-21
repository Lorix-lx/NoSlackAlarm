// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.ringing.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import lorix.noslackalarm.common.constants.Constants.RingingPhase
import lorix.noslackalarm.ringing.domain.SnoozeManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 响铃期间「按电源键 = 贪睡」的实现。
 *
 * 只在 [RingingPhase.RINGING] 相位生效：答题关闭后的起床验证阶段（[RingingPhase.VERIFYING]）
 * 本身就是息屏采样，若仍按关屏触发贪睡，每晚都会凭空多出一次 10 分钟贪睡。
 */
class ScreenStateReceiver : BroadcastReceiver() {

    // 防止短时间内重复触发贪睡
    private var lastSnoozeTime: Long = 0L

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ScreenStateEntryPoint {
        fun snoozeManager(): SnoozeManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SCREEN_OFF) return
        if (RingingStateStore.phase(context) != RingingPhase.RINGING) return

        val alarmId = RingingStateStore.currentAlarmId(context)
        if (alarmId == -1L) return

        val now = System.currentTimeMillis()
        if (now - lastSnoozeTime <= 1000L) return
        lastSnoozeTime = now

        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            ScreenStateEntryPoint::class.java
        )
        entryPoint.snoozeManager().triggerSnooze(alarmId)
    }
}
