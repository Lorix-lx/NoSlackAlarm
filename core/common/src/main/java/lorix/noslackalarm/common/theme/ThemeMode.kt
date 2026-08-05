// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common.theme

/**
 * 主题模式：跟随系统 / 强制亮色 / 强制暗色。
 *
 * 选项/枚举化，以 `value` 作为 DataStore 持久化字符串，
 * `from(value)` 负责字符串与枚举互转，避免裸字符串散落在多处。
 */
enum class ThemeMode(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun from(value: String): ThemeMode =
            entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}
