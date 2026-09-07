// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import lorix.noslackalarm.data.model.AlarmEntity

@Database(
    entities = [AlarmEntity::class],
    // 3：新增 alarms.skipNextTriggerMillis（「仅关闭下一次响铃」），迁移见 DataModule.MIGRATION_2_3
    version = 3,
    exportSchema = false
)
abstract class AlarmDatabase : RoomDatabase() {

    abstract fun alarmDao(): AlarmDao
}
