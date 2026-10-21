// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common.constants

object Constants {

    const val DEFAULT_SNOOZE_MINUTES = 10
    const val DOUBLE_PRESS_INTERVAL_MS = 500L
    const val MAX_SKETCHPAD_POINTS = 5000
    const val ALARM_CHANNEL_ID = "alarm_service_channel"
    const val ALARM_CHANNEL_ID_NO_VIBRATE = "alarm_service_channel_no_vibrate"
    const val ALARM_NOTIFICATION_ID = 1001
    const val ACTION_ALARM_TRIGGER = "lorix.noslackalarm.ALARM_TRIGGER"
    const val ACTION_SNOOZE = "lorix.noslackalarm.ACTION_SNOOZE"
    const val ACTION_TRIGGER_RINGING = "lorix.noslackalarm.ACTION_TRIGGER_RINGING"
    const val SKETCHPAD_ALARM_PAUSE_MS = 30_000L // 打开白板时暂停震动/闹铃的时长（仅一次），到点未解答则自动恢复

    // ============ 防赖床检测（Wake Check） ============
    //
    // 流程：答题关闭闹钟后，前台服务静默观察约 30 秒；若手机始终没有明显移动，
    // 判定为用户又睡回去，再次响起同一闹钟并要求重新答题关闭（每轮最多复响 1 次）。
    // 判据与阈值的取舍见 WakeCheckDecision / WakeCheckSampler 的注释。

    /** 答题关闭闹钟：由响铃页发给 AlarmService，替代原先直接 stopService。 */
    const val ACTION_DISMISS = "lorix.noslackalarm.ACTION_DISMISS"

    /** 验证期间的低优先级通知渠道（不发声、不振动，避免在复响前又响一声）。 */
    const val WAKE_CHECK_CHANNEL_ID = "wake_check_channel"
    const val WAKE_CHECK_NOTIFICATION_ID = 1002

    /** 沉降期：关闭闹钟后的头 8 秒不采样，丢弃「把手机放回床头柜」这次动作。 */
    const val WAKE_CHECK_SETTLE_MS = 8_000L

    /** 采样期：沉降期结束后的 22 秒，按 [WAKE_CHECK_BUCKET_MS] 切桶。 */
    const val WAKE_CHECK_SAMPLE_MS = 22_000L

    /** 单个运动判定桶的时长。 */
    const val WAKE_CHECK_BUCKET_MS = 1_000L

    /** 桶内加速度标准差超过该值（m/s²）即记为「有运动」。阈值取宽松侧，宁可判成动过。 */
    const val WAKE_CHECK_ACTIVE_STD_THRESHOLD = 0.15f

    /** 桶内加速度峰值绝对值超过该值（m/s²）即记为「有运动」。 */
    const val WAKE_CHECK_ACTIVE_PEAK_THRESHOLD = 0.8f

    /**
     * 采样期内至少要拿到的样本数（50Hz 下约 1.2 秒）。
     *
     * 低于该值说明传感器注册成功但没有正常回调（部分 ROM / 省电模式下会出现），
     * 此时按「传感器不可用」处理并放弃验证，绝不复响。
     */
    const val WAKE_CHECK_MIN_SAMPLES = 60

    /**
     * 「持续移动」判定：22 个桶里至少要有这么多个被判为有运动，才算真正起床。
     *
     * 6 秒大约相当于「起身走几步」；仅仅翻个身、伸手拿水、按掉闹钟都不足以结束验证。
     * 这是结束验证的**唯一**条件（屏幕亮灭只记录、不参与判定）。
     */
    const val WAKE_CHECK_MIN_ACTIVE_BUCKETS = 6

    /**
     * 安全上限：单次起床验证最多复响多少轮。
     *
     * 按要求验证会一轮接一轮持续到「检测到持续移动」为止，这里只做防跑飞兜底：
     * 手机被遗留在家、或传感器异常时不会无限循环。
     */
    const val WAKE_CHECK_MAX_ROUNDS = 10

    /** 安全上限：从第一次关闭闹钟起算，验证最多持续这么久（超时后不再复响）。 */
    const val WAKE_CHECK_SESSION_MAX_MS = 20 * 60 * 1000L

    /** 验证服务看门狗：超过该时长无论采样是否完成都自毁并注销传感器，防止卡死耗电。 */
    const val WAKE_CHECK_WATCHDOG_MS = 60_000L

    /** 验证服务持有的 CPU 唤醒锁时长：必须覆盖沉降 + 采样 + 余量。 */
    const val WAKE_CHECK_WAKE_LOCK_MS = 45_000L

    /** 响铃会话相位（落在 SharedPreferences 的 "ringing_phase" 上）。 */
    enum class RingingPhase(val value: String) {
        /** 既未响铃也未验证（空闲）。 */
        IDLE("idle"),

        /** 正在响铃（含复响）。此时关屏 = 用户主动贪睡。 */
        RINGING("ringing"),

        /** 已关闭，正在做起床验证。此时关屏只是采样信号，绝不能触发贪睡。 */
        VERIFYING("verifying"),

        /** 当前无有效会话。 */
        UNKNOWN("unknown");

        companion object {
            fun from(value: String?): RingingPhase =
                entries.firstOrNull { it.value == value } ?: UNKNOWN
        }
    }
}
