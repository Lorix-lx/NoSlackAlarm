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
    val skipNextTriggerMillis: Long = 0,
    /**
     * 「响铃后删除此闹钟」选项（默认关，仅一次闹钟生效）；
     * 与领域模型 [lorix.noslackalarm.domain.model.Alarm] 的 deleteAfterRing 对应。
     */
    val deleteAfterRing: Boolean = false,
    /**
     * 「答题关闭后待删除」标记：响铃答题关闭后置位并停用，下次打开应用展示列表前统一删除；
     * 与领域模型 [lorix.noslackalarm.domain.model.Alarm] 的 pendingDelete 对应。
     */
    val pendingDelete: Boolean = false
)
