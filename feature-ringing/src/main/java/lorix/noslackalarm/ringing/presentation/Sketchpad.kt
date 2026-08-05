// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.ringing.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import android.view.MotionEvent
import lorix.noslackalarm.common.constants.Constants
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Sketchpad(
    modifier: Modifier = Modifier,
    strokeColor: Color = MiuixTheme.colorScheme.onSurface,
    strokeWidth: Float = 8f,
    clearTrigger: Int = 0,
    onClear: () -> Unit = {}
) {
    var paths by remember { mutableStateOf(ArrayDeque<Pair<Path, Color>>()) }
    var currentPath by remember { mutableStateOf(Path()) }
    var currentPoints by remember { mutableIntStateOf(0) }
    var totalPoints by remember { mutableIntStateOf(0) }

    androidx.compose.runtime.LaunchedEffect(clearTrigger) {
        if (clearTrigger > 0) {
            paths = ArrayDeque()
            currentPath = Path()
            currentPoints = 0
            totalPoints = 0
            onClear()
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (totalPoints >= Constants.MAX_SKETCHPAD_POINTS) {
                            paths = ArrayDeque(paths).also { it.removeFirstOrNull() }
                            totalPoints = (totalPoints - 100).coerceAtLeast(0)
                        }
                        currentPath = Path().apply { moveTo(event.x, event.y) }
                        currentPoints = 1
                    }
                    MotionEvent.ACTION_MOVE -> {
                        currentPath.lineTo(event.x, event.y)
                        currentPoints++
                    }
                    MotionEvent.ACTION_UP -> {
                        currentPath.lineTo(event.x, event.y)
                        paths = ArrayDeque(paths).also { it.addLast(currentPath to strokeColor) }
                        totalPoints += currentPoints
                        currentPath = Path()
                        currentPoints = 0
                    }
                }
                true
            }
    ) {
        paths.forEach { (path, color) ->
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
        if (currentPoints > 0) {
            drawPath(
                path = currentPath,
                color = strokeColor,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
