// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import lorix.noslackalarm.common.LocalSettingsRepository
import lorix.noslackalarm.common.RecentTaskHelper
import lorix.noslackalarm.common.SettingsDefaults
import lorix.noslackalarm.common.navigation.NavJson
import lorix.noslackalarm.common.navigation.Screen
import lorix.noslackalarm.common.theme.NoSlackAlarmTheme
import lorix.noslackalarm.common.util.suggestDefaultAlarmTime
import java.util.Calendar
import lorix.noslackalarm.common.theme.applySystemBarChrome
import lorix.noslackalarm.common.theme.ThemeMode
import lorix.noslackalarm.domain.repository.SettingsRepository
import lorix.noslackalarm.domain.repository.AlarmRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Scaffold
import javax.inject.Inject

// 系统开屏退出动画时长（毫秒）。当内容（含强制主题）首帧就绪后，由系统层对整张开屏做 alpha 淡出，
// 实现从「系统色开屏」到「真实主题首页」的全屏交叉淡化，兜住「强制主题与系统相反」的暗/亮硬切。
// 仅本文件使用，作为文件级私有常量放在顶部便于维护。
private const val SPLASH_EXIT_DURATION_MS = 800

// 当「系统主题」与「app 内设置主题」一致时，系统开屏同样做淡出退出动画，
// 但用更短的时长，作轻量揭示过渡。
private const val SPLASH_EXIT_SAME_THEME_DURATION_MS = 300

