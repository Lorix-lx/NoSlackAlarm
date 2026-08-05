// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.alarm.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class WheelTimeConversionTest {

    @Test
    fun `to24HourFrom12 handles midnight and noon`() {
        // 12 AM -> 0
        assertEquals(0, to24HourFrom12(12, false))
        // 12 PM -> 12
        assertEquals(12, to24HourFrom12(12, true))
    }

    @Test
    fun `to24HourFrom12 general cases`() {
        // 1 PM -> 13
        assertEquals(13, to24HourFrom12(1, true))
        // 1 AM -> 1
        assertEquals(1, to24HourFrom12(1, false))
        // 11 PM -> 23
        assertEquals(23, to24HourFrom12(11, true))
    }

    @Test
    fun `from24HourTo12 roundtrip`() {
        // test some values
        val cases = listOf(0, 1, 11, 12, 13, 23)
        for (h in cases) {
            val (h12, isPm) = from24HourTo12(h)
            val back = to24HourFrom12(h12, isPm)
            assertEquals(h, back)
        }
    }
}
