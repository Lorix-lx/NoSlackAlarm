// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.alarm.presentation

import lorix.noslackalarm.alarm.R

// ============ 导入布局组件 ============
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Lock

// ============ 导入 Miuix 组件 ============
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.squircle.squircleSurface

// ============ 导入状态管理和交互 ============
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
// ============ 导入动画组件 ============
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer

// ============ 导入UI修饰符和工具 ============
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

// ============ 导入依赖注入和主题 ============
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import lorix.noslackalarm.common.LocalSettingsRepository
import lorix.noslackalarm.system.permission.collectPermissionUiItems
import lorix.noslackalarm.system.permission.hasMissingPermissions
import lorix.noslackalarm.common.RecentTaskHelper
import lorix.noslackalarm.common.SettingsDefaults
import lorix.noslackalarm.common.DeleteHoldLevel
import lorix.noslackalarm.common.theme.NoSlackAlarmTheme
import lorix.noslackalarm.common.theme.LocalIsLight
import lorix.noslackalarm.common.theme.ScrimActive
import lorix.noslackalarm.domain.model.Alarm as AlarmModel
import lorix.noslackalarm.common.util.formatSkipDateLabel
import lorix.noslackalarm.common.util.nextRingTimeMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SwitchDefaults
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.window.WindowListPopup
import lorix.noslackalarm.alarm.presentation.components.CollapsibleTopAppBar
import lorix.noslackalarm.alarm.presentation.components.rememberFixedDropdownPositionProvider
import kotlin.math.absoluteValue

/**
 * 无涟漪的顶栏图标按钮。
 *
 * Miuix IconButton 内部 clickable 未暴露 indication 参数，无法通过公开 API 关闭涟漪，
 * 故在此自封装：用 SinkFeedback（按压缩放下沉）替代默认涟漪，点击时触发系统触觉反馈（短震）。
 * 尺寸、圆角、语义与原 IconButton 保持一致。
 */
