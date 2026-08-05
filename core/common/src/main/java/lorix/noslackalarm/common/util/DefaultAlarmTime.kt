// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common.util

import java.util.Locale

/**
 * 根据「点击新建闹钟」的时刻，推算编辑页的默认时间。
 *
 * 规则：
 * - 若当前处于用户配置的夜间区间 [nightStartMin, nightEndMin)，返回固定时间 (fixedHour, fixedMinute)；
 * - 否则返回「当前时刻的下一分钟」（点击时刻精确时分 +1 分钟，23:59 → 00:00 自动回绕）。
 *
 * 夜间区间以「当日分钟数」(0..1439) 表示。当 nightStartMin > nightEndMin 时视为跨午夜区间，
 * 例如 22:00(1320) ~ 06:00(360) 表示 22:00 至次日 06:00。
 *
 * @param nowMinuteOfDay 当前时刻，单位：当日分钟数 (0..1439)
 * @param nightStartMin 夜间区间开始（含），当日分钟数
 * @param nightEndMin 夜间区间结束（不含），当日分钟数
 * @param fixedHour 夜间默认闹钟小时
 * @param fixedMinute 夜间默认闹钟分钟
 * @return 默认时间的 (hour, minute)
 */
fun suggestDefaultAlarmTime(
    nowMinuteOfDay: Int,
    nightStartMin: Int,
    nightEndMin: Int,
    fixedHour: Int,
    fixedMinute: Int
): Pair<Int, Int> {
    val isNight = if (nightStartMin <= nightEndMin) {
        nowMinuteOfDay in nightStartMin until nightEndMin
    } else {
        nowMinuteOfDay >= nightStartMin || nowMinuteOfDay < nightEndMin
    }
    return if (isNight) {
        fixedHour to fixedMinute
    } else {
        val next = (nowMinuteOfDay + 1) % 1440
        (next / 60) to (next % 60)
    }
}

/** 将当日分钟数格式化为 "HH:mm"。 */
fun formatMinuteOfDay(min: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", min / 60, min % 60)
