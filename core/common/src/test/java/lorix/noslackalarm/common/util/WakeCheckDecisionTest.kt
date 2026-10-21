// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common.util

import lorix.noslackalarm.common.constants.Constants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 起床验证判定的单元测试。
 *
 * 关键语义：**持续移动**是结束验证的唯一条件；零散几秒的运动、亮屏、解锁都不放行。
 */
class WakeCheckDecisionTest {

    private fun outcome(
        activeBuckets: Int = 0,
        totalBuckets: Int = SAMPLE_BUCKETS,
        maxPeak: Float = 0f,
        significantMotionFired: Boolean = false,
        screenInteractiveAtEnd: Boolean = false,
    ) = WakeCheckOutcome(
        activeBuckets = activeBuckets,
        totalBuckets = totalBuckets,
        maxPeak = maxPeak,
        significantMotionFired = significantMotionFired,
        screenInteractiveAtEnd = screenInteractiveAtEnd,
    )

    @Test
    fun `全程静止时复响`() {
        assertTrue(shouldReRing(outcome()))
        assertFalse(isSustainedMovement(outcome()))
    }

    @Test
    fun `零散运动不足以结束验证`() {
        // 只动了一两秒（翻身、按闹钟），仍然算赖床
        assertTrue(shouldReRing(outcome(activeBuckets = 2)))
        assertFalse(isSustainedMovement(outcome(activeBuckets = 2)))
    }

    @Test
    fun `持续移动达到阈值即结束验证`() {
        val threshold = Constants.WAKE_CHECK_MIN_ACTIVE_BUCKETS
        assertTrue(isSustainedMovement(outcome(activeBuckets = threshold)))
        assertFalse(shouldReRing(outcome(activeBuckets = threshold)))
        // 阈值差一个桶仍不放行
        assertTrue(shouldReRing(outcome(activeBuckets = threshold - 1)))
    }

    @Test
    fun `亮屏或解锁不再作为放行条件`() {
        // 躺着刷手机但没起身走动：依然会被再次叫醒（这是防赖床的预期行为）
        val lyingInBedScrolling = outcome(activeBuckets = 1, screenInteractiveAtEnd = true)
        assertTrue(shouldReRing(lyingInBedScrolling))
        assertFalse(isSustainedMovement(lyingInBedScrolling))
    }

    @Test
    fun `显著运动传感器单独触发不足以放行`() {
        assertTrue(shouldReRing(outcome(activeBuckets = 0, significantMotionFired = true)))
    }

    @Test
    fun `没有采到任何桶（传感器不可用）时不覆响`() {
        assertFalse(shouldReRing(outcome(totalBuckets = 0)))
        assertFalse(isSustainedMovement(outcome(totalBuckets = 0)))
    }

    @Test
    fun `桶判定在两个阈值上都是宽松侧`() {
        // 标准差略高于阈值即算动过
        assertTrue(isActiveBucket(Constants.WAKE_CHECK_ACTIVE_STD_THRESHOLD + 0.01f, 0f))
        // 峰值取绝对值，负向冲击同样算动过
        assertTrue(isActiveBucket(0f, -(Constants.WAKE_CHECK_ACTIVE_PEAK_THRESHOLD + 0.01f)))
        // 两者都低于阈值时保持静止
        assertFalse(isActiveBucket(0f, 0f))
    }

    private companion object {
        /** 22 秒采样期 / 1 秒桶 = 22 个桶。 */
        const val SAMPLE_BUCKETS = 22
    }
}