@Composable
private fun RippleFreeIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val currentOnClick by rememberUpdatedState(onClick)

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
            .squircleSurface(color = Color.Unspecified, cornerRadius = 40.dp)
            .clickable(
                role = Role.Button,
                interactionSource = interactionSource,
                indication = SinkFeedback(),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentOnClick()
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * 主页与设置页共用的开关配色。
 * - 亮色模式：未启用(off)轨道统一为比背景略深的灰(surfaceVariant)，确保设置页灰底与主页白卡上观感一致；
 * - 暗色模式：沿用 Miuix 默认开关配色（SwitchDefaults.switchColors()），不复写未启用轨道
 */
internal @Composable
fun alarmSwitchColors() = if (LocalIsLight.current) {
    SwitchDefaults.switchColors(
        uncheckedTrackColor = MiuixTheme.colorScheme.surfaceVariant,
        disabledUncheckedTrackColor = MiuixTheme.colorScheme.surfaceVariant
    )
} else {
    SwitchDefaults.switchColors()
}

/**
 * 闹钟列表主界面
 *
 * 这是一个完整的页面布局，包含：
 * - 顶部栏（显示标题和更多选项）
 * - 中间内容区（显示闹钟列表或空状态提示）
 * - 右下角浮动按钮（添加新闹钟）
 *
 * @param viewModel 闹钟列表的视图模型，负责管理数据和业务逻辑
 * @param onNavigateToEdit 点击闹钟卡片时跳转到编辑页面的回调
 * @param onNavigateToCreate 点击浮动按钮时跳转到创建页面的回调
 */
@Composable
fun AlarmListScreen(
    viewModel: AlarmListViewModel = hiltViewModel(),
    highlightAlarmId: Long? = null,
    onHighlightConsumed: () -> Unit = {},
    onNavigateToEdit: (lorix.noslackalarm.domain.model.Alarm) -> Unit = {},
    onNavigateToCreate: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToPermissionInfo: () -> Unit = {}
) {
    // 从ViewModel获取当前界面的状态（包含闹钟列表数据）
    val state by viewModel.state.collectAsState()

    // 控制右上角菜单是否展开的状态
    var menuExpanded by remember { mutableStateOf(false) }

    // 声明本页的 Overlay 激活态，供 NoSlackAlarmTheme 翻转系统栏图标深浅。
    // 只聚合走 MiuixPopupHost（Overlay*）的弹层：重复闹钟拦截弹窗与卡片旁的关闭方式面板；
    // 右上角 menuExpanded 用的是独立 Dialog 窗口（WindowListPopup），自有窗口级变暗，不在此列。
    ScrimActive(state.showDuplicateDialog || state.disableChoiceTarget != null)

    // 隐藏后台任务相关
    val context = LocalContext.current
    val settingsRepository = LocalSettingsRepository.current
    val hideFromRecents by settingsRepository.hideFromRecents.collectAsState(initial = SettingsDefaults.HIDE_FROM_RECENTS)
    val showDebugButton by settingsRepository.showDebugButton.collectAsState(initial = SettingsDefaults.SHOW_DEBUG_BUTTON)
    // 主页卡片长按删除时长档位：fast/default/slow ；映射为实际按住毫秒数传入卡片。
    val deleteHoldLevel by settingsRepository.deleteHoldLevel.collectAsState(initial = SettingsDefaults.DELETE_HOLD_LEVEL)
    val deleteHoldMs = DeleteHoldLevel.from(deleteHoldLevel).ms

    // 卡片边框高亮：新建闹钟返回（外部）与开关类操作（内部）共用同一套 1 秒高亮窗口。
    // highlightSession 为高亮会话计数器，每次触发自增；下面的副作用观察到新会话即点亮边框，
    // 1 秒后熄灭并清空目标，避免旧 id 残留误高亮其他卡片。
    var highlightTargetId by remember { mutableStateOf<Long?>(null) }
    var highlightActive by remember { mutableStateOf(false) }
    var highlightSession by remember { mutableIntStateOf(0) }
    LaunchedEffect(highlightAlarmId) {
        if (highlightAlarmId != null) {
            highlightTargetId = highlightAlarmId
            highlightSession++
            // 通知父级清空，避免下次返回主页时残留的旧 id 误高亮其他卡片
            onHighlightConsumed()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.highlightEvent.collect { alarmId ->
            highlightTargetId = alarmId
            highlightSession++
        }
    }
    LaunchedEffect(highlightSession) {
        if (highlightSession == 0) return@LaunchedEffect
        highlightActive = true
        delay(HIGHLIGHT_DURATION_MS)
        highlightActive = false
        highlightTargetId = null
    }

    // 权限状态检测：进入页与 onResume 时刷新，用于决定是否展示权限警告卡片。
    // 全部权限已开启后卡片自动隐藏；从设置页返回时重新检测。
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionRefresh by remember { mutableIntStateOf(0) }
    val permissionItems by produceState(
        initialValue = emptyList<lorix.noslackalarm.system.permission.PermissionUiItem>(),
        key1 = permissionRefresh
    ) {
        value = collectPermissionUiItems(context)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // 用户手动确认「已开启」的权限集合（无检测 API 的权限，如自启动 / 锁屏显示）。
    // 被确认后视为已满足，使权限提醒卡在用户确实已手动开启后消失。
    val acknowledged by settingsRepository.acknowledgedPermissions.collectAsState(
        initial = SettingsDefaults.ACKNOWLEDGED_PERMISSIONS
    )
    val showPermissionCard = hasMissingPermissions(permissionItems, acknowledged)

    // 顶栏大标题折叠滚动行为：冷启动为展开态（贴左大标题），上滑列表时折叠为顶部居中标题
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            Box {
                CollapsibleTopAppBar(
                    title = "不赖闹钟",
                    largeTitle = "不赖闹钟",
                    actions = {
                        // 更多选项按钮（三个点的图标）
                        RippleFreeIconButton(onClick = { menuExpanded = true }) {
                            Icon(MiuixIcons.More, contentDescription = "更多")
                        }
                    },
                    color = MiuixTheme.colorScheme.background,
                    titleColor = MiuixTheme.colorScheme.onBackground,
                    largeTitleColor = MiuixTheme.colorScheme.onBackground,
                    scrollBehavior = scrollBehavior
                )
                // 弹出菜单（与锚点按钮在同一 Box 内）。
                // 这里刻意用 Window* 而非 Overlay*：定位依赖 Dialog 窗口坐标 + 自定义
                // rememberFixedDropdownPositionProvider()，才能让菜单在大标题展开/折叠两种状态下
                // 保持同一位置（Overlay* 走屏幕内 Popup 锚点，无法拿到窗口坐标系）。
                // 代价是该菜单渲染在独立 Dialog 窗口里，变暗由窗口级 enableWindowDim 绘制、
                // 不经过 Scaffold 的 MiuixPopupHost，故不会触发本项目的 ScrimActive 系统栏图标翻转。
                WindowListPopup(
                    show = menuExpanded,
                    alignment = PopupPositionProvider.Align.End,
                    onDismissRequest = { menuExpanded = false },
                    popupPositionProvider = rememberFixedDropdownPositionProvider(),
                ) {
                    ListPopupColumn {
                        DropdownImpl(
                            text = "设置",
                            optionSize = if (showDebugButton) 3 else 2,
                            index = 0,
                            isSelected = false,
                            onSelectedIndexChange = {
                                menuExpanded = false
                                onNavigateToSettings()
                            }
                        )
                        DropdownImpl(
                            text = "关于",
                            optionSize = if (showDebugButton) 3 else 2,
                            index = 1,
                            isSelected = false,
                            onSelectedIndexChange = {
                                menuExpanded = false
                                onNavigateToAbout()
                            }
                        )
                        if (showDebugButton) {
                            DropdownImpl(
                                text = "调试",
                                optionSize = 3,
                                index = 2,
                                isSelected = false,
                                onSelectedIndexChange = {
                                    menuExpanded = false
                                    viewModel.createDebugAlarm()
                                }
                            )
                        }
                    }
                }
            }
        },

        floatingActionButton = {
            // 新建闹钟按钮：亮色用白底+主题色加号+细灰边框；暗色保持主题色实心+白图标。
            val isLight = LocalIsLight.current
            FloatingActionButton(
                onClick = onNavigateToCreate,
                containerColor = if (isLight) MiuixTheme.colorScheme.surface else MiuixTheme.colorScheme.primary,
                modifier = if (isLight) Modifier.border(0.5.dp, MiuixTheme.colorScheme.outline, CircleShape) else Modifier
            ) {
                Icon(
                    MiuixIcons.Add,
                    contentDescription = "新建闹钟",
                    tint = if (isLight) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onPrimary
                )
            }
        },

        containerColor = MiuixTheme.colorScheme.background,
        modifier = Modifier.graphicsLayer()
    ) { padding ->

        // 无权限缺失且无闹钟时，保留全屏居中空态；其余情况统一走可滚动列表
        if (!showPermissionCard && state.alarms.isEmpty()) {
            EmptyAlarmList(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 权限缺失时，在列表顶部以类似闹钟卡片的警告卡提示，点击进入权限信息页
                if (showPermissionCard) {
                    item(key = "permission_warning") {
                        PermissionWarningCard(onClick = onNavigateToPermissionInfo)
                    }
                }

                if (state.alarms.isEmpty()) {
                    item(key = "empty") {
                        EmptyAlarmList(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp)
                        )
                    }
                } else {
                    // 遍历闹钟列表，为每个闹钟创建一个卡片。
                    // 每张卡片外包一层锚 Box：待选操作的卡片在该 Box 内组合选择面板，
                    // 面板即以 Box 为锚贴近卡片出现。
                    items(state.alarms, key = { it.id }) { alarm ->
                        val choiceTarget = state.disableChoiceTarget
                        // 以目标闹钟快照恒定计算下次响铃（非目标项也求值，保证组合顺序稳定），
                        // 面板文案不随等待期间时刻漂移
                        val choiceNextRing = remember(choiceTarget) {
                            choiceTarget?.let { nextRingTimeMillis(it) } ?: 0L
                        }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            AlarmItem(
                                alarm = alarm,
                                highlighted = highlightActive && alarm.id == highlightTargetId,
                                holdMs = deleteHoldMs,
                                // 目标卡片在弹窗打开期间临时按关闭态呈现，取消后自动复原
                                choicePending = choiceTarget?.id == alarm.id,
                                onToggle = { viewModel.requestToggle(alarm) },
                                onEdit = { onNavigateToEdit(alarm) },
                                onDelete = { viewModel.deleteAlarm(alarm) }
                            )
                            if (choiceTarget?.id == alarm.id) {
                                DisableChoicePanel(
                                    nextRingMillis = choiceNextRing,
                                    onSkipNextOnly = viewModel::skipNextRingOnly,
                                    onDisablePermanently = viewModel::disableRepeatingAlarm,
                                    onDismiss = viewModel::dismissDisableChoice
                                )
                            }
                        }
                    }
                }
            }

            // 重复闹钟拦截提示：命中响铃倒计时相同的闹钟时拒绝创建并弹窗
            OverlayDialog(
                show = state.showDuplicateDialog,
                title = stringResource(R.string.duplicate_title),
                summary = stringResource(R.string.duplicate_message),
                onDismissRequest = viewModel::dismissDuplicateDialog
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        text = stringResource(R.string.duplicate_confirm),
                        onClick = viewModel::dismissDuplicateDialog,
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }

        }
    }
}

