// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Alarm(
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val repeatDays: Int,
    val isEnabled: Boolean,
    val mode: AlarmMode,
    val ringtoneUri: String? = null,
    val label: String? = null,
    val snoozeCount: Int = 0,
    val scheduledDate: String = ""
)