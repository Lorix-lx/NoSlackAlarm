// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.alarm.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import lorix.noslackalarm.alarm.domain.ScheduleAlarmUseCase
import lorix.noslackalarm.domain.model.Alarm
import lorix.noslackalarm.domain.model.AlarmMode
import lorix.noslackalarm.domain.repository.AlarmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class AlarmListUiState(
    val alarms: List<Alarm> = emptyList(),
    val isLoading: Boolean = false,
    val showDuplicateDialog: Boolean = false
)

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val scheduleAlarmUseCase: ScheduleAlarmUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(AlarmListUiState(isLoading = true))
    val state: StateFlow<AlarmListUiState> = _state.asStateFlow()

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

    fun toggleAlarm(alarm: Alarm) {
        viewModelScope.launch {
            scheduleAlarmUseCase.toggleEnabled(alarm)
        }
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
