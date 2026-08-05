// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common

/**
 * 主页闹钟卡片「长按删除」触发所需的按住时长档位。
 *
 * 三个档位按 UI 展示顺序排列：
 * - [FAST]:    快（左侧）
 * - [DEFAULT]: 默认（中间）
 * - [SLOW]:    慢（右侧）
 *
 * [value] 为持久化到 DataStore 的字符串键，[ms] 为对应的实际按住毫秒数（算法魔法数，
 * 集中在此而非散落业务代码，符合常量管理规则）。
 */
enum class DeleteHoldLevel(val value: String, val ms: Int, val label: String) {
    FAST("FAST", 600, "快"),
    DEFAULT("DEFAULT", 1000, "默认"),
    SLOW("SLOW", 2000, "慢");

    companion object {
        fun from(value: String?): DeleteHoldLevel =
            entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}
