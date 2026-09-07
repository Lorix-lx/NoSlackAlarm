// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import lorix.noslackalarm.data.model.AlarmEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    @Query("SELECT * FROM alarms ORDER BY hour, minute ASC")
    fun getAllAlarms(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmById(id: Long): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE isEnabled = 1")
    fun getEnabledAlarms(): Flow<List<AlarmEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: AlarmEntity): Long

    @Update
    suspend fun updateAlarm(alarm: AlarmEntity)

    @Delete
    suspend fun deleteAlarm(alarm: AlarmEntity)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteAlarmById(id: Long)

    @Query("UPDATE alarms SET snoozeCount = 0")
    suspend fun resetAllSnoozeCounts()

    @Query("DELETE FROM alarms WHERE repeatDays = 0 AND scheduledDate != '' AND scheduledDate < :today")
    suspend fun deleteExpiredOneTimeAlarms(today: String)

    /**
     * 删除所有「答题关闭后待删除」的闹钟（pendingDelete = 1 且已停用）。
     * 供下次打开应用、展示主页面列表前统一清理；isEnabled = 0 兜底防误删被重新启用的闹钟。
     */
    @Query("DELETE FROM alarms WHERE pendingDelete = 1 AND isEnabled = 0")
    suspend fun deletePendingDeleteAlarms()

    @Query("SELECT * FROM alarms WHERE repeatDays = 0 AND scheduledDate = :today AND isEnabled = 1")
    suspend fun getTodayOneTimeAlarms(today: String): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE repeatDays = 0 AND scheduledDate != '' AND isEnabled = 1")
    suspend fun getEnabledOneTimeAlarms(): List<AlarmEntity>

    /**
     * 一次性禁用所有已过时的仅一次闹钟。
     * 条件：日期已过 OR (日期相同但小时/分钟已过)
     * 相比 SELECT + 循环 UPDATE，单条 SQL 零延迟。
     */
    @Query("""
        UPDATE alarms SET isEnabled = 0
        WHERE repeatDays = 0 AND scheduledDate != '' AND isEnabled = 1
        AND (scheduledDate < :today
             OR (scheduledDate = :today AND (hour < :nowHour OR (hour = :nowHour AND minute <= :nowMinute))))
    """)
    suspend fun disableExpiredOneTimeAlarms(today: String, nowHour: Int, nowMinute: Int)
}
