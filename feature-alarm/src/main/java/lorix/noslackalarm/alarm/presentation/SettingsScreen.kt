// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.alarm.presentation

// ============ 导入布局组件 ============
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

// ============ 导入 Miuix 组件 ============
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

// ============ 导入 Miuix Preference 组件 ============
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ============ 导入状态管理和交互 ============
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import lorix.noslackalarm.common.LocalSettingsRepository
import lorix.noslackalarm.common.SettingsDefaults
import lorix.noslackalarm.common.DeleteHoldLevel
import lorix.noslackalarm.common.KeyboardLayout
import lorix.noslackalarm.common.theme.ThemeMode
import lorix.noslackalarm.common.theme.ScrimActive
import lorix.noslackalarm.common.util.formatMinuteOfDay
import kotlinx.coroutines.launch

/**
 * 设置页面
 *
 * 使用 Miuix Preference 组件构建的设置页面，包含：
 * - 闹钟设置：默认提醒模式、智能默认时间、删除确认时长
 * - 答题设置：答题键盘布局
 * - 外观：主题模式
 * - 其他：后台隐藏、权限信息
 *
 * @param onNavigateBack 返回上一页的回调
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToPermissionInfo: () -> Unit = {}
) {
    val settingsRepository = LocalSettingsRepository.current
    val coroutineScope = rememberCoroutineScope()

    // 从 DataStore 读取当前设置值
    val defaultMode by settingsRepository.defaultMode.collectAsState(initial = SettingsDefaults.DEFAULT_MODE)
    val maxSnoozeCount by settingsRepository.maxSnoozeCount.collectAsState(initial = SettingsDefaults.MAX_SNOOZE_COUNT)
    val hideFromRecents by settingsRepository.hideFromRecents.collectAsState(initial = SettingsDefaults.HIDE_FROM_RECENTS)
    val showDebugButton by settingsRepository.showDebugButton.collectAsState(initial = SettingsDefaults.SHOW_DEBUG_BUTTON)

    // 智能默认时间配置
    val nightStartMin by settingsRepository.nightStartMin.collectAsState(initial = SettingsDefaults.NIGHT_START_MIN)
    val nightEndMin by settingsRepository.nightEndMin.collectAsState(initial = SettingsDefaults.NIGHT_END_MIN)
    val nightFixedHour by settingsRepository.nightFixedHour.collectAsState(initial = SettingsDefaults.NIGHT_FIXED_HOUR)
    val nightFixedMinute by settingsRepository.nightFixedMinute.collectAsState(initial = SettingsDefaults.NIGHT_FIXED_MIN)

    // 答题键盘布局
    val keyboardLayout by settingsRepository.keyboardLayout.collectAsState(initial = SettingsDefaults.KEYBOARD_LAYOUT)
    val keyboardLayoutLabels = remember {
        listOf("电话键盘 (1 2 3)", "计算器 (7 8 9)")
    }
    val keyboardLayoutValues = remember {
        listOf(KeyboardLayout.PHONE.value, KeyboardLayout.CALCULATOR.value)
    }
    val selectedKeyboardIndex = remember(keyboardLayout) {
        keyboardLayoutValues.indexOf(keyboardLayout).coerceAtLeast(0)
    }

    // 主题模式
    val themeMode by settingsRepository.themeMode.collectAsState(initial = SettingsDefaults.THEME_MODE)
    val themeModeLabels = remember { listOf("跟随系统", "亮色", "暗色") }
    val themeModeValues = remember {
        listOf(ThemeMode.SYSTEM.value, ThemeMode.LIGHT.value, ThemeMode.DARK.value)
    }
    val selectedThemeIndex = remember(themeMode) {
        themeModeValues.indexOf(themeMode).coerceAtLeast(0)
    }

    // 删除确认时长（长按删除闹钟所需按住时长档位）：fast/default/slow
    val deleteHoldLevel by settingsRepository.deleteHoldLevel.collectAsState(initial = SettingsDefaults.DELETE_HOLD_LEVEL)
    val deleteHoldIndex = remember(deleteHoldLevel) {
        DeleteHoldLevel.entries.indexOf(DeleteHoldLevel.from(deleteHoldLevel)).coerceAtLeast(0)
    }
    // 档位标签即枚举顺序：快(左) / 默认(中) / 慢(右)
    val deleteHoldLabels = remember { DeleteHoldLevel.entries.map { it.label } }

    // 智能默认时间弹窗的显隐状态
    var showSmartTimeDialog by remember { mutableStateOf(false) }

    // 是否有任意下拉弹窗（OverlayDropdownPreference）展开中；与 showSmartTimeDialog 共同决定遮罩是否激活。
    var dropdownExpanded by remember { mutableStateOf(false) }

    // 声明当前页面存在激活的 Overlay（下拉展开 或 智能默认时间弹窗）；
    // 由 NoSlackAlarmTheme 通过 LocalScrimActive 集中驱动系统栏/导航栏随遮罩同步变暗，无需手动 import 全局状态。
    ScrimActive(showSmartTimeDialog || dropdownExpanded)

    // 模式列表映射：标签 -> 枚举值
    val modeLabels = remember {
        listOf("仅震动","仅铃声", "震动+铃声")
    }
    val modeValues = remember {
        listOf("VIBRATE_ONLY","RINGTONE_ONLY","BOTH",)
    }
    val selectedModeIndex = remember(defaultMode) {
        modeValues.indexOf(defaultMode).coerceAtLeast(0)
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "设置",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回"
                        )
                    }
                },
                color = MiuixTheme.colorScheme.background,
                titleColor = MiuixTheme.colorScheme.onBackground
            )
        },
        containerColor = MiuixTheme.colorScheme.background,
        modifier = Modifier.graphicsLayer()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ======= 闹钟设置分组 =======
            SmallTitle(text = "闹钟设置")

            // 默认提醒模式
            OverlayDropdownPreference(
                insideMargin = PaddingValues(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                title = "默认提醒模式",
                summary = "创建新闹钟时的默认提醒方式",
                items = modeLabels,
                onExpandedChange = { dropdownExpanded = it },
                selectedIndex = selectedModeIndex,
                onSelectedIndexChange = { index ->
                    if (index in modeValues.indices) {
                        coroutineScope.launch {
                            settingsRepository.setDefaultMode(modeValues[index])
                        }
                    }
                }
            )

            // 智能默认时间
            ArrowPreference(
                insideMargin = PaddingValues(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                title = "智能默认时间",
                summary = "${formatMinuteOfDay(nightStartMin)}~${formatMinuteOfDay(nightEndMin)} → ${String.format("%02d:%02d", nightFixedHour, nightFixedMinute)}",
                onClick = { showSmartTimeDialog = true },
                holdDownState = showSmartTimeDialog
            )

            // 删除确认时长：长按删除闹钟需按住的时长（快 / 默认 / 慢）
            OverlayDropdownPreference(
                insideMargin = PaddingValues(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                title = "删除确认时长",
                summary = "长按删除闹钟需按住的时长",
                items = deleteHoldLabels,
                onExpandedChange = { dropdownExpanded = it },
                selectedIndex = deleteHoldIndex,
                onSelectedIndexChange = { index ->
                    if (index in DeleteHoldLevel.entries.indices) {
                        coroutineScope.launch {
                            settingsRepository.setDeleteHoldLevel(DeleteHoldLevel.entries[index].value)
                        }
                    }
                }
            )

            // ======= 分类分割线：闹钟设置 / 答题设置 =======
            HorizontalDivider(
                modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
            )

            // ======= 答题设置分组 =======
            SmallTitle(text = "答题设置")

            OverlayDropdownPreference(
                insideMargin = PaddingValues(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                title = "答题键盘布局",
                summary = "选择解锁闹钟的数学键盘布局",
                items = keyboardLayoutLabels,
                onExpandedChange = { dropdownExpanded = it },
                selectedIndex = selectedKeyboardIndex,
                onSelectedIndexChange = { index ->
                    if (index in keyboardLayoutValues.indices) {
                        coroutineScope.launch {
                            settingsRepository.setKeyboardLayout(keyboardLayoutValues[index])
                        }
                    }
                }
            )

            // ======= 分类分割线：答题设置 / 其他 =======
            HorizontalDivider(
                modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
            )

            // ======= 外观设置分组 =======
            SmallTitle(text = "外观")

            OverlayDropdownPreference(
                insideMargin = PaddingValues(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                title = "主题模式",
                summary = "亮色、暗色或跟随系统",
                items = themeModeLabels,
                onExpandedChange = { dropdownExpanded = it },
                selectedIndex = selectedThemeIndex,
                onSelectedIndexChange = { index ->
                    if (index in themeModeValues.indices) {
                        coroutineScope.launch {
                            settingsRepository.setThemeMode(themeModeValues[index])
                        }
                    }
                }
            )

            // ======= 分类分割线：外观设置 / 其他 =======
            HorizontalDivider(
                modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
            )

            // ======= 其他设置分组 =======
            SmallTitle(text = "其他")

            SwitchPreference(
                insideMargin = PaddingValues(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                title = "后台隐藏",
                summary = "在最近任务中隐藏app",
                checked = hideFromRecents,
                onCheckedChange = { hide ->
                    coroutineScope.launch {
                        settingsRepository.setHideFromRecents(hide)
                    }
                },
                switchColors = alarmSwitchColors()
            )

            SwitchPreference(
                insideMargin = PaddingValues(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                title = "调试开关",
                summary = "在主页面下拉菜单显示调试入口",
                checked = showDebugButton,
                onCheckedChange = { show ->
                    coroutineScope.launch {
                        settingsRepository.setShowDebugButton(show)
                    }
                },
                switchColors = alarmSwitchColors()
            )

            // 权限信息：跳转应用内权限信息页，查看并管理应用所需权限
            ArrowPreference(
                insideMargin = PaddingValues(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                title = "权限信息",
                summary = "查看并管理闹钟响铃所需的各项权限",
                onClick = onNavigateToPermissionInfo
            )
        }

        // 智能默认时间弹窗（含开始时间、结束时间、默认闹钟）
        SmartTimeDialog(
            show = showSmartTimeDialog,
            nightStartMin = nightStartMin,
            nightEndMin = nightEndMin,
            nightFixedHour = nightFixedHour,
            nightFixedMinute = nightFixedMinute,
            onConfirm = { startMin, endMin, fixedH, fixedM ->
                coroutineScope.launch {
                    settingsRepository.setNightStartMin(startMin)
                    settingsRepository.setNightEndMin(endMin)
                    settingsRepository.setNightFixedHour(fixedH)
                    settingsRepository.setNightFixedMinute(fixedM)
                }
            },
            onDismiss = { showSmartTimeDialog = false }
        )
    }
}

/**
 * 智能默认时间弹窗：三行时间列表 + 共享 NumberPicker 选中式编辑。
 * 点击可快速切换要调整的时间，只占一组拾取器空间，弹窗居中不贴底。
 *
 * @param show 是否显示
 * @param nightStartMin 开始时间（当日分钟数）
 * @param nightEndMin 结束时间（当日分钟数）
 * @param nightFixedHour 默认闹钟时
 * @param nightFixedMinute 默认闹钟分
 * @param onConfirm 确认回调(startMin, endMin, fixedHour, fixedMinute)
 * @param onDismiss 关闭回调
 */
