// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.hilt.navigation.compose.hiltViewModel
import lorix.noslackalarm.alarm.presentation.AlarmEditScreen
import lorix.noslackalarm.alarm.presentation.AlarmEditViewModel
import lorix.noslackalarm.alarm.presentation.AlarmListScreen
import lorix.noslackalarm.alarm.presentation.RepeatScreen
import lorix.noslackalarm.alarm.presentation.SettingsScreen
import lorix.noslackalarm.common.LocalSettingsRepository
import lorix.noslackalarm.common.SettingsDefaults
import lorix.noslackalarm.common.navigation.NavJson
import lorix.noslackalarm.common.navigation.Navigator
import lorix.noslackalarm.common.navigation.Screen
import lorix.noslackalarm.common.util.suggestDefaultAlarmTime
import lorix.noslackalarm.domain.model.Alarm
import lorix.noslackalarm.permission.PermissionInfoScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import java.util.Calendar

// 点击后到真正开始跳转之间的停顿（ms）
private val NavPreJumpPauseMs = 80

// 编辑页 ← 重复设置页的「选择结果」通道标识
private const val RepeatDaysResultKey = "alarm_edit_repeat_days"

/**
 * 转场观感总开关。
 *
 * - `false`（默认）：保持升级到 Miuix 0.9.4 之前的观感——不做前缘圆角裁剪、不加全屏调暗；
 * - `true`：改用 Miuix 0.9.4 的默认观感——前缘按设备系统圆角做平滑裁剪 + 50% 调暗。
 *
 * 两种取值都保持「转场期间拦截输入」，与升级前 `blockInputDuringTransition = true` 的行为一致。
 * 注意 Miuix 0.9.4 中该参数默认值已改为 false，必须显式传入才能维持原有拦截语义。
 */
private const val UseMiuixDefaultNavEffects = false

// 升级前观感：关前缘圆角裁剪、关全屏调暗，但保留转场期间输入拦截。
private val LegacyNavEffects = NavDisplayEffects(
    enableCornerClip = false,
    dimAmount = 0f,
    blockInputDuringTransition = true,
)

/**
 * 按 [UseMiuixDefaultNavEffects] 构建转场效果。
 * 效果对象是 `@Immutable` 的 data class，取值相等时 Compose 会跳过重组，故这里无需额外缓存。
 */
@Composable
private fun navDisplayEffects(): NavDisplayEffects =
    if (UseMiuixDefaultNavEffects) {
        val cornerRadius = rememberNavSystemCornerRadius()
        remember(cornerRadius) {
            NavDisplayEffects(
                cornerClipRadius = cornerRadius,
                dimAmount = 0.5f,
                blockInputDuringTransition = true,
            )
        }
    } else {
        LegacyNavEffects
    }

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

    // 返回栈：以传入的 initialBackStack 作为首组合初始栈（由 MainActivity 决定 = 持久化栈 / 快捷方式 / 主页面）。
    // 必须把路由超类型 Screen 作为显式类型参数传入，才能让整个密封层级参与序列化；
    // 其内部用 rememberSaveable 持有，配置变更与进程恢复时由它接管还原。
    val backStack = rememberNavBackStack<Screen>(*initialBackStack.toTypedArray())

    // Navigator 只是返回栈 + 跨屏结果通道的薄封装，随 backStack 实例一起 remember 一次即可。
    val navigator = remember(backStack) { Navigator(backStack) }

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

    NavDisplay(
        backStack = navigator.backStack,
        // 用带守卫的 pop 覆盖默认的 removeLastOrNull：只在栈内还有上一页时才弹栈，
        // 根页面（AlarmList）交由系统返回键结束 Activity。
        // 另外，重复 push 相同路由值会被 miuix-nav 以 contentKey 冲突拒绝，
        // 因此各跳转点仍是应用层职责：统一用 navigateWithPause + `backStack.none {}` 守卫保证幂等。
        onBack = { navigator.pop() },
        effects = navDisplayEffects(),
    ) {
        // 根路由：无上一页可返回，不开启侧滑返回（entry 的 swipeDismiss 默认继承转场默认值 None）。
        entry<Screen.AlarmList> {
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

        entry<Screen.AlarmEdit>(swipeDismiss = NavSwipeDirection.LeftToRight) { screen ->
            // 每个 entry 拥有独立的 LifecycleOwner 与 ViewModelStoreOwner：这里的实例作用域就是
            // 「本页在返回栈上的存续期」，被 pop 时随之清理，不再与其它页面共享同一个实例。
            val editViewModel: AlarmEditViewModel = hiltViewModel()
            // 接收「重复设置页」回传的选择。消费后立刻清空通道，避免下次进入编辑页误读上一次的陈旧结果。
            LaunchedEffect(Unit) {
                navigator.observeResult<Int>(RepeatDaysResultKey).collect { days ->
                    navigator.clearResult(RepeatDaysResultKey)
                    editViewModel.setRepeatDays(days)
                }
            }
            AlarmEditScreen(
                viewModel = editViewModel,
                alarmId = screen.alarmId,
                initialHour = screen.hour,
                initialMinute = screen.minute,
                onNavigateBack = { newAlarmId ->
                    highlightAlarmId = newAlarmId
                    navigator.pop()
                },
                // 把编辑页当前的重复规则带给重复设置页，作为它的初始选中态；
                // 用户最终选择经结果通道回传（见上方 observeResult）。
                onNavigateToRepeat = { currentRepeatDays ->
                    navigateWithPause {
                        if (backStack.none { it is Screen.Repeat }) {
                            navigator.navigateForResult(
                                Screen.Repeat(repeatDays = currentRepeatDays),
                                RepeatDaysResultKey,
                            )
                        }
                    }
                }
            )
        }

        entry<Screen.Repeat>(swipeDismiss = NavSwipeDirection.LeftToRight) { screen ->
            RepeatScreen(
                repeatDays = screen.repeatDays,
                // setResult 内部完成「emit 结果 + pop 本页」两步
                onRepeatDaysSelected = { days ->
                    navigator.setResult(RepeatDaysResultKey, days)
                },
                onNavigateBack = { navigator.pop() },
            )
        }

        entry<Screen.Settings>(swipeDismiss = NavSwipeDirection.LeftToRight) {
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

        entry<Screen.About>(swipeDismiss = NavSwipeDirection.LeftToRight) {
            AboutScreen(
                onNavigateBack = { navigator.pop() }
            )
        }

        entry<Screen.PermissionInfo>(swipeDismiss = NavSwipeDirection.LeftToRight) {
            PermissionInfoScreen(
                onNavigateBack = { navigator.pop() }
            )
        }
    }
}
