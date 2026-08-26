// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common

import lorix.noslackalarm.common.KeyboardLayout
import lorix.noslackalarm.common.theme.ThemeMode

/**
 * 业务设置的「单一默认值来源」（Single Source of Truth）。
 *
 * 所有 DataStore 持久化默认值，与 Compose 层 `collectAsState(initial = ...)` 的
 * 占位默认值，都必须引用本对象的常量，禁止在两处各自硬编码字面量。
 * 否则会出现默认值漂移（一边改了另一边没改）
 *
 * 本对象位于 core:common，所有模块均可访问；core:data 的 SettingsDataStore
 * 与各 feature/app 的 UI 共用同一份常量，改默认值时只需改这里一处。
 */
object SettingsDefaults {
    const val DEFAULT_MODE = "BOTH"
    const val MAX_SNOOZE_COUNT = 5
    const val HIDE_FROM_RECENTS = false
    const val SHOW_DEBUG_BUTTON = false

    // 是否已同意《用户协议与隐私政策》：首次启动时未同意会全屏拦截，同意后持久化为 true，不再提示。
    const val AGREEMENT_ACCEPTED = false

    // 主题模式默认值：默认跟随系统明暗。
    // 取值须为 ThemeMode 的 value（system/light/dark）。
    val THEME_MODE: String = ThemeMode.SYSTEM.value

    // 智能默认时间（当日分钟数，0..1439）
    const val NIGHT_START_MIN = 1200   // 20:00
    const val NIGHT_END_MIN = 360      // 06:00
    const val NIGHT_FIXED_HOUR = 7
    const val NIGHT_FIXED_MIN = 30

    // 答题键盘布局，须与 KeyboardLayout.PHONE.value 保持一致（由枚举驱动，避免二次硬编码）
    val KEYBOARD_LAYOUT: String = KeyboardLayout.PHONE.value

    // 主页卡片长按删除时长档位，默认 DEFAULT 。
    // 取值须为 DeleteHoldLevel 的 value（fast/default/slow）。
    val DELETE_HOLD_LEVEL: String = DeleteHoldLevel.DEFAULT.value

    // 用户手动确认「已开启」的权限集合（适用于无标准检测 API 的权限，如自启动 / 锁屏显示）。
    // 这些权限在某些 ROM 上可能无 API 可检测授权状态，统一按「建议开启」展示；用户自行在系统设置
    // 开启后，可在权限页点「已开启」将其标记，使闹钟列表的权限提醒卡消失。默认空集。
    val ACKNOWLEDGED_PERMISSIONS: Set<String> = emptySet()
}
