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
    val skipNextTriggerMillis: Long = NO_SKIPPED_RING,
    /**
     * 「响铃后删除此闹钟」选项，仅对仅一次闹钟（repeatDays == 0）生效，默认关闭。
     *
     * 开启后，闹钟响铃并答题关闭时不会立即删除：响铃流程会把该闹钟停用并置
     * [pendingDelete] = true，待下次打开应用、进入主页面之前统一删除，
     * 避免删除发生在响铃事务中或在主页面“突然消失”。重复闹钟永远不会被自动删除。
     */
    val deleteAfterRing: Boolean = false,
    /**
     * 「答题关闭后待删除」标记（仅一次闹钟 + [deleteAfterRing] 时由响铃流程置位，不直接删除）。
     *
     * 置位时该闹钟同时被停用；下次打开应用展示主页面之前会批量删除带此标记的闹钟。
     * 若用户在删除前重新启用或编辑保存该闹钟，相应路径会清除此标记并视为取消删除。
     */
    val pendingDelete: Boolean = false
) {
    companion object {
        /** 「未跳过任何响铃」的哨兵值，见 [skipNextTriggerMillis]。 */
        const val NO_SKIPPED_RING: Long = 0L
    }
}