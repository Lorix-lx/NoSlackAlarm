// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.alarm.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.text.format.DateFormat
import lorix.noslackalarm.alarm.R
import lorix.noslackalarm.common.theme.NoSlackAlarmTheme
import lorix.noslackalarm.common.util.isCompactHeight
import top.yukonga.miuix.kmp.basic.Text
import kotlin.math.abs

private const val HOURS_PER_DAY = 24
/** 12 小时制小时轮的「面数」：12 个面要表达 24 小时，需要额外记住滚过了几个半天 */
private const val HOURS_PER_HALF_DAY = 12
private const val MINUTES_PER_HOUR = 60

/** 向下取整的取模，保证负数也返回非负结果（Kotlin 的 `%` 对负数返回负值） */
private fun floorMod(value: Int, modulus: Int): Int {
    return ((value % modulus) + modulus) % modulus
}

private fun hour24ToDisplay(hour24: Int, is24Hour: Boolean): Int {
    return if (is24Hour) {
        hour24
    } else {
        val h12 = hour24 % HOURS_PER_HALF_DAY
        if (h12 == 0) HOURS_PER_HALF_DAY else h12
    }
}

private fun displayToHour24(displayHour: Int, is24Hour: Boolean, isPm: Boolean): Int {
    return if (is24Hour) {
        floorMod(displayHour, HOURS_PER_DAY)
    } else {
        val d = displayHour % HOURS_PER_HALF_DAY
        if (isPm) d + HOURS_PER_HALF_DAY else d
    }
}

/**
 * 12 小时制小时轮的绝对位置 -> 24 小时制小时。
 *
 * 小时轮只有 12 个面却要表达 24 小时，因此用一个连续整数 `absPos` 记录滚轮的位置：
 * 每向下滚过一格 `absPos + 1`。由此可推出
 * `hour24 = absPos mod 24`、`显示值 = absPos mod 12`、`上下午 = floorDiv(absPos, 12) 的奇偶`。
 * 于是「滚到 12 时上下午自动翻转」是模型的自然结果，无需任何边界特判。
 */
private fun absPosToHour24(absPos: Int): Int = floorMod(absPos, HOURS_PER_DAY)

/** 12 小时制小时轮的绝对位置 -> 滚轮显示值（1..12） */
private fun absPosToDisplayHour(absPos: Int): Int {
    val d = floorMod(absPos, HOURS_PER_HALF_DAY)
    return if (d == 0) HOURS_PER_HALF_DAY else d
}

/** 12 小时制小时轮的绝对位置 -> 是否为下午 */
private fun absPosToIsPm(absPos: Int): Boolean {
    return Math.floorDiv(absPos, HOURS_PER_HALF_DAY) % 2 != 0
}

/** 12 面滚轮上从 [from] 转到 [to] 的最短带符号增量，取值 -6..6 */
private fun shortestDeltaOnWheel(from: Int, to: Int): Int {
    var delta = (to - from) % HOURS_PER_HALF_DAY
    if (delta > HOURS_PER_HALF_DAY / 2) delta -= HOURS_PER_HALF_DAY
    if (delta < -HOURS_PER_HALF_DAY / 2) delta += HOURS_PER_HALF_DAY
    return delta
}

/**
 * 时间选择器（AM/PM + Hour + Minute），在 24 小时制下隐藏 AM/PM。
 *
 * 滚动过程中实时联动：
 * - 12 小时制下小时轮滚过 12 时，上下午自动循环切换；
 * - 小时/分钟一经变化即回调，顶部的下次响铃预测随之实时刷新。
 *
 * 滚轮使用 [LiveNumberPicker]（Miuix NumberPicker 的 fork 版），因为官方组件只在滚动
 * 完全停止后才回调一次，无法支撑实时联动。
 */
