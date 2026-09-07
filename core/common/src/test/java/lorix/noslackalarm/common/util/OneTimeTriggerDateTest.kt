// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * 仅一次闹钟触发日期的计算测试。
 *
 * 该日期是仅一次闹钟的生命周期凭据（过期自动删除 / 自动禁用 / 调度守卫都以它为条件），
 * 一旦算错会导致过期闹钟无法清理或被重新调度，故在此锁定边界行为。
 */
class OneTimeTriggerDateTest {

    /** 构造指定「年/月/日 时:分」的本地时间戳。 */
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `今日时刻未过则触发日为今天`() {
        assertEquals("2026-09-05", oneTimeTriggerDate(7, 30, at(2026, 9, 5, 6, 0)))
    }

    @Test
    fun `今日时刻已过则触发日为明天`() {
        assertEquals("2026-09-06", oneTimeTriggerDate(7, 30, at(2026, 9, 5, 8, 0)))
    }

    @Test
    fun `时刻恰好等于当前则顺延到明天`() {
        // 边界：<= now 视为已过，与调度逻辑「响铃时刻必须严格晚于当前」保持一致
        assertEquals("2026-09-06", oneTimeTriggerDate(7, 30, at(2026, 9, 5, 7, 30)))
    }

    @Test
    fun `跨月边界顺延正确`() {
        assertEquals("2026-10-01", oneTimeTriggerDate(7, 30, at(2026, 9, 30, 8, 0)))
    }

    @Test
    fun `跨年边界顺延正确`() {
        assertEquals("2027-01-01", oneTimeTriggerDate(22, 0, at(2026, 12, 31, 23, 0)))
    }

    @Test
    fun `触发日与下次响铃时刻落在同一天`() {
        // 保证 scheduledDate 与 nextRingTimeMillis 对「仅一次闹钟何时响」的判断不会互相打架
        val now = at(2026, 9, 5, 6, 0)
        val ring = nextRingTimeMillis(
            lorix.noslackalarm.domain.model.Alarm(
                hour = 7,
                minute = 30,
                repeatDays = 0,
                isEnabled = true,
                mode = lorix.noslackalarm.domain.model.AlarmMode.BOTH
            ),
            now
        )
        assertEquals(oneTimeTriggerDate(7, 30, now), formatDate(ring))
    }

    private fun formatDate(millis: Long): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date(millis))
    }
}
