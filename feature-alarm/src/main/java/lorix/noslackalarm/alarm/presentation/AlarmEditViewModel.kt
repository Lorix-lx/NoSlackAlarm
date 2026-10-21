// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.alarm.presentation

import android.util.Log
import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
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
    /**
     * 仅一次闹钟的实际触发日期（yyyy-MM-dd），重复闹钟为空串。
     *
     * 编辑页表单不展示它，但编辑保存时要原样带回去：它是仅一次闹钟的生命周期凭据
     * （过期自动删除 / 自动禁用都以此列为条件），丢失会让该闹钟永远无法被清理。
     * 新建时留空。注意这里不必自行重算——ScheduleAlarmUseCase 在写入前会统一校正，
     * 保证它与实际响铃日落在同一天。
     */
    val scheduledDate: String = "",
    /**
     * 「响铃后删除此闹钟」选项值：仅重复=仅一次（repeatDays == 0）时有语义，默认关闭。
     * 重复切换为其他规则时由 [setRepeatDays] 自动重置为 false。
     */
    val deleteAfterRing: Boolean = false,
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
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        private const val TAG = "AlarmEditViewModel"

        /** 草稿写回的尾随去抖时长（ms）：时间滚轮滚动期间会高频触发状态更新，避免每次都写 SavedStateHandle。 */
        private const val DRAFT_WRITE_DEBOUNCE_MS = 400L

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

        /** 重复预设 → 重复天数的纯映射（周一~周日按位）；[RepeatPreset.CUSTOM] 无固定取值，返回 null。 */
        fun presetToDays(preset: RepeatPreset): Int? = when (preset) {
            RepeatPreset.ONCE -> 0
            RepeatPreset.DAILY -> 127
            RepeatPreset.WEEKDAYS -> 31
            RepeatPreset.WEEKENDS -> 96
            RepeatPreset.CUSTOM -> null
        }

        /** SavedStateHandle 中草稿字段的键名，集中在此避免字符串散落。 */
        private object DraftKeys {
            const val ALARM_ID = "draft_alarm_id"
            const val HOUR = "draft_hour"
            const val MINUTE = "draft_minute"
            const val REPEAT_DAYS = "draft_repeat_days"
            const val ENABLED = "draft_enabled"
            const val MODE = "draft_mode"
            const val LABEL = "draft_label"
            const val SCHEDULED_DATE = "draft_scheduled_date"
            const val DELETE_AFTER_RING = "draft_delete_after_ring"

            val ALL = listOf(
                ALARM_ID, HOUR, MINUTE, REPEAT_DAYS, ENABLED,
                MODE, LABEL, SCHEDULED_DATE, DELETE_AFTER_RING
            )
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
     * 初始化 ViewModel 参数（由 miuix-nav 的路由值经 entry 透传进来）。
     *
     * ViewModel 的作用域与「本页在返回栈上的存续期」一致：每次进入编辑页都是独立实例，
     * 被 pop 时随之清理，因此这里只需处理「同一实例内的重复初始化」——即配置变更/重组
     * 导致 LaunchedEffect 重跑的场景。通过跟踪上次初始化的 alarmId，相同 alarmId 不重复加载数据。
     */
    fun initialize(alarmId: Long, hour: Int, minute: Int) {
        this.alarmId = alarmId

        val isSameAlarm = lastInitializedAlarmId == alarmId
        lastInitializedAlarmId = alarmId

        // 进程被系统回收后重新进入本页时，SavedStateHandle 里可能留有上一次未保存的编辑内容。
        // 仅当草稿的 alarmId 与本次路由参数一致时才采用，避免串到别的闹钟上。
        val draft = if (isSameAlarm) null else readValidDraft(alarmId)

        _state.update { it.copy(isLoading = !isSameAlarm && alarmId != -1L && draft == null) }

        if (draft != null) {
            Log.d(TAG, "init: 恢复未保存草稿 alarmId=$alarmId")
            _state.value = draft
            return
        }

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
                // 新建闹钟的触发日期由写入前统一补算，避免沿用上次编辑残留的日期
                scheduledDate = "",
                // 新建默认关闭「响铃后删除此闹钟」（该选项仅在仅一次闹钟下展示/生效）
                deleteAfterRing = false,
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
                        scheduledDate = alarm.scheduledDate,
                        deleteAfterRing = alarm.deleteAfterRing,
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
            // 「响铃后删除此闹钟」仅对仅一次闹钟有效：重复切到其他规则（days != 0）时自动重置为关闭
            val newState = it.copy(
                repeatDays = days,
                deleteAfterRing = if (days == 0) it.deleteAfterRing else false
            )
            val (nextHours, nextMinutes) = calculateNextAlarmTimeDiff(newState.hour, newState.minute, newState.repeatDays)
            newState.copy(nextAlarmHours = nextHours, nextAlarmMinutes = nextMinutes)
        }
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

    fun setDeleteAfterRing(enabled: Boolean) {
        _state.update { it.copy(deleteAfterRing = enabled) }
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
                snoozeCount = 0,
                scheduledDate = current.scheduledDate,
                deleteAfterRing = current.deleteAfterRing,
                // 编辑保存视为用户接管该闹钟：清除可能残留的「答题关闭后待删除」标记，
                // 避免其下次打开应用时被批量删除。
                pendingDelete = false
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

    /**
     * 草稿写回：用 collectLatest + delay 实现尾随去抖。
     * 时间滚轮滚动期间会高频回调 [setTime]，若每次状态变化都同步写 SavedStateHandle 开销偏大；
     * 这里只在状态静默 [DRAFT_WRITE_DEBOUNCE_MS] 之后写一次。
     */
    private fun observeDraftWrites() {
        viewModelScope.launch {
            _state
                .drop(1) // 丢弃初始状态：还没有任何用户输入，无需写回
                .collectLatest { state ->
                    delay(DRAFT_WRITE_DEBOUNCE_MS)
                    writeDraft(state)
                }
        }
    }

    /** 把当前表单快照写入 SavedStateHandle（各字段均为基础类型，无需自定义序列化）。 */
    private fun writeDraft(state: AlarmEditUiState) {
        savedStateHandle[DraftKeys.ALARM_ID] = state.alarmId
        savedStateHandle[DraftKeys.HOUR] = state.hour
        savedStateHandle[DraftKeys.MINUTE] = state.minute
        savedStateHandle[DraftKeys.REPEAT_DAYS] = state.repeatDays
        savedStateHandle[DraftKeys.ENABLED] = state.isEnabled
        savedStateHandle[DraftKeys.MODE] = state.mode.name
        savedStateHandle[DraftKeys.LABEL] = state.label
        savedStateHandle[DraftKeys.SCHEDULED_DATE] = state.scheduledDate
        savedStateHandle[DraftKeys.DELETE_AFTER_RING] = state.deleteAfterRing
    }

    /**
     * 读取草稿快照。只有草稿中的 alarmId 与本次路由参数一致时才认为可用——
     * 否则说明它是别的闹钟（或上一次编辑）留下的残留，应当忽略。
     */
    private fun readValidDraft(alarmId: Long): AlarmEditUiState? {
        val draftAlarmId: Long = savedStateHandle[DraftKeys.ALARM_ID] ?: return null
        if (draftAlarmId != alarmId) return null

        val hour: Int = savedStateHandle[DraftKeys.HOUR] ?: return null
        val minute: Int = savedStateHandle[DraftKeys.MINUTE] ?: return null
        val repeatDays: Int = savedStateHandle[DraftKeys.REPEAT_DAYS] ?: return null
        val isEnabled: Boolean = savedStateHandle[DraftKeys.ENABLED] ?: return null
        val modeName: String = savedStateHandle[DraftKeys.MODE] ?: return null
        val label: String = savedStateHandle[DraftKeys.LABEL] ?: return null
        val scheduledDate: String = savedStateHandle[DraftKeys.SCHEDULED_DATE] ?: return null
        val deleteAfterRing: Boolean = savedStateHandle[DraftKeys.DELETE_AFTER_RING] ?: return null

        // 枚举名可能因版本回退等原因失效，解析失败时退回默认值而不是抛异常
        val mode = runCatching { AlarmMode.valueOf(modeName) }.getOrDefault(AlarmMode.BOTH)
        val (nextHours, nextMinutes) = calculateNextAlarmTimeDiff(hour, minute, repeatDays)
        return AlarmEditUiState(
            alarmId = alarmId,
            hour = hour,
            minute = minute,
            repeatDays = repeatDays,
            isEnabled = isEnabled,
            mode = mode,
            label = label,
            scheduledDate = scheduledDate,
            deleteAfterRing = deleteAfterRing,
            nextAlarmHours = nextHours,
            nextAlarmMinutes = nextMinutes,
        )
    }

    /**
     * 清除草稿。草稿的生命周期必须严格等于 entry：本页被 pop（ViewModel 被清理）后，
     * 若下一次「新建闹钟」的路由值与本次完全相同（contentKey 相同），旧草稿会被误读——
     * 那正是本次要消除的「上次输入残留」问题。
     */
    private fun clearDraft() {
        DraftKeys.ALL.forEach { key -> savedStateHandle.remove<Any>(key) }
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
        observeDraftWrites()
    }

    override fun onCleared() {
        super.onCleared()
        cancelUpdateTimer()
        clearDraft()
    }
}