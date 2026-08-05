// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.data.repository

import lorix.noslackalarm.data.local.AlarmDao
import lorix.noslackalarm.data.model.AlarmEntity
import lorix.noslackalarm.domain.model.Alarm
import lorix.noslackalarm.domain.model.AlarmMode
import lorix.noslackalarm.domain.repository.AlarmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRepositoryImpl @Inject constructor(
    private val alarmDao: AlarmDao
) : AlarmRepository {

    override fun getAllAlarms(): Flow<List<Alarm>> {
        return alarmDao.getAllAlarms().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getAlarmById(id: Long): Alarm? {
        return alarmDao.getAlarmById(id)?.toDomain()
    }

    override fun getEnabledAlarms(): Flow<List<Alarm>> {
        return alarmDao.getEnabledAlarms().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun insertAlarm(alarm: Alarm): Long {
        return alarmDao.insertAlarm(alarm.toEntity())
    }

    override suspend fun updateAlarm(alarm: Alarm) {
        alarmDao.updateAlarm(alarm.toEntity())
    }

    override suspend fun deleteAlarm(alarm: Alarm) {
        alarmDao.deleteAlarm(alarm.toEntity())
    }

    override suspend fun deleteAlarmById(id: Long) {
        alarmDao.deleteAlarmById(id)
    }

    override suspend fun resetAllSnoozeCounts() {
        alarmDao.resetAllSnoozeCounts()
    }

    override suspend fun deleteExpiredOneTimeAlarms(today: String) {
        alarmDao.deleteExpiredOneTimeAlarms(today)
    }

    override suspend fun getTodayOneTimeAlarms(today: String): List<Alarm> {
        return alarmDao.getTodayOneTimeAlarms(today).map { it.toDomain() }
    }

    override suspend fun getEnabledOneTimeAlarms(): List<Alarm> {
        return alarmDao.getEnabledOneTimeAlarms().map { it.toDomain() }
    }

    override suspend fun disableExpiredOneTimeAlarms(today: String, nowHour: Int, nowMinute: Int) {
        alarmDao.disableExpiredOneTimeAlarms(today, nowHour, nowMinute)
    }
}

fun AlarmEntity.toDomain(): Alarm = Alarm(
    id = id,
    hour = hour,
    minute = minute,
    repeatDays = repeatDays,
    isEnabled = isEnabled,
    mode = AlarmMode.valueOf(mode),
    ringtoneUri = ringtoneUri,
    label = label,
    snoozeCount = snoozeCount,
    scheduledDate = scheduledDate
)

fun Alarm.toEntity(): AlarmEntity = AlarmEntity(
    id = id,
    hour = hour,
    minute = minute,
    repeatDays = repeatDays,
    isEnabled = isEnabled,
    mode = mode.name,
    ringtoneUri = ringtoneUri,
    label = label,
    snoozeCount = snoozeCount,
    scheduledDate = scheduledDate
)
