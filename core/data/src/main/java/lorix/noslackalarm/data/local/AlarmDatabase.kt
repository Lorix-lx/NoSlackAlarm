// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import lorix.noslackalarm.data.model.AlarmEntity

@Database(
    entities = [AlarmEntity::class],
    // 3：新增 alarms.skipNextTriggerMillis（「仅关闭下一次响铃」），迁移见 DataModule.MIGRATION_2_3
    // 4：新增 alarms.deleteAfterRing / alarms.pendingDelete（「响铃后删除此闹钟」），迁移见 DataModule.MIGRATION_3_4
    version = 4,
    exportSchema = false
)
abstract class AlarmDatabase : RoomDatabase() {

    abstract fun alarmDao(): AlarmDao
}
