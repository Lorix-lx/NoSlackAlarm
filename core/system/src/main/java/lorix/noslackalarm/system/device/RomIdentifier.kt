// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.system.device

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.util.Locale

/**
 * ROM / 设备类型识别。
 *
 * 引导逻辑必须依赖本类做 ROM 自适应，禁止在 UI 层写死任何厂商路径。
 */
enum class RomType {
    STOCK_AOSP,  // 原生 Android / 模拟器 / Pixel
    MIUI,        // 小米 / 红米
    EMUI,        // 华为 / 荣耀 (旧，EMUI)
    HONOR,       // 荣耀 (新，MagicOS)
    COLOR_OS,    // OPPO / 一加 / realme
    ORIGIN_OS,   // vivo
    ONE_UI,      // 三星
    OTHER
}

object RomIdentifier {

    /** 小米系统「电量与性能」应用包名，用于省电白名单免选择直达。 */
    private const val MIUI_POWERKEEPER_PACKAGE = "com.miui.powerkeeper"

    val romType: RomType by lazy { identify() }

    /** 是否为原生 AOSP（含模拟器）。原生系统没有厂商专属的省电/锁屏/后台弹出权限。 */
    val isStockAosp: Boolean get() = romType == RomType.STOCK_AOSP

    /**
     * 国产深度定制 ROM 会接管 [Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS]，
     * 直接跳到自家的「省电策略」页，且不要求调用方持有 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 权限。
     * 这类 ROM 用标准 Intent 即可。
     */
    val usesVendorBatteryIntent: Boolean
        get() = romType in setOf(
            RomType.MIUI, RomType.EMUI, RomType.HONOR, RomType.COLOR_OS, RomType.ORIGIN_OS
        )

    /**
     * 当前 ROM 是否需要展示「省电 / 电池优化」引导。
     * 原生 AOSP（含模拟器）的电池设置页没有「不受限制」选项，
     * 只有「允许后台使用」开关，无需引导用户操作。
     */
    val needsBatteryGuidance: Boolean
        get() = usesVendorBatteryIntent

    /** 跳转到电池优化 / 省电设置的正确入口（按 ROM 自适应）。 */
    fun batteryOptimizationIntent(context: Context, packageName: String): Intent {
        if (!usesVendorBatteryIntent) {
            // 原生 / 三星等：跳应用详情页，用户再点「电池 → 不受限制」。
            // 注意：未声明 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 权限时，
            // ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 在原生系统会被静默丢弃，故走应用详情。
            return appDetailsIntent(packageName)
        }
        val base = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        // 小米系统上该隐式 Intent 会被「手机管家：耗电排行」与「电量与性能」同时注册，
        // 直接启动会弹出系统选择器。这里动态解析出「电量与性能」(com.miui.powerkeeper)
        // 实际处理该 action 的 Activity，改用显式 ComponentName 直达，效果与用户手动选择一致。
        // 动态解析而非硬编码 Activity 类名：其名称随 MIUI/HyperOS 版本变化，硬编码跨版本易失效。
        val target = runCatching {
            context.packageManager
                .queryIntentActivities(base, PackageManager.MATCH_DEFAULT_ONLY)
                .firstOrNull { it.activityInfo.packageName == MIUI_POWERKEEPER_PACKAGE }
        }.getOrNull()
        if (target != null) {
            return Intent(base).apply {
                component = ComponentName(
                    target.activityInfo.packageName,
                    target.activityInfo.name
                )
            }
        }
        // 未命中（如精简版系统无该入口）时回退原隐式 Intent，由调用方 resolveActivity 兜底。
        return base
    }

