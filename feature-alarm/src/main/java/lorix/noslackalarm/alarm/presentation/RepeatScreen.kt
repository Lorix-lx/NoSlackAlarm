// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.alarm.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CheckboxDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import lorix.noslackalarm.common.theme.ScrimActive
import lorix.noslackalarm.common.ui.DialogTitle

private val DAY_ENTRIES = listOf(
    1 to "星期一",
    2 to "星期二",
    4 to "星期三",
    8 to "星期四",
    16 to "星期五",
    32 to "星期六",
    64 to "星期日"
)

private val PRESET_ENTRIES = listOf(
    RepeatPreset.ONCE to "仅一次",
    RepeatPreset.DAILY to "每天",
    RepeatPreset.WEEKDAYS to "周一至周五",
    RepeatPreset.WEEKENDS to "周六周日",
    RepeatPreset.CUSTOM to "自定义"
)

/**
 * 重复规则设置页。
 *
 * 本页不再依赖编辑页的 ViewModel：初始选中态由路由参数 [repeatDays] 带入，
 * 用户的选择通过 [onRepeatDaysSelected] 回传给导航层（由其写入结果通道并弹出本页），
 * 从而与编辑页彻底解耦——两者各自持有独立实例。
 *
 * @param repeatDays 进入本页时编辑页当前的重复规则（按位表示周一~周日）
 * @param onRepeatDaysSelected 选定结果回调；实现内部完成「发出结果 + 弹出本页」
 * @param onNavigateBack 仅用于顶部返回按钮/取消路径的逐层返回
 */
@Composable
fun RepeatScreen(
    repeatDays: Int,
    onRepeatDaysSelected: (Int) -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    val currentPreset = AlarmEditViewModel.resolvePreset(repeatDays)

    var showCustomSheet by remember { mutableStateOf(false) }
    var tempSelectedDays by remember { mutableIntStateOf(repeatDays) }

    // 自定义天数弹窗走 MiuixPopupHost（OverlayDialog），展开时让系统栏图标随遮罩翻为浅色
    ScrimActive(showCustomSheet)

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "重复",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                color = MiuixTheme.colorScheme.background,
                titleColor = MiuixTheme.colorScheme.onBackground
            )
        },
        containerColor = MiuixTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            PRESET_ENTRIES.forEach { (preset, label) ->
                val isSelected = !showCustomSheet && currentPreset == preset
                RepeatPresetRow(
                    label = label,
                    isSelected = isSelected,
                    showArrow = preset == RepeatPreset.CUSTOM,
                    onClick = {
                        if (preset == RepeatPreset.CUSTOM) {
                            tempSelectedDays = repeatDays
                            showCustomSheet = true
                        } else {
                            val days = AlarmEditViewModel.presetToDays(preset)
                            if (days != null) onRepeatDaysSelected(days)
                        }
                    }
                )
            }
        }

        if (showCustomSheet) {
            RepeatCustomSheet(
                selectedDays = tempSelectedDays,
                onDayToggle = { bit ->
                    tempSelectedDays = tempSelectedDays xor bit
                },
                onConfirm = {
                    // 先收起弹层再回传：回传会同步弹出本页，若此时弹层仍在会跟着页面一起飞出
                    showCustomSheet = false
                    onRepeatDaysSelected(tempSelectedDays)
                },
                onDismiss = {
                    showCustomSheet = false
                }
            )
        }
    }
}

@Composable
private fun RepeatPresetRow(
    label: String,
    isSelected: Boolean,
    showArrow: Boolean = false,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(12.dp)
    val selectedBg = MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
    val defaultBg = MiuixTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(cardShape)
            .background(
                color = if (isSelected) selectedBg else defaultBg,
                shape = cardShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            Text(
                text = "✓",
                style = MiuixTheme.textStyles.button,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Spacer(Modifier.width(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.title4,
            color = if (isSelected) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onBackground
        )
        Spacer(Modifier.weight(1f))
        if (showArrow) {
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun RepeatCustomSheet(
    selectedDays: Int,
    onDayToggle: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    OverlayDialog(
        show = true,
        title = null,
        onDismissRequest = onDismiss
    ) {
        Column(Modifier.fillMaxWidth()) {
            DialogTitle("自定义")
            // 内容区：weight 占满剩余高度并滚动，保证底部按钮始终可见、不被裁剪
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                DAY_ENTRIES.forEach { (bit, label) ->
                    val isChecked = (selectedDays and bit) != 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDayToggle(bit) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        Checkbox(
                            state = ToggleableState(isChecked),
                            onClick = { onDayToggle(bit) },
                            colors = CheckboxDefaults.checkboxColors(
                                uncheckedBackgroundColor = MiuixTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "确定",
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    enabled = selectedDays != 0,
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}
