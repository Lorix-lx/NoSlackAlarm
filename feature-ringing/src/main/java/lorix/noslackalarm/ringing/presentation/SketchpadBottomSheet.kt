// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.ringing.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import lorix.noslackalarm.common.theme.NoSlackAlarmTheme
import lorix.noslackalarm.common.theme.LocalIsLight
import lorix.noslackalarm.ringing.domain.MathProblem
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * 白板覆盖层，替代 ModalBottomSheet 解决触摸冲突。
 * 使用固定 Box 全屏覆盖，不会触发底部弹窗的滑动手势，
 * 手指在白板上绘制时不会被背后的按钮或滚动行为拦截。
 */
@Composable
fun SketchpadBottomSheet(
    problem: MathProblem,
    onDismiss: () -> Unit
) {
    // 首画笔
    val inkColor = colorScheme.onSurface
    val tertiaryContainer = colorScheme.tertiaryContainer
    var strokeColor by remember { mutableStateOf(inkColor) }
    var clearTrigger by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .align(Alignment.BottomCenter)
                .background(
                    colorScheme.surface,
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "题目: ${problem.expression}",
                    style = MiuixTheme.textStyles.title3,
                    color = colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(MiuixIcons.Close, contentDescription = "关闭", tint = colorScheme.onSurface)
                }
            }

            Sketchpad(
                modifier = Modifier
                    .fillMaxWidth()
                .weight(1f)
                .background(colorScheme.surface),
                strokeColor = strokeColor,
                clearTrigger = clearTrigger,
                onClear = { clearTrigger = 0 }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isInk = strokeColor == inkColor
                val isCyan = strokeColor == tertiaryContainer

                Button(
                    onClick = { strokeColor = inkColor },
                    colors = ButtonDefaults.buttonColors(
                        color = colorScheme.surfaceVariant
                    ),
                    cornerRadius = 12.dp,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (isInk) Modifier.border(
                                2.dp,
                                inkColor,
                                RoundedCornerShape(12.dp)
                            ) else Modifier
                        )
                ) {
                    Text(text = if (LocalIsLight.current) "黑色" else "白色")
                }
                Button(
                    onClick = { strokeColor = tertiaryContainer },
                    colors = ButtonDefaults.buttonColors(
                        color = colorScheme.surfaceVariant
                    ),
                    cornerRadius = 12.dp,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (isCyan) Modifier.border(
                                2.dp,
                                tertiaryContainer,
                                RoundedCornerShape(12.dp)
                            ) else Modifier
                        )
                ) {
                    Text(text = "青色")
                }
                Button(
                    onClick = { clearTrigger++ },
                    colors = ButtonDefaults.buttonColors(color = colorScheme.errorContainer),
                    cornerRadius = 12.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "清空", color = colorScheme.onError)
                }
            }
        }
    }
}

@Preview
@Composable
private fun SketchpadBottomSheetPreview() {
    NoSlackAlarmTheme {
        SketchpadBottomSheet(
            problem = MathProblem("23 + 17 = ?", "40"),
            onDismiss = {}
        )
    }
}