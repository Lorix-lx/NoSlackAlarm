// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 矮屏高度阈值：窗口高度小于该值时按「矮屏」处理，启用紧凑布局。
 * 与 Miuix `DialogDefaults.isLargeScreen` 的 480dp 高度边界保持一致。
 */
private val COMPACT_HEIGHT_THRESHOLD: Dp = 480.dp

/**
 * 判定当前窗口是否为「矮屏」（窗口高度小于 [threshold]）。
 *
 * 用于在低高度窗口（横屏手机、分屏/小窗）下启用紧凑布局：缩小字号、
 * 减少拾取器可见项、限宽键盘等，保证内容可达、弹窗按钮始终可见、不溢出。
 *
 * @param threshold 矮屏高度阈值，默认 [COMPACT_HEIGHT_THRESHOLD]
 * @return true=当前窗口高度小于阈值，应采用紧凑布局
 */
@Composable
fun isCompactHeight(threshold: Dp = COMPACT_HEIGHT_THRESHOLD): Boolean {
    return LocalWindowInfo.current.containerDpSize.height < threshold
}
