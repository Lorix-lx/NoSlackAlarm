// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.data.di

import android.content.Context
import androidx.room.Room
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

        @Provides
        @Singleton
        fun provideAlarmDatabase(@ApplicationContext context: Context): AlarmDatabase {
            return Room.databaseBuilder(
                context,
                AlarmDatabase::class.java,
                "alarm_database.db"
            )
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