// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common.util

import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** 一天的毫秒数，用于按自然日换算日期差（[startOfDay] 已抹掉时分秒，故可安全按天整除）。 */
private val DAY_MS = TimeUnit.DAYS.toMillis(1)

/** 周一 … 周日 的短标签，与闹钟列表的重复日期文案口径一致。 */
private val WEEKDAY_LABELS = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 把「被跳过的那次响铃时刻」格式化为「9月6日（明天）」这样的短标签。
 *
 * 括号内按远近依次为：今天 / 明天 / 后天 / 周几。供关闭方式弹窗的选项按钮
 * 与闹钟卡片的临时关闭提示共用，保证两处口径一致。
 *
 * @param triggerMillis 被跳过的那次响铃时刻（毫秒）
 * @param nowMillis 当前时刻（毫秒），便于测试
 */
fun formatSkipDateLabel(triggerMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = triggerMillis }
    val datePart = String.format(
        Locale.getDefault(),
        "%d月%d日",
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    val relativePart = when (dayDifference(triggerMillis, nowMillis)) {
        0 -> "今天"
        1 -> "明天"
        2 -> "后天"
        else -> weekdayLabel(triggerMillis)
    }
    return "$datePart（$relativePart）"
}

/** 两个时刻相差的自然日天数（今天=0，明天=1），按各自所在日的 0 点相减。 */
private fun dayDifference(targetMillis: Long, nowMillis: Long): Int {
    // 抹掉时分秒后用一天毫秒数整除；四舍五入以吸收时钟/时区的亚日级偏差
    val diff = startOfDay(targetMillis) - startOfDay(nowMillis)
    return (diff.toDouble() / DAY_MS).roundToInt()
}

/** 返回某个时刻当天 0 点的毫秒值。 */
private fun startOfDay(millis: Long): Long {
    return Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/** 某个时刻对应的「周X」短标签。 */
private fun weekdayLabel(millis: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = millis }
    // Calendar.MONDAY=2 … SUNDAY=1，平移到 0=周一 … 6=周日
    val index = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    return WEEKDAY_LABELS[index]
}
