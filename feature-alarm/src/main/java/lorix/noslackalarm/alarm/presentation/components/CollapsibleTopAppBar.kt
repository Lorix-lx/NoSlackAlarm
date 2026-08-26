// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0
//
// 本文件改编自 Miuix 0.9.3 的 TopAppBar.kt（Apache-2.0）：
// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.alarm.presentation.components

// ============ 导入布局组件 ============
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirst
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

/**
 * Miuix 风格的「可折叠大标题」顶栏，基于 [top.yukonga.miuix.kmp.basic.TopAppBar] 改编。
 *
 * 与库 [top.yukonga.miuix.kmp.basic.TopAppBar] 的唯一差异：展开态大标题
 * （[largeTitle]）的字重可通过 [largeTitleFontWeight] 配置（默认细体 Light，
 * 契合小米原生闹钟「大而细」的大标题质感）。其余折叠动画、滚动联动、insets
 * 处理与库实现保持一致。
 *
 * @param title 折叠态顶部居中的小标题（title3 字号、Medium 字重）
 * @param modifier 应用到顶栏的 [Modifier]
 * @param color 顶栏背景色
 * @param titleColor 折叠态小标题文字色
 * @param largeTitle 展开态贴左的大标题
 * @param largeTitleColor 展开态大标题文字色
 * @param largeTitleFontWeight 展开态大标题字重（默认 Light）
 * @param subtitle 标题下方副标题
 * @param subtitleColor 副标题文字色
 * @param navigationIcon 左侧导航图标
 * @param actions 右侧操作图标
 * @param scrollBehavior 控制折叠/展开的 [ScrollBehavior]
 * @param defaultWindowInsetsPadding 是否应用默认窗口 insets 内边距
 * @param titlePadding 标题与大标题的横向内边距
 * @param navigationIconPadding 导航图标起始内边距
 * @param actionIconPadding 操作图标末端内边距
 * @param bottomContent 标题栏下方的内容
 */
@Composable
fun CollapsibleTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    titleColor: Color = MiuixTheme.colorScheme.onSurface,
    largeTitle: String = title,
    largeTitleColor: Color = MiuixTheme.colorScheme.onSurface,
    largeTitleFontWeight: FontWeight = FontWeight.Light,
    subtitle: String = "",
    subtitleColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
    defaultWindowInsetsPadding: Boolean = true,
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: Dp = TopAppBarDefaults.NavigationIconPadding,
    actionIconPadding: Dp = TopAppBarDefaults.ActionIconPadding,
    bottomContent: @Composable () -> Unit = {},
) {
    // Wrap the given actions in a Row.
    val actionsRow =
        @Composable {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }

    CollapsibleTopAppBarLayout(
        title = title,
        color = color,
        titleColor = titleColor,
        largeTitle = largeTitle,
        largeTitleColor = largeTitleColor,
        largeTitleFontWeight = largeTitleFontWeight,
        subtitle = subtitle,
        subtitleColor = subtitleColor,
        navigationIcon = navigationIcon,
        actions = actionsRow,
        titlePadding = titlePadding,
        navigationIconPadding = navigationIconPadding,
        actionIconPadding = actionIconPadding,
        scrollBehavior = scrollBehavior,
        modifier = modifier,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        bottomContent = bottomContent,
    )
}

/**
 * 可折叠大标题顶栏的私有布局（改编自 Miuix [top.yukonga.miuix.kmp.basic.TopAppBar] 的
 * `TopAppBarLayout`）。仅差异：大标题字重改由 [largeTitleFontWeight] 控制。
 */
