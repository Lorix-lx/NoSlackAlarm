// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0
//
// Fork 自 Miuix 0.9.3 的 top.yukonga.miuix.kmp.basic.NumberPicker
// （miuix-0.9.3/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/NumberPicker.kt，Apache-2.0）。
// 原组件只在「拖动结束且惯性 + 吸附动画全部跑完」后回调一次 onValueChange，
// 无法满足「滚动过程中实时联动」的需求，故复制一份并新增：
//   - onLiveRawIndexChange：滚动过程中逐帧回调未取模的虚拟索引
//   - onScrollStateChange：滚动开始 / 结束
// 视觉、惯性手感、触觉反馈与渲染逻辑逐字保留，升级 Miuix 时需同步对照上游该文件的改动。

package lorix.noslackalarm.alarm.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

/**
 * 带「滚动中实时回调」的 [NumberPicker]（Fork 自 Miuix）。
 *
 * 与官方组件的唯一差异是多了两个回调，其余参数语义、默认值、视觉与手感完全一致。
 *
 * @param value The current selected value. If outside [range], it will be coerced into the range.
 * @param onValueChange The callback invoked when the selected value changes（滚动停止后）.
 * @param modifier The modifier to be applied to the [LiveNumberPicker].
 * @param enabled Whether the [LiveNumberPicker] is enabled for user interaction.
 * @param range The range of selectable values.
 * @param label A function that converts a value to its display string.
 * @param visibleItemCount The number of visible items. Must be odd and at least 3.
 * @param wrapAround Whether the picker wraps around from the last item to the first (infinite scrolling).
 * @param onLiveRawIndexChange 滚动过程中（拖动 + 惯性 + 吸附）实时回调的虚拟索引。
 *   取值为 `受控值索引 + round(滚动偏移)`，**未取模**，因此物理连续且带方向，
 *   调用方用「相邻两次的增量」即可无歧义地推出滚过了几格（不会因为快速滚动跨多格
 *   而被最短路径算法误判方向）。滚轮静止时也可能因受控值变化而回调，调用方需自行判重。
 * @param onScrollStateChange 滚动开始（true）/ 结束（false）。结束一定在吸附动画跑完后发出，
 *   且若上一次惯性尚未结束就再次按下，旧的结束事件不会误发。
 * @param colors The [LiveNumberPickerColors] for this [LiveNumberPicker].
 * @param textStyle The [TextStyle] for the picker items.
 * @param itemHeight The height of each item in the picker.
 */
