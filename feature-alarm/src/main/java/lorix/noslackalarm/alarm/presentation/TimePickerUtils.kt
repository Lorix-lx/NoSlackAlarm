// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.alarm.presentation

/**
 * 用于滚轮和数字钟测试相关的 12/24 小时制转换辅助函数。
 */
fun to24HourFrom12(hour12: Int, isPm: Boolean): Int {
    require(hour12 in 1..12)
    return if (hour12 == 12) {
        if (isPm) 12 else 0
    } else {
        if (isPm) hour12 + 12 else hour12
    }
}

fun from24HourTo12(hour24: Int): Pair<Int, Boolean> {
    require(hour24 in 0..23)
    val isPm = hour24 >= 12
    val h = hour24 % 12
    val hour12 = if (h == 0) 12 else h
    return Pair(hour12, isPm)
}