@Composable
private fun CollapsibleTopAppBarLayout(
    title: String,
    color: Color,
    titleColor: Color,
    largeTitleColor: Color,
    largeTitleFontWeight: FontWeight,
    subtitle: String,
    subtitleColor: Color,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    titlePadding: Dp,
    navigationIconPadding: Dp,
    actionIconPadding: Dp,
    scrollBehavior: ScrollBehavior?,
    modifier: Modifier = Modifier,
    largeTitle: String = title,
    defaultWindowInsetsPadding: Boolean = true,
    bottomContent: @Composable () -> Unit = {},
) {
    // Producer lambdas — reads stay in layout/draw phases so scroll never recomposes this subtree.
    val scrolledOffset = remember(scrollBehavior) {
        { scrollBehavior?.state?.heightOffset ?: 0f }
    }
    val largeTitleAlpha = remember(scrollBehavior) {
        {
            val frac = scrollBehavior?.state?.collapsedFraction ?: 0f
            1f - (frac * 3f).coerceIn(0f, 1f)
        }
    }
    val updateHeightOffsetLimit = remember(scrollBehavior) {
        { height: Int ->
            scrollBehavior?.state?.let { state ->
                val limit = -height.toFloat()
                if (state.heightOffsetLimit != limit) state.heightOffsetLimit = limit
            }
            Unit
        }
    }

    // Boolean derivedStateOf invalidates only on flip → the spring fires once per crossing, not per frame.
    val smallTitleVisible by remember(scrollBehavior) {
        derivedStateOf {
            (scrollBehavior?.state?.collapsedFraction ?: 0f) * 3f >= 1f
        }
    }
    val smallTitleAlpha = remember { Animatable(if (smallTitleVisible) 1f else 0f) }
    val smallTitleTranslationY = remember { Animatable(if (smallTitleVisible) 0f else 20f) }

    LaunchedEffect(smallTitleVisible) {
        if (smallTitleVisible) {
            val showSpec = folmeSpring<Float>(damping = 1.0f, response = 0.3f)
            launch { smallTitleAlpha.animateTo(1f, showSpec) }
            launch { smallTitleTranslationY.animateTo(0f, showSpec) }
        } else {
            val hideSpec = folmeSpring<Float>(damping = 1.0f, response = 0.15f)
            launch { smallTitleAlpha.animateTo(0f, hideSpec) }
            launch { smallTitleTranslationY.animateTo(20f, hideSpec) }
        }
    }

    val animatedTitleColor by animateColorAsState(
        targetValue = titleColor,
        animationSpec = tween(durationMillis = 50),
    )
    val animatedLargeTitleColor by animateColorAsState(
        targetValue = largeTitleColor,
        animationSpec = tween(durationMillis = 50),
    )
    val animatedSubtitleColor by animateColorAsState(
        targetValue = subtitleColor,
        animationSpec = tween(durationMillis = 50),
    )

    Layout(
        {
            Box(
                Modifier
                    .layoutId("navigationIcon")
                    .padding(start = navigationIconPadding),
            ) {
                navigationIcon()
            }
            Box(
                Modifier
                    .layoutId("title")
                    .padding(horizontal = titlePadding)
                    .graphicsLayer {
                        alpha = smallTitleAlpha.value
                        translationY = smallTitleTranslationY.value
                    },
            ) {
                Text(
                    text = title,
                    color = animatedTitleColor,
                    fontSize = MiuixTheme.textStyles.title3.fontSize,
                    fontWeight = FontWeight.Medium,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            }
            Box(
                Modifier
                    .layoutId("actionIcons")
                    .padding(end = actionIconPadding),
            ) {
                actions()
            }
            Box(
                Modifier
                    .layoutId("largeTitle")
                    .padding(top = TopAppBarDefaults.CollapsedHeight)
                    .padding(horizontal = titlePadding)
                    .graphicsLayer { alpha = largeTitleAlpha() },
            ) {
                Column(
                    modifier = Modifier
                        .offset {
                            val v = scrolledOffset()
                            IntOffset(0, v.fastRoundToInt())
                        }
                        .onSizeChanged { updateHeightOffsetLimit(it.height) },
                ) {
                    Text(
                        text = largeTitle,
                        color = animatedLargeTitleColor,
                        fontSize = MiuixTheme.textStyles.title1.fontSize,
                        fontWeight = largeTitleFontWeight,
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            color = animatedSubtitleColor,
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                }
            }
            if (subtitle.isNotEmpty()) {
                Box(
                    Modifier
                        .layoutId("smallSubtitle")
                        .graphicsLayer {
                            alpha = smallTitleAlpha.value
                            translationY = smallTitleTranslationY.value
                        },
                ) {
                    Text(
                        text = subtitle,
                        color = animatedSubtitleColor,
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
            Box(Modifier.layoutId("bottomContent")) {
                bottomContent()
            }
        },
        modifier = modifier
            .background(color)
            .then(
                if (defaultWindowInsetsPadding) {
                    Modifier
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                } else {
                    Modifier
                },
            )
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures { /* Consume click */ }
            },
    ) { measurables, constraints ->
        val navigationIconPlaceable =
            measurables
                .fastFirst { it.layoutId == "navigationIcon" }
                .measure(constraints.copy(minWidth = 0, minHeight = 0))

        val actionIconsPlaceable =
            measurables
                .fastFirst { it.layoutId == "actionIcons" }
                .measure(constraints.copy(minWidth = 0, minHeight = 0))

        val maxTitleWidth =
            if (constraints.maxWidth == Constraints.Infinity) {
                constraints.maxWidth
            } else {
                (constraints.maxWidth - navigationIconPlaceable.width - actionIconsPlaceable.width)
                    .coerceAtLeast(0)
            }
        val titleMaxWidth =
            if (maxTitleWidth == Constraints.Infinity) {
                maxTitleWidth
            } else {
                (maxTitleWidth * TitleWidthFraction).fastRoundToInt()
            }

        val titlePlaceable =
            measurables
                .fastFirst { it.layoutId == "title" }
                .measure(constraints.copy(minWidth = 0, maxWidth = titleMaxWidth, minHeight = 0))

        val largeTitlePlaceable =
            measurables
                .fastFirst { it.layoutId == "largeTitle" }
                .measure(
                    constraints.copy(
                        minWidth = 0,
                        minHeight = 0,
                        maxHeight = Constraints.Infinity,
                    ),
                )

        val smallSubtitlePlaceable =
            measurables
                .firstOrNull { it.layoutId == "smallSubtitle" }
                ?.measure(constraints.copy(minWidth = 0, maxWidth = titleMaxWidth, minHeight = 0))

        val bottomContentPlaceable =
            measurables
                .fastFirst { it.layoutId == "bottomContent" }
                .measure(constraints.copy(minWidth = 0, minHeight = 0))

        val collapsedHeight = TopAppBarDefaults.CollapsedHeight.roundToPx()
        // largeTitle Box has top padding = collapsedHeight, so subtract it back for the pure expansion.
        val expansion = (largeTitlePlaceable.height - collapsedHeight).coerceAtLeast(0)
        val barHeight = if (expansion > 0) {
            val offset = scrolledOffset()
            val collapseFraction = if (offset.isNaN()) {
                0f
            } else {
                (abs(offset) / expansion.toFloat()).coerceIn(0f, 1f)
            }
            lerp(
                start = collapsedHeight,
                stop = collapsedHeight + expansion,
                fraction = 1f - collapseFraction,
            )
        } else {
            collapsedHeight
        }

        val verticalCenter = collapsedHeight / 2
        val smallSubtitleHeight = smallSubtitlePlaceable?.height ?: 0
        val smallSubtitleBottom = verticalCenter + titlePlaceable.height / 2 + smallSubtitleHeight
        val expandedBottomPadding = if (smallSubtitlePlaceable != null) {
            TopAppBarDefaults.SubtitleBottomPadding.roundToPx()
        } else {
            TopAppBarDefaults.LargeTitleBottomPadding.roundToPx()
        }
        val contentTop = maxOf(barHeight + expandedBottomPadding, smallSubtitleBottom + expandedBottomPadding)
        val layoutHeight = contentTop + bottomContentPlaceable.height

        layout(constraints.maxWidth, layoutHeight) {
            // Navigation icon
            navigationIconPlaceable.placeRelative(
                x = 0,
                y = verticalCenter - navigationIconPlaceable.height / 2,
            )

            // Title
            var baseX = (constraints.maxWidth - titlePlaceable.width) / 2
            if (baseX < navigationIconPlaceable.width) {
                baseX += (navigationIconPlaceable.width - baseX)
            } else if (baseX + titlePlaceable.width > constraints.maxWidth - actionIconsPlaceable.width) {
                baseX += ((constraints.maxWidth - actionIconsPlaceable.width) - (baseX + titlePlaceable.width))
            }
            titlePlaceable.placeRelative(
                x = baseX,
                y = verticalCenter - titlePlaceable.height / 2,
            )

            // Small subtitle (centered below small title, same alpha as small title)
            smallSubtitlePlaceable?.placeRelative(
                x = (constraints.maxWidth - smallSubtitlePlaceable.width) / 2,
                y = verticalCenter + titlePlaceable.height / 2,
            )

            // Action icons
            actionIconsPlaceable.placeRelative(
                x = constraints.maxWidth - actionIconsPlaceable.width,
                y = verticalCenter - actionIconsPlaceable.height / 2,
            )

            // Large title (includes large subtitle in a Column)
            largeTitlePlaceable.placeRelative(x = 0, y = 0)

            // Bottom content (pinned, below bar and subtitle)
            bottomContentPlaceable.placeRelative(x = 0, y = contentTop)
        }
    }
}

/**
 * 固定定位的下拉菜单 [PopupPositionProvider]。
 *
 * 问题根因：主页顶栏外层 Box 作为 [androidx.compose.ui.window.Dialog] 弹窗的锚点，其高度随
 * 大标题展开/折叠变化（展开含大标题扩展区、折叠仅剩折叠标题栏），而库默认
 * [top.yukonga.miuix.kmp.basic.ListPopupDefaults.dropdownPositionProvider] 基于锚点底部
 * `anchorBounds.bottom` 计算弹窗 Y，导致展开/折叠两种状态下「更多」菜单位置漂移。
 *
 * 本 provider 将弹窗 Y 固定为「锚点顶部 + 状态栏高度 + 折叠标题栏高度 + 外边距」，
 * 恰等于折叠态下 `anchorBounds.bottom + margin`，保证标题处于任意状态时菜单都在同一位置。
 * X 方向沿用 [PopupPositionProvider.Align.End] 的右对齐逻辑（锚点为全宽 Box，右侧恒定不变）。
 *
 * @param horizontalMargin 弹窗横向外边距（对齐库默认 16.dp）
 * @param verticalMargin 弹窗纵向外边距（默认 0）
 */
@Composable
fun rememberFixedDropdownPositionProvider(
    horizontalMargin: Dp = 16.dp,
    verticalMargin: Dp = 0.dp,
): PopupPositionProvider {
    val density = LocalDensity.current
    val statusBarTopPx = WindowInsets.statusBars.getTop(density)
    val collapsedHeightPx = with(density) { TopAppBarDefaults.CollapsedHeight.roundToPx() }
    return remember(horizontalMargin, verticalMargin, statusBarTopPx, collapsedHeightPx) {
        FixedDropdownPositionProvider(
            margins = PaddingValues(horizontal = horizontalMargin, vertical = verticalMargin),
            statusBarTopPx = statusBarTopPx,
            collapsedHeightPx = collapsedHeightPx,
        )
    }
}

/** [rememberFixedDropdownPositionProvider] 返回的固定定位实现。 */
private class FixedDropdownPositionProvider(
    private val margins: PaddingValues,
    private val statusBarTopPx: Int,
    private val collapsedHeightPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowBounds: IntRect,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
        popupMargin: IntRect,
        alignment: PopupPositionProvider.Align,
    ): IntOffset {
        val resolvedAlignment = resolveAlignment(alignment, layoutDirection)
        val offsetX = if (resolvedAlignment == PopupPositionProvider.Align.End) {
            anchorBounds.right - popupContentSize.width - popupMargin.right
        } else {
            anchorBounds.left + popupMargin.left
        }
        // 固定弹窗 Y：锚点顶 + 状态栏 + 折叠标题栏高度 + 外边距（与折叠态 anchorBounds.bottom + margin 一致）
        val offsetY = anchorBounds.top + statusBarTopPx + collapsedHeightPx + popupMargin.top
        return IntOffset(
            x = offsetX.coerceIn(
                windowBounds.left,
                (windowBounds.right - popupContentSize.width - popupMargin.right).coerceAtLeast(windowBounds.left),
            ),
            y = offsetY.coerceIn(
                (windowBounds.top + popupMargin.top).coerceAtMost(windowBounds.bottom - popupContentSize.height - popupMargin.bottom),
                windowBounds.bottom - popupContentSize.height - popupMargin.bottom,
            ),
        )
    }

    override fun getMargins(): PaddingValues = margins
}

/** 将 [PopupPositionProvider.Align] 按 RTL/LTR 布局方向解析（Miuix 内部同款逻辑）。 */
private fun resolveAlignment(
    alignment: PopupPositionProvider.Align,
    layoutDirection: LayoutDirection,
): PopupPositionProvider.Align {
    if (layoutDirection == LayoutDirection.Ltr) return alignment
    return when (alignment) {
        PopupPositionProvider.Align.Start -> PopupPositionProvider.Align.End
        PopupPositionProvider.Align.End -> PopupPositionProvider.Align.Start
        PopupPositionProvider.Align.TopStart -> PopupPositionProvider.Align.TopEnd
        PopupPositionProvider.Align.TopEnd -> PopupPositionProvider.Align.TopStart
        PopupPositionProvider.Align.BottomStart -> PopupPositionProvider.Align.BottomEnd
        PopupPositionProvider.Align.BottomEnd -> PopupPositionProvider.Align.BottomStart
    }
}

// Slack so the centred title isn't butted against nav/actions.
private val TitleWidthFraction = 0.9
