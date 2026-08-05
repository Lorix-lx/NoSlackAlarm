// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm

import android.app.Application
import lorix.noslackalarm.ringing.service.SnoozeResetReceiver
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NoSlackAlarmApp : Application() {

    override fun onCreate() {
        super.onCreate()
        SnoozeResetReceiver.scheduleNextReset(this)
    }

    companion object {
        /**
         * 进程新鲜度标志：类加载时随进程初始化为 true，整个进程生命周期内（多次 Activity 重建）保持，
         * 仅在进程被系统杀死后重新创建才重置为 true。
         *
         * 用途：区分「同进程内 Activity 被重建」（如开启「隐藏最近任务」后从桌面图标再次进入，系统会新建
         * Activity 实例；或旋转等配置变更）与「进程全新启动」（杀掉进程后重新进入）。
         * 二者 `savedInstanceState` 均为 null，无法靠它区分，故用本进程级标志：
         * - true（新手进程）：MainActivity 首帧直接回到 [AlarmList]（或快捷方式栈），不恢复持久化返回栈；
         * - false（同进程重建）：MainActivity 读回 DataStore 持久化的返回栈恢复用户最后停留的页面。
         * MainActivity.onCreate 计算完 initialBackStack 后置为 false，确保本次首帧已正确决策。
         */
        @JvmField
        var isFreshProcess: Boolean = true
    }
}
