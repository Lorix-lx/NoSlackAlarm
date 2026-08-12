// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.alarm.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import lorix.noslackalarm.alarm.domain.ScheduleAlarmUseCase
import lorix.noslackalarm.domain.model.Alarm
import lorix.noslackalarm.domain.model.AlarmMode
import lorix.noslackalarm.domain.repository.AlarmRepository
import lorix.noslackalarm.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class AlarmEditUiState(
    val alarmId: Long = -1L,
    val hour: Int = 7,
    val minute: Int = 30,
    val repeatDays: Int = 0,
    val isEnabled: Boolean = true,
    val mode: AlarmMode = AlarmMode.BOTH,
    val label: String = "",
    val isSaving: Boolean = false,
    val showAccessibilityTip: Boolean = false,
    val isLoading: Boolean = false,
    val nextAlarmHours: Int = 0,
    val nextAlarmMinutes: Int = 0,
    val showDuplicateDialog: Boolean = false
)

enum class RepeatPreset { ONCE, DAILY, WEEKDAYS, WEEKENDS, CUSTOM }

@HiltViewModel
class AlarmEditViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val scheduleAlarmUseCase: ScheduleAlarmUseCase,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "AlarmEditViewModel"

        private val DAY_LABEL_MAP = listOf(
            1 to "周一", 2 to "周二", 4 to "周三", 8 to "周四",
            16 to "周五", 32 to "周六", 64 to "周日"
        )

        fun resolvePreset(days: Int): RepeatPreset = when (days) {
            0 -> RepeatPreset.ONCE
            127 -> RepeatPreset.DAILY
            31 -> RepeatPreset.WEEKDAYS
            96 -> RepeatPreset.WEEKENDS
            else -> RepeatPreset.CUSTOM
        }

        fun getRepeatDisplay(days: Int): String {
            return when (resolvePreset(days)) {
                RepeatPreset.ONCE -> "仅一次"
                RepeatPreset.DAILY -> "每天"
                RepeatPreset.WEEKDAYS -> "周一至周五"
                RepeatPreset.WEEKENDS -> "周六周日"
                RepeatPreset.CUSTOM -> DAY_LABEL_MAP
                    .filter { (bit, _) -> days and bit != 0 }
                    .joinToString(" ") { it.second }
            }
        }
    }

    private var alarmId: Long = -1L

    private val _state = MutableStateFlow(AlarmEditUiState())
    val state: StateFlow<AlarmEditUiState> get() = _state.asStateFlow()

    private val _savedEvent = Channel<Long?>(Channel.CONFLATED)
    val savedEvent = _savedEvent.receiveAsFlow()

    private var lastInitializedAlarmId: Long? = null
    private var updateTimerJob: Job? = null

    /**
     * 初始化 ViewModel 参数（由 Navigation3 的 Screen 参数传入）。
     * 由于 ViewModel 是 Activity 作用域的（Navigation3 不给 entry 创建
     * 独立 ViewModelStoreOwner），同一 ViewModel 可能被多次复用。
     * 这里通过跟踪上次初始化的 alarmId，当 alarmId 变化时允许重新初始化。
     *
     * 新建闹钟页的 alarmId 恒为 -1L：连续新建时 lastInitializedAlarmId 也是 -1L，
     * 会被 isSameAlarm 误判为同一闹钟而跳过重置。因此在「离开新建页」的三个时机
     * （保存成功返回 / 顶栏返回 / 系统返回键）由 [onEditScreenExited] 将
     * lastInitializedAlarmId 置空，保证下一次进入新建页时正常重置为空白表单。
     */
    fun initialize(alarmId: Long, hour: Int, minute: Int) {
        this.alarmId = alarmId

        val isSameAlarm = lastInitializedAlarmId == alarmId
        lastInitializedAlarmId = alarmId
        _state.update { it.copy(isLoading = !isSameAlarm && alarmId != -1L) }

        // 相同的 alarmId 不重复加载数据（防止 LaunchedEffect 重复触发）
        // if (isSameAlarm) return

        if (alarmId != -1L) {
            Log.d(TAG, "init: alarmId=$alarmId, hour=$hour, minute=$minute")
            // 即使 isSameAlarm，也传当前 repeatDays 来更准确计算倒计时
            val effectiveRepeatDays = _state.value.repeatDays
            val (nextHours, nextMinutes) = calculateNextAlarmTimeDiff(hour, minute, effectiveRepeatDays)
            _state.update { 
                it.copy(
                    alarmId = alarmId, 
                    hour = hour, 
                    minute = minute, 
                    isLoading = !isSameAlarm,  // 同一条目不显示加载圈，但依然加载数据
                    nextAlarmHours = nextHours, 
                    nextAlarmMinutes = nextMinutes
                ) 
            }
            if (!isSameAlarm) {
                loadAlarm()
            }
        } else if (!isSameAlarm) {
            // 新建闹钟：重置为默认值（仅首次初始化时）
            val (nextHours, nextMinutes) = calculateNextAlarmTimeDiff(hour, minute, 0)
            _state.update { it.copy(
                alarmId = -1L, 
                hour = hour, 
                minute = minute, 
                repeatDays = 0, 
                isEnabled = true,
                mode = AlarmMode.BOTH,
                label = "", 
                isLoading = false, 
                nextAlarmHours = nextHours, 
                nextAlarmMinutes = nextMinutes
            ) }
            // 异步读取用户设置的默认提醒模式并应用
            viewModelScope.launch {
                try {
                    val modeStr = settingsRepository.defaultMode.first()
                    val defaultMode = AlarmMode.valueOf(modeStr)
                    _state.update { it.copy(mode = defaultMode) }
                } catch (e: Exception) {
                    Log.w(TAG, "读取默认提醒模式失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 离开新建闹钟页时调用（保存成功返回 / 顶栏返回 / 系统返回键）。
     * 新建页 alarmId 恒为 -1L，若不清除 lastInitializedAlarmId，下一次进入新建页时
     * isSameAlarm 会误判为 true，导致 label 等字段残留上次新建的值。
     * 编辑已有闹钟（alarmId != -1L）时调用无副作用。
     */
    fun onEditScreenExited() {
        if (alarmId == -1L) {
            lastInitializedAlarmId = null
        }
    }

    private fun loadAlarm() {
        viewModelScope.launch {
            val alarm = alarmRepository.getAlarmById(alarmId)
            if (alarm != null) {
                Log.d(TAG, "loadAlarm id=$alarmId -> ${alarm.hour}:${alarm.minute}")
                _state.update { state ->
                    val (nextHours, nextMinutes) = calculateNextAlarmTimeDiff(state.hour, state.minute, alarm.repeatDays)
                    state.copy(
                        repeatDays = alarm.repeatDays,
                        isEnabled = alarm.isEnabled,
                        mode = alarm.mode,
                        label = alarm.label ?: "",
                        isLoading = false,
                        nextAlarmHours = nextHours,
                        nextAlarmMinutes = nextMinutes
                    )
                }
            } else {
                Log.d(TAG, "loadAlarm id=$alarmId -> not found")
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setTime(hour: Int, minute: Int) {
        _state.update { 
            val newState = it.copy(hour = hour, minute = minute)
            val (nextHours, nextMinutes) = calculateNextAlarmTimeDiff(newState.hour, newState.minute, newState.repeatDays)
            newState.copy(nextAlarmHours = nextHours, nextAlarmMinutes = nextMinutes)
        }
    }

    fun setRepeatDays(days: Int) {
        _state.update { 
            val newState = it.copy(repeatDays = days)
            val (nextHours, nextMinutes) = calculateNextAlarmTimeDiff(newState.hour, newState.minute, newState.repeatDays)
            newState.copy(nextAlarmHours = nextHours, nextAlarmMinutes = nextMinutes)
        }
    }

    fun setRepeatPreset(preset: RepeatPreset) {
        val days = when (preset) {
            RepeatPreset.ONCE -> 0
            RepeatPreset.DAILY -> 127
            RepeatPreset.WEEKDAYS -> 31
            RepeatPreset.WEEKENDS -> 96
            RepeatPreset.CUSTOM -> return
        }
        setRepeatDays(days)
    }

    private fun calculateNextAlarmTimeDiff(hour: Int, minute: Int, repeatDays: Int): Pair<Int, Int> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val now = System.currentTimeMillis()

        if (repeatDays == 0) {
            if (calendar.timeInMillis <= now) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        } else {
            val todayWeekday = getCalendarWeekdayBit(Calendar.getInstance())
            if ((repeatDays and todayWeekday) == 0 || calendar.timeInMillis <= now) {
                for (i in 1..7) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                    val futureWeekday = getCalendarWeekdayBit(calendar)
                    if ((repeatDays and futureWeekday) != 0) {
                        break
                    }
                }
            }
        }

        val diffMillis = calendar.timeInMillis - now
        val diffHours = (diffMillis / (1000 * 60 * 60)).toInt()
        val diffMinutes = ((diffMillis % (1000 * 60 * 60)) / (1000 * 60)).toInt()

        return Pair(diffHours, diffMinutes)
    }

    private fun getCalendarWeekdayBit(calendar: Calendar): Int {
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 4
            Calendar.THURSDAY -> 8
            Calendar.FRIDAY -> 16
            Calendar.SATURDAY -> 32
            Calendar.SUNDAY -> 64
            else -> 0
        }
    }

    fun setMode(mode: AlarmMode) {
        _state.update { it.copy(mode = mode) }
    }

    fun setLabel(label: String) {
        _state.update { it.copy(label = label) }
    }

    fun save() {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val current = _state.value
            val alarm = Alarm(
                id = if (alarmId == -1L) 0 else alarmId,
                hour = current.hour,
                minute = current.minute,
                repeatDays = current.repeatDays,
                isEnabled = current.isEnabled,
                mode = current.mode,
                ringtoneUri = null,
                label = current.label.takeIf { it.isNotBlank() },
                snoozeCount = 0
            )
            // 拦截与已有闹钟完全相同的闹钟（时间 + 重复 一致）。
            // 编辑时传入被编辑闹钟自身的 id，isDuplicate 会排除自身，不会误伤。
            if (scheduleAlarmUseCase.isDuplicate(alarm)) {
                _state.update { it.copy(isSaving = false, showDuplicateDialog = true) }
                return@launch
            }
            // 新建时返回新插入的 id 用于返回主页后高亮；编辑时返回被编辑闹钟的 id，
            // 让主页高亮正确的卡片（而不是残留的旧 id）
            val createdId = if (alarmId == -1L) {
                scheduleAlarmUseCase.schedule(alarm)
            } else {
                scheduleAlarmUseCase.update(alarm)
                alarmId
            }
            val shouldShowTip = if (current.isEnabled) {
                false
            } else {
                false
            }
            _state.update { it.copy(isSaving = false, showAccessibilityTip = shouldShowTip) }
            _savedEvent.trySend(createdId)
        }
    }

    /** 关闭重复提示弹窗并复位标志。 */
    fun dismissDuplicateDialog() {
        _state.update { it.copy(showDuplicateDialog = false) }
    }

    fun dismissAccessibilityTip() {
        _state.update { it.copy(showAccessibilityTip = false) }
    }

    private fun startUpdateTimer() {
        updateTimerJob?.cancel()
        updateTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _state.update { state ->
                    val (nextHours, nextMinutes) = calculateNextAlarmTimeDiff(state.hour, state.minute, state.repeatDays)
                    state.copy(nextAlarmHours = nextHours, nextAlarmMinutes = nextMinutes)
                }
            }
        }
    }

    private fun cancelUpdateTimer() {
        updateTimerJob?.cancel()
        updateTimerJob = null
    }

    init {
        startUpdateTimer()
    }

    override fun onCleared() {
        super.onCleared()
        cancelUpdateTimer()
    }
}