    /** 跳转到应用详情页（所有 ROM 均支持，作为兜底跳转）。 */
    fun appDetailsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }

    /** 省电引导文案（按 ROM 区分叫法与路径）。仅当 [needsBatteryGuidance] 为 true 时调用。 */
    fun batteryGuidanceSummary(): String =
        // 仅国产 ROM 会走到这里，原生/模拟器由 needsBatteryGuidance 拦截，不会展示
        when (romType) {
            RomType.HONOR ->
                "为确保闹钟后台正常运行，请将不赖闹钟加入电池优化白名单。\n\n步骤：进入「设置 → 隐私 → 权限管理 → 特殊访问权限 → 电池优化」，将本应用设为不被优化。"
            RomType.COLOR_OS, RomType.ORIGIN_OS ->
                "为确保闹钟后台正常运行，请将不赖闹钟设为不被后台耗电管理限制。\n\n步骤：进入「设置 → 电池 → 后台耗电管理」，将本应用设为允许后台运行。"
            else ->
                "为确保闹钟后台正常运行，请将不赖闹钟加入省电白名单。\n\n步骤：进入「省电策略」→ 选择「无限制」。"
        }

    /** 省电 / 电池优化权限项的标题（按 ROM 区分叫法）。 */
    fun batteryPermissionTitle(): String =
        when (romType) {
            RomType.HONOR -> "电池优化"
            RomType.COLOR_OS, RomType.ORIGIN_OS -> "后台耗电管理"
            else -> "省电白名单"
        }

    /** 省电 / 电池优化权限项的说明（按 ROM 区分路径）。 */
    fun batteryPermissionDescription(): String =
        when (romType) {
            RomType.HONOR ->
                "防止系统限制后台运行导致闹钟失效，请在电池优化中将应用设为不被优化"
            RomType.COLOR_OS, RomType.ORIGIN_OS ->
                "防止系统限制后台运行导致闹钟失效，请在「设置 → 电池 → 后台耗电管理」中将应用设为允许后台运行"
            else ->
                "防止系统限制后台运行导致闹钟失效"
        }

    /** 自启动权限项的说明（按 ROM 区分路径）。 */
    fun autoStartGuidanceDescription(): String =
        when (romType) {
            RomType.HONOR ->
                "防止系统管家清理后台导致闹钟无法运行，请在应用启动管理中允许自启动、关联启动与后台活动"
            RomType.COLOR_OS ->
                "防止系统管家清理后台导致闹钟无法运行，请在应用启动管理中允许自启动、关联启动与后台活动"
            RomType.ORIGIN_OS ->
                "防止系统管家清理后台导致闹钟无法运行，请在 i管家「应用管理 → 自启动管理」中允许自启动"
            else ->
                "防止系统管家清理后台导致闹钟无法运行"
        }

    /**
     * 当前 ROM 是否需要引导「锁屏显示 / 后台弹出界面」。
     * 该条目映射 MIUI「权限管理 → 其他权限」下的具体权限，是 MIUI 专属概念。
     * 其余国产 ROM（华为 / 荣耀 / OPPO / vivo）虽各有类似权限，但路径与叫法各不相同、
     * 且无统一可跳转组件，展示反而会误导用户（点进去找不到对应入口）；故仅 MIUI 展示。
     * 原生 AOSP / 三星无此概念，自然不展示。
     */
    val needsLockScreenAndBackgroundPopupGuidance: Boolean
        get() = romType == RomType.MIUI

    /**
     * 当前 ROM 是否需要引导「自启动 / 开机自启动」权限。
     * 自启动是国产深度定制 ROM 的专属能力，原生 AOSP 与三星没有统一的厂商自启动管理页，故不展示。
     */
    val needsAutoStartGuidance: Boolean
        get() = usesVendorBatteryIntent

    /**
     * 跳转到厂商「自启动」管理页（按 ROM 自适应）。
     * 原生 / 三星等无此概念时兜底到应用详情页。
     *
     * 注意：自启动没有标准 Android API 可检测是否已开启，也无统一 Settings.ACTION_*，
     * 故按 ROM 返回厂商安全中心的具体 Activity；调用方需用 resolveActivity 兜底防“点了没反应”。
     */
    fun autoStartIntent(packageName: String): Intent {
        var component = when (romType) {
            RomType.MIUI -> ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            RomType.EMUI -> ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
            RomType.HONOR -> ComponentName(
                "com.hihonor.systemmanager",
                "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
            RomType.COLOR_OS -> ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
            RomType.ORIGIN_OS -> ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
            )
            else -> return appDetailsIntent(packageName)
        }
        return Intent().apply { component = component }
    }

    private fun identify(): RomType {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val brand = Build.BRAND.lowercase(Locale.ROOT)
        val fingerprint = Build.FINGERPRINT.lowercase(Locale.ROOT)
        val model = Build.MODEL.lowercase(Locale.ROOT)

        // 模拟器视为原生 AOSP
        if (fingerprint.contains("generic") || fingerprint.contains("emulator") ||
            model.contains("emulator") || model.contains("sdk")
        ) {
            return RomType.STOCK_AOSP
        }

        return when {
            // 荣耀（MagicOS）manufacturer 为 honor，须在华为分支前判定，避免被归入 EMUI
            manufacturer.contains("honor") ->
                RomType.HONOR
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") || brand.contains("redmi") ->
                RomType.MIUI
            manufacturer.contains("huawei") || brand.contains("huawei") || brand.contains("honor") ->
                RomType.EMUI
            manufacturer.contains("oppo") || manufacturer.contains("oneplus") || manufacturer.contains("realme") ->
                RomType.COLOR_OS
            manufacturer.contains("vivo") ->
                RomType.ORIGIN_OS
            manufacturer.contains("samsung") ->
                RomType.ONE_UI
            manufacturer.contains("google") ->
                RomType.STOCK_AOSP
            else -> RomType.OTHER
        }
    }

    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase(Locale.ROOT)
        val model = Build.MODEL.lowercase(Locale.ROOT)
        return fingerprint.contains("generic") || fingerprint.contains("emulator") ||
                model.contains("emulator") || model.contains("sdk")
    }
}
