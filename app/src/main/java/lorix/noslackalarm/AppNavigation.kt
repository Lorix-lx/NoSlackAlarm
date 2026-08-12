// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplayTransitionEffects
import androidx.hilt.navigation.compose.hiltViewModel
import lorix.noslackalarm.alarm.presentation.AlarmEditScreen
import lorix.noslackalarm.alarm.presentation.AlarmEditViewModel
import lorix.noslackalarm.alarm.presentation.AlarmListScreen
import lorix.noslackalarm.alarm.presentation.RepeatScreen
import lorix.noslackalarm.alarm.presentation.SettingsScreen
import lorix.noslackalarm.AboutScreen
import lorix.noslackalarm.permission.PermissionInfoScreen
import lorix.noslackalarm.common.LocalSettingsRepository
import lorix.noslackalarm.common.SettingsDefaults
import lorix.noslackalarm.common.navigation.NavJson
import lorix.noslackalarm.common.navigation.Navigator
import lorix.noslackalarm.common.navigation.Screen
import lorix.noslackalarm.common.util.suggestDefaultAlarmTime
import lorix.noslackalarm.domain.model.Alarm
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

// 点击后到真正开始跳转之间的停顿（ms）
private val NavPreJumpPauseMs = 80

@Composable
fun AppNavigation(
    // 冷启动初始返回栈：普通场景为 [AlarmList]（进程全新启动即从主页面开始）。
    // 桌面「创建闹钟」快捷方式场景由 MainActivity 传入 [AlarmList, AlarmEdit(默认时间)]，使首帧直接渲染创建页。
    initialBackStack: List<Screen> = listOf(Screen.AlarmList),
    // 桌面「创建闹钟」快捷方式请求标志（由 MainActivity 透传）：置位时直接进入新建闹钟界面
    requestCreateAlarm: Boolean = false,
    // 跳转完成后由本函数回调复位，避免重组重复跳转
    onConsumed: () -> Unit = {},
) {
    // 新建闹钟保存后返回的 id，用于在闹钟列表页高亮对应卡片（仅创建时非空）
    var highlightAlarmId by remember { mutableStateOf<Long?>(null) }

    // 与 AlarmEditScreen 同享 Activity 作用域下的同一 ViewModel 实例（Navigation3 不给 entry 创建独立 ViewModelStoreOwner），
    // 用于拦截系统返回键时通知「离开新建闹钟页」。
    val editViewModel: AlarmEditViewModel = hiltViewModel()

    // 返回栈：以传入的 initialBackStack 作为首组合初始栈（由 MainActivity 决定 = 持久化栈 / 快捷方式 / 主页面）。
    // 使用 rememberNavBackStack 在首组合时构造一次，后续 push/pop 由其内部持有。
    val backStack = rememberNavBackStack(*initialBackStack.toTypedArray())

    val navigator = remember { Navigator(backStack) }

    // 点击后先停顿再跳转，并防止停顿窗口内的重复点击
    val navScope = rememberCoroutineScope()
    var navPending by remember { mutableStateOf(false) }
    val navigateWithPause: (() -> Unit) -> Unit = navigateWithPause@{ action ->
        if (navPending) return@navigateWithPause
        navPending = true
        navScope.launch {
            delay(NavPreJumpPauseMs.toLong())
            action()
            navPending = false
        }
    }

    // 读取「智能默认时间」配置，用于点击 + 时推算新建闹钟的默认时间
    val settings = LocalSettingsRepository.current
    val nightStartMin by settings.nightStartMin.collectAsState(initial = SettingsDefaults.NIGHT_START_MIN)
    val nightEndMin by settings.nightEndMin.collectAsState(initial = SettingsDefaults.NIGHT_END_MIN)
    val nightFixedHour by settings.nightFixedHour.collectAsState(initial = SettingsDefaults.NIGHT_FIXED_HOUR)
    val nightFixedMinute by settings.nightFixedMinute.collectAsState(initial = SettingsDefaults.NIGHT_FIXED_MIN)

    // 持久化返回栈：每次返回栈变化（push/pop）则把完整栈（List<Screen>）序列化写入 DataStore，
    // 供同进程内 Activity 被重建时（如开启「隐藏最近任务」后从桌面图标再次进入新建 Activity 实例）
    // 由 MainActivity 读回以恢复用户最后停留的页面及返回栈。是否真正恢复由 NoSlackAlarmApp.isFreshProcess 门控，
    // 冷启动（全新进程）不会恢复。首帧恢复后也会立即写回（幂等）。
    LaunchedEffect(Unit) {
        snapshotFlow { backStack.filterIsInstance<Screen>() }
            .collect { stack -> settings.setNavBackStack(NavJson.encodeToString(stack)) }
    }

    // 桌面「创建闹钟」快捷方式：标志置位时直接 push 新建闹钟界面（复用上方智能默认时间推算），
    // 完成后回调 onConsumed 复位。key 为 requestCreateAlarm，确保冷启动与后台复用各触发一次。
    LaunchedEffect(requestCreateAlarm) {
        if (!requestCreateAlarm) return@LaunchedEffect
        if (backStack.none { it is Screen.AlarmEdit }) {
            val now = Calendar.getInstance()
            val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val (h, m) = suggestDefaultAlarmTime(
                nowMinuteOfDay = nowMin,
                nightStartMin = nightStartMin,
                nightEndMin = nightEndMin,
                fixedHour = nightFixedHour,
                fixedMinute = nightFixedMinute,
            )
            navigator.push(Screen.AlarmEdit(hour = h, minute = m))
        }
        onConsumed()
    }

    val entryProvider = remember(backStack) {
        entryProvider<NavKey> {
            entry(Screen.AlarmList) {
                AlarmListScreen(
                    highlightAlarmId = highlightAlarmId,
                    onHighlightConsumed = { highlightAlarmId = null },
                    onNavigateToEdit = { alarm: Alarm ->
                        // 进入编辑页前先清空高亮状态，避免返回时残留旧 id 误高亮其他卡片
                        highlightAlarmId = null
                        navigateWithPause {
                            if (backStack.none {
                                    it is Screen.AlarmEdit && it.alarmId == alarm.id
                                }
                            ) {
                                navigator.push(
                                    Screen.AlarmEdit(
                                        alarmId = alarm.id,
                                        hour = alarm.hour,
                                        minute = alarm.minute,
                                    )
                                )
                            }
                        }
                    },
                    onNavigateToPermissionInfo = {
                        navigateWithPause {
                            if (backStack.none { it is Screen.PermissionInfo }) {
                                navigator.push(Screen.PermissionInfo)
                            }
                        }
                    },
                    onNavigateToCreate = {
                        navigateWithPause {
                            if (backStack.none { it is Screen.AlarmEdit }) {
                                val now = Calendar.getInstance()
                                val nowMin =
                                    now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                                val (h, m) = suggestDefaultAlarmTime(
                                    nowMinuteOfDay = nowMin,
                                    nightStartMin = nightStartMin,
                                    nightEndMin = nightEndMin,
                                    fixedHour = nightFixedHour,
                                    fixedMinute = nightFixedMinute,
                                )
                                navigator.push(Screen.AlarmEdit(hour = h, minute = m))
                            }
                        }
                    },
                    onNavigateToSettings = {
                        navigateWithPause {
                            if (backStack.none { it is Screen.Settings }) {
                                navigator.push(Screen.Settings)
                            }
                        }
                    },
                    onNavigateToAbout = {
                        navigateWithPause {
                            if (backStack.none { it is Screen.About }) {
                                navigator.push(Screen.About)
                            }
                        }
                    }
                )
            }

            entry<Screen.AlarmEdit> { screen ->
                AlarmEditScreen(
                    alarmId = screen.alarmId,
                    initialHour = screen.hour,
                    initialMinute = screen.minute,
                    onNavigateBack = { newAlarmId ->
                        highlightAlarmId = newAlarmId
                        navigator.pop()
                    },
                    onNavigateToRepeat = {
                        navigateWithPause {
                            if (backStack.none { it is Screen.Repeat }) {
                                navigator.push(Screen.Repeat)
                            }
                        }
                    }
                )
            }

            entry(Screen.Repeat) {
                RepeatScreen(
                    onNavigateBack = { navigator.pop() }
                )
            }

            entry(Screen.Settings) {
                SettingsScreen(
                    onNavigateBack = { navigator.pop() },
                    onNavigateToPermissionInfo = {
                        navigateWithPause {
                            if (backStack.none { it is Screen.PermissionInfo }) {
                                navigator.push(Screen.PermissionInfo)
                            }
                        }
                    }
                )
            }

            entry(Screen.About) {
                AboutScreen(
                    onNavigateBack = { navigator.pop() }
                )
            }

            entry(Screen.PermissionInfo) {
                PermissionInfoScreen(
                    onNavigateBack = { navigator.pop() }
                )
            }
        }
    }

    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = entryProvider,
    )

    val transitionEffects = remember {
        NavDisplayTransitionEffects(
            enableCornerClip = false,
            dimAmount = 0f,
            blockInputDuringTransition = true,
            popDirectionFollowsSwipeEdge = false,
        )
    }

    NavDisplay(
        entries = entries,
        onBack = {
            // 系统返回键离开新建闹钟页时清除初始化标记，保证下次新建闹钟时表单重置为空白。
            // 仅拦截栈顶为新建页（alarmId == -1L）的场景；从 Repeat 返回时栈顶是 Repeat，
            // 不会误触发，仍能保留用户在重复页的选择。
            val top = backStack.filterIsInstance<Screen>().lastOrNull()
            if (top is Screen.AlarmEdit && top.alarmId == -1L) {
                editViewModel.onEditScreenExited()
            }
            navigator.pop()
        },
        transitionEffects = transitionEffects,
    )
}