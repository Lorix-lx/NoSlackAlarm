// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common

import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Alarm
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Reset

/**
 * 将权限项的 [lorix.noslackalarm.system.permission.PermissionUiItem.iconKey] 映射为具体 Miuix 图标。
 *
 * 图标映射集中于此（core:common 已依赖 Miuix），使 core:system 的权限模型保持纯数据、
 * 不引入 UI 依赖；feature-alarm 与 app 两处 UI 复用同一映射，避免图标散落不一致。
 */
fun permissionIcon(iconKey: String): ImageVector = when (iconKey) {
    "alarm" -> MiuixIcons.Alarm
    "overlay" -> MiuixIcons.Layers
    "battery" -> MiuixIcons.Reset
    "notification" -> MiuixIcons.Messages
    "autostart" -> MiuixIcons.Play
    "lock_screen" -> MiuixIcons.Lock
    else -> MiuixIcons.Info
}
