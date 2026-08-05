// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.alarm.presentation.components

// ============ 导入布局组件 ============
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize

// ============ 导入 Miuix 组件 ============
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ============ 导入状态管理和工具 ============
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============ 导入主题 ============
import lorix.noslackalarm.common.theme.NoSlackAlarmTheme

// ============ 常量定义 ============
/**
 * 日期标签列表
 * 对应周一到周日
 */
private val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 日期位编码列表
 * 使用位运算来表示选中的日期：
 * - 周一: 1   (二进制: 0000001)
 * - 周二: 2   (二进制: 0000010)
 * - 周三: 4   (二进制: 0000100)
 * - 周四: 8   (二进制: 0001000)
 * - 周五: 16  (二进制: 0010000)
 * - 周六: 32  (二进制: 0100000)
 * - 周日: 64  (二进制: 1000000)
 * 
 * 通过位或运算可以组合多天，例如：
 * - 周一+周二: 1 | 2 = 3
 * - 全部7天: 1 | 2 | 4 | 8 | 16 | 32 | 64 = 127
 */
private val DAY_BITS = listOf(1, 2, 4, 8, 16, 32, 64)

/**
 * 重复日期选择器组件
 * 
 * 显示7个小方块（周一到周日），用户可以点击选择重复的日期
 * 选中的日期会高亮显示
 * 
 * @param selectedDays 当前选中的日期（位编码）
 * @param onDayToggle 点击某一天时的回调（传入该天的位编码）
 * @param modifier UI修饰符
 */
@Composable
fun RepeatDaySelector(
    selectedDays: Int,
    onDayToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 水平排列，7个小方块均匀分布
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween // 两端对齐，均匀分布
    ) {
        // 遍历7天的位编码
        DAY_BITS.forEachIndexed { index, bit ->
            // 判断当前这一天是否被选中（使用位与运算）
            val isSelected = (selectedDays and bit) != 0
            
            // 使用 Miuix Button 模拟 FilterChip
            Button(
                onClick = { onDayToggle(bit) },
                modifier = Modifier.size(width = 40.dp, height = 34.dp),
                cornerRadius = 8.dp,
                insideMargin = PaddingValues(4.dp),
                colors = ButtonDefaults.buttonColors(
                    color = if (isSelected) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.surfaceVariant,
                    contentColor = if (isSelected) MiuixTheme.colorScheme.onPrimary
                                  else MiuixTheme.colorScheme.onSurfaceSecondary
                )
            ) {
                Text(
                    text = DAY_LABELS[index],
                    style = MiuixTheme.textStyles.button
                )
            }
        }
    }
}

/**
 * 界面预览
 * 
 * 在Android Studio中可以实时预览这个界面
 * 预览时选中了周一到周五（1+2+4+8+16=31）
 */
@Preview
@Composable
private fun RepeatDaySelectorPreview() {
    NoSlackAlarmTheme {
        RepeatDaySelector(
            selectedDays = 1 or 2 or 4 or 8 or 16, // 周一到周五
            onDayToggle = {}
        )
    }
}
