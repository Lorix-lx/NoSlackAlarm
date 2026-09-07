// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.ringing.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import lorix.noslackalarm.common.SettingsDefaults
import lorix.noslackalarm.common.constants.Constants
import lorix.noslackalarm.domain.model.Alarm
import lorix.noslackalarm.domain.model.AlarmMode
import lorix.noslackalarm.domain.repository.AlarmRepository
import lorix.noslackalarm.ringing.domain.MathProblem
import lorix.noslackalarm.ringing.domain.MathProblemGenerator
import lorix.noslackalarm.ringing.domain.ValidateMathAnswerUseCase
import lorix.noslackalarm.ringing.service.RingtonePlayer
import lorix.noslackalarm.ringing.service.VibrationController
import lorix.noslackalarm.system.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RingingUiState(
    val currentTime: String = "",
    val problem: MathProblem = MathProblem("", ""),
    val input: String = "",
    val isWrong: Boolean = false,
    val wrongCount: Int = 0,
    val snoozeCount: Int = 0,
    val maxSnoozeCount: Int = SettingsDefaults.MAX_SNOOZE_COUNT,
    val showSketchpad: Boolean = false,
    val isFinished: Boolean = false
)

@HiltViewModel
class RingingViewModel @Inject constructor(
    private val validateMathAnswerUseCase: ValidateMathAnswerUseCase,
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler,
    private val ringtonePlayer: RingtonePlayer,
    private val vibrationController: VibrationController
) : ViewModel() {

    private var alarmId: Long = -1L

    private val _state = MutableStateFlow(RingingUiState())
    val state: StateFlow<RingingUiState> = _state.asStateFlow()

    private var currentAlarm: Alarm? = null

    // 打开白板时暂停提醒仅执行一次，避免反复暂停/恢复
    private var sketchpadPauseDone = false

    fun initAlarmId(id: Long) {
        if (alarmId == id) {
            return
        }
        alarmId = id
        loadAlarm()
    }

    private fun loadAlarm() {
        viewModelScope.launch {
            currentAlarm = alarmRepository.getAlarmById(alarmId)
            currentAlarm?.let { alarm ->
                _state.update {
                    it.copy(snoozeCount = alarm.snoozeCount)
                }
            }
        }
    }

    init {
        generateNewProblem()
    }

    fun updateTime(time: String) {
        _state.update { it.copy(currentTime = time) }
    }

    fun onNumberClick(number: String) {
        if (_state.value.isWrong) {
            _state.update { it.copy(isWrong = false) }
        }
        val newInput = _state.value.input + number
        if (newInput.length <= 6) {
            _state.update { it.copy(input = newInput) }
        }
    }

    fun onDelete() {
        val current = _state.value.input
        if (current.isNotEmpty()) {
            _state.update { it.copy(input = current.dropLast(1)) }
        }
    }

    fun onSubmit() {
        val currentState = _state.value
        if (currentState.input.isEmpty()) {
            return
        }

        val isCorrect = validateMathAnswerUseCase(currentState.problem, currentState.input)
        if (isCorrect) {
            _state.update { it.copy(isFinished = true) }
            viewModelScope.launch {
                alarmScheduler.cancelSnooze(alarmId)
                currentAlarm?.let { alarm ->
                    var updated = if (alarm.snoozeCount > 0) {
                        alarm.copy(snoozeCount = 0)
                    } else alarm
                    // 仅一次闹钟：解题完成后自动关闭。
                    // 若开启了「响铃后删除此闹钟」，不立即删除，而是置「待删除」标记，
                    // 由下次打开应用进入主页面前的批量清理统一删除。
                    if (alarm.repeatDays == 0) {
                        updated = updated.copy(
                            isEnabled = false,
                            pendingDelete = alarm.deleteAfterRing
                        )
                    }
                    if (updated != alarm) {
                        alarmRepository.updateAlarm(updated)
                    }
                }
            }
        } else {
            val newWrongCount = currentState.wrongCount + 1
            if (newWrongCount >= 3) {
                generateNewProblem()
                _state.update {
                    it.copy(input = "", isWrong = true, wrongCount = 0)
                }
            } else {
                _state.update {
                    it.copy(input = "", isWrong = true, wrongCount = newWrongCount)
                }
            }
        }
    }

    fun onSnooze() {
        viewModelScope.launch {
            currentAlarm?.let { alarm ->
                val newCount = alarm.snoozeCount + 1
                alarmRepository.updateAlarm(alarm.copy(snoozeCount = newCount))
            }
        }
    }

    fun onOpenSketchpad() {
        _state.update { it.copy(showSketchpad = true) }
        // 打开白板：暂停对应提醒方式一段时间一次且仅一次，到点未解答则恢复
        if (sketchpadPauseDone) return
        sketchpadPauseDone = true

        val mode = currentAlarm?.mode
        val needsRingtone = mode != AlarmMode.VIBRATE_ONLY
        val needsVibration = mode != AlarmMode.RINGTONE_ONLY

        if (needsRingtone) ringtonePlayer.stop()
        if (needsVibration) vibrationController.stop()

        viewModelScope.launch {
            delay(Constants.SKETCHPAD_ALARM_PAUSE_MS)
            // 若已解答关闭闹钟（服务已停止）则不恢复，避免误重启响铃
            if (_state.value.isFinished) return@launch
            if (needsRingtone) ringtonePlayer.play()
            if (needsVibration) vibrationController.start()
        }
    }

    fun onCloseSketchpad() {
        _state.update { it.copy(showSketchpad = false) }
    }

    private fun generateNewProblem() {
        _state.update { it.copy(problem = MathProblemGenerator.generate()) }
    }
}
