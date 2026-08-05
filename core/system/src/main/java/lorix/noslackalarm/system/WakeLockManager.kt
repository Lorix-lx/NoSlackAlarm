// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.system

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WakeLockManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * 获取 CPU 唤醒锁，保持 CPU 运行。
     * 屏幕唤醒由 RingingActivity 的 setTurnScreenOn(true) + 清单属性处理。
     */
    fun acquire(timeout: Long = 60_000L) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "noslackalarm:AlarmWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(timeout)
        }
    }

    fun release() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
            wakeLock = null
        }
    }
}
