// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common.constants

object Constants {

    const val DEFAULT_SNOOZE_MINUTES = 10
    const val DOUBLE_PRESS_INTERVAL_MS = 500L
    const val MAX_SKETCHPAD_POINTS = 5000
    const val ALARM_CHANNEL_ID = "alarm_service_channel"
    const val ALARM_CHANNEL_ID_NO_VIBRATE = "alarm_service_channel_no_vibrate"
    const val ALARM_NOTIFICATION_ID = 1001
    const val ACTION_ALARM_TRIGGER = "lorix.noslackalarm.ALARM_TRIGGER"
    const val ACTION_SNOOZE = "lorix.noslackalarm.ACTION_SNOOZE"
    const val ACTION_TRIGGER_RINGING = "lorix.noslackalarm.ACTION_TRIGGER_RINGING"
    const val SKETCHPAD_ALARM_PAUSE_MS = 30_000L // 打开白板时暂停震动/闹铃的时长（仅一次），到点未解答则自动恢复
}
