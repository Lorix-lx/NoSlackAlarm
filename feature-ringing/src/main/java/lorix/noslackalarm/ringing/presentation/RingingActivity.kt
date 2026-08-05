// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.ringing.presentation

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
                        RingingScreen()
                    }
                }
            }
        }
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
                        alarmRepository.updateAlarm(alarm.copy(isEnabled = false))
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
