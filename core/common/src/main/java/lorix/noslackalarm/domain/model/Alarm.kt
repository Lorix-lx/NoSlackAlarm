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
    val scheduledDate: String = "",
    /**
     * 被跳过的那次响铃时刻（毫秒）；[NO_SKIPPED_RING] 表示未跳过任何一次。
     *
     * 用于「仅关闭下一次响铃」：字段记录被跳过的那次响铃时刻，`isEnabled` 仍为 true。
     * 该时刻一过此字段即自然失效（见 [lorix.noslackalarm.common.util.nextRingTimeMillis] 与
     * UI 的派生判定），因此无需任何后台任务或清理逻辑来恢复闹钟。
     */
    val skipNextTriggerMillis: Long = NO_SKIPPED_RING
) {
    companion object {
        /** 「未跳过任何响铃」的哨兵值，见 [skipNextTriggerMillis]。 */
        const val NO_SKIPPED_RING: Long = 0L
    }
}