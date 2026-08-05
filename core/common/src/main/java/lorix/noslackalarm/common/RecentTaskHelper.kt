// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log

object RecentTaskHelper {

    private const val TAG = "RecentTaskHelper"

    /**
     * 将当前应用的任务从最近任务列表中隐藏或恢复。
     *
     * 使用 ActivityManager.AppTask.setExcludeFromRecents() 动态控制，
     * 无需在 Manifest 中静态声明 android:excludeFromRecents。
     * 这使得用户可以在运行时自由开关此功能。
     *
     * @param context 上下文（建议传入 Activity）
     * @param exclude true = 从最近任务隐藏，false = 恢复显示
     */
    fun applyExcludeFromRecents(context: Context, exclude: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Log.w(TAG, "API < 21，不支持动态隐藏最近任务")
            return
        }

        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val tasks = am.appTasks
            if (tasks.isEmpty()) {
                Log.w(TAG, "未找到应用任务，无法设置 excludeFromRecents=$exclude")
                return
            }
            for (task in tasks) {
                task.setExcludeFromRecents(exclude)
            }
            Log.d(TAG, "已${if (exclude) "隐藏" else "恢复"}最近任务")
        } catch (e: SecurityException) {
            Log.w(TAG, "权限不足，无法设置最近任务隐藏: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "设置最近任务隐藏失败: ${e.message}", e)
        }
    }
}
