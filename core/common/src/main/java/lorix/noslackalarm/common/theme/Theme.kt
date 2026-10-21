// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common.theme

import android.app.Activity
import android.os.Build
import android.view.Window
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

// 深色色板：将 Color 中定义的物理色值按 Miuix 语义字段组装成完整 ColorScheme。
private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceSecondary = OnSurfaceSecondary,
    surfaceContainer = Surface,
    onSurfaceContainer = OnSurface,
    surfaceContainerHigh = Surface,
    surfaceContainerHighest = Surface,
    onSurfaceVariantActions = OnSurfaceVariantActions,
    outline = Outline,
    dividerLine = OutlineVariant,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer
)

// 亮色色板：与 DarkColorScheme 字段一一对应，物理色值取自 Color.kt 的 Light* 系列。
private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    secondaryVariant = LightSecondaryVariant,
    onSecondaryVariant = LightOnSecondaryVariant,
    disabledSecondaryVariant = LightDisabledSecondaryVariant,
    disabledOnSecondaryVariant = LightDisabledOnSecondaryVariant,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceSecondary = LightOnSurfaceSecondary,
    surfaceContainer = LightSurface,
    onSurfaceContainer = LightOnSurface,
    surfaceContainerHigh = LightSurface,
    surfaceContainerHighest = LightSurface,
    onSurfaceVariantActions = LightOnSurfaceVariantActions,
    outline = LightOutline,
    dividerLine = LightOutlineVariant,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer
)

// 暴露当前是否处于亮色主题的 CompositionLocal，供业务层做"仅亮色分支"的视觉微调（如白底卡片、白底 FAB）。
// 默认 false（暗色），保证未包在 NoSlackAlarmTheme 内的旧/Preview 调用点仍走暗色分支。
val LocalIsLight = compositionLocalOf { false }

// 是否有覆盖层（下拉弹窗、对话框等）正在显示。
// 用于让系统状态栏/导航栏的图标在遮罩激活时翻为浅色（暗色遮罩上深色图标看不清）。
// 该状态由 NoSlackAlarmTheme 集中持有并通过 LocalScrimActive 向下提供；需要遮罩的页面用 ScrimActive(active) 声明，
// 由 Theme.kt 统一订阅并据此切换系统栏图标深浅。
val LocalScrimActive = compositionLocalOf<MutableState<Boolean>> {
    error("LocalScrimActive 必须在 NoSlackAlarmTheme 组合树内使用")
}

/**
 * 声明当前页面是否有 Overlay 正在展开（下拉弹窗、OverlayDialog 等）。
 * 由 NoSlackAlarmTheme 通过 [LocalScrimActive] 集中汇聚，统一驱动系统栏/导航栏图标深浅；
 * 各屏只需声明自身激活态，无需触碰系统栏逻辑、也无需 import 全局状态。
 */
@Composable
fun ScrimActive(active: Boolean) {
    val state = LocalScrimActive.current
    SideEffect { state.value = active }
}

/**
 * 将系统栏（状态栏/三键导航栏）设为透明并同步图标深浅到指定明暗主题，作为「系统栏外观」的单一真相源。
 * 同时被两处调用：
 *  - [NoSlackAlarmTheme] 的 SideEffect：运行时主题/遮罩变化时的响应式更新（含窗口底色兜底）。
 *  - MainActivity 开屏退出时刻：兜住 MIUI/HyperOS 忽略首帧代码设置系统栏颜色的时序坑，确保冷启动即与 App 主题一致。
 *
 * 透明系统栏在 API 26–34 由代码显式写入，API 35+ 由系统强制 edge-to-edge 保证（见
 * [applyLegacyTransparentSystemBars]）；两段路径对调用方透明。
 *
 * @param window 目标窗口
 * @param useDark true=暗色主题，false=亮色主题（决定非遮罩态下图标深浅）
 * @param scrimActive 覆盖层（弹窗/对话框）是否激活；激活时图标强制浅色，避免暗色遮罩上深色图标看不清
 * @param backgroundArgb 可选：窗口底色（兜底系统栏透出区域不露黑边）；开屏退出点无需传，传 null 跳过
 */