@Composable
private fun SmartTimeDialog(
    show: Boolean,
    nightStartMin: Int,
    nightEndMin: Int,
    nightFixedHour: Int,
    nightFixedMinute: Int,
    onConfirm: (Int, Int, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    var startHour by remember { mutableIntStateOf(nightStartMin / 60) }
    var startMinute by remember { mutableIntStateOf(nightStartMin % 60) }
    var endHour by remember { mutableIntStateOf(nightEndMin / 60) }
    var endMinute by remember { mutableIntStateOf(nightEndMin % 60) }
    var fixedHour by remember { mutableIntStateOf(nightFixedHour) }
    var fixedMinute by remember { mutableIntStateOf(nightFixedMinute) }

    // 每次打开时，重置状态
    LaunchedEffect(show) {
        if (show) {
            selectedIndex = 0
            startHour = nightStartMin / 60
            startMinute = nightStartMin % 60
            endHour = nightEndMin / 60
            endMinute = nightEndMin % 60
            fixedHour = nightFixedHour
            fixedMinute = nightFixedMinute
        }
    }

    // 稳定化的回调：只在 selectedIndex 变化时才重建 lambda
    val onHourChange: (Int) -> Unit = remember(selectedIndex) {
        { newValue ->
            when (selectedIndex) {
                0 -> startHour = newValue
                1 -> endHour = newValue
                else -> fixedHour = newValue
            }
        }
    }
    val onMinuteChange: (Int) -> Unit = remember(selectedIndex) {
        { newValue ->
            when (selectedIndex) {
                0 -> startMinute = newValue
                1 -> endMinute = newValue
                else -> fixedMinute = newValue
            }
        }
    }

    OverlayDialog(
        title = "智能默认时间",
        show = show,
        onDismissRequest = onDismiss
    ) {
        Column {
            // 三行可选时间列表
            TimeSelectRow(
                label = "开始时间",
                hour = startHour,
                minute = startMinute,
                selected = selectedIndex == 0,
                onClick = { selectedIndex = 0 }
            )
            TimeSelectRow(
                label = "结束时间",
                hour = endHour,
                minute = endMinute,
                selected = selectedIndex == 1,
                onClick = { selectedIndex = 1 }
            )
            TimeSelectRow(
                label = "默认闹钟",
                hour = fixedHour,
                minute = fixedMinute,
                selected = selectedIndex == 2,
                onClick = { selectedIndex = 2 }
            )
        }

        Spacer(Modifier.height(12.dp))

        // 共享 NumberPicker — 用 key 强制切换时重建，避免旧状态残留
        key(selectedIndex) {
            val currentHour = when (selectedIndex) {
                0 -> startHour; 1 -> endHour; else -> fixedHour
            }
            val currentMinute = when (selectedIndex) {
                0 -> startMinute; 1 -> endMinute; else -> fixedMinute
            }
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                NumberPicker(
                    value = currentHour,
                    onValueChange = onHourChange,
                    range = 0..23,
                    label = { it.toString().padStart(2, '0') },
                    wrapAround = true,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = ":",
                    style = MiuixTheme.textStyles.title1,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                NumberPicker(
                    value = currentMinute,
                    onValueChange = onMinuteChange,
                    range = 0..59,
                    label = { it.toString().padStart(2, '0') },
                    wrapAround = true,
                    modifier = Modifier.weight(1f),
                )
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
                onClick = {
                    onConfirm(
                        startHour * 60 + startMinute,
                        endHour * 60 + endMinute,
                        fixedHour,
                        fixedMinute
                    )
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

/**
 * 可选的单行时间条目：圆点指示器 + 标签 + 当前时间值
 */
@Composable
private fun TimeSelectRow(
    label: String,
    hour: Int,
    minute: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = if (selected) "●" else "○",
            color = if (selected) MiuixTheme.colorScheme.primary
            else MiuixTheme.colorScheme.onSurfaceSecondary,
            style = MiuixTheme.textStyles.body1,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            color = if (selected) MiuixTheme.colorScheme.primary
            else MiuixTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = String.format("%02d:%02d", hour, minute),
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
        )
    }
}