@Composable
fun LiveNumberPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    range: IntRange = 0..10,
    label: (Int) -> String = { it.toString() },
    visibleItemCount: Int = 5,
    wrapAround: Boolean = false,
    onLiveRawIndexChange: (Int) -> Unit = {},
    onScrollStateChange: (Boolean) -> Unit = {},
    colors: LiveNumberPickerColors = LiveNumberPickerDefaults.colors(),
    textStyle: TextStyle = MiuixTheme.textStyles.title1,
    itemHeight: Dp = LiveNumberPickerDefaults.ItemHeight,
) {
    require(visibleItemCount % 2 == 1 && visibleItemCount >= 3) {
        "visibleItemCount must be odd and at least 3, but was $visibleItemCount"
    }
    require(range.first <= range.last) {
        "range must not be empty"
    }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnLiveRawIndexChange by rememberUpdatedState(onLiveRawIndexChange)
    val currentOnScrollStateChange by rememberUpdatedState(onScrollStateChange)
    val itemCount = range.last - range.first + 1
    val coercedValue = value.coerceIn(range)
    val currentIndex = coercedValue - range.first
    val halfVisibleCount = visibleItemCount / 2
    val hapticFeedback = LocalHapticFeedback.current

    // Drag offset (updated synchronously, no coroutine needed)
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // Fling/snap animated offset
    val flingAnimatable = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isUserScrolling by remember { mutableStateOf(false) }
    var itemHeightPx by remember { mutableIntStateOf(0) }
    // 滚动批次号：用于区分「上一次惯性未结束就再次按下」产生的过期结束事件
    var scrollToken by remember { mutableIntStateOf(0) }

    // Total offset combines drag + fling
    val totalOffset by remember {
        derivedStateOf { dragOffset + flingAnimatable.value }
    }

    // Sync offset when value changes externally
    LaunchedEffect(coercedValue) {
        if (!isDragging && dragOffset == 0f) {
            flingAnimatable.snapTo(0f)
        }
    }

    // Haptic feedback when crossing item boundaries during scroll
    val effectiveIndex by remember(currentIndex, itemCount, wrapAround) {
        derivedStateOf {
            val rawIndex = currentIndex + totalOffset.fastRoundToInt()
            if (wrapAround) {
                ((rawIndex % itemCount) + itemCount) % itemCount
            } else {
                rawIndex.coerceIn(0, itemCount - 1)
            }
        }
    }

    // 滚动中的实时虚拟索引（未取模）。用 rememberUpdatedState 持有受控值索引，
    // 保证 derivedStateOf / snapshotFlow 观察到的是同一个稳定的 State 实例，
    // 外部受控值变化后仍能读到最新值。
    val currentIndexRef by rememberUpdatedState(currentIndex)
    val rawIndex by remember {
        derivedStateOf { currentIndexRef + totalOffset.fastRoundToInt() }
    }

    var lastHapticIndex by remember { mutableIntStateOf(currentIndex) }
    LaunchedEffect(Unit) {
        snapshotFlow { effectiveIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index != lastHapticIndex) {
                    if (isUserScrolling) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    lastHapticIndex = index
                }
            }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { rawIndex }
            .distinctUntilChanged()
            .collect { currentOnLiveRawIndexChange(it) }
    }

    val totalHeight = itemHeight * visibleItemCount

    val draggableState = rememberDraggableState { delta ->
        if (itemHeightPx > 0) {
            val newOffset = dragOffset - delta / itemHeightPx
            dragOffset = if (wrapAround) {
                newOffset
            } else {
                newOffset.coerceIn(
                    -(currentIndex.toFloat()),
                    (itemCount - 1 - currentIndex).toFloat(),
                )
            }
        }
    }

    val displayValue = label(coercedValue)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
            .clipToBounds()
            .semantics {
                contentDescription = "$displayValue, ${range.first} - ${range.last}"
            }
            .onSizeChanged { size ->
                itemHeightPx = size.height / visibleItemCount
            }
            .then(
                if (enabled) {
                    Modifier.draggable(
                        orientation = Orientation.Vertical,
                        state = draggableState,
                        onDragStarted = {
                            // Stop any ongoing fling animation
                            flingAnimatable.stop()
                            // Absorb fling remainder into drag
                            dragOffset += flingAnimatable.value
                            flingAnimatable.snapTo(0f)
                            isDragging = true
                            isUserScrolling = true
                            scrollToken++
                            currentOnScrollStateChange(true)
                        },
                        onDragStopped = { velocity ->
                            val token = scrollToken
                            isDragging = false
                            if (itemHeightPx > 0) {
                                // Transfer drag offset to fling animatable
                                val currentDragOffset = dragOffset
                                dragOffset = 0f
                                flingAnimatable.snapTo(currentDragOffset)

                                val velocityInItems = -velocity / itemHeightPx
                                val decay = exponentialDecay<Float>(frictionMultiplier = 2f)
                                // Set bounds for non-wrap mode
                                if (!wrapAround) {
                                    val min = -(currentIndex.toFloat())
                                    val max = (itemCount - 1 - currentIndex).toFloat()
                                    flingAnimatable.updateBounds(min, max)
                                }
                                // Fling with real decay for natural inertia
                                flingAnimatable.animateDecay(velocityInItems, decay)
                                // Reset bounds
                                flingAnimatable.updateBounds(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)
                                // Snap to nearest item
                                val snappedTarget = flingAnimatable.value.fastRoundToInt().toFloat()
                                flingAnimatable.animateTo(
                                    targetValue = snappedTarget,
                                    animationSpec = spring(dampingRatio = 1f, stiffness = 400f),
                                )
                                val offsetInt = flingAnimatable.value.fastRoundToInt()
                                val newIndex = if (wrapAround) {
                                    ((currentIndex + offsetInt) % itemCount + itemCount) % itemCount
                                } else {
                                    (currentIndex + offsetInt).coerceIn(0, itemCount - 1)
                                }
                                val newValue = range.first + newIndex
                                if (newValue != coercedValue) {
                                    currentOnValueChange(newValue)
                                }
                                isUserScrolling = false
                                flingAnimatable.snapTo(0f)
                            } else {
                                isUserScrolling = false
                            }
                            // 只有最后一次滚动（未被新的按下打断）才发出结束事件
                            if (token == scrollToken) {
                                currentOnScrollStateChange(false)
                            }
                        },
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (itemHeightPx > 0) {
            val currentTotalOffset = totalOffset
            val centerItemOffset = currentTotalOffset - currentTotalOffset.fastRoundToInt()
            val roundedOffset = currentTotalOffset.fastRoundToInt()
            val selectedColor = colors.selectedTextColor(enabled)
            val unselectedColor = colors.unselectedTextColor(enabled)
            val resolvedTextStyle = if (textStyle.fontWeight == null) textStyle.copy(fontWeight = FontWeight.SemiBold) else textStyle

            for (i in -halfVisibleCount - 1..halfVisibleCount + 1) {
                val rawItemIndex = currentIndex + i + roundedOffset
                val itemIndex = if (wrapAround) {
                    ((rawItemIndex % itemCount) + itemCount) % itemCount
                } else {
                    if (rawItemIndex !in 0..<itemCount) continue
                    rawItemIndex
                }

                val distanceFromCenter = i.toFloat() - centerItemOffset
                val normalizedDistance = (abs(distanceFromCenter) / (halfVisibleCount + 0.5f)).coerceIn(0f, 1f)

                val alpha = (1f - normalizedDistance) * (1f - normalizedDistance * 0.5f)
                val scale = 1f - 0.2f * normalizedDistance
                val yOffset = distanceFromCenter * itemHeightPx

                val textColor = Color(
                    red = lerp(selectedColor.red, unselectedColor.red, normalizedDistance),
                    green = lerp(selectedColor.green, unselectedColor.green, normalizedDistance),
                    blue = lerp(selectedColor.blue, unselectedColor.blue, normalizedDistance),
                    alpha = lerp(selectedColor.alpha, unselectedColor.alpha, normalizedDistance),
                )

                Text(
                    text = label(range.first + itemIndex),
                    modifier = Modifier
                        .graphicsLayer {
                            this.alpha = alpha
                            scaleX = scale
                            scaleY = scale
                            translationY = yOffset
                        },
                    style = resolvedTextStyle,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Contains default values used by [LiveNumberPicker].
 */
object LiveNumberPickerDefaults {

    val ItemHeight = 45.dp

    /**
     * Creates the default [LiveNumberPickerColors].
     *
     * @param selectedTextColor The color for the selected (center) item text.
     * @param unselectedTextColor The color for unselected item text.
     * @param disabledSelectedTextColor The color for the selected item text when disabled.
     * @param disabledUnselectedTextColor The color for unselected item text when disabled.
     */
    @Composable
    fun colors(
        selectedTextColor: Color = MiuixTheme.colorScheme.onSurface,
        unselectedTextColor: Color = MiuixTheme.colorScheme.onSurfaceSecondary,
        disabledSelectedTextColor: Color = MiuixTheme.colorScheme.disabledOnSecondary,
        disabledUnselectedTextColor: Color = MiuixTheme.colorScheme.disabledOnSecondary,
    ): LiveNumberPickerColors = remember(
        selectedTextColor,
        unselectedTextColor,
        disabledSelectedTextColor,
        disabledUnselectedTextColor,
    ) {
        LiveNumberPickerColors(
            selectedTextColor = selectedTextColor,
            unselectedTextColor = unselectedTextColor,
            disabledSelectedTextColor = disabledSelectedTextColor,
            disabledUnselectedTextColor = disabledUnselectedTextColor,
        )
    }
}

/**
 * The colors for a [LiveNumberPicker].
 *
 * @see LiveNumberPickerDefaults.colors
 */
@Immutable
data class LiveNumberPickerColors(
    private val selectedTextColor: Color,
    private val unselectedTextColor: Color,
    private val disabledSelectedTextColor: Color,
    private val disabledUnselectedTextColor: Color,
) {

    @Stable
    internal fun selectedTextColor(enabled: Boolean): Color = if (enabled) selectedTextColor else disabledSelectedTextColor

    @Stable
    internal fun unselectedTextColor(enabled: Boolean): Color = if (enabled) unselectedTextColor else disabledUnselectedTextColor
}
