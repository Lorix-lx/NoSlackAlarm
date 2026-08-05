// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common

/**
 * 答题键盘（数字键盘）布局。
 *
 * - [PHONE]: 第一行 1 2 3 / 第二行 4 5 6 / 第三行 7 8 9（电话键盘式），为默认布局
 * - [CALCULATOR]: 第一行 7 8 9 / 第二行 4 5 6 / 第三行 1 2 3（计算器式）
 */
enum class KeyboardLayout(val value: String) {
    PHONE("PHONE"),
    CALCULATOR("CALCULATOR");

    companion object {
        fun from(value: String?): KeyboardLayout =
            entries.firstOrNull { it.value == value } ?: PHONE
    }
}
