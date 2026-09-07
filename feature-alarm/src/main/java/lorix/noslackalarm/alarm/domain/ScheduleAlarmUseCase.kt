// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.alarm.domain

import lorix.noslackalarm.common.util.isSkippingNextRing
import lorix.noslackalarm.common.util.nextRingTimeMillis
import lorix.noslackalarm.common.util.oneTimeTriggerDate
import lorix.noslackalarm.domain.model.Alarm
import lorix.noslackalarm.domain.repository.AlarmRepository
import lorix.noslackalarm.system.AlarmScheduler
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ScheduleAlarmUseCase @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler
) {

    suspend fun schedule(alarm: Alarm): Long {
        // 仅一次闹钟：scheduledDate 设为实际触发日期（非创建日期）
        val withDate = ensureScheduledDate(alarm)
        // 插入数据库并使用数据库返回的 id 安排闹钟，避免使用临时 id=0 导致 PendingIntent 冲突
        val savedId = alarmRepository.insertAlarm(withDate)
        val saved = withDate.copy(id = savedId)
        alarmScheduler.scheduleAlarm(saved)
        // 如果之前使用临时 id=0 创建过 PendingIntent（例如调试创建流程），尝试取消它以避免重复触发
        if (savedId != 0L) {
            alarmScheduler.cancelAlarm(0L)
        }
        return savedId
    }

    /**
     * 保证仅一次闹钟的触发日期（yyyy-MM-dd）与「按当前时间算出的响铃日」一致，重复闹钟不做处理。
     *
     * 该日期是仅一次闹钟的生命周期凭据：过期自动删除 / 自动禁用、以及调度前的
     * 「日期已过则不调度」守卫都以它为条件。一旦为空或与实际响铃日不符，后果都不轻：
     * - 为空 → 清理逻辑全部静默失效，过期闹钟永远留在列表里，甚至被重新调度；
     * - 早于实际响铃日（编辑时把时间改到已过时刻会发生）→ 闹钟还没响就被清理掉，即漏响。
     *
     * 因此这里不是「为空才补」，而是每次写入都校正成权威值，让
     * `scheduledDate` 与 [nextRingTimeMillis] 永远落在同一天。
     * [schedule] 与 [update] 两条写入路径共用，覆盖新建、编辑保存、重复改仅一次等全部来源。
     */
    private fun ensureScheduledDate(alarm: Alarm): Alarm {
        if (alarm.repeatDays != 0) return alarm
        val expected = oneTimeTriggerDate(alarm.hour, alarm.minute)
        return if (alarm.scheduledDate == expected) alarm else alarm.copy(scheduledDate = expected)
    }

    suspend fun update(alarm: Alarm) {
        val withDate = ensureScheduledDate(alarm)
        alarmRepository.updateAlarm(withDate)
        if (withDate.isEnabled) {
            alarmScheduler.scheduleAlarm(withDate)
        } else {
            alarmScheduler.cancelAlarm(withDate.id)
        }
    }

    suspend fun delete(alarm: Alarm) {
        alarmRepository.deleteAlarm(alarm)
        alarmScheduler.cancelAlarm(alarm.id)
    }

    /**
     * 判断候选闹钟是否与已有闹钟重复（禁止创建）。
     *
     * 重复判定维度（按产品定义）：「响铃倒计时相同」即下次响铃时刻相同。
     * 使用 core:common 的 nextRingTimeMillis 计算下次响铃时刻，比较该时刻是否一致，
     * 该维度天然覆盖「时间(hour, minute) + 重复(repeatDays) 完全相同」的场景，
     * 并额外拦截「倒计时相同但配置不同」的闹钟（如每日 7:30 与仅一次 7:30 同一天响铃）。
     * 其余字段（模式、标签、铃声、启用状态等）不参与比较。
     * 排除自身（按 id），因此编辑保存时传入被编辑闹钟的 id 即可同时实现「排除自身」。
     */
    suspend fun isDuplicate(alarm: Alarm): Boolean {
        // 比较前归零「跳过一次」字段：跳过态闹钟的「再下一次」是被临时顺延的结果，
        // 不代表它本身的响铃规律，不应参与重复判定。
        val targetRing = nextRingTimeMillis(alarm.copy(skipNextTriggerMillis = Alarm.NO_SKIPPED_RING))
        return alarmRepository.getAllAlarms().first().any { existing ->
            existing.id != alarm.id &&
                nextRingTimeMillis(existing.copy(skipNextTriggerMillis = Alarm.NO_SKIPPED_RING)) == targetRing
        }
    }

    /**
     * 「仅关闭下一次响铃」：把当前的下次响铃时刻记为被跳过的一次，闹钟本体保持启用。
     *
     * 调用前提：闹钟当前处于启用且未跳过状态（由调用方保证），否则 [nextRingTimeMillis]
     * 算出的基准不是「下一次」，记录会失去意义，故此处直接返回。
     *
     * 被跳过的那次由 [nextRingTimeMillis] 从「下次响铃」中剔除，此处重新调度即排到再下一次；
     * 被跳过的时刻一过，跳过自动失效，闹钟恢复正常，无需任何后台任务。
     */
    suspend fun skipNextRing(alarm: Alarm) {
        if (!alarm.isEnabled || alarm.isSkippingNextRing) return
        val updated = alarm.copy(skipNextTriggerMillis = nextRingTimeMillis(alarm))
        alarmRepository.updateAlarm(updated)
        alarmScheduler.scheduleAlarm(updated)
    }

    /** 取消「仅关闭下一次响铃」，立即恢复该闹钟的常规调度。 */
    suspend fun clearSkipNext(alarm: Alarm) {
        val updated = alarm.copy(skipNextTriggerMillis = Alarm.NO_SKIPPED_RING)
        alarmRepository.updateAlarm(updated)
        alarmScheduler.scheduleAlarm(updated)
    }

    suspend fun toggleEnabled(alarm: Alarm) {
        val updated = alarm.copy(isEnabled = !alarm.isEnabled)
        alarmRepository.updateAlarm(updated)
        if (updated.isEnabled) {
            alarmScheduler.scheduleAlarm(updated)
        } else {
            alarmScheduler.cancelAlarm(updated.id)
        }
    }

    suspend fun restoreAllAlarms() {
        val enabledAlarms = alarmRepository.getEnabledAlarms().first()
        enabledAlarms.forEach { alarm ->
            alarmScheduler.scheduleAlarm(alarm)
        }
    }
}