/**
 * 卡片旁的操作选择面板（锚定在对应闹钟卡片上）。
 *
 * 用 Miuix 的 [OverlayListPopup] 以组合处的锚 Box 定位：面板自动贴近卡片下方，
 * 下方空间不足时翻到上方防溢出；宽度由 [ListPopupColumn] 按内容自适应（200–288.dp），
 * 观感类似菜单气泡。保留整屏变暗与点外部 / 返回键收起。
 *
 * 每行文字左对齐；本组件必须与目标闹钟卡片置于同一锚 Box 内，否则无法贴近卡片。
 *
 * @param nextRingMillis 该闹钟的下次响铃时刻，用于渲染「仅在…关闭一次」的日期
 * @param onSkipNextOnly 仅关闭下一次响铃
 * @param onDisablePermanently 永久关闭该重复闹钟
 * @param onDismiss 取消或点击遮罩/返回键关闭
 */
@Composable
private fun DisableChoicePanel(
    nextRingMillis: Long,
    onSkipNextOnly: () -> Unit,
    onDisablePermanently: () -> Unit,
    onDismiss: () -> Unit
) {
    OverlayListPopup(
        show = true,
        // 面板右缘贴近卡片（锚 Box）右缘，靠近卡片右侧的开关位置
        alignment = PopupPositionProvider.Align.End,
        // 右侧水平留 8.dp 间隙，避免面板紧贴屏幕/卡片最右缘
        popupPositionProvider = ListPopupDefaults.dropdownPositionProvider(horizontalMargin = 8.dp),
        onDismissRequest = onDismiss,
    ) {
        ListPopupColumn {
            // 标题行：居中，用选项同级的 onSurface 保证可读性；顶部留出与菜单首行一致的呼吸空间
            Text(
                text = stringResource(R.string.disable_choice_title),
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
            )
            // 两个选项文字同色（onSurface），左对齐
            PopupChoiceRow(
                text = stringResource(
                    R.string.disable_choice_skip_once,
                    formatSkipDateLabel(nextRingMillis)
                ),
                textColor = MiuixTheme.colorScheme.onSurface,
                onClick = onSkipNextOnly
            )
            PopupChoiceRow(
                text = stringResource(R.string.disable_choice_disable_forever),
                textColor = MiuixTheme.colorScheme.onSurface,
                onClick = onDisablePermanently
            )
            // 「取消」按钮置于面板最后一行：外边左右留 16.dp，
            // 使按钮宽度只比面板宽度小一点，同时作为末行给底部留出呼吸空间
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp)
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 面板内可左对齐的可点击行，观感对齐 Miuix 下拉菜单行。
 *
 * @param text 行文案
 * @param textColor 文案颜色
 * @param textAlign 文字对齐方式：选项行左对齐；需要时可用居中
 * @param onClick 点击回调
 */
@Composable
private fun PopupChoiceRow(
    text: String,
    textColor: Color,
    onClick: () -> Unit,
    textAlign: TextAlign = TextAlign.Start,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(),
                onClick = onClick
            )
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = 12.dp,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            color = textColor,
            textAlign = textAlign,
            // 撑满行宽，使 textAlign 真正生效（否则文本按其内容宽度排版）
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 权限警告卡片
 *
 * 类闹钟卡片样式，检测到有权限未开启时显示在列表顶部。含警告图标与提示文案，
 * 整卡可点击进入「权限信息」页。配色沿用主题（亮色白卡 / 暗色 primaryContainer），
 * 图标与强调文字使用 error 色以传达警示。
 *
 * @param onClick 点击卡片的回调（跳转权限信息页）
 */
@Composable
private fun PermissionWarningCard(onClick: () -> Unit) {
    val isLight = LocalIsLight.current
    val cardColor = if (isLight) {
        MiuixTheme.colorScheme.surface
    } else {
        MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(),
                onClick = onClick
            ),
        colors = CardDefaults.defaultColors(color = cardColor),
        cornerRadius = 16.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = MiuixIcons.Info,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "建议开启权限",
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = "为了确保闹钟正常启动，请开启相关权限，点击进入权限设置",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** 卡片边框高亮持续时间（ms）：点亮后停留该时长再淡出为透明。 */
private const val HIGHLIGHT_DURATION_MS = 1000L

/**
 * 计算启用态倒计时所需的进度与文案，集中单一心跳，供内容列文字行共用。
 *
 * 同时产出「是否正处于仅关闭下一次响铃的临时关闭态」：它由当前时间派生，
 * 复用同一份每秒心跳即可在被跳过的时刻一过时自动失效，无需额外的计时协程。
 */
@Composable
private fun rememberCountdown(alarm: AlarmModel): CountdownInfo {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(alarm.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    // 跟随 now 实时重算：跨天后能自动指向新的下次响铃，跳过态失效后也能立刻恢复正常倒计时
    val nextRing = nextRingTimeMillis(alarm, now)
    val remainingMs = (nextRing - now).coerceAtLeast(0L)
    val skipping = alarm.skipNextTriggerMillis != AlarmModel.NO_SKIPPED_RING &&
        alarm.skipNextTriggerMillis > now
    return CountdownInfo(text = formatCountdown(remainingMs), skippingNextRing = skipping)
}

/** 倒计时文案的快照，由 [rememberCountdown] 在同一心跳内产出，避免重复计算。 */
private data class CountdownInfo(
    val text: String,
    /** 该闹钟是否正处于「仅关闭下一次响铃」的临时关闭态。 */
    val skippingNextRing: Boolean
)

/** 将剩余毫秒格式化为紧凑倒计时文案（天 / 时 / 分）。 */
private const val MINUTE_MS = 60 * 1000L

private fun formatCountdown(remainingMs: Long): String {
    val totalMinutes = remainingMs / MINUTE_MS
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes / 60) % 24
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "${days}天${hours}时"
        hours > 0 -> "${hours}时${minutes}分"
        else -> "${minutes}分"
    }
}

/**
 * 单个闹钟卡片组件
 *
 * 每个卡片显示一个闹钟的信息：
 * - 时间
 * - 重复周期和（提醒方式 / 仅关闭下一次响铃的提示）
 * - 可选的标签
 * - 开关按钮
 * - 删除按钮
 *
 * @param alarm 闹钟数据对象
 * @param choicePending 该卡片是否正在等待「选择操作」面板的关闭方式选择：
 *   等待期间只把滑块临时显示为关闭（不真禁用、不改数据），取消后自动复原
 * @param onToggle 开关闹钟的回调函数（关闭方式的分流由 ViewModel 负责）
 * @param onEdit 编辑闹钟的回调函数
 * @param onDelete 删除闹钟的回调函数
 */
@Composable
private fun AlarmItem(
    alarm: AlarmModel,
    highlighted: Boolean = false,
    holdMs: Int = DeleteHoldLevel.DEFAULT.ms,
    choicePending: Boolean = false,
    onToggle: () -> Unit,
    onEdit: (AlarmModel) -> Unit,
    onDelete: () -> Unit
) {
    // 倒计时文案：统一心跳计算，启用态在「重复日期 + 提醒方式」行右侧展示；禁用态不渲染。
    // 同时产出跳过态标志，随每秒心跳自动失效，实现「仅关闭下一次响铃」的到点自动恢复。
    val countdown = rememberCountdown(alarm)
    // 有效启用态：处于临时关闭（跳过一次）时闹钟本体仍为启用，但对用户呈现为关闭。
    // 卡片其余外观（背景/透明度/倒计时）都以它为准，choicePending 只作用于滑块显示。
    val effectiveEnabled = alarm.isEnabled && !countdown.skippingNextRing

    // 亮色下卡片为纯白(surface)；暗色沿用 primaryContainer 的启用/禁用两档透明度。
    val isLight = LocalIsLight.current
    val cardColor by animateColorAsState(
        targetValue = if (isLight) {
            MiuixTheme.colorScheme.surface
        } else if (effectiveEnabled) {
            MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) // 启用状态的背景颜色
        } else {
            MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) // 禁用状态的背景颜色
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (effectiveEnabled) 1f else 0.5f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )

    // 高亮时显示彩色边框，否则透明（始终保留边框占位，避免高亮时布局抖动）
    val borderColor by animateColorAsState(
        targetValue = if (highlighted) MiuixTheme.colorScheme.primary else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )

    // ===== 长按删除交互状态 =====
    // progress: 0f(红线缩回右侧中点) → 1f(红线在左侧中点汇合)
    // isPressed: 手指是否正按住删除按钮
    // isDeleting: 已触发删除，锁定循环避免回退
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
    // 松开收回线条比按住扫向左边更快一点（约 0.6 倍时长），更跟手。
    val retractMs = (holdMs * 0.6f).toInt()
    // 边框进度：按住时 target=1（holdMs 从右中扫到左中），松手时 target=0（retractMs 原路更快退回）。
    // animateFloatAsState 返回标准 Compose State，组合中读取必触发重组，红线据此实时刷新。
    var isPressed by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    // 内层包裹 Row 的 Box 实测尺寸，仅取决于 Row 内容，不受删除进度 Canvas 反撑，
    // 根除禁用态下陈旧尺寸把卡片下方撑出空白的死锁。
    var cardBoxSize by remember { mutableStateOf(IntSize.Zero) }

    val maskProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = if (isPressed) tween(durationMillis = holdMs, easing = LinearEasing)
                        else tween(durationMillis = retractMs, easing = LinearEasing)
    )

    // 按住满 holdMs（maskProgress 到达 1）触发短震动并删除；中途松手协程被取消，不删除。
    LaunchedEffect(isPressed) {
        if (isDeleting || !isPressed) return@LaunchedEffect
        snapshotFlow { maskProgress }.first { it >= 0.999f }
        vibrate(vibrator) // 满 holdMs：短震动反馈
        isDeleting = true
        onDelete()        // 自动删除（手指可仍按住）
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .border(2.dp, borderColor, RoundedCornerShape(16.dp)),
        colors = CardDefaults.defaultColors(color = cardColor),
        cornerRadius = 16.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { cardBoxSize = it }
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = SinkFeedback(),
                            onClick = { onEdit(alarm) }
                        ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 时间跟随系统制式（与 TimePicker 一致）：24 小时制保持原样，
                    // 12 小时制转换为 1–12 小时，并在时间右下角显示小一号的「上午/下午」。
                    val is24Hour = DateFormat.is24HourFormat(context)
                    val (hour12, isPm) = from24HourTo12(alarm.hour)
                    val timeText = if (is24Hour) {
                        String.format("%02d:%02d", alarm.hour, alarm.minute)
                    } else {
                        String.format("%02d:%02d", hour12, alarm.minute)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.alpha(contentAlpha)
                    ) {
                        Text(
                            text = timeText,
                            style = MiuixTheme.textStyles.title1,
                            modifier = Modifier.alignByBaseline()
                        )
                        if (!is24Hour) {
                            Text(
                                text = stringResource(if (isPm) R.string.pm else R.string.am),
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier.alignByBaseline()
                            )
                        }
                        if (effectiveEnabled) {
                            Text(
                                text = stringResource(R.string.countdown_description, countdown.text),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier.alignByBaseline()
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 临时关闭态下用「仅关闭 x月x日（周几）」取代提醒方式信息
                        val secondaryText = if (countdown.skippingNextRing) {
                            stringResource(
                                R.string.skip_next_hint,
                                formatSkipDateLabel(alarm.skipNextTriggerMillis)
                            )
                        } else {
                            alarm.mode.label
                        }
                        Text(
                            text = formatRepeatDays(alarm.repeatDays) + "  " + secondaryText,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = contentAlpha)
                        )
                    }

                    val labelText = alarm.label
                    if (!labelText.isNullOrBlank()) {
                        Text(
                            text = labelText,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.7f * contentAlpha)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(
                        // 等待关闭方式选择期间，滑块临时按关闭显示；闹钟并未被禁用
                        checked = effectiveEnabled && !choicePending,
                        onCheckedChange = { onToggle() },
                        colors = alarmSwitchColors()
                    )
                    // 删除按钮：长按 2 秒删除
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isPressed = true
                                        try {
                                            tryAwaitRelease()
                                        } finally {
                                            isPressed = false // 松手/移出按钮/列表滚动均回退
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            MiuixIcons.Delete,
                            contentDescription = "删除（长按）",
                            tint = MiuixTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            }

            // 长按删除进度：沿卡片圆角矩形边框绘制的红色(error)进度描边。
            // 从右侧竖线中点分叉出上下两路，按住时同步往左扫，满按住时长在左侧中点汇合；
            // 松手/中断由 maskProgress 退回 0、原路缩回右中。纯 Canvas 不拦截触摸。
            // Canvas 尺寸取自 Row 内层 Box 实测值，不反向撑高卡片（避免禁用态空白）。
            val deleteLineColor = MiuixTheme.colorScheme.error
            if (cardBoxSize != IntSize.Zero) {
                val boxW = with(LocalDensity.current) { cardBoxSize.width.toDp() }
                val boxH = with(LocalDensity.current) { cardBoxSize.height.toDp() }
                Canvas(Modifier.size(boxW, boxH)) {
                    drawProgressBorder(maskProgress, deleteLineColor, 2.dp.toPx())
                }
            }
        }
    }
}

/**
 * 沿卡片圆角矩形边框绘制长按删除进度红线。
 *
 * 路径从右侧竖线中点分叉为上下两路（上臂、下臂），各自沿半圆角边框往左扫，
 * 在左侧中点汇合。两臂长度相等，故同一 progress 下同步到达左中。
 * progress 0→1：红线从右中长出、往左扫，至 1 在左中闭合；松手/中断退回 0 缩回右中。
 *
 * @param progress 0f(红线缩回右中) → 1f(在左中闭合)
 * @param color    红色主题色（MiuixTheme.colorScheme.error）
 * @param strokePx 描边宽度（像素），对齐卡片 2.dp 边框
 */
private fun DrawScope.drawProgressBorder(progress: Float, color: Color, strokePx: Float) {
    if (progress <= 0f) return
    // 内缩半个线宽，使描边整体落在卡片裁剪区内（不被 16.dp 圆角裁掉边缘）
    val inset = strokePx / 2f
    val left = inset
    val top = inset
    val right = size.width - inset
    val bottom = size.height - inset
    val midY = (top + bottom) / 2f
    val r = 16.dp.toPx().coerceAtMost(minOf((right - left) / 2f, (bottom - top) / 2f))
    val style = Stroke(width = strokePx, cap = StrokeCap.Round)
    drawArm(buildArmPath(isTop = true, left, top, right, bottom, midY, r), progress, color, style)
    drawArm(buildArmPath(isTop = false, left, top, right, bottom, midY, r), progress, color, style)
}

/**
 * 构建单臂圆角边框路径：从右中分叉，沿对应半圆角边框扫到左中。
 * - isTop=true：右中 → 右上角 → 上边 → 左上角 → 左中
 * - isTop=false：右中 → 右下角 → 下边 → 左下角 → 左中
 */
private fun buildArmPath(
    isTop: Boolean,
    left: Float, top: Float, right: Float, bottom: Float, midY: Float, r: Float
): Path {
    val p = Path()
    p.moveTo(right, midY)
    if (isTop) {
        p.lineTo(right, top + r)
        p.arcTo(Rect(right - 2 * r, top, right, top + 2 * r), 0f, -90f, false) // 右上圆角
        p.lineTo(left + r, top)
        p.arcTo(Rect(left, top, left + 2 * r, top + 2 * r), 270f, -90f, false) // 左上圆角
        p.lineTo(left, midY)
    } else {
        p.lineTo(right, bottom - r)
        p.arcTo(Rect(right - 2 * r, bottom - 2 * r, right, bottom), 0f, 90f, false) // 右下圆角
        p.lineTo(left + r, bottom)
        p.arcTo(Rect(left, bottom - 2 * r, left + 2 * r, bottom), 90f, 90f, false) // 左下圆角
        p.lineTo(left, midY)
    }
    return p
}

/**
 * 用 PathMeasure 按 progress 截取单臂路径前缀段并描边（圆角段也能精确截断）。
 */
private fun DrawScope.drawArm(path: Path, progress: Float, color: Color, style: Stroke) {
    if (progress <= 0f) return
    val pm = PathMeasure()
    pm.setPath(path, false)
    val seg = Path()
    pm.getSegment(0f, pm.length * progress, seg, true)
    drawPath(seg, color, style = style)
}

/**
 * 长按满后触发一次短震动反馈
 */
private fun vibrate(vibrator: Vibrator) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(30)
    }
}

