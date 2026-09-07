// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.alarm.presentation

// ============ 导入布局组件 ============
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size

// ============ 导入图标和素材 ============
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward

// ============ 导入 Miuix 组件 ============
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ============ 导入状态管理和交互 ============
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

// ============ 导入UI修饰符和工具 ============
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.res.stringResource
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.overlay.OverlayDialog

// ============ 导入依赖注入和自定义组件 ============
import androidx.hilt.navigation.compose.hiltViewModel
import lorix.noslackalarm.alarm.R
import lorix.noslackalarm.alarm.presentation.components.TimePicker
import lorix.noslackalarm.common.theme.NoSlackAlarmTheme
import lorix.noslackalarm.domain.model.AlarmMode
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.TextFieldDefaults

/**
 * 闹钟编辑/新建界面
 * 
 * 用于创建新闹钟或编辑已有闹钟的界面，包含：
 * - 时间选择器（上下午（12小时制）、小时和分钟滑块）
 * - 重复日期选择器（周一到周日）
 * - 提醒方式选择器
 * - 标签输入框（可选）
 * - 保存按钮
 * 
 * @param viewModel 编辑页面的视图模型，管理表单状态和保存逻辑
 * @param alarmId 要编辑的闹钟ID，-1表示新建（用于Navigation3）
 * @param initialHour 初始小时（用于Navigation3）
 * @param initialMinute 初始分钟（用于Navigation3）
 * @param onNavigateBack 返回上一页的回调，参数为新建闹钟的 id（编辑时为空）
 */
