// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common.util

import lorix.noslackalarm.domain.model.Alarm
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 闹钟「下次响铃 / 上次响铃」的绝对毫秒计算，作为 UI 展示与 [lorix.noslackalarm.system.AlarmScheduler]
 * 调度逻辑共享的单一真相源，避免两处各自实现导致逻辑漂移。
 *
 * 位掩码约定（与 AlarmScheduler 一致）：周一 = 1(1<<0)，周二 = 2(1<<1)，…，周日 = 64(1<<6)。
 */
private const val DAY_MS = 24 * 60 * 60 * 1000L

/** 日期格式（yyyy-MM-dd），与 [Alarm.scheduledDate] 及数据层过期清理 SQL 的字符串比较口径一致。 */
private const val DATE_PATTERN = "yyyy-MM-dd"

/**
 * 从 [fromMillis] 起的下次响铃时刻（毫秒），不感知「跳过一次」。
 *
 * - 仅一次闹钟（repeatDays == 0）：若今日时刻已过则顺延至明日。
 * - 重复闹钟：从基准日向后 1~7 天找第一个命中日。
 *
 * 抽出基准时间参数，是为了让 [nextRingTimeMillis] 能以「被跳过的那次响铃时刻」为基准再算一次。
 */
private fun nextRingAfter(alarm: Alarm, fromMillis: Long): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = fromMillis
        set(Calendar.HOUR_OF_DAY, alarm.hour)
        set(Calendar.MINUTE, alarm.minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    if (alarm.repeatDays == 0) {
        if (calendar.timeInMillis <= fromMillis) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    val baseWeekday = getWeekdayBit(calendar)
    // 基准日命中且时刻未到：就是基准日
    if ((alarm.repeatDays and baseWeekday) != 0 && calendar.timeInMillis > fromMillis) {
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
 * 下次响铃时刻（毫秒），已排除被「仅关闭下一次响铃」跳过的那一次。
 *
 * - 仅一次闹钟（repeatDays == 0）：若今日时刻已过则顺延至明日。
 * - 重复闹钟：从今天向后 1~7 天找第一个命中日。
 * - 若 [Alarm.skipNextTriggerMillis] 指向的正是（或晚于）算出的候选，说明这一次已被跳过，
 *   改以该时刻为基准再算一次，顺延到再下一次。
 *
 * 「到点自动恢复」由本函数天然实现：候选时刻恒大于当前时间，一旦跳过时刻已过，
 * 候选必然大于它，跳过自动失效，无需任何后台任务或清理逻辑。
 */
fun nextRingTimeMillis(alarm: Alarm, nowMillis: Long = System.currentTimeMillis()): Long {
    val candidate = nextRingAfter(alarm, nowMillis)
    val skipped = alarm.skipNextTriggerMillis
    // 仅重复闹钟存在「跳过一次」语义：仅一次闹钟响过即失效，跳过它等价于禁用，
    // 不属于本次功能范畴，故即便残留脏值也不参与顺延。
    val skipApplies = alarm.repeatDays != 0 &&
        skipped != Alarm.NO_SKIPPED_RING &&
        candidate <= skipped
    return if (skipApplies) nextRingAfter(alarm, skipped) else candidate
}

/**
 * 仅一次闹钟的实际触发日期（yyyy-MM-dd）。
 *
 * 今日该时刻已过 → 明天，否则 → 今天。避免把「创建日期」当成触发日期，
 * 使明天才响的闹钟被误判为已过期。
 *
 * 该日期是仅一次闹钟的生命周期凭据：过期自动删除 / 自动禁用、以及调度前的
 * 「日期已过则不调度」守卫都以它为条件，故任何写入路径都必须保证它非空。
 */
fun oneTimeTriggerDate(
    hour: Int,
    minute: Int,
    nowMillis: Long = System.currentTimeMillis()
): String {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (calendar.timeInMillis <= nowMillis) {
        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }
    return SimpleDateFormat(DATE_PATTERN, Locale.getDefault()).format(calendar.time)
}

/**
 * 该闹钟是否正处于「仅关闭下一次响铃」的临时关闭状态。
 *
 * 以当前时间判定：[Alarm.skipNextTriggerMillis] 记录的被跳过时刻一过即返回 false，
 * 卡片外观与调度同时自动恢复，无需后台任务或清理逻辑。
 */
val Alarm.isSkippingNextRing: Boolean
    get() = skipNextTriggerMillis != Alarm.NO_SKIPPED_RING &&
        skipNextTriggerMillis > System.currentTimeMillis()

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
