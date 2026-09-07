// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.alarm.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import lorix.noslackalarm.alarm.domain.ScheduleAlarmUseCase
import lorix.noslackalarm.common.util.isSkippingNextRing
import lorix.noslackalarm.domain.model.Alarm
import lorix.noslackalarm.domain.model.AlarmMode
import lorix.noslackalarm.domain.repository.AlarmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class AlarmListUiState(
    val alarms: List<Alarm> = emptyList(),
    val isLoading: Boolean = false,
    val showDuplicateDialog: Boolean = false,
    /**
     * 正在等待选择关闭方式的重复闹钟；非空即展示「仅关闭下一次 / 永久关闭」弹窗。
     * 仅一次闹钟与停用中的闹钟不会走到该弹窗。
     */
    val disableChoiceTarget: Alarm? = null
)

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val scheduleAlarmUseCase: ScheduleAlarmUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(AlarmListUiState(isLoading = true))
    val state: StateFlow<AlarmListUiState> = _state.asStateFlow()

    /**
     * 卡片高亮事件：任何开关类写入成功后发出该闹钟 id，由界面触发一次卡片边框高亮。
     * 高亮属于纯界面反馈，故只发事件、不由 ViewModel 持有高亮状态。
     */
    private val _highlightEvent = Channel<Long>(Channel.CONFLATED)
    val highlightEvent: Flow<Long> = _highlightEvent.receiveAsFlow()

    init {
        loadAlarms()
    }

    /**
     * 加载闹钟列表前，先清理已过期的仅一次闹钟。
     * 这样用户每次进入列表页都保证看到的是最新状态，
     * 即使锁屏解题后台禁用失败也不影响。
     */
    private fun loadAlarms() {
        viewModelScope.launch {
            // 清理已过期的仅一次闹钟 · 在数据展示前完成
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
            alarmRepository.deleteExpiredOneTimeAlarms(today)
            val cal = Calendar.getInstance()
            val nowHour = cal.get(Calendar.HOUR_OF_DAY)
            val nowMinute = cal.get(Calendar.MINUTE)
            alarmRepository.getTodayOneTimeAlarms(today).forEach { alarm ->
                if (alarm.hour < nowHour || (alarm.hour == nowHour && alarm.minute <= nowMinute)) {
                    alarmRepository.updateAlarm(alarm.copy(isEnabled = false))
                }
            }

            // 然后加载列表
            alarmRepository.getAllAlarms().collect { alarms ->
                _state.update { it.copy(alarms = alarms, isLoading = false) }
            }
        }
    }

    /**
     * 卡片开关点击的统一入口，按闹钟当前状态分发：
     * - 处于「仅关闭下一次响铃」的临时关闭态 → 取消跳过、立即恢复启用；
     * - 已永久禁用 → 直接启用；
     * - 仅一次闹钟 → 维持原有直接禁用行为，不弹窗；
     * - 启用的重复闹钟 → 弹出关闭方式二选一，等用户决定。
     *
     * 前三种会真正写入数据，写完发出高亮事件；第四种只打开弹窗，不发高亮。
     */
    fun requestToggle(alarm: Alarm) {
        viewModelScope.launch {
            when {
                alarm.isSkippingNextRing -> {
                    scheduleAlarmUseCase.clearSkipNext(alarm)
                    notifyHighlight(alarm.id)
                }
                !alarm.isEnabled || alarm.repeatDays == 0 -> {
                    scheduleAlarmUseCase.toggleEnabled(alarm)
                    notifyHighlight(alarm.id)
                }
                else -> _state.update { it.copy(disableChoiceTarget = alarm) }
            }
        }
    }

    /** 关闭方式弹窗选择「仅在 x月x日 关闭一次」。 */
    fun skipNextRingOnly() {
        val alarm = _state.value.disableChoiceTarget ?: return
        viewModelScope.launch {
            scheduleAlarmUseCase.skipNextRing(alarm)
            notifyHighlight(alarm.id)
            dismissDisableChoice()
        }
    }

    /** 关闭方式弹窗选择「关闭这个重复闹钟」（等同永久禁用）。 */
    fun disableRepeatingAlarm() {
        val alarm = _state.value.disableChoiceTarget ?: return
        viewModelScope.launch {
            scheduleAlarmUseCase.toggleEnabled(alarm)
            notifyHighlight(alarm.id)
            dismissDisableChoice()
        }
    }

    /** 关闭关闭方式弹窗并复位目标（取消操作也走这里）。 */
    fun dismissDisableChoice() {
        _state.update { it.copy(disableChoiceTarget = null) }
    }

    /** 发出一次卡片高亮请求。 */
    private fun notifyHighlight(alarmId: Long) {
        _highlightEvent.trySend(alarmId)
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            scheduleAlarmUseCase.delete(alarm)
        }
    }

    /**
     * 创建调试闹钟：仅一次、仅震动、当前时间下一分钟。
     */
    fun createDebugAlarm() {
        viewModelScope.launch {
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.MINUTE, 1)
            val alarm = Alarm(
                hour = cal.get(java.util.Calendar.HOUR_OF_DAY),
                minute = cal.get(java.util.Calendar.MINUTE),
                repeatDays = 0,
                isEnabled = true,
                mode = AlarmMode.VIBRATE_ONLY,
                ringtoneUri = null,
                label = "调试闹钟"
            )
            // 拦截与已有闹钟完全相同的调试闹钟（时间 + 重复 一致）
            if (scheduleAlarmUseCase.isDuplicate(alarm)) {
                _state.update { it.copy(showDuplicateDialog = true) }
                return@launch
            }
            scheduleAlarmUseCase.schedule(alarm)
        }
    }

    /** 关闭重复提示弹窗并复位标志。 */
    fun dismissDuplicateDialog() {
        _state.update { it.copy(showDuplicateDialog = false) }
    }
}
