// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common.theme

import androidx.compose.ui.graphics.Color

/**
 * 解析十六进制颜色字符串，支持 `#RRGGBB` 与 `#AARRGGBB` 两种格式。
 *
 * 作为 [Color.Companion] 的扩展函数，可直接以 `Color.parse("#6366F1")` 调用，
 * 从而保留源码中的 HEX 可读性，同时兼容 Compose 的 [Color]。
 */
fun Color.Companion.parse(hex: String): Color {
    val clean = hex.removePrefix("#")
    require(clean.length == 6 || clean.length == 8) { "Invalid hex color: $hex" }
    val value = (if (clean.length == 6) "FF$clean" else clean).toLong(16)
    return Color(value)
}

/**
 * 应用颜色
 *
 * 设计原则：
 * 1. 物理色值只在此处定义一次，禁止在 UI 中硬编码颜色值。
 * 2. 业务层应通过 [top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme] 取色，
 *    仅在确实无法走主题时才使用本文件的具名常量。
 *
 */

// ============ 暗色主题物理色值 ============
// ============ 主色调 ============
val Primary = Color.parse("#6366F1")            // 主强调色：主要操作按钮、选中态、焦点高亮
val OnPrimary = Color.parse("#FFFFFF")          // 位于 Primary 之上的文字/图标颜色
val PrimaryContainer = Color.parse("#4A3FA0")    // Primary 的容器背景（如选中项底纹、强调卡片）
val OnPrimaryContainer = Color.parse("#D6D3FF") // 位于 PrimaryContainer 之上的文字/图标颜色

// ============ 次色调 ============
val Secondary = Color.parse("#64748B")          // 次要强调色：次要操作、标签、补充信息
val OnSecondary = Color.parse("#CBD5E1")        // 位于 Secondary 之上的文字/图标颜色
val SecondaryContainer = Color.parse("#7C2D5C")  // Secondary 的容器背景
val OnSecondaryContainer = Color.parse("#FCE7F3") // 位于 SecondaryContainer 之上的文字/图标颜色

// ============ 第三色调 ============
val TertiaryContainer = Color.parse("#00E5FF")  // 第三色容器背景：用于多分类区分（如不同提醒类型）#036475
val OnTertiaryContainer = Color.parse("#E0F2FE") // 位于 TertiaryContainer 之上的文字/图标颜色

// ============ 背景与表面 ============
val Background = Color.parse("#0F172A")          // 页面根背景
val OnBackground = Color.parse("#F1F5F9")        // 页面正文文字/图标颜色
val Surface = Color.parse("#1E293B")            // 抬升表面背景：卡片、对话框、弹层等
val OnSurface = Color.parse("#F1F5F9")          // 位于 Surface 之上的文字/图标颜色
val SurfaceVariant = Color.parse("#334155")     // 次级表面背景：输入框、分组底色等
val OnSurfaceSecondary = Color.parse("#77FFFFFF")    // 辅助/次要文字、提示文字颜色（77%不透明度的白色）
val OnSurfaceVariantActions = Color.parse("#66FFFFFF") //半透明白色（暗色主题下为灰色）

// ============ 边框与轮廓 ============
val Outline = Color.parse("#475569")            // 边框、分割线、控件描边
val OutlineVariant = Color.parse("#334155")      // 更弱的分割线/边框变体

// ============ 错误色 ============
val Error = Color.parse("#EF4444")              // 错误态强调色：错误文字、危险操作
val OnError = Color.parse("#FFFFFF")             // 位于 Error 之上的文字/图标颜色
val ErrorContainer = Color.parse("#930F1A")      // 错误提示容器背景
val OnErrorContainer = Color.parse("#FEE2E2")    // 位于 ErrorContainer 之上的文字/图标颜色

