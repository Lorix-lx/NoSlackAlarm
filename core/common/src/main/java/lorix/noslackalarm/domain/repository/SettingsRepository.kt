// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    /** 预热 DataStore，提前加载所有配置到内存，消除首次 collect 的磁盘 I/O 延迟 */
    suspend fun warmUp()

    val defaultMode: Flow<String>
    suspend fun setDefaultMode(mode: String)

    val maxSnoozeCount: Flow<Int>
    suspend fun setMaxSnoozeCount(count: Int)

    val hideFromRecents: Flow<Boolean>
    suspend fun setHideFromRecents(hide: Boolean)

    // 是否在下拉菜单显示「调试」入口（默认 false）
    val showDebugButton: Flow<Boolean>
    suspend fun setShowDebugButton(show: Boolean)

    // 新建闹钟的智能默认时间配置（默认值见 SettingsDefaults）
    val nightStartMin: Flow<Int>        // 夜间区间开始（当日分钟数），默认 1200 (20:00)
    suspend fun setNightStartMin(min: Int)
    val nightEndMin: Flow<Int>          // 夜间区间结束（当日分钟数），默认 360 (06:00)
    suspend fun setNightEndMin(min: Int)
    val nightFixedHour: Flow<Int>       // 夜间默认闹钟小时，默认 7
    suspend fun setNightFixedHour(hour: Int)
    val nightFixedMinute: Flow<Int>     // 夜间默认闹钟分钟，默认 30
    suspend fun setNightFixedMinute(minute: Int)

    // 答题键盘布局：PHONE(123/456/789) 或 CALCULATOR(789/456/123)，默认 PHONE
    val keyboardLayout: Flow<String>
    suspend fun setKeyboardLayout(layout: String)

    // 主题模式：system/light/dark（见 SettingsDefaults.THEME_MODE）
    val themeMode: Flow<String>
    suspend fun setThemeMode(mode: String)

    // 主页卡片长按删除时长档位：fast/default/slow，默认 default（见 SettingsDefaults.DELETE_HOLD_LEVEL）
    val deleteHoldLevel: Flow<String>
    suspend fun setDeleteHoldLevel(level: String)

    // 用户手动确认「已开启」的权限集合（无标准检测 API 的权限，如自启动 / 锁屏显示）
    val acknowledgedPermissions: Flow<Set<String>>
    suspend fun acknowledgePermission(key: String)
    suspend fun revokeAcknowledgedPermission(key: String)

    // 导航返回栈持久化（List<Screen> 的 JSON）：用于 Activity 在同进程内被重建时（如开启「隐藏最近任务」后
    // 从桌面图标再次进入会新建 Activity 实例）恢复用户最后停留的页面及返回栈。空字符串表示尚无持久化数据，
    // 读取方应回退到 [AlarmList]。真正区分「同进程重建（应恢复）」与「进程全新启动（应回主页）」
    // 由NoSlackAlarmApp.isFreshProcess 门控，本接口只负责读写。
    val navBackStack: Flow<String>
    suspend fun setNavBackStack(json: String)
}