@Composable
fun TimePicker(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val hourRange = if (is24Hour) 0..23 else 1..HOURS_PER_HALF_DAY

    // 滚轮的受控值：滚动期间必须冻结。滚轮的视觉位置 = 受控值索引 + 内部滚动偏移，
    // 而内部偏移只在滚动停止时才归零；若把实时值回灌给 value，二者会叠加导致滚轮
    // 位置错乱 / 回跳。因此这里单独维护一份「冻结值」，只在滚动结束或外部变更时更新。
    var wheelHour24 by remember { mutableIntStateOf(hour) }
    var wheelMinute by remember { mutableIntStateOf(minute) }
    // 上下午轮的冻结值（0 = 上午，1 = 下午）。它只在自身被拖动时冻结；小时轮滚动引起
    // 的上下午变化必须实时反映，由下方 SideEffect 同步。
    var wheelAmPm by remember { mutableIntStateOf(if (hour >= HOURS_PER_HALF_DAY) 1 else 0) }
    var isHourWheelScrolling by remember { mutableStateOf(false) }
    var isMinuteWheelScrolling by remember { mutableStateOf(false) }
    var isAmPmWheelScrolling by remember { mutableStateOf(false) }

    // 12 小时制下小时轮的绝对位置（见 [absPosToHour24]），以及上一次回调的虚拟索引
    var hourAbsPos by remember { mutableIntStateOf(hour) }
    var lastHourRawIndex by remember { mutableIntStateOf(0) }

    val isPm = hour >= HOURS_PER_HALF_DAY
    val amPmFromHour = if (isPm) 1 else 0

    // 外部（非滚动）变更：进入页面载入闹钟、或拖动上下午轮导致 hour / minute 变化。
    // 用 SideEffect 而不是 LaunchedEffect：滚动中 minute 会高频变化，LaunchedEffect 会
    // 每次重组都取消重建协程，SideEffect 只是每次重组后做一次轻量判等。
    SideEffect {
        if (!isHourWheelScrolling && wheelHour24 != hour) {
            wheelHour24 = hour
            hourAbsPos = hour
        }
        if (!isMinuteWheelScrolling && wheelMinute != minute) {
            wheelMinute = minute
        }
        // 小时轮实时滚动、或拖动上下午轮推高了 hour 时，上下午轮都要实时跳变
        if (!isAmPmWheelScrolling && wheelAmPm != amPmFromHour) {
            wheelAmPm = amPmFromHour
        }
    }

    val displayHourInt = hour24ToDisplay(hour, is24Hour)
    val displayHour = String.format("%02d", displayHourInt)
    val timeDesc = stringResource(R.string.time_picker_current, displayHour, String.format("%02d", minute))

    // 矮屏（横屏/小窗）下收紧高度：时间字号缩小、拾取器可见项减少；竖屏保持原样
    val compactHeight = isCompactHeight()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top display
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = displayHour,
                fontSize = if (compactHeight) 40.sp else 64.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { contentDescription = timeDesc }
            )
            Text(
                text = ":",
                fontSize = if (compactHeight) 40.sp else 64.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Text(
                text = String.format("%02d", minute),
                fontSize = if (compactHeight) 40.sp else 64.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(if (compactHeight) 0.dp else 8.dp))

        // NumberPicker row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (!is24Hour) {
                val amLabel = stringResource(R.string.am)
                val pmLabel = stringResource(R.string.pm)
                // 上下午轮同样实时：拖动过半即实时切换上下午并刷新预测。
                // 它的 value 用冻结值 wheelAmPm（拖动期间不回灌），但 wheelAmPm 会跟随
                // 小时轮引起的上下午变化实时跳变。
                LiveNumberPicker(
                    value = wheelAmPm,
                    onValueChange = { idx ->
                        // 先关闭滚动标志：吸附结束后偏移归零会产生一次伪实时回调
                        isAmPmWheelScrolling = false
                        wheelAmPm = idx
                        val newHour = displayToHour24(hour24ToDisplay(hour, false), false, idx == 1)
                        if (newHour != hour) onHourChange(newHour)
                    },
                    onLiveRawIndexChange = liveAmPm@{ rawIndex ->
                        if (!isAmPmWheelScrolling) return@liveAmPm
                        // 上下午轮只有两项且不循环，偏移被 clamp，索引只可能是 0 或 1
                        val newHour = displayToHour24(hour24ToDisplay(hour, false), false, rawIndex.coerceIn(0, 1) == 1)
                        if (newHour != hour) onHourChange(newHour)
                    },
                    onScrollStateChange = { isAmPmWheelScrolling = it },
                    range = 0..1,
                    label = { if (it == 0) amLabel else pmLabel },
                    wrapAround = false,
                    modifier = Modifier.weight(1f),
                    visibleItemCount = 3
                )
                Spacer(modifier = Modifier.width(if (compactHeight) 0.dp else 8.dp))
            }

            val wheelDisplayHour = hour24ToDisplay(wheelHour24, is24Hour)
            LiveNumberPicker(
                value = wheelDisplayHour,
                onValueChange = { committedDisplayHour ->
                    // 先关闭滚动标志：吸附结束后偏移归零会产生一次伪实时回调，
                    // 早于此处的提交值才是最终真值，后续回调一律忽略
                    isHourWheelScrolling = false
                    val newHour24 = if (is24Hour) {
                        floorMod(committedDisplayHour, HOURS_PER_DAY)
                    } else {
                        // 滚轮提交的显示小时是最终真值；绝对位置可能因最后一次回调未覆盖到
                        // 吸附后的那一帧而有 ±1 格漂移，先用最短增量校正，再取上下午。
                        hourAbsPos += shortestDeltaOnWheel(absPosToDisplayHour(hourAbsPos), committedDisplayHour)
                        displayToHour24(committedDisplayHour, false, absPosToIsPm(hourAbsPos))
                    }
                    wheelHour24 = newHour24
                    hourAbsPos = newHour24
                    if (newHour24 != hour) onHourChange(newHour24)
                },
                onLiveRawIndexChange = liveHour@{ rawIndex ->
                    if (!isHourWheelScrolling) return@liveHour
                    val newHour24 = if (is24Hour) {
                        // 24 小时制：滚轮 0..23 与小时一一对应（range 起点为 0），索引即小时
                        floorMod(rawIndex, HOURS_PER_DAY)
                    } else {
                        // 12 小时制：累加索引增量得到绝对位置，上下午随之自动循环
                        val delta = rawIndex - lastHourRawIndex
                        lastHourRawIndex = rawIndex
                        // 单帧不可能滚过一整圈，超大的增量是「吸附结束后偏移归零」产生的伪事件
                        if (delta == 0 || abs(delta) >= HOURS_PER_DAY) return@liveHour
                        hourAbsPos += delta
                        absPosToHour24(hourAbsPos)
                    }
                    if (newHour24 != hour) onHourChange(newHour24)
                },
                onScrollStateChange = { scrolling ->
                    if (scrolling) {
                        // 记录本次滚动的起点索引。若上一次惯性尚未结束就再次按下，
                        // 旧的 lastHourRawIndex 仍然连续，不能重置，否则会多算一段增量。
                        if (!isHourWheelScrolling) lastHourRawIndex = wheelDisplayHour - hourRange.first
                        isHourWheelScrolling = true
                    } else {
                        isHourWheelScrolling = false
                    }
                },
                range = hourRange,
                label = { String.format("%02d", it) },
                wrapAround = true,
                modifier = Modifier.weight(2f),
                visibleItemCount = if (compactHeight) 3 else 5
            )

            Spacer(modifier = Modifier.width(if (compactHeight) 0.dp else 8.dp))

            LiveNumberPicker(
                value = wheelMinute,
                onValueChange = { committedMinute ->
                    // 同上：先关闭滚动标志，忽略吸附结束后偏移归零产生的伪实时回调
                    isMinuteWheelScrolling = false
                    wheelMinute = committedMinute
                    if (committedMinute != minute) onMinuteChange(committedMinute)
                },
                onLiveRawIndexChange = liveMinute@{ rawIndex ->
                    if (!isMinuteWheelScrolling) return@liveMinute
                    // 分钟轮 range 起点为 0，索引即分钟
                    val newMinute = floorMod(rawIndex, MINUTES_PER_HOUR)
                    if (newMinute != minute) onMinuteChange(newMinute)
                },
                onScrollStateChange = { isMinuteWheelScrolling = it },
                range = 0..59,
                label = { String.format("%02d", it) },
                wrapAround = true,
                modifier = Modifier.weight(2f),
                visibleItemCount = if (compactHeight) 3 else 5
            )
        }
    }
}

@Preview
@Composable
private fun TimePickerPreview() {
    NoSlackAlarmTheme {
        TimePicker(
            hour = 7,
            minute = 30,
            onHourChange = {},
            onMinuteChange = {}
        )
    }
}