fun applySystemBarChrome(
    window: Window,
    useDark: Boolean,
    scrimActive: Boolean = false,
    backgroundArgb: Int? = null
) {
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    // 遮罩激活时图标强制浅色；否则跟随主题。系统栏透明，由 App 画布（含 Miuix scrim）透出，
    // 使状态栏/导航栏与内容共用同一层遮罩、弹窗时全屏同步变暗。
    val lightIcons = !useDark || scrimActive
    insetsController.isAppearanceLightStatusBars = lightIcons
    insetsController.isAppearanceLightNavigationBars = lightIcons
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(false)
        // 关闭手势/三键导航下系统对导航栏的强制对比遮罩，使透明导航栏纯透、与应用画布一致。
        window.isNavigationBarContrastEnforced = false
    }
    applyLegacyTransparentSystemBars(window)
    backgroundArgb?.let { window.setBackgroundDrawable(it.toDrawable()) }
}

/**
 * API 26–34 的透明系统栏兜底：这些版本需要显式把系统栏颜色写成透明，App 画布（含 Miuix 自绘
 * 的全屏 scrim）才能透出到状态栏/导航栏区域。
 *
 * API 35（Android 15）起系统强制 edge-to-edge，`statusBarColor` / `navigationBarColor` 已废弃且
 * 不再生效（系统栏恒为透明），因此这里直接跳过，不做无意义的废弃 API 调用。
 */
@Suppress("DEPRECATION")
private fun applyLegacyTransparentSystemBars(window: Window) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) return
    window.statusBarColor = Color.Transparent.toArgb()
    window.navigationBarColor = Color.Transparent.toArgb()
}

// 应用统一主题入口，替代 Miuix 原生 MiuixTheme；所有页面都应在其内部渲染，以保证全应用使用同一套色板。
// themeMode 决定采用亮色还是暗色色板；默认 DARK 以兼容未传参的 Preview 与旧调用点。
@Composable
fun NoSlackAlarmTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (useDark) DarkColorScheme else LightColorScheme
    // 遮罩激活状态由 Theme.kt 集中持有（归属组合树、随主题生命周期），并向下提供。
    val scrimActiveState = remember { mutableStateOf(false) }
    // 在 SideEffect 之外读取遮罩状态，明确建立组合依赖：遮罩开关时 NoSlackAlarmTheme 重组、下方 SideEffect 重跑。
    val scrimActive = scrimActiveState.value
    val view = LocalView.current

    // 通过 LocalIsLight 把当前明暗状态向下传递，供需要亮色专属外观的组件使用（暗色默认 false）；
    // 同时通过 LocalScrimActive 把遮罩状态下发，供各屏 ScrimActive(...) 写入。
    CompositionLocalProvider(
        LocalIsLight provides !useDark,
        LocalScrimActive provides scrimActiveState
    ) {
        MiuixTheme(colors = colorScheme) {
            if (!view.isInEditMode) {
                // 让系统状态栏/导航栏「透明」，使 App 画布上 Miuix 自己绘制、已铺满全屏的那层 scrim
                // （DialogContentLayout / MiuixPopupUtils 用 fillMaxSize + drawBehind 画的半透明暗色矩形）
                // 直接透出到系统栏区域。这样状态栏/导航栏与 App 内容共用「同一层」遮罩、逐帧天然同步，
                // 不再由我们单独写 statusBarColor 去模拟变暗。
                // 遮罩中翻浅色图标（暗色遮罩上深色图标看不清）；关闭后恢复主题深浅。
                SideEffect {
                    val window = (view.context as Activity).window
                    // 复用单一真相源 applySystemBarChrome：透明系统栏 + 图标深浅，并兜底窗口底色为当前主题背景。
                    applySystemBarChrome(
                        window = window,
                        useDark = useDark,
                        scrimActive = scrimActive,
                        backgroundArgb = colorScheme.background.toArgb()
                    )
                }
            }
            content()
        }
    }
}
