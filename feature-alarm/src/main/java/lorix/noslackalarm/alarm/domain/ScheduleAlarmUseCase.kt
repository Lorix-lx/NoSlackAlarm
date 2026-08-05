// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.alarm.domain

import lorix.noslackalarm.common.util.nextRingTimeMillis
import lorix.noslackalarm.domain.model.Alarm
import lorix.noslackalarm.domain.repository.AlarmRepository
import lorix.noslackalarm.system.AlarmScheduler
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

class ScheduleAlarmUseCase @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler
) {

    suspend fun schedule(alarm: Alarm): Long {
        // 仅一次闹钟：scheduledDate 设为实际触发日期（非创建日期）
        val withDate = if (alarm.repeatDays == 0 && alarm.scheduledDate.isEmpty()) {
            alarm.copy(scheduledDate = calculateTriggerDate(alarm))
        } else alarm
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
     * 计算仅一次闹钟的实际触发日期（yyyy-MM-dd）。
     * 如果今日时间已过 → 明天，否则 → 今天。
     * 避免 scheduledDate = 创建日期 导致误禁用明天闹钟。
     */
    private fun calculateTriggerDate(alarm: Alarm): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            sdf.format(cal.time)
        } else {
            sdf.format(cal.time)
        }
    }

    suspend fun update(alarm: Alarm) {
        alarmRepository.updateAlarm(alarm)
        if (alarm.isEnabled) {
            alarmScheduler.scheduleAlarm(alarm)
        } else {
            alarmScheduler.cancelAlarm(alarm.id)
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
        val targetRing = nextRingTimeMillis(alarm)
        return alarmRepository.getAllAlarms().first().any { existing ->
            existing.id != alarm.id &&
                nextRingTimeMillis(existing) == targetRing
        }
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
