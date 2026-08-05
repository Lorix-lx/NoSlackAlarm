// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common.util

import lorix.noslackalarm.domain.model.Alarm
import java.util.Calendar

/**
 * 闹钟「下次响铃 / 上次响铃」的绝对毫秒计算，作为 UI 展示与 [lorix.noslackalarm.system.AlarmScheduler]
 * 调度逻辑共享的单一真相源，避免两处各自实现导致逻辑漂移。
 *
 * 位掩码约定（与 AlarmScheduler 一致）：周一 = 1(1<<0)，周二 = 2(1<<1)，…，周日 = 64(1<<6)。
 */
private const val DAY_MS = 24 * 60 * 60 * 1000L

/**
 * 下次响铃时刻（毫秒）。
 * - 仅一次闹钟（repeatDays == 0）：若今日时刻已过则顺延至明日。
 * - 重复闹钟：从今天向后 1~7 天找第一个命中日。
 */
fun nextRingTimeMillis(alarm: Alarm): Long {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, alarm.hour)
        set(Calendar.MINUTE, alarm.minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    if (alarm.repeatDays == 0) {
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    val todayWeekday = getWeekdayBit(calendar)
    // 今天命中且时刻未到：就是今天
    if ((alarm.repeatDays and todayWeekday) != 0 && calendar.timeInMillis > System.currentTimeMillis()) {
        return calendar.timeInMillis
    }

    // 否则向后找下一个命中日
    for (i in 1..7) {
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        if ((alarm.repeatDays and getWeekdayBit(calendar)) != 0) {
            return calendar.timeInMillis
        }
    }
    // 兜底（repeatDays 非法时不应触发）
    return calendar.timeInMillis
}

/**
 * 上一次响铃时刻（毫秒），用于计算「当前等待周期已过去的比例」。
 * - 重复闹钟：从今天向前 0~7 天找最后一个 <= now 的命中日。
 * - 仅一次闹钟无历史响铃，退化为 [nextRingTimeMillis] - [DAY_MS]。
 */
fun previousRingTimeMillis(alarm: Alarm): Long {
    if (alarm.repeatDays == 0) {
        return nextRingTimeMillis(alarm) - DAY_MS
    }
    val now = System.currentTimeMillis()
    val base = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, alarm.hour)
        set(Calendar.MINUTE, alarm.minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    for (offset in 0..7) {
        val candidate = (base.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -offset)
        }
        if ((alarm.repeatDays and getWeekdayBit(candidate)) != 0 && candidate.timeInMillis <= now) {
            return candidate.timeInMillis
        }
    }
    return base.timeInMillis - DAY_MS
}

/**
 * 将 Calendar 的 DAY_OF_WEEK 转为项目位掩码：周一=1 … 周日=64。
 */
private fun getWeekdayBit(calendar: Calendar): Int {
    return when (calendar.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 4
        Calendar.THURSDAY -> 8
        Calendar.FRIDAY -> 16
        Calendar.SATURDAY -> 32
        Calendar.SUNDAY -> 64
        else -> 0
    }
}
