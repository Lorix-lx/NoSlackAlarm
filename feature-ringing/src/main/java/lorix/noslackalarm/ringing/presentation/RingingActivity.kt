// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.ringing.presentation

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import lorix.noslackalarm.common.LocalSettingsRepository
import lorix.noslackalarm.common.SettingsDefaults
import lorix.noslackalarm.common.theme.NoSlackAlarmTheme
import lorix.noslackalarm.common.theme.ThemeMode
import lorix.noslackalarm.domain.repository.AlarmRepository
import lorix.noslackalarm.domain.repository.SettingsRepository
import lorix.noslackalarm.ringing.service.AlarmService
import lorix.noslackalarm.system.OverlayManager
import lorix.noslackalarm.system.ScreenLockHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class RingingActivity : ComponentActivity() {

    @Inject
    lateinit var overlayManager: OverlayManager

    @Inject
    lateinit var screenLockHelper: ScreenLockHelper

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    /**
     * 当前这一轮响铃的 Intent。
     *
     * Activity 是 singleTask：贪睡重响、防赖床复响都会复用它并走 [onNewIntent]，
     * 因此不能用 `intent` 一次性读取，必须转成 Compose 状态才能驱动界面重开一轮。
     */
    private var ringIntent by mutableStateOf<Intent?>(null)

    /** 响铃代次：同一闹钟连续两次响铃的 id 相同，只有自增代次能触发界面重置。 */
    private var ringGeneration by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        screenLockHelper.requestDismissKeyguard(this)
        overlayManager.showOverlay()

        ringIntent = intent

        setContent {
            CompositionLocalProvider(
                LocalSettingsRepository provides settingsRepository
            ) {
                val themeMode by settingsRepository.themeMode.collectAsState(initial = SettingsDefaults.THEME_MODE)
                NoSlackAlarmTheme(themeMode = ThemeMode.from(themeMode)) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val currentRing = ringIntent
                        RingingScreen(
                            alarmId = currentRing
                                ?.getLongExtra(AlarmService.EXTRA_ALARM_ID, -1L) ?: -1L,
                            isWakeCheck = currentRing
                                ?.getBooleanExtra(AlarmService.EXTRA_IS_WAKE_CHECK, false) ?: false,
                            ringGeneration = ringGeneration,
                        )
                    }
                }
            }
        }
    }

    /**
     * 单例 Activity 被复用时的入口：贪睡重响、防赖床复响都会走到这里。
     *
     * 只处理真正带 `alarm_id` 的响铃 Intent（[onUserLeaveHint] 的重拉不带额外信息，直接忽略），
     * 更新可观察的 [ringIntent] 并自增 [ringGeneration]，驱动界面换一道新题重新开始。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 防止把「拉回前台」的空白 Intent 当成新一轮响铃
        if (intent.getLongExtra(AlarmService.EXTRA_ALARM_ID, -1L) == -1L) return

        setIntent(intent)
        ringIntent = intent
        ringGeneration++

        // 复用实例时窗口的 turnScreenOn 不会自动重放，这里补一次，确保新响铃能把屏幕点亮
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
        }
        screenLockHelper.requestDismissKeyguard(this)
    }

    /**
     * 在 Activity 销毁前，同步禁用仅一次闹钟。
     * 不依赖 ViewModelScope（会被 finish 提前取消），
     * 使用 runBlocking 保证 DB 操作在 super.finish() 前完成。
     */
    override fun finish() {
        val alarmId = intent?.getLongExtra("alarm_id", -1L) ?: -1L
        if (alarmId != -1L) {
            runBlocking(Dispatchers.IO) {
                alarmRepository.getAlarmById(alarmId)?.let { alarm ->
                    if (alarm.repeatDays == 0 && alarm.isEnabled) {
                        // 开启「响铃后删除此闹钟」时同样只标记待删除（不立即删），
                        // 与 RingingViewModel 答题成功路径幂等：任一路径先写结果一致。
                        alarmRepository.updateAlarm(
                            alarm.copy(isEnabled = false, pendingDelete = alarm.deleteAfterRing)
                        )
                    }
                }
            }
        }
        super.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayManager.hideOverlay()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Intercept back press
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val intent = android.content.Intent(this, RingingActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }
}