// 桌面长按弹窗「创建闹钟」快捷方式投递的自定义 action；shortcuts.xml 使用同字面量。
private const val ACTION_CREATE_ALARM = "lorix.noslackalarm.action.CREATE_ALARM"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    private var hideFromRecentsEnabled = false

    // 桌面快捷方式「创建闹钟」请求标志：冷启动（onCreate）与后台复用（onNewIntent）均置位，
    // 透传给 AppNavigation，由其跳转到新建闹钟界面后复位，避免重组重复跳转。
    private var requestCreateAlarm by mutableStateOf(false)
    private var observeJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 桌面「创建闹钟」快捷方式冷启动：读取 intent action 置位跳转标志。
        // 仅在首次创建时置位（savedInstanceState==null），避免旋转等配置变更重建时重复跳转。
        if (savedInstanceState == null && intent?.action == ACTION_CREATE_ALARM) {
            requestCreateAlarm = true
        }

        // 冷启动初始返回栈：
        // - 桌面「创建闹钟」快捷方式：首帧即返回 [AlarmList, AlarmEdit(默认时间)]，首帧直接渲染创建页，
        //   无「先到主页再跳创建页」的可见闪烁；
        // - 同进程内 Activity 被重建（开启「隐藏最近任务」后从桌面图标再次进入会新建 Activity 实例、
        //   或旋转等配置变更）：读回 DataStore 持久化的返回栈恢复用户最后停留的页面；
        // - 进程全新启动（杀掉进程后重进、首次冷启）：回到主页面 [AlarmList]。
        // 是否「同进程重建」由 NoSlackAlarmApp.isFreshProcess 区分（true=新进程）。见 buildInitialBackStack。
        val initialBackStack = buildInitialBackStack(requestCreateAlarm)

        // 首帧决策完成，将进程新鲜度置否：后续同进程内的 Activity 重建（Home 重进、旋转）将恢复持久化栈。
        // 进程被杀死后再次启动会是全新进程，isFreshProcess 随类加载重置为 true，自然回到主页。
        NoSlackAlarmApp.isFreshProcess = false

        // 原生开屏门控：DataStore 预热完成前持续显示系统级 SplashScreen，
        // 消除冷启动白屏。
        // 必须在 setContent 之前调用；API < 31 时 installSplashScreen 可能为空操作（no-op），
        // 由 windowBackground 兜底无白屏，预热仍会照常执行。
        var dataReady = false
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !dataReady }

        // 预取主题真实值：
        // 1.作为 Compose 首帧占位（避免冷启动导航栏/状态栏停系统色）；
        // 2.与设备真实暗/亮模式比对，判断是否需淡出动画。系统开屏正盖屏，runBlocking 同步读取无白屏风险。
        val initialThemeMode = runBlocking(Dispatchers.IO) { settingsRepository.themeMode.first() }

        // 预取「协议同意状态」真实值：作为 UserAgreementGate 的首帧占位，避免冷启动首帧误弹/闪烁。
        val initialAgreementAccepted = runBlocking(Dispatchers.IO) {
            settingsRepository.agreementAccepted.first()
        }
        // 设备当前的真实暗/亮模式（与 app 强制主题无关，来自系统 UI 模式）。
        val systemIsDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        // app 实际生效的暗/亮：跟随系统→等同系统；强制亮/暗→固定。
        val appIsDark = when (ThemeMode.from(initialThemeMode)) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> systemIsDark
        }
        // 当「app 实际暗/亮」与「设备真实暗/亮」不一致时，开屏（系统色）与首页（app 主题）存在硬切，
        // 此时用较长 alpha 淡出做交叉淡化；一致时同样做淡出但用更短时长方作轻量揭示。
        val needFade = appIsDark != systemIsDark

        splashScreen.setOnExitAnimationListener { provider ->
            // 在开屏真正退出、主窗口可见的这一刻「二次写入」系统栏颜色。某些操作系统可能会忽略首帧。
            // 通过代码设置的 statusBarColor/navigationBarColor（仅在窗口可见后才生效），导致冷启动
            // 进入主页后系统栏停在系统色，必须进设置页手动改主题（Flow 重发）才纠正。此处兜底该时序坑，
            // 使冷启动即与 App 主题一致。
            applySystemBarColors(appIsDark)
            if (needFade) {
                provider.view
                    .animate()
                    .alpha(0f)
                    .setDuration(SPLASH_EXIT_DURATION_MS.toLong())
                    .withEndAction(provider::remove)
                    .start()
            } else {
                provider.view
                    .animate()
                    .alpha(0f)
                    .setDuration(SPLASH_EXIT_SAME_THEME_DURATION_MS.toLong())
                    .withEndAction(provider::remove)
                    .start()
            }
        }

        enableEdgeToEdge()

        // 启动时：先统一删除「答题关闭后待删除」的闹钟，再清理过期的仅一次闹钟，
        // 保证主页面展示前已完成待删清理
        lifecycleScope.launch(Dispatchers.IO) {
            alarmRepository.deletePendingDeleteAlarms()
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val cal = java.util.Calendar.getInstance()
            alarmRepository.disableExpiredOneTimeAlarms(today, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
            alarmRepository.deleteExpiredOneTimeAlarms(today)
        }

        observeJob = lifecycleScope.launch(Dispatchers.IO) {
            settingsRepository.hideFromRecents.collect { enabled ->
                hideFromRecentsEnabled = enabled
                RecentTaskHelper.applyExcludeFromRecents(this@MainActivity, enabled)
            }
        }

        // 预热 DataStore；无论成功失败都放开开屏，避免预热异常时卡死在开屏。
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { settingsRepository.warmUp() }
            }
            dataReady = true
        }

        // 首帧即用真实主题值占位（上面已预取），主题变化后由 Flow 继续驱动重组；首帧就绪后
        // 放开开屏，由上面的系统层退出动画完成交叉淡化（跟随系统时不淡化）。
        setContent {
            CompositionLocalProvider(
                LocalSettingsRepository provides settingsRepository,
            ) {
                val themeMode by settingsRepository.themeMode.collectAsState(initial = initialThemeMode)
                val effectiveMode = ThemeMode.from(themeMode)

                NoSlackAlarmTheme(themeMode = effectiveMode) {
                    // 根 Scaffold：仅作为应用级 Overlay 弹窗（如协议门控）的 MiuixPopupHost 宿主容器。
                    // 透明背景 + 零 insets，不改动任何导航与子页面布局；Overlay* 弹窗默认 renderInRootScaffold=true，
                    // 渲染在根 popupHost（z-index 最高、全屏覆盖），从而让协议弹窗天然盖住所有子页面
                    // （含桌面「创建闹钟」快捷方式直达的创建页）。
                    Scaffold(
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        modifier = Modifier.fillMaxSize(),
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                        ) {
                            AppNavigation(
                                initialBackStack = initialBackStack,
                                requestCreateAlarm = requestCreateAlarm,
                                onConsumed = { requestCreateAlarm = false },
                            )
                            // 首次启动协议确认门控：未同意时全屏拦截，同意后才放行主界面
                            UserAgreementGate(initialAgreed = initialAgreementAccepted)
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        RecentTaskHelper.applyExcludeFromRecents(this, hideFromRecentsEnabled)
    }

    /**
     * App 已驻留后台时再次点击桌面「创建闹钟」快捷方式：系统复用现有 Activity 并投递新 Intent，
     * 在此读取 action 置位跳转标志，由 AppNavigation 的 LaunchedEffect 触发跳转。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_CREATE_ALARM) {
            requestCreateAlarm = true
        }
    }

    /**
     * 构建冷启动初始返回栈：
     * - 桌面「创建闹钟」快捷方式：首帧即返回 [AlarmList, AlarmEdit(智能默认时间)]，使首帧直接渲染创建页，
     *   彻底避免原先「先渲染主页、再 LaunchedEffect push 创建页」的可见闪烁；返回键仍可回到主页。
     *   AppNavigation 的 LaunchedEffect 有守卫（栈内已含 AlarmEdit 则不重复 push），故不会重复跳转。
     * - 同进程内 Activity 重建（isFreshProcess == false）：读回 DataStore 持久化的返回栈，恢复用户最后
     *   停留的页面及返回栈（解决「隐藏最近任务」开启后从桌面图标再次进入会新建 Activity 实例导致丢页）。
     * - 进程全新启动（isFreshProcess == true）：始终从主页面 [AlarmList] 开始（杀掉进程后重进的正确行为）。
     */
    private fun buildInitialBackStack(requestCreateAlarm: Boolean): List<Screen> {
        if (requestCreateAlarm) return computeShortcutCreateStack()
        if (!NoSlackAlarmApp.isFreshProcess) return readPersistedBackStack()
        return listOf(Screen.AlarmList)
    }

    /**
     * 从 DataStore 读回持久化的导航返回栈（List<Screen> 的 JSON）。用于同进程内 Activity 被重建时
     * 恢复到用户最后停留的页面及返回栈。
     * 返回空串（尚无数据）或解析异常时回退到 [Screen.AlarmList]。
     */
    private fun readPersistedBackStack(): List<Screen> {
        val json = runBlocking(Dispatchers.IO) { settingsRepository.navBackStack.first() }
        if (json.isBlank()) return listOf(Screen.AlarmList)
        return runCatching { NavJson.decodeFromString<List<Screen>>(json) }
            .getOrElse { emptyList() }
            .takeIf { it.isNotEmpty() }
            ?: listOf(Screen.AlarmList)
    }

    /** 推算桌面「创建闹钟」快捷方式首帧创建页的默认时间（智能默认时间） */
    private fun computeShortcutCreateStack(): List<Screen> {
        val (h, m) = runBlocking(Dispatchers.IO) {
            val nightStartMin = settingsRepository.nightStartMin.first()
            val nightEndMin = settingsRepository.nightEndMin.first()
            val fixedHour = settingsRepository.nightFixedHour.first()
            val fixedMinute = settingsRepository.nightFixedMinute.first()
            val now = Calendar.getInstance()
            val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            suggestDefaultAlarmTime(
                nowMinuteOfDay = nowMin,
                nightStartMin = nightStartMin,
                nightEndMin = nightEndMin,
                fixedHour = fixedHour,
                fixedMinute = fixedMinute,
            )
        }
        return listOf(Screen.AlarmList, Screen.AlarmEdit(hour = h, minute = m))
    }

    /**
     * 开屏退出、主窗口可见的那一刻再调用一次系统栏外观写入，兜住某些操作系统
     * 可能发生的忽略「首帧代码设置系统栏颜色」的时序坑，确保冷启动即与 App 主题一致。
     * 实际逻辑委托给 [applySystemBarChrome]（[NoSlackAlarmTheme] 的 SideEffect 也调用同一函数），
     * 作为系统栏外观的单一真相源。
     *
     * @param useDark true=暗色主题，false=亮色主题
     */
    private fun applySystemBarColors(useDark: Boolean) {
        applySystemBarChrome(window = window, useDark = useDark)
    }

    override fun onDestroy() {
        observeJob?.cancel()
        super.onDestroy()
    }
}