/**
 * 空状态组件
 *
 * 当没有闹钟时显示的提示界面
 */
@Composable
private fun EmptyAlarmList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "还没有闹钟",
            style = MiuixTheme.textStyles.title2,
            color = MiuixTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "点击右下角 + 开始创建",
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 格式化重复日期显示
 *
 * 将数字编码转换为人可读的日期描述
 * 例如：
 * - 0 → "仅一次"
 * - 127 → "每天"
 * - 31 → "周一至周五"
 * - 96 → "周末"
 * - 3 → "周一, 周二"
 *
 * @param days 日期的位编码（每个bit代表一天）
 * @return 格式化的日期字符串
 */
private fun formatRepeatDays(days: Int): String {
    return when (days) {
        0 -> "仅一次"    // 不重复
        127 -> "每天"     // 全部7天：1+2+4+8+16+32+64=127
        31 -> "周一至周五" // 工作日：1+2+4+8+16=31
        96 -> "周末"      // 周六+周日：32+64=96
        else -> {
            // 自定义重复：只显示选中的几天
            val labels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
            val bits = listOf(1, 2, 4, 8, 16, 32, 64)
            labels.filterIndexed { index, _ -> (days and bits[index]) != 0 }
                .joinToString(", ")
                .ifEmpty { "仅一次" }
        }
    }
}

/**
 * 界面预览
 *
 * 在Android Studio中可以实时预览这个界面
 */
@Preview
@Composable
private fun AlarmListScreenPreview() {
    NoSlackAlarmTheme {
        AlarmListScreen()
    }
}
