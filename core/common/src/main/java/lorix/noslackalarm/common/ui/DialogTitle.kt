// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 弹窗标题：渲染效果与 Miuix `OverlayDialog` / `WindowDialog` 自带的标题一致
 * （title4 字号、中字重、居中、下留白 12dp）。
 *
 * 供「内容区 weight 滚动 + 底部固定按钮」的弹窗结构使用：这类弹窗将标题放在 content 内部，
 * 使 weight 滚动区能正确扣除标题高度，避免内容占满剩余空间时弹窗总高超出窗口、
 * 底部按钮被 `squircleSurface` 裁剪不可见（横屏/矮屏下的经典溢出）。
 *
 * @param text 标题文本
 */
@Composable
fun DialogTitle(text: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        text = text,
        fontSize = MiuixTheme.textStyles.title4.fontSize,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        color = MiuixTheme.colorScheme.onBackground
    )
}
