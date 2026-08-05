// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.domain.repository

import lorix.noslackalarm.domain.model.Alarm
import kotlinx.coroutines.flow.Flow

interface AlarmRepository {
    fun getAllAlarms(): Flow<List<Alarm>>
    suspend fun getAlarmById(id: Long): Alarm?
    fun getEnabledAlarms(): Flow<List<Alarm>>
    suspend fun insertAlarm(alarm: Alarm): Long
    suspend fun updateAlarm(alarm: Alarm)
    suspend fun deleteAlarm(alarm: Alarm)
    suspend fun deleteAlarmById(id: Long)
    suspend fun resetAllSnoozeCounts()

    suspend fun deleteExpiredOneTimeAlarms(today: String)

    suspend fun getTodayOneTimeAlarms(today: String): List<Alarm>

    suspend fun getEnabledOneTimeAlarms(): List<Alarm>

    suspend fun disableExpiredOneTimeAlarms(today: String, nowHour: Int, nowMinute: Int)
}
