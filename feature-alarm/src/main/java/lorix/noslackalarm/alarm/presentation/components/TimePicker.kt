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
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Text

private fun hour24ToDisplay(hour24: Int, is24Hour: Boolean): Int {
    return if (is24Hour) {
        hour24
    } else {
        val h12 = hour24 % 12
        if (h12 == 0) 12 else h12
    }
}

private fun displayToHour24(displayHour: Int, is24Hour: Boolean, isPm: Boolean): Int {
    return if (is24Hour) {
        displayHour % 24
    } else {
        val d = displayHour % 12
        if (isPm) d + 12 else d
    }
}

/**
 * 使用 Miuix NumberPicker 实现的时间选择器（AM/PM + Hour + Minute），在 24 小时制下隐藏 AM/PM
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

    val displayHourInt = hour24ToDisplay(hour, is24Hour)
    val displayHour = String.format("%02d", displayHourInt)
    val timeDesc = stringResource(R.string.time_picker_current, displayHour, String.format("%02d", minute))

    val isPm = hour >= 12
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
                NumberPicker(
                    value = if (isPm) 1 else 0,
                    onValueChange = { idx ->
                        val isPmNew = idx == 1
                        val displayH = hour24ToDisplay(hour, false)
                        val newHour = displayToHour24(displayH, false, isPmNew)
                        onHourChange(newHour)
                    },
                    range = 0..1,
                    label = { if (it == 0) amLabel else pmLabel },
                    wrapAround = false,
                    modifier = Modifier.weight(1f),
                    visibleItemCount = 3
                )
                Spacer(modifier = Modifier.width(if (compactHeight) 0.dp else 8.dp))
            }

            val hourRange = if (is24Hour) 0..23 else 1..12
            val selectedHour = if (is24Hour) hour else hour24ToDisplay(hour, false)
            NumberPicker(
                value = selectedHour,
                onValueChange = { value ->
                    if (is24Hour) {
                        onHourChange(value)
                    } else {
                        val newHour = displayToHour24(value, false, isPm)
                        onHourChange(newHour)
                    }
                },
                range = hourRange,
                label = { String.format("%02d", it) },
                wrapAround = true,
                modifier = Modifier.weight(2f),
                visibleItemCount = if (compactHeight) 3 else 5
            )

            Spacer(modifier = Modifier.width(if (compactHeight) 0.dp else 8.dp))

            NumberPicker(
                value = minute,
                onValueChange = { onMinuteChange(it) },
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