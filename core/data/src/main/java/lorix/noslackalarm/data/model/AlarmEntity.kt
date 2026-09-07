// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val repeatDays: Int,
    val isEnabled: Boolean,
    val mode: String,
    val ringtoneUri: String?,
    val label: String?,
    val snoozeCount: Int = 0,
    val scheduledDate: String = "",  // yyyy-MM-dd
    /** 被跳过的那次响铃时刻(ms)，0 = 未跳过；与领域模型 [lorix.noslackalarm.domain.model.Alarm] 对应。 */
    val skipNextTriggerMillis: Long = 0
)
