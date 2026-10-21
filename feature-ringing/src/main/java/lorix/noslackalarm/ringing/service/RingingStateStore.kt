// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.ringing.service

import android.content.Context
import lorix.noslackalarm.common.constants.Constants.RingingPhase

/**
 * 响铃会话状态（SharedPreferences）。
 *
 * 三个消费者：`AlarmService`（写）、`WakeCheckService`（写）、`ScreenStateReceiver`（读）。
 * 集中在这里是为了避免同一个 prefs 文件被三处各自拼字符串、以及「相位漏写」这类问题。
 *
 * 相位的唯一用途是区分「响铃中按电源键 = 贪睡」与「验证期间关屏 = 正常现象」：
 * 只有 [RingingPhase.RINGING] 才允许触发贪睡。
 */
internal object RingingStateStore {

    private const val PREFS_NAME = "ringing_state"
    private const val KEY_CURRENT_ALARM_ID = "current_alarm_id"
    private const val KEY_CURRENT_ALARM_MODE = "current_alarm_mode"
    private const val KEY_RINGING_PHASE = "ringing_phase"

    /** 本轮「起床验证」已经复响过几次（跨多轮复响累计）。 */
    private const val KEY_WAKE_CHECK_ROUND = "wake_check_round"

    /** 本轮验证首次开始的时刻（毫秒），用于 20 分钟兜底。 */
    private const val KEY_WAKE_CHECK_STARTED_AT = "wake_check_started_at"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 开始（或重新开始）响铃：记录闹钟、提醒模式、是否为防赖床复响。
     *
     * 非复响（即新闹钟或贪睡后的响铃）会顺带清空上一晚的验证轮次统计；
     * 复响则保留，因为验证会跨多轮持续到检测到持续移动为止。
     */
    fun setRinging(context: Context, alarmId: Long, mode: String, isWakeCheck: Boolean) {
        val editor = prefs(context).edit()
            .putLong(KEY_CURRENT_ALARM_ID, alarmId)
            .putString(KEY_CURRENT_ALARM_MODE, mode)
            .putString(KEY_RINGING_PHASE, RingingPhase.RINGING.value)
        if (!isWakeCheck) {
            editor.remove(KEY_WAKE_CHECK_ROUND).remove(KEY_WAKE_CHECK_STARTED_AT)
        }
        editor.apply()
    }

    /** 已关闭闹钟，进入起床验证：此时关屏属于采样信号，不是贪睡。 */
    fun setVerifying(context: Context) {
        prefs(context).edit()
            .putString(KEY_RINGING_PHASE, RingingPhase.VERIFYING.value)
            .apply()
    }

    /** 会话彻底结束：清空闹钟信息并置空闲，避免跨会话残留。 */
    fun setIdle(context: Context) {
        prefs(context).edit()
            .remove(KEY_CURRENT_ALARM_ID)
            .remove(KEY_CURRENT_ALARM_MODE)
            .putString(KEY_RINGING_PHASE, RingingPhase.IDLE.value)
            .apply()
    }

    fun phase(context: Context): RingingPhase =
        RingingPhase.from(prefs(context).getString(KEY_RINGING_PHASE, null))

    fun currentAlarmId(context: Context): Long =
        prefs(context).getLong(KEY_CURRENT_ALARM_ID, -1L)

    fun currentMode(context: Context): String? =
        prefs(context).getString(KEY_CURRENT_ALARM_MODE, null)

    // ============ 起床验证：跨多轮复响的会话统计 ============

    /** 已复响轮次（0 表示刚关闭原始响铃、还没复响过）。 */
    fun wakeCheckRound(context: Context): Int =
        prefs(context).getInt(KEY_WAKE_CHECK_ROUND, 0)

    /** 首次进入验证的时刻；0 表示本轮尚未开始。 */
    fun wakeCheckStartedAt(context: Context): Long =
        prefs(context).getLong(KEY_WAKE_CHECK_STARTED_AT, 0L)

    /** 记录验证起点（幂等）：只在第一次关闭闹钟时写入，之后多轮复响共用同一时间基准。 */
    fun startWakeCheckSessionIfNeeded(context: Context) {
        val prefs = prefs(context)
        if (prefs.getLong(KEY_WAKE_CHECK_STARTED_AT, 0L) != 0L) return
        prefs.edit()
            .putLong(KEY_WAKE_CHECK_STARTED_AT, System.currentTimeMillis())
            .apply()
    }

    /** 复响一次：轮次 +1（用于 [lorix.noslackalarm.common.constants.Constants.WAKE_CHECK_MAX_ROUNDS] 兜底）。 */
    fun incrementWakeCheckRound(context: Context) {
        val prefs = prefs(context)
        prefs.edit()
            .putInt(KEY_WAKE_CHECK_ROUND, prefs.getInt(KEY_WAKE_CHECK_ROUND, 0) + 1)
            .apply()
    }

    /** 验证结束（检测到持续移动 / 放弃 / 超时）：清空轮次与起点。 */
    fun clearWakeCheckSession(context: Context) {
        prefs(context).edit()
            .remove(KEY_WAKE_CHECK_ROUND)
            .remove(KEY_WAKE_CHECK_STARTED_AT)
            .apply()
    }
}