// ============ 亮色主题物理色值 ============
// 与深色色板语义字段一一对应，仅物理色值取浅色基调（背景浅、表面白、文字深）。
// 业务层仍统一通过 MiuixTheme.colorScheme 取色，本组常量仅供 LightColorScheme 组装使用。
val LightPrimary = Color.parse("#6366F1")            // 主强调色（沿用品牌色）
val LightOnPrimary = Color.parse("#FFFFFF")          // 位于 LightPrimary 之上的文字/图标颜色
val LightPrimaryContainer = Color.parse("#E0E0FF")    // LightPrimary 的容器背景
val LightOnPrimaryContainer = Color.parse("#312E81")  // 位于 LightPrimaryContainer 之上的文字/图标颜色
val LightSecondary = Color.parse("#64748B")          // 次要强调色
val LightOnSecondary = Color.parse("#FFFFFF")         // 位于 LightSecondary 之上的文字/图标颜色
val LightSecondaryContainer = Color.parse("#E2E8F0")  // LightSecondary 的容器背景
val LightOnSecondaryContainer = Color.parse("#1E293B") // 位于 LightSecondaryContainer 之上的文字/图标颜色
// 次级强调变体：Miuix TextButton/Button 默认背景（含权限页「撤销」、设置弹窗「取消」等）。
// 默认 Miuix 亮色为 #F0F0F0（过浅、几乎与白底融为一体），此处加深为 #D9D9D9 以保证可见、可点。
val LightSecondaryVariant = Color.parse("#D9D9D9")
val LightOnSecondaryVariant = Color.parse("#0F172A")       // 位于 LightSecondaryVariant 之上的文字/图标颜色（近黑，强对比）
val LightDisabledSecondaryVariant = Color.parse("#EAEAEA") // 禁用态次级变体背景
val LightDisabledOnSecondaryVariant = Color.parse("#A1A1A6") // 禁用态次级变体文字
val LightTertiaryContainer = Color.parse("#06B6D4")   // 第三色容器背景（亮色画板上的青笔，加深以保证可见）
val LightOnTertiaryContainer = Color.parse("#075985") // 位于 LightTertiaryContainer 之上的文字/图标颜色
val LightBackground = Color.parse("#F2F2F7")          // 页面根背景（iOS/MIUI 风格冷调浅灰，与纯白卡片形成协调的「灰底白卡」分组观感）
val LightOnBackground = Color.parse("#0F172A")        // 页面正文文字/图标颜色（深）
val LightSurface = Color.parse("#FFFFFF")            // 抬升表面背景：卡片、对话框、弹层等
val LightOnSurface = Color.parse("#0F172A")          // 位于 LightSurface 之上的文字/图标颜色
// 次级表面背景：输入框、分组底色、开关未启用轨道、画板画布等。
// 取比 LightBackground(#F2F2F7) 略深、彼此接近的浅灰(#E5E5EA)，
// 在维持「未启用轨道/分组底色仍比背景稍深、边界可辨」的同时，与亮色页面更协调。
val LightSurfaceVariant = Color.parse("#E5E5EA")
val LightOnSurfaceSecondary = Color.parse("#77000000")  // 辅助/次要文字、提示文字颜色（77%不透明度的黑色）
val LightOnSurfaceVariantActions = Color.parse("#66000000") //半透明黑色（亮色主题下为灰色）
val LightOutline = Color.parse("#D1D1D6")            // 边框、分割线、控件描边（亮色下细灰描边，如白底 FAB 边框）
val LightOutlineVariant = Color.parse("#D8D8DD")      // 更弱的分割线/边框变体（在浅灰背景上仍可见）
val LightError = Color.parse("#DC2626")              // 错误态强调色（亮底上稍深以便辨识）
val LightOnError = Color.parse("#FFFFFF")            // 位于 LightError 之上的文字/图标颜色
val LightErrorContainer = Color.parse("#af111e")      // 错误提示容器背景
val LightOnErrorContainer = Color.parse("#FEE2E2")    // 位于 LightErrorContainer 之上的文字/图标颜色
