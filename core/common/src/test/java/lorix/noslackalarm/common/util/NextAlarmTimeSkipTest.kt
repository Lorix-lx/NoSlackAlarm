// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common.util

import lorix.noslackalarm.domain.model.Alarm
import lorix.noslackalarm.domain.model.AlarmMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * 「仅关闭下一次响铃」的时间计算与文案测试。
 *
 * 覆盖：跳过生效时顺延到再下一次、跳过时刻已过后自动失效、按周几重复的顺延，
 * 以及日期文案的 今天 / 明天 / 后天 / 周几 四档。
 */
class NextAlarmTimeSkipTest {

    private companion object {
        /** 位掩码：周一=1 … 周日=64，与项目约定一致。 */
        const val MONDAY = 1
        const val ALL_DAYS = 127

        /** 一天的毫秒数，用于把基准时间推进到次日。 */
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }

    private fun alarm(repeatDays: Int, skip: Long = Alarm.NO_SKIPPED_RING): Alarm {
        return Alarm(
            hour = 7,
            minute = 30,
            repeatDays = repeatDays,
            isEnabled = true,
            mode = AlarmMode.BOTH,
            skipNextTriggerMillis = skip
        )
    }

    /** 构造指定「年/月/日 时:分」的本地时间戳。 */
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** 从 [from] 当天起（含当天）往后找第一个目标星期几，并返回该日 [hour]:[minute]。 */
    private fun nextWeekdayAt(
        from: Long,
        weekday: Int,
        hour: Int,
        minute: Int
    ): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        while (cal.get(Calendar.DAY_OF_WEEK) != weekday) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    @Test
    fun `每天闹钟 跳过今天这次则顺延到明天`() {
        val now = at(2026, 9, 5, 6, 0)
        val todayRing = at(2026, 9, 5, 7, 30)
        val tomorrowRing = at(2026, 9, 6, 7, 30)

        assertEquals(todayRing, nextRingTimeMillis(alarm(ALL_DAYS), now))
        assertEquals(tomorrowRing, nextRingTimeMillis(alarm(ALL_DAYS, skip = todayRing), now))
    }

    @Test
    fun `跳过时刻已过则跳过自动失效`() {
        // now 已是当天 8:00，被跳过的是当天 7:30（早已过去）
        val now = at(2026, 9, 5, 8, 0)
        val passedRing = at(2026, 9, 5, 7, 30)
        val tomorrowRing = at(2026, 9, 6, 7, 30)

        assertEquals(
            tomorrowRing,
            nextRingTimeMillis(alarm(ALL_DAYS, skip = passedRing), now)
        )
    }

    @Test
    fun `仅周一闹钟 跳过本周一则顺延到下周一`() {
        val now = at(2026, 9, 5, 6, 0) // 周六
        val thisMonday = nextWeekdayAt(now, Calendar.MONDAY, 7, 30)
        val nextMonday = nextWeekdayAt(thisMonday + DAY_MS, Calendar.MONDAY, 7, 30)

        assertEquals(thisMonday, nextRingTimeMillis(alarm(MONDAY), now))
        assertEquals(nextMonday, nextRingTimeMillis(alarm(MONDAY, skip = thisMonday), now))
    }

    @Test
    fun `仅一次闹钟不参与跳过 即便残留跳过值`() {
        // 仅一次闹钟不弹关闭方式弹窗，跳过语义对无意义的它不生效，
        // 即便数据库残留了跳过值也应维持原有「今日已过顺延明日」行为
        val now = at(2026, 9, 5, 6, 0)
        assertEquals(
            at(2026, 9, 5, 7, 30),
            nextRingTimeMillis(alarm(repeatDays = 0, skip = at(2026, 9, 6, 7, 30)), now)
        )
    }

    @Test
    fun `跳过态标志随当前时间自动失效`() {
        val now = at(2026, 9, 5, 6, 0)
        val ring = at(2026, 9, 5, 7, 30)
        val alarm = alarm(ALL_DAYS, skip = ring)

        assertEquals(true, alarm.isSkipping(now))
        assertEquals(false, alarm.isSkipping(ring))
        assertEquals(false, alarm.isSkipping(ring + 1))
        assertEquals(false, alarm(ALL_DAYS).isSkipping(now))
    }

    @Test
    fun `日期文案区分 今天 明天 后天 周几`() {
        val now = at(2026, 9, 5, 6, 0)
        val weekdayLabels = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

        fun label(dayOffset: Int): String {
            val target = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }
            return formatSkipDateLabel(target.timeInMillis, now)
        }

        assertEquals("9月5日（今天）", label(0))
        assertEquals("9月6日（明天）", label(1))
        assertEquals("9月7日（后天）", label(2))
        // 3 天及以后降级为「周几」
        val thirdDay = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 3)
        }
        val expected = weekdayLabels[(thirdDay.get(Calendar.DAY_OF_WEEK) - 1) % 7]
        assertEquals("9月8日（$expected）", label(3))
    }

    /** 用指定「当前时间」判断闹钟是否处于跳过态（测试内避免依赖真实时钟）。 */
    private fun Alarm.isSkipping(nowMillis: Long): Boolean {
        return skipNextTriggerMillis != Alarm.NO_SKIPPED_RING && skipNextTriggerMillis > nowMillis
    }
}