@Composable
fun AlarmEditScreen(
    viewModel: AlarmEditViewModel = hiltViewModel(),
    alarmId: Long = -1L,
    initialHour: Int = 7,
    initialMinute: Int = 30,
    onNavigateBack: (newAlarmId: Long?) -> Unit = {},
    onNavigateToRepeat: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    // Navigation3 参数初始化
    LaunchedEffect(alarmId, initialHour, initialMinute) {
        viewModel.initialize(alarmId, initialHour, initialMinute)
    }

    // 使用 Channel 一次性事件监听保存完成，不会因状态残留而误触发
    LaunchedEffect(Unit) {
        viewModel.savedEvent.collect { newAlarmId ->
            // 保存成功后清除新建页初始化标记，保证下次新建闹钟时表单重置为空白
            viewModel.onEditScreenExited()
            onNavigateBack(newAlarmId)
        }
    }

    // Scaffold - 脚手架布局
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = if (state.alarmId == -1L) "新建闹钟" else "编辑闹钟",
                subtitle = if (state.isEnabled) "${state.nextAlarmHours}小时${state.nextAlarmMinutes}分钟后响铃"
                        else "未启用该闹钟",
                navigationIcon = {
                    IconButton(onClick = {
                        // 顶栏返回离开新建页时清除初始化标记，保证下次新建闹钟时表单重置为空白
                        viewModel.onEditScreenExited()
                        onNavigateBack(null)
                    }) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                color = MiuixTheme.colorScheme.background,
                titleColor = MiuixTheme.colorScheme.onBackground,
                subtitleColor = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        },
        containerColor = MiuixTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 内容区：可滚动，保证矮屏/横屏下内容可达；竖屏下占满剩余高度，保存按钮仍在底部
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // 1.时间选择器或加载占位
                if (state.isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "加载中...", style = MiuixTheme.textStyles.body1)
                    }
                } else {
                    TimePicker(
                        hour = state.hour,
                        minute = state.minute,
                        onHourChange = { viewModel.setTime(it, state.minute) },
                        onMinuteChange = { viewModel.setTime(state.hour, it) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)
                    )
                }

                // 在“重复”行整组上方插入间距
                Spacer(modifier = Modifier.height(39.dp))

                // 2 & 3. 重复 + 提醒方式（作为一组连续列表项，行间无空隙）
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 2.重复选择
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateToRepeat)
                            .padding(start = 28.dp, end = 25.dp, top = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "重复",
                            style = MiuixTheme.textStyles.headline1,
                            color = MiuixTheme.colorScheme.onBackground
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = AlarmEditViewModel.getRepeatDisplay(state.repeatDays),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                            Spacer(Modifier.size(4.dp))
                            Icon(
                                imageVector = MiuixIcons.ChevronForward,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    // 3.提醒方式（OverlayDropdownPreference 弹窗单选）
                    OverlayDropdownPreference(
                        insideMargin = PaddingValues(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 12.dp),
                        title = "提醒方式",
                        items = AlarmMode.entries.map { it.label },
                        selectedIndex = AlarmMode.entries.indexOf(state.mode).coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            viewModel.setMode(AlarmMode.entries[index])
                        }
                    )

                    // 3.1 仅一次闹钟专属：「响铃后删除此闹钟」（默认关）。
                    // 重复切到其他规则时该开关由 ViewModel 自动重置为关闭并隐藏。
                    if (state.repeatDays == 0) {
                        SwitchPreference(
                            title = "响铃后删除此闹钟",
                            checked = state.deleteAfterRing,
                            onCheckedChange = viewModel::setDeleteAfterRing,
                            // 与主页/设置页开关统一使用 alarmSwitchColors()，保证明暗主题下滑块配色一致
                            switchColors = alarmSwitchColors(),
                            insideMargin = PaddingValues(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 12.dp)
                        )
                    }
                }

                // 插入间距
                Spacer(modifier = Modifier.height(8.dp))

                // 4.标签输入框（点击弹出弹窗编辑）
                var showTagDialog by remember { mutableStateOf(false) }
                var editingTag by remember { mutableStateOf("") }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp)
                        .background(
                            color = MiuixTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            editingTag = state.label
                            showTagDialog = true
                        }
                        .padding(start = 18.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "标签",
                            style = MiuixTheme.textStyles.headline1,
                            color = MiuixTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (state.label.isBlank()) {
                            Text(
                                text = "请输入",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        } else {
                            Text(
                                text = state.label,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        }
                    }
                }

                TagEditDialog(
                    show = showTagDialog,
                    currentLabel = editingTag,
                    onLabelChange = { editingTag = it },
                    onConfirm = {
                        viewModel.setLabel(editingTag)
                        showTagDialog = false
                    },
                    onDismiss = { showTagDialog = false }
                )
            }

            // 5.保存/更新按钮：固定在底部，矮屏/横屏下不受内容区滚动影响，始终可见
            Button(
                onClick = viewModel::save,
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                enabled = !state.isSaving
            ) {
                Text(text = if (state.alarmId == -1L) "保存" else "更新")
            }
        }

        // 重复闹钟拦截提示：命中响铃倒计时相同的闹钟时拒绝保存并弹窗
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

/**
 * 编辑闹钟标签底部弹窗
 *
 * 点击标签输入框后从底部弹出的编辑窗口，包含：
 * - 标题"编辑闹钟标签"于顶部居中
 * - 输入框自动获取焦点并打开输入法
 * - 取消/确定按钮
 */
@Composable
private fun TagEditDialog(
    show: Boolean,
    currentLabel: String,
    onLabelChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    LaunchedEffect(show) {
        if (show) {
            // 打开弹窗时若有原有内容，将光标定位到内容最右侧
            textFieldValue = TextFieldValue(
                text = currentLabel,
                selection = TextRange(currentLabel.length)
            )
            delay(100)
            focusRequester.requestFocus()
        }
    }

    if (show) {
        WindowDialog(
            show = show,
            title = "编辑闹钟标签",
            onDismissRequest = onDismiss
        ) {
            Column {
                Box(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = textFieldValue,
                        onValueChange = {
                            textFieldValue = it
                            onLabelChange(it.text)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        colors = TextFieldDefaults.textFieldColors(
                            backgroundColor = MiuixTheme.colorScheme.surfaceVariant
                        ),
                        textStyle = MiuixTheme.textStyles.body1
                    )
                    if (textFieldValue.text.isEmpty()) {
                        Text(
                            text = "请输入",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            modifier = Modifier
                                .matchParentSize()
                                .padding(start = 16.dp, end = 16.dp)
                                .wrapContentHeight(align = Alignment.CenterVertically)
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
                            onConfirm()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }
}
