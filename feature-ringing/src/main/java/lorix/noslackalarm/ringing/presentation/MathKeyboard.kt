// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.ringing.presentation

// ============ 导入布局组件 ============
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth

// ============ 导入 Miuix 组件 ============
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ============ 导入形状和状态管理 ============
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============ 导入主题与布局配置 ============
import lorix.noslackalarm.common.KeyboardLayout
import lorix.noslackalarm.common.theme.NoSlackAlarmTheme
import lorix.noslackalarm.common.util.isCompactHeight

/**
 * 数学键盘组件
 *
 * 用于输入数学题答案的数字键盘，键盘布局由 [layout] 决定：
 * - [KeyboardLayout.PHONE]：第一行 1 2 3，第二行 4 5 6，第三行 7 8 9
 * - [KeyboardLayout.CALCULATOR]：第一行 7 8 9，第二行 4 5 6，第三行 1 2 3
 * 第四行固定为：删除 0 确定
 *
 * @param layout 键盘布局，默认 [KeyboardLayout.PHONE]
 * @param onNumberClick 点击数字按钮的回调（传入数字字符串）
 * @param onDelete 点击删除按钮的回调
 * @param onSubmit 点击提交按钮的回调
 * @param modifier UI修饰符
 */
@Composable
fun MathKeyboard(
    layout: KeyboardLayout = KeyboardLayout.PHONE,
    onNumberClick: (String) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 获取触觉反馈（震动）
    val haptic = LocalHapticFeedback.current
    // 矮屏（横屏/小窗）下收紧：行/列间距与按键宽高比调整、文字缩小；竖屏保持原样
    val compactHeight = isCompactHeight()
    val itemSpacing = if (compactHeight) 2.dp else 8.dp

    // 按布局生成三行数字（第四行的 0 固定，不随布局变化）
    val numberRows: List<List<String>> = when (layout) {
        KeyboardLayout.PHONE -> listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9")
        )
        KeyboardLayout.CALCULATOR -> listOf(
            listOf("7", "8", "9"),
            listOf("4", "5", "6"),
            listOf("1", "2", "3")
        )
    }

    // 主布局：垂直列
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(itemSpacing) // 按钮之间间距
    ) {
        val rowModifier = Modifier.fillMaxWidth()

        // ========== 数字行（由 layout 决定顺序）==========
        numberRows.forEach { rowDigits ->
            Row(
                modifier = rowModifier,
                horizontalArrangement = Arrangement.spacedBy(itemSpacing) // 按钮之间间距
            ) {
                rowDigits.forEach { digit ->
                    NumberButton(digit, Modifier.weight(1f), compact = compactHeight) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNumberClick(digit)
                    }
                }
            }
        }

        // ========== 第四行：删除 0 确定 ==========
        Row(
            modifier = rowModifier,
            horizontalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            // 删除按钮
            ActionButton(
                "删除", Modifier.weight(1f),
                containerColor = MiuixTheme.colorScheme.surfaceVariant,
                contentColor = MiuixTheme.colorScheme.onSurface,
                compact = compactHeight
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDelete()
            }
            // 数字0
            NumberButton(
                "0", Modifier.weight(1f), compact = compactHeight
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onNumberClick("0")
            }
            // 确定按钮
            ActionButton(
                "确定", Modifier.weight(1f),
                containerColor = MiuixTheme.colorScheme.primary,
                compact = compactHeight
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSubmit()
            }
        }
    }
}

/**
 * 数字按钮组件
 *
 * 用于显示0-9数字的按钮，使用主题的表面颜色
 *
 * @param text 按钮上显示的数字
 * @param modifier UI修饰符
 * @param compact 矮屏紧凑模式：按键更扁、文字更小（默认 false 保持原样）
 * @param onClick 点击事件回调
 */
@Composable
private fun NumberButton(
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        // 紧凑模式下不再用 aspectRatio 锁高（会被内容撑住且裁切文字），
        // 改为收紧 minHeight / insideMargin，让按钮贴近文字，整体更小
        modifier = modifier.then(if (compact) Modifier else Modifier.aspectRatio(1.3f)), // 宽高比1.3:1
        cornerRadius = if (compact) 12.dp else 16.dp,
        minHeight = if (compact) 24.dp else ButtonDefaults.MinHeight,
        insideMargin = if (compact) {
            PaddingValues(horizontal = 4.dp, vertical = 2.dp)
        } else {
            ButtonDefaults.InsideMargin
        },
        colors = ButtonDefaults.buttonColors(
            color = MiuixTheme.colorScheme.surfaceVariant,
            contentColor = MiuixTheme.colorScheme.onSurface
        )
    ) {
        Text(text = text, style = MiuixTheme.textStyles.title1)
    }
}

/**
 * 功能按钮组件
 *
 * 用于清除、提交、删除等操作的按钮，使用自定义颜色
 *
 * @param text 按钮上显示的文字
 * @param modifier UI修饰符
 * @param containerColor 按钮背景色（自定义）
 * @param contentColor 按钮文字颜色（自定义）
 * @param compact 矮屏紧凑模式：按键更扁、文字更小（默认 false 保持原样）
 * @param onClick 点击事件回调
 */
@Composable
private fun ActionButton(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        // 紧凑模式下不再用 aspectRatio 锁高（会被内容撑住且裁切文字），
        // 改为收紧 minHeight / insideMargin，让按钮贴近文字，整体更小
        modifier = modifier.then(if (compact) Modifier else Modifier.aspectRatio(1.3f)), // 宽高比1.3:1
        cornerRadius = if (compact) 12.dp else 16.dp,
        minHeight = if (compact) 24.dp else ButtonDefaults.MinHeight,
        insideMargin = if (compact) {
            PaddingValues(horizontal = 4.dp, vertical = 2.dp)
        } else {
            ButtonDefaults.InsideMargin
        },
        colors = if (contentColor != androidx.compose.ui.graphics.Color.Unspecified) {
            ButtonDefaults.buttonColors(color = containerColor, contentColor = contentColor)
        } else {
            ButtonDefaults.buttonColors(color = containerColor)
        }
    ) {
        Text(text = text, style = MiuixTheme.textStyles.title2)
    }
}

/**
 * 界面预览
 *
 * 在Android Studio中可以实时预览这个界面
 */
@Preview
@Composable
private fun MathKeyboardPreview() {
    NoSlackAlarmTheme {
        MathKeyboard(
            onNumberClick = {},
            onDelete = {},
            onSubmit = {}
        )
    }
}
