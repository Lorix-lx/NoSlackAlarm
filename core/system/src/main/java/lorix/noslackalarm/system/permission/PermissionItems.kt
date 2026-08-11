// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.system.permission

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import lorix.noslackalarm.system.device.RomIdentifier

/**
 * 单条权限项的 UI 数据模型（纯数据，不依赖 Miuix，便于放在 core:system）。
 *
 * - [granted] 为 `true`/`false` 表示可检测并已判定的授权状态；
 *   `null` 表示该权限无标准 API 可检测（如自启动、锁屏显示/后台弹出界面），
 *   统一按「建议开启」展示，由 UI 决定样式。
 * - [iconKey] 为图标语义键，UI 层（core:common 的 [lorix.noslackalarm.common.permissionIcon]）
 *   据此映射到具体 Miuix 图标，避免在本（非 UI）模块引入 Miuix 依赖。
 * - [buildIntent] 构造跳转到该权限对应系统设置页的 Intent，按 ROM 自适应。
 */
data class PermissionUiItem(
    val key: String,
    val title: String,
    val description: String,
    val granted: Boolean?,
    val iconKey: String,
    /**
     * 若非空，表示该权限可通过系统弹窗直接申请（而不仅是跳转设置页）。
     * 当前仅通知权限（[Manifest.permission.POST_NOTIFICATIONS]）支持；
     * 其余权限（精确闹钟 / 悬浮窗 / 省电 / 自启动 / 锁屏）因系统限制只能跳转设置页。
     */
    val inAppPermission: String? = null,
    /**
     * 是否允许用户在「进入过对应系统设置页」后，手动点「我已开启」将其标记为已确认。
     *
     * 用于无法被标准 API 可靠检测的权限：
     * - 自启动 / 锁屏显示（[granted] 为 `null`，本就无检测 API）；
     * - 省电白名单：部分国产 ROM 的厂商「省电策略」选「无限制」后，
     *   [PowerManager.isIgnoringBatteryOptimizations] 仍可能返回 `false`，无法自动刷新，
     *   故也允许用户在进入系统页后手动确认。
     */
    val allowManualConfirm: Boolean = false,
    val buildIntent: (Context) -> Intent
) {
    /**
     * 跳转该权限对应的设置页。带 [Intent.resolveActivity] 兜底到应用详情页，
     * 杜绝“点了没反应”；整段包 try/catch 防止异常崩溃。
     */
    fun launch(context: Context) {
        val intent = runCatching { buildIntent(context) }
            .getOrElse { RomIdentifier.appDetailsIntent(context.packageName) }
        val resolved = if (intent.resolveActivity(context.packageManager) != null) {
            intent
        } else {
            RomIdentifier.appDetailsIntent(context.packageName)
        }
        runCatching { context.startActivity(resolved) }
    }
}

/**
 * 收集当前应展示的权限项列表。
 *
 * 依据 API 级别 + [RomIdentifier] ROM 自适应判断 + 当前授权状态返回列表：
 * - 精确闹钟 / 悬浮窗 / 通知：有标准 API 可检测，[granted] 为真实布尔值；
 * - 省电白名单：仅国产 ROM 且 API M+ 展示，[granted] 为真实布尔值；
 * - 自启动 / 锁屏显示·后台弹出界面：无检测 API，仅对应国产 ROM 展示，[granted] 为 `null`。
 *
 * 该函数为轻量同步系统 API 读取，主线程调用无 I/O 风险；建议在进入页与 onResume 时调用以刷新。
 */
fun collectPermissionUiItems(context: Context): List<PermissionUiItem> {
    val pkg = context.packageName
    val items = mutableListOf<PermissionUiItem>()

    // 精确闹钟（Android 12+ 需精确闹钟权限）
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        items += PermissionUiItem(
            key = "exact_alarm",
            title = "精确闹钟",
            description = "确保闹钟在设定时间准时响起",
            granted = alarmManager.canScheduleExactAlarms(),
            iconKey = "alarm",
            buildIntent = { Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) }
        )
    }

    // 悬浮窗（响铃时锁定并弹出界面）
    items += PermissionUiItem(
        key = "overlay",
        title = "悬浮窗",
        description = "响铃时在其它界面上弹出与锁定提醒",
        granted = Settings.canDrawOverlays(context),
        iconKey = "overlay",
        buildIntent = {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$pkg"))
        }
    )

    // 省电 / 电池优化白名单（仅国产 ROM 有可操作的「省电策略/电池优化」入口）
    if (RomIdentifier.needsBatteryGuidance && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        items += PermissionUiItem(
            key = "battery",
            title = RomIdentifier.batteryPermissionTitle(),
            description = RomIdentifier.batteryPermissionDescription(),
            granted = powerManager.isIgnoringBatteryOptimizations(pkg),
            iconKey = "battery",
            buildIntent = { context -> RomIdentifier.batteryOptimizationIntent(context, pkg) }
        )
    }

    // 通知（Android 13+）
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        items += PermissionUiItem(
            key = "notification",
            title = "通知",
            description = "响铃时显示通知与提示",
            granted = granted,
            iconKey = "notification",
            inAppPermission = Manifest.permission.POST_NOTIFICATIONS,
            buildIntent = {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                }
            }
        )
    }

    // 自启动 / 启动管理（仅国产 ROM，无标准 API 可检测，统一建议开启）
    if (RomIdentifier.needsAutoStartGuidance) {
        items += PermissionUiItem(
            key = "autostart",
            title = RomIdentifier.autoStartPermissionTitle(),
            description = RomIdentifier.autoStartGuidanceDescription(),
            granted = null,
            iconKey = "autostart",
            allowManualConfirm = true,
            buildIntent = { RomIdentifier.autoStartIntent(pkg) }
        )
    }

    // 锁屏显示 / 后台弹出界面（仅国产 ROM，无标准 API 可检测，统一建议开启）
    if (RomIdentifier.needsLockScreenAndBackgroundPopupGuidance) {
        items += PermissionUiItem(
            key = "lock_screen",
            title = "锁屏显示 / 后台弹出界面",
            description = "点击后进入 权限管理->其他权限",
            granted = null,
            iconKey = "lock_screen",
            allowManualConfirm = true,
            buildIntent = { RomIdentifier.appDetailsIntent(pkg) }
        )
    }

    return items
}

/**
 * 是否存在任一未开启（或不可检测）的权限项。
 *
 * [acknowledged] 为用户在权限页手动确认「已开启」的权限 key 集合（仅适用于无标准检测 API
 * 的权限，如自启动 / 锁屏显示）。被确认的权限视为已满足，不再计入缺失，使闹钟列表的
 * 权限提醒卡在用户确实已手动开启后消失。
 */
fun hasMissingPermissions(
    items: List<PermissionUiItem>,
    acknowledged: Set<String> = emptySet()
): Boolean = items.any { it.granted != true && it.key !in acknowledged }
