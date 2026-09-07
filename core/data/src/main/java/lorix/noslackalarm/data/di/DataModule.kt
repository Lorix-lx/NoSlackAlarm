// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import lorix.noslackalarm.data.local.AlarmDatabase
import lorix.noslackalarm.data.local.AlarmDao
import lorix.noslackalarm.data.repository.AlarmRepositoryImpl
import lorix.noslackalarm.data.repository.SettingsRepositoryImpl
import lorix.noslackalarm.domain.repository.AlarmRepository
import lorix.noslackalarm.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    companion object {

        /**
         * 2 → 3：为「仅关闭下一次响铃」新增 skipNextTriggerMillis 列。
         *
         * 必须显式提供迁移：下方构造器带 `fallbackToDestructiveMigration(dropAllTables = true)`，
         * 缺迁移时 Room 会直接清空 alarms 表，导致用户已有闹钟全部丢失。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE alarms ADD COLUMN skipNextTriggerMillis INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * 3 → 4：为「响铃后删除此闹钟」新增 deleteAfterRing（选项）与 pendingDelete（答题关闭后待删除标记）两列。
         *
         * 必须显式提供迁移：下方构造器带 `fallbackToDestructiveMigration(dropAllTables = true)`，
         * 缺迁移时 Room 会直接清空 alarms 表，导致用户已有闹钟全部丢失。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE alarms ADD COLUMN deleteAfterRing INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE alarms ADD COLUMN pendingDelete INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        @Provides
        @Singleton
        fun provideAlarmDatabase(@ApplicationContext context: Context): AlarmDatabase {
            return Room.databaseBuilder(
                context,
                AlarmDatabase::class.java,
                "alarm_database.db"
            )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }

        @Provides
        @Singleton
        fun provideAlarmDao(database: AlarmDatabase): AlarmDao {
            return database.alarmDao()
        }

        @Provides
        @Singleton
        fun provideAlarmRepository(impl: AlarmRepositoryImpl): AlarmRepository {
            return impl
        }
    }
}