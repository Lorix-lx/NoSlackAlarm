// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.data.repository

import lorix.noslackalarm.data.local.SettingsDataStore
import lorix.noslackalarm.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: SettingsDataStore
) : SettingsRepository {

    override suspend fun warmUp() {
        dataStore.warmUp()
    }

    override val defaultMode: Flow<String> = dataStore.defaultMode

    override suspend fun setDefaultMode(mode: String) {
        dataStore.setDefaultMode(mode)
    }

    override val maxSnoozeCount: Flow<Int> = dataStore.maxSnoozeCount

    override suspend fun setMaxSnoozeCount(count: Int) {
        dataStore.setMaxSnoozeCount(count)
    }

    override val hideFromRecents: Flow<Boolean> = dataStore.hideFromRecents

    override suspend fun setHideFromRecents(hide: Boolean) {
        dataStore.setHideFromRecents(hide)
    }

    override val showDebugButton: Flow<Boolean> = dataStore.showDebugButton

    override suspend fun setShowDebugButton(show: Boolean) {
        dataStore.setShowDebugButton(show)
    }

    override val nightStartMin: Flow<Int> = dataStore.nightStartMin

    override suspend fun setNightStartMin(min: Int) {
        dataStore.setNightStartMin(min)
    }

    override val nightEndMin: Flow<Int> = dataStore.nightEndMin

    override suspend fun setNightEndMin(min: Int) {
        dataStore.setNightEndMin(min)
    }

    override val nightFixedHour: Flow<Int> = dataStore.nightFixedHour

    override suspend fun setNightFixedHour(hour: Int) {
        dataStore.setNightFixedHour(hour)
    }

    override val nightFixedMinute: Flow<Int> = dataStore.nightFixedMinute

    override suspend fun setNightFixedMinute(minute: Int) {
        dataStore.setNightFixedMinute(minute)
    }

    override val keyboardLayout: Flow<String> = dataStore.keyboardLayout

    override suspend fun setKeyboardLayout(layout: String) {
        dataStore.setKeyboardLayout(layout)
    }

    override val themeMode: Flow<String> = dataStore.themeMode

    override suspend fun setThemeMode(mode: String) {
        dataStore.setThemeMode(mode)
    }

    override val deleteHoldLevel: Flow<String> = dataStore.deleteHoldLevel

    override suspend fun setDeleteHoldLevel(level: String) {
        dataStore.setDeleteHoldLevel(level)
    }

    override val acknowledgedPermissions: Flow<Set<String>> = dataStore.acknowledgedPermissions

    override suspend fun acknowledgePermission(key: String) {
        dataStore.acknowledgePermission(key)
    }

    override suspend fun revokeAcknowledgedPermission(key: String) {
        dataStore.revokeAcknowledgedPermission(key)
    }

    override val navBackStack: Flow<String> = dataStore.navBackStack

    override suspend fun setNavBackStack(json: String) {
        dataStore.setNavBackStack(json)
    }
}