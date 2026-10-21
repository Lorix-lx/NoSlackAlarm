// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common.util

import lorix.noslackalarm.common.constants.Constants
import kotlin.math.abs

/**
 * 一次「起床验证」的采样结果。
 *
 * 采样由 `core:system` 的 WakeCheckSampler 完成（8 秒沉降 + 22 秒采样，按 1 秒切桶），
 * 判定逻辑放在这里保持纯函数、可单测，便于后续调整窗口长度或阈值。
 *
 * @param activeBuckets 被判为「有运动」的桶数
 * @param totalBuckets 实际采集到的桶数（传感器注册失败时为 0）
 * @param maxPeak 所有样本中动态加速度绝对值（m/s²）的最大值，仅用于观测与调参
 * @param significantMotionFired 硬件显著运动传感器是否在采样期内触发
 * @param screenInteractiveAtEnd 采样结束时屏幕是否仍处于亮屏（交互）状态
 */
data class WakeCheckOutcome(
    val activeBuckets: Int,
    val totalBuckets: Int,
    val maxPeak: Float,
    val significantMotionFired: Boolean,
    val screenInteractiveAtEnd: Boolean,
)

/**
 * 单个 1 秒运动桶是否算「有运动」。
 *
 * 阈值取宽松侧：只要有一点像样的动静就判成动过，从而放过用户。
 * 原因是本检测的代价不对称——误判为「还在睡」会把人吵醒两次，误判为「已起床」只是漏抓一次。
 *
 * @param std 桶内动态加速度的标准差（m/s²）
 * @param peak 桶内动态加速度的峰值（m/s²，内部取绝对值）
 */
fun isActiveBucket(std: Float, peak: Float): Boolean =
    std > Constants.WAKE_CHECK_ACTIVE_STD_THRESHOLD ||
        abs(peak) > Constants.WAKE_CHECK_ACTIVE_PEAK_THRESHOLD

/**
 * 是否检测到「足够长时间的移动」——这是结束起床验证的**唯一**条件。
 *
 * 判定看的是累计运动时长而不是「有没有动过一下」：翻身、伸手拿水、按掉闹钟都只有零散几秒，
 * 不足以放行；只有 22 秒采样窗口里累计达到
 * [Constants.WAKE_CHECK_MIN_ACTIVE_BUCKETS] 秒的持续活动（起身走几步）才算真起床。
 *
 * 屏幕亮灭、是否解锁都不参与判定：用户可能只是躺着刷手机，不构成起床。
 * 这两个字段仍保留在 [WakeCheckOutcome] 里，仅用于日志与调参。
 */
fun isSustainedMovement(outcome: WakeCheckOutcome): Boolean =
    outcome.totalBuckets > 0 &&
        outcome.activeBuckets >= Constants.WAKE_CHECK_MIN_ACTIVE_BUCKETS

/**
 * 是否应当再次响起闹钟：采样有效、但没检测到足够长的移动。
 *
 * 与 [isSustainedMovement] 互为反面；传感器不可用（[WakeCheckOutcome.totalBuckets] 为 0）时
 * 返回 false —— 拿不到数据时绝不能凭空复响。
 */
fun shouldReRing(outcome: WakeCheckOutcome): Boolean =
    outcome.totalBuckets > 0 && !isSustainedMovement(outcome)
