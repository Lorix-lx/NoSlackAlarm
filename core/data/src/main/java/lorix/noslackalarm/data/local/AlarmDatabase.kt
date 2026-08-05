// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import lorix.noslackalarm.data.model.AlarmEntity

@Database(
    entities = [AlarmEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AlarmDatabase : RoomDatabase() {

    abstract fun alarmDao(): AlarmDao
}
