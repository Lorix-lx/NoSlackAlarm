// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.data.repository

import lorix.noslackalarm.data.model.AlarmEntity
import lorix.noslackalarm.domain.model.Alarm
import lorix.noslackalarm.domain.model.AlarmMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmMappingTest {

    @Test
    fun `entity to domain mapping is correct`() {
        val entity = AlarmEntity(
            id = 1,
            hour = 7,
            minute = 30,
            repeatDays = 31,
            isEnabled = true,
            mode = "BOTH",
            ringtoneUri = null,
            label = "测试",
            snoozeCount = 2,
            deleteAfterRing = true,
            pendingDelete = false
        )
        val domain = entity.toDomain()
        assertEquals(1L, domain.id)
        assertEquals(7, domain.hour)
        assertEquals(30, domain.minute)
        assertEquals(31, domain.repeatDays)
        assertEquals(true, domain.isEnabled)
        assertEquals(AlarmMode.BOTH, domain.mode)
        assertEquals("测试", domain.label)
        assertEquals(2, domain.snoozeCount)
        assertEquals(true, domain.deleteAfterRing)
        assertEquals(false, domain.pendingDelete)
    }

    @Test
    fun `domain to entity mapping is correct`() {
        val domain = Alarm(
            id = 1,
            hour = 7,
            minute = 30,
            repeatDays = 31,
            isEnabled = true,
            mode = AlarmMode.VIBRATE_ONLY,
            ringtoneUri = null,
            label = "测试",
            snoozeCount = 0,
            deleteAfterRing = true,
            pendingDelete = true
        )
        val entity = domain.toEntity()
        assertEquals(1L, entity.id)
        assertEquals("VIBRATE_ONLY", entity.mode)
        assertEquals(true, entity.deleteAfterRing)
        assertEquals(true, entity.pendingDelete)
    }
}
