// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import lorix.noslackalarm.common.SettingsDefaults
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore = context.dataStore

    val defaultMode: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_MODE] ?: SettingsDefaults.DEFAULT_MODE
    }

    suspend fun setDefaultMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_MODE] = mode
        }
    }

    val maxSnoozeCount: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_MAX_SNOOZE_COUNT] ?: SettingsDefaults.MAX_SNOOZE_COUNT
    }

    suspend fun setMaxSnoozeCount(count: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_MAX_SNOOZE_COUNT] = count
        }
    }

    // 是否从最近任务中隐藏应用
    val hideFromRecents: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_HIDE_FROM_RECENTS] ?: SettingsDefaults.HIDE_FROM_RECENTS
    }

    suspend fun setHideFromRecents(hide: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_HIDE_FROM_RECENTS] = hide
        }
    }

    // 是否在下拉菜单显示「调试」入口
    val showDebugButton: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SHOW_DEBUG_BUTTON] ?: SettingsDefaults.SHOW_DEBUG_BUTTON
    }

    suspend fun setShowDebugButton(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_DEBUG_BUTTON] = show
        }
    }

    // 新建闹钟智能默认时间：夜间区间（当日分钟数）与夜间固定默认时间
    val nightStartMin: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_NIGHT_START_MIN] ?: SettingsDefaults.NIGHT_START_MIN
    }

    suspend fun setNightStartMin(min: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_NIGHT_START_MIN] = min
        }
    }

    val nightEndMin: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_NIGHT_END_MIN] ?: SettingsDefaults.NIGHT_END_MIN
    }

    suspend fun setNightEndMin(min: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_NIGHT_END_MIN] = min
        }
    }

    val nightFixedHour: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_NIGHT_FIXED_HOUR] ?: SettingsDefaults.NIGHT_FIXED_HOUR
    }

    suspend fun setNightFixedHour(hour: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_NIGHT_FIXED_HOUR] = hour
        }
    }

    val nightFixedMinute: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_NIGHT_FIXED_MIN] ?: SettingsDefaults.NIGHT_FIXED_MIN
    }

    suspend fun setNightFixedMinute(minute: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_NIGHT_FIXED_MIN] = minute
        }
    }

    // 答题键盘布局：PHONE(123/456/789) 或 CALCULATOR(789/456/123)
    val keyboardLayout: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_KEYBOARD_LAYOUT] ?: SettingsDefaults.KEYBOARD_LAYOUT
    }

    suspend fun setKeyboardLayout(layout: String) {
        dataStore.edit { prefs ->
            prefs[KEY_KEYBOARD_LAYOUT] = layout
        }
    }

    // 主题模式：system/light/dark
    val themeMode: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: SettingsDefaults.THEME_MODE
    }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode
        }
    }

    // 主页卡片长按删除时长档位：fast/default/slow
    val deleteHoldLevel: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_DELETE_HOLD_LEVEL] ?: SettingsDefaults.DELETE_HOLD_LEVEL
    }

    suspend fun setDeleteHoldLevel(level: String) {
        dataStore.edit { prefs ->
            prefs[KEY_DELETE_HOLD_LEVEL] = level
        }
    }

    // 用户手动确认「已开启」的权限集合（无标准检测 API 的权限，如自启动 / 锁屏显示）
    val acknowledgedPermissions: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[KEY_ACKNOWLEDGED_PERMISSIONS] ?: SettingsDefaults.ACKNOWLEDGED_PERMISSIONS
    }

    suspend fun acknowledgePermission(key: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_ACKNOWLEDGED_PERMISSIONS] ?: emptySet()
            prefs[KEY_ACKNOWLEDGED_PERMISSIONS] = current + key
        }
    }

    suspend fun revokeAcknowledgedPermission(key: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_ACKNOWLEDGED_PERMISSIONS] ?: emptySet()
            prefs[KEY_ACKNOWLEDGED_PERMISSIONS] = current - key
        }
    }

    // 导航返回栈持久化（List<Screen> 的 JSON 字符串）；空串表示尚无数据。
    val navBackStack: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_NAV_BACK_STACK] ?: ""
    }

    suspend fun setNavBackStack(json: String) {
        dataStore.edit { prefs ->
            prefs[KEY_NAV_BACK_STACK] = json
        }
    }

    /** 预热：读取一次完整的 DataStore 文件，令后续 collectAsState 直接从缓存取数 */
    suspend fun warmUp() {
        dataStore.data.first()
    }

    companion object {
        private val KEY_DEFAULT_MODE = stringPreferencesKey("default_mode")
        private val KEY_MAX_SNOOZE_COUNT = intPreferencesKey("max_snooze_count")
        private val KEY_HIDE_FROM_RECENTS = booleanPreferencesKey("hide_from_recents")
        private val KEY_SHOW_DEBUG_BUTTON = booleanPreferencesKey("show_debug_button")
        private val KEY_NIGHT_START_MIN = intPreferencesKey("night_start_min")
        private val KEY_NIGHT_END_MIN = intPreferencesKey("night_end_min")
        private val KEY_NIGHT_FIXED_HOUR = intPreferencesKey("night_fixed_hour")
        private val KEY_NIGHT_FIXED_MIN = intPreferencesKey("night_fixed_min")
        private val KEY_KEYBOARD_LAYOUT = stringPreferencesKey("keyboard_layout")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DELETE_HOLD_LEVEL = stringPreferencesKey("delete_hold_level")
        private val KEY_ACKNOWLEDGED_PERMISSIONS = stringSetPreferencesKey("acknowledged_permissions")
        private val KEY_NAV_BACK_STACK = stringPreferencesKey("nav_back_stack")
    }
}
