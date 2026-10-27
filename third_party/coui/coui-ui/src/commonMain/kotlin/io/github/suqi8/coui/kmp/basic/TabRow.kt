// Copyright 2025, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.basic

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A [TabRow] with COUI style, without the container capsule.
 *
 * Renders the COUI segment button (see [TabRowWithContour]) without the container fill: only the
 * sliding capsule indicator with its drop shadow, on a transparent background by default.
 *
 * @param tabs The text to be displayed in the [TabRow].
 * @param selectedTabIndex The selected tab index of the [TabRow].
 * @param onTabSelected The callback when a tab is selected.
 * @param modifier The modifier to be applied to the [TabRow].
 * @param colors The colors of the [TabRow].
 * @param minWidth The minimum width of the tab in [TabRow].
 * @param maxWidth The maximum width of the tab in [TabRow].
 * @param height The height of the [TabRow].
 * @param cornerRadius The corner radius of the sliding indicator in [TabRow].
 * @param itemSpacing The spacing between tabs in [TabRow].
 * @param contentAlignment The content alignment of the tab in [TabRow].
 * @param listState The [LazyListState] to be used for the [TabRow].
 * @param interactionSource The [MutableInteractionSource] to be used for the [TabRow].
 * @param indication The [Indication] to be used for the [TabRow].
 */
@Composable
@NonRestartableComposable
fun TabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: TabRowColors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent),
    minWidth: Dp = TabRowDefaults.TabRowMinWidth,
    maxWidth: Dp = TabRowDefaults.TabRowMaxWidth,
    height: Dp = TabRowDefaults.TabRowHeight,
    cornerRadius: Dp = TabRowDefaults.TabRowCornerRadius,
    itemSpacing: Dp = 0.dp,
    contentAlignment: Alignment = Alignment.Center,
    listState: LazyListState? = null,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
) {
    SegmentButtonRow(
        tabs = tabs,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected,
        modifier = modifier,
        colors = colors,
        minWidth = minWidth,
        maxWidth = maxWidth,
        height = height,
        cornerRadius = cornerRadius,
        contourPadding = 0.dp,
        itemSpacing = itemSpacing,
        contentAlignment = contentAlignment,
        listState = listState,
        interactionSource = interactionSource,
        indication = indication,
    )
}

/**
 * A [TabRowWithContour] with COUI style.
 *
 * A port of the ColorOS 16 segmented control (COUISegmentButtonLayout, default `SegmentButton`
 * style). For the `SegmentButton.Tiny` style, pass [TabRowDefaults.TabRowWithContourTinyHeight],
 * [TabRowDefaults.TabRowWithContourTinyCornerRadius] and
 * [TabRowDefaults.TabRowWithContourTinyPadding].
 *
 * @param tabs The text to be displayed in the [TabRow].
 * @param selectedTabIndex The selected tab index of the [TabRow].
 * @param onTabSelected The callback when a tab is selected.
 * @param modifier The modifier to be applied to the [TabRow].
 * @param colors The colors of the [TabRow].
 * @param minWidth The minimum width of the tab in [TabRow].
 * @param maxWidth The maximum width of the tab in [TabRow].
 * @param height The height of the [TabRow].
 * @param cornerRadius The corner radius of the sliding indicator in [TabRow].
 * @param contourPadding The inset between the container capsule and the sliding indicator.
 * @param itemSpacing The spacing between tabs in [TabRow].
 * @param contentAlignment The content alignment of the tab in [TabRow].
 * @param listState The [LazyListState] to be used for the [TabRow].
 * @param interactionSource The [MutableInteractionSource] to be used for the [TabRow].
 * @param indication The [Indication] to be used for the [TabRow].
 */
@Composable
@NonRestartableComposable
fun TabRowWithContour(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: TabRowColors = TabRowDefaults.tabRowColors(),
    minWidth: Dp = TabRowDefaults.TabRowWithContourMinWidth,
    maxWidth: Dp = TabRowDefaults.TabRowWithContourMaxWidth,
    height: Dp = TabRowDefaults.TabRowWithContourHeight,
    cornerRadius: Dp = TabRowDefaults.TabRowWithContourCornerRadius,
    contourPadding: Dp = TabRowDefaults.TabRowWithContourPadding,
    itemSpacing: Dp = 0.dp,
    contentAlignment: Alignment = Alignment.Center,
    listState: LazyListState? = null,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
) {
    SegmentButtonRow(
        tabs = tabs,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected,
        modifier = modifier,
        colors = colors,
        minWidth = minWidth,
        maxWidth = maxWidth,
        height = height,
        cornerRadius = cornerRadius,
        contourPadding = contourPadding,
        itemSpacing = itemSpacing,
        contentAlignment = contentAlignment,
        listState = listState,
        interactionSource = interactionSource,
        indication = indication,
    )
}

/**
 * Shared implementation of the COUI segment button, following COUISegmentButtonLayout.onDraw.
 */
@Composable
private fun SegmentButtonRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    colors: TabRowColors,
    minWidth: Dp,
    maxWidth: Dp,
    height: Dp,
    cornerRadius: Dp,
    contourPadding: Dp,
    itemSpacing: Dp,
    contentAlignment: Alignment,
    listState: LazyListState?,
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    modifier: Modifier = Modifier,
) {
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    val isDarkTheme = COUITheme.colorScheme.background.luminance() < 0.5f
    val shadowColor = if (isDarkTheme) IndicatorShadowDark else IndicatorShadowLight
    val shadowPenumbraColor = remember(shadowColor) { shadowColor.copy(alpha = shadowColor.alpha * 0.5f) }
    val containerColor = colors.backgroundColor(false)
    val indicatorColor = colors.backgroundColor(true)
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelFontSize = COUITheme.textStyles.body2.fontSize
    // COUI measures segments with the selected appearance (couiTextButtonM: 14sp, medium).
    val measureStyle = remember(labelFontSize) { TextStyle(fontSize = labelFontSize, fontWeight = FontWeight.Medium) }
    val resolvedListState = listState ?: rememberLazyListState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .height(height),
    ) {
        val viewportPx = with(density) { (this@BoxWithConstraints.maxWidth - contourPadding * 2).toPx() }
        val minWidthPx = with(density) { minWidth.roundToPx() }
        val maxWidthPx = if (maxWidth == Dp.Infinity) Int.MAX_VALUE else with(density) { maxWidth.roundToPx() }
        val spacingPx = with(density) { itemSpacing.roundToPx() }
        val segmentPaddingPx = with(density) { SegmentPaddingHorizontal.roundToPx() }

        // COUISegmentButtonLayout.calculateChildrenWidths: natural label widths distributed to fill the row.
        val segmentWidthsPx = remember(
            tabs,
            viewportPx,
            minWidthPx,
            maxWidthPx,
            spacingPx,
            segmentPaddingPx,
            textMeasurer,
            measureStyle,
        ) {
            val naturalWidths = tabs.map { label ->
                max(
                    textMeasurer.measure(text = label, style = measureStyle, maxLines = 1).size.width +
                        2 * segmentPaddingPx,
                    minWidthPx,
                )
            }
            val availablePx = viewportPx - spacingPx * max(0, tabs.size - 1)
            computeSegmentWidths(naturalWidths, availablePx, minWidthPx, maxWidthPx)
        }
        val segmentLeftsPx = remember(segmentWidthsPx, spacingPx) {
            buildList {
                var left = 0
                for (widthPx in segmentWidthsPx) {
                    add(left)
                    left += widthPx + spacingPx
                }
            }
        }

        // Initialized from the current selection so the first frame shows the indicator at rest.
        val indicatorCenterX = remember {
            val index = selectedTabIndex.coerceIn(0, max(0, segmentWidthsPx.lastIndex))
            Animatable(
                if (segmentWidthsPx.isEmpty()) 0f else segmentLeftsPx[index] + segmentWidthsPx[index] / 2f,
            )
        }
        val indicatorWidth = remember {
            val index = selectedTabIndex.coerceIn(0, max(0, segmentWidthsPx.lastIndex))
            Animatable(if (segmentWidthsPx.isEmpty()) 0f else segmentWidthsPx[index].toFloat())
        }
        val indicatorScale = remember { Animatable(1f) }
        var selectedSegmentPressed by remember { mutableStateOf(false) }
        var lastSettledSelectedTabIndex by remember(resolvedListState) { mutableIntStateOf(-1) }

        // COUI animateIndicatorToPosition: independent position and width springs.
        LaunchedEffect(selectedTabIndex, segmentWidthsPx, segmentLeftsPx) {
            if (segmentWidthsPx.isEmpty()) return@LaunchedEffect
            val index = selectedTabIndex.coerceIn(0, segmentWidthsPx.lastIndex)
            val targetWidth = segmentWidthsPx[index].toFloat()
            val targetCenter = segmentLeftsPx[index] + targetWidth / 2f
            if (lastSettledSelectedTabIndex < 0 || lastSettledSelectedTabIndex == selectedTabIndex) {
                indicatorCenterX.snapTo(targetCenter)
                indicatorWidth.snapTo(targetWidth)
            } else {
                launch { indicatorCenterX.animateTo(targetCenter, SegmentButtonSpring) }
                launch { indicatorWidth.animateTo(targetWidth, SegmentButtonSpring) }
            }
        }

        // Keep the selected tab visible when the row overflows and scrolls.
        LaunchedEffect(selectedTabIndex, viewportPx, resolvedListState, segmentWidthsPx) {
            if (segmentWidthsPx.isNotEmpty()) {
                val index = selectedTabIndex.coerceIn(0, segmentWidthsPx.lastIndex)
                val offsetPx = -((viewportPx - segmentWidthsPx[index]) / 2f).roundToInt()
                if (lastSettledSelectedTabIndex < 0 || lastSettledSelectedTabIndex == selectedTabIndex) {
                    resolvedListState.scrollToItem(index, offsetPx)
                } else {
                    resolvedListState.animateScrollToItem(index, offsetPx)
                }
            }
            lastSettledSelectedTabIndex = selectedTabIndex
        }

        // COUIPressFeedbackHelper: pressing the selected segment scales the indicator around its center.
        LaunchedEffect(selectedSegmentPressed, selectedTabIndex, segmentWidthsPx, height, contourPadding) {
            val target = if (selectedSegmentPressed && segmentWidthsPx.isNotEmpty()) {
                val index = selectedTabIndex.coerceIn(0, segmentWidthsPx.lastIndex)
                with(density) {
                    pressedScaleEndRatio(
                        Size(segmentWidthsPx[index].toFloat(), (height - contourPadding * 2).toPx()),
                    )
                }
            } else {
                1f
            }
            indicatorScale.animateTo(target, SegmentButtonSpring)
        }

        val scrollOffset by remember(segmentLeftsPx, resolvedListState) {
            derivedStateOf {
                val firstIndex = resolvedListState.firstVisibleItemIndex
                (segmentLeftsPx.getOrElse(firstIndex) { 0 } + resolvedListState.firstVisibleItemScrollOffset).toFloat()
            }
        }
        val onSelectedPressedChange: (Boolean) -> Unit = remember { { pressed -> selectedSegmentPressed = pressed } }
        val containerPath = remember { Path() }
        val indicatorPath = remember { Path() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // 1. Container capsule (COUISegmentButtonLayout.drawButtonBackground).
                    val containerRadius = min((cornerRadius + contourPadding).toPx(), size.height / 2f)
                    if (containerColor.alpha > 0f) {
                        drawRoundRect(color = containerColor, cornerRadius = CornerRadius(containerRadius))
                    }
                    if (segmentWidthsPx.isEmpty()) return@drawBehind
                    val pad = contourPadding.toPx()
                    val scale = indicatorScale.value
                    val indicatorW = indicatorWidth.value * scale
                    val indicatorH = (size.height - 2f * pad) * scale
                    if (indicatorW <= 0f || indicatorH <= 0f) return@drawBehind
                    val centerX = pad + indicatorCenterX.value - scrollOffset
                    val centerY = size.height / 2f
                    val rect = Rect(
                        centerX - indicatorW / 2f,
                        centerY - indicatorH / 2f,
                        centerX + indicatorW / 2f,
                        centerY + indicatorH / 2f,
                    )
                    val radius = min(cornerRadius.toPx(), indicatorH / 2f)
                    indicatorPath.reset()
                    indicatorPath.addRoundRect(RoundRect(rect, CornerRadius(radius)))
                    containerPath.reset()
                    containerPath.addRoundRect(
                        RoundRect(Rect(Offset.Zero, size), CornerRadius(containerRadius)),
                    )
                    clipPath(containerPath) {
                        // 2. Indicator shadow, clipped outside the indicator (Region.Op.DIFFERENCE);
                        // the 1dp blur is approximated with a half-alpha penumbra pass.
                        clipPath(indicatorPath, clipOp = ClipOp.Difference) {
                            val shadowDy = IndicatorShadowOffsetY.toPx()
                            val shadowBlur = IndicatorShadowBlurRadius.toPx()
                            drawRoundRect(
                                color = shadowPenumbraColor,
                                topLeft = Offset(rect.left - shadowBlur, rect.top + shadowDy - shadowBlur),
                                size = Size(indicatorW + 2 * shadowBlur, indicatorH + 2 * shadowBlur),
                                cornerRadius = CornerRadius(radius + shadowBlur),
                            )
                            drawRoundRect(
                                color = shadowColor,
                                topLeft = Offset(rect.left, rect.top + shadowDy),
                                size = Size(indicatorW, indicatorH),
                                cornerRadius = CornerRadius(radius),
                            )
                        }
                        // 3. Indicator capsule fill.
                        drawRoundRect(
                            color = indicatorColor,
                            topLeft = rect.topLeft,
                            size = rect.size,
                            cornerRadius = CornerRadius(radius),
                        )
                    }
                },
        ) {
            // 4. Labels, composed on top of the canvas.
            LazyRow(
                state = resolvedListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contourPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                overscrollEffect = null,
            ) {
                itemsIndexed(tabs) { index, label ->
                    SegmentTab(
                        text = label,
                        isSelected = selectedTabIndex == index,
                        onClick = { currentOnTabSelected.invoke(index) },
                        width = with(density) { segmentWidthsPx[index].toDp() },
                        color = colors.contentColor(selectedTabIndex == index),
                        fontSize = labelFontSize,
                        contentAlignment = contentAlignment,
                        interactionSource = interactionSource,
                        indication = indication,
                        onSelectedPressedChange = onSelectedPressedChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentTab(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    width: Dp,
    color: Color,
    fontSize: TextUnit,
    contentAlignment: Alignment,
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    onSelectedPressedChange: (Boolean) -> Unit,
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val currentOnSelectedPressedChange by rememberUpdatedState(onSelectedPressedChange)
    LaunchedEffect(isPressed, isSelected) {
        currentOnSelectedPressedChange(isPressed && isSelected)
    }
    // COUIPressFeedbackHelper on the segment label.
    val scaleProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = SegmentButtonSpring,
        label = "segmentPressScale",
    )
    // COUISegmentButtonLayout.setSelectedAt: color crossfades over 300ms, weight switches instantly.
    val textColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(TextColorAnimationDuration, easing = TextColorEasing),
        label = "segmentTextColor",
    )
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(width)
            .graphicsLayer {
                val scale = 1f - (1f - pressedScaleEndRatio(size)) * scaleProgress
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = indication,
                onClick = onClick,
            )
            .semantics {
                role = Role.Tab
                selected = isSelected
            }
            .padding(horizontal = SegmentPaddingHorizontal),
        contentAlignment = contentAlignment,
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Resolve the widths of all segments (COUISegmentButtonLayout.onMeasure).
 *
 * When the row cannot give every segment [minWidthPx] the segments keep their natural widths and
 * the row scrolls instead.
 */
private fun computeSegmentWidths(
    naturalWidths: List<Int>,
    availablePx: Float,
    minWidthPx: Int,
    maxWidthPx: Int,
): List<Int> {
    val count = naturalWidths.size
    if (count == 0) return emptyList()
    if (availablePx <= 0f || availablePx / count < minWidthPx) {
        return naturalWidths.map { min(it, maxWidthPx) }
    }
    return distributeSegmentWidths(naturalWidths, availablePx).map { min(it, maxWidthPx) }
}

/**
 * Distribute the available width among segments (COUISegmentButtonLayout.calculateChildrenWidths):
 * narrow segments keep their natural width and the remaining space is split among the wider ones.
 */
private fun distributeSegmentWidths(
    naturalWidths: List<Int>,
    availablePx: Float,
): List<Int> {
    val count = naturalWidths.size
    val sortedIndices = naturalWidths.indices.sortedBy { naturalWidths[it] }
    val fairShare = availablePx / count
    val widths = FloatArray(count)
    var remaining = availablePx
    var share = fairShare
    var lockedIndex = -1
    var i = 0
    while (i < count) {
        val natural = naturalWidths[sortedIndices[i]].toFloat()
        if (natural < share) {
            widths[sortedIndices[i]] = natural
            remaining -= natural
            i++
        } else if (lockedIndex == i) {
            while (i < count) {
                widths[sortedIndices[i]] = share
                i++
            }
            remaining = 0f
        } else {
            share = remaining / (count - i)
            lockedIndex = i
        }
    }
    if (lockedIndex == -1) {
        return List(count) { fairShare.roundToInt() }
    }
    if (remaining > 0f) {
        for (j in 0 until count - 1) {
            widths[sortedIndices[j]] += remaining / (count - 1)
        }
    }
    return List(count) { widths[it].roundToInt() }
}

object TabRowDefaults {

    /**
     * The default height of the [TabRow]. COUI `SegmentButton` style height (40dp).
     */
    val TabRowHeight = 40.dp

    /**
     * The default height of the [TabRowWithContour]. COUI `SegmentButton` style height (40dp).
     */
    val TabRowWithContourHeight = 40.dp

    /**
     * The height of the COUI `SegmentButton.Tiny` style (32dp), for use as [TabRowWithContour]'s
     * `height` together with [TabRowWithContourTinyPadding] and [TabRowWithContourTinyCornerRadius].
     */
    val TabRowWithContourTinyHeight = 32.dp

    /**
     * The default corner radius of the sliding indicator in [TabRow]. COUI draws capsules
     * (radius = height / 2), hence 20dp for the full 40dp height.
     */
    val TabRowCornerRadius = 20.dp

    /**
     * The default corner radius of the sliding indicator in [TabRowWithContour]. Half the 32dp
     * indicator height (40dp container minus the 4dp inset on both sides).
     */
    val TabRowWithContourCornerRadius = 16.dp

    /**
     * The indicator corner radius of the COUI `SegmentButton.Tiny` style: (32dp - 2 * 2dp) / 2.
     */
    val TabRowWithContourTinyCornerRadius = 14.dp

    /**
     * The default inset between the container and the indicator in [TabRowWithContour].
     * COUI `SegmentButton` style `android:padding` (4dp).
     */
    val TabRowWithContourPadding = 4.dp

    /**
     * The contour inset of the COUI `SegmentButton.Tiny` style (`android:padding` 2dp).
     */
    val TabRowWithContourTinyPadding = 2.dp

    /**
     * The default minimum width of the tab in [TabRow]. COUI `coui_segment_min_width` (52dp).
     */
    val TabRowMinWidth = 52.dp

    /**
     * The default minimum width of the tab in [TabRowWithContour].
     * COUI `coui_segment_min_width` (52dp).
     */
    val TabRowWithContourMinWidth = 52.dp

    /**
     * The default maximum width of the tab in [TabRow]. COUI segments have no maximum width.
     */
    val TabRowMaxWidth = Dp.Infinity

    /**
     * The default maximum width of the tab in [TabRowWithContour]. COUI segments have no maximum width.
     */
    val TabRowWithContourMaxWidth = Dp.Infinity

    /**
     * The default colors for the [TabRow] and [TabRowWithContour], following
     * COUISegmentButtonLayout: both label states default to `couiColorLabelPrimary` — the
     * selected state is distinguished by the indicator and the medium font weight.
     */
    @Composable
    fun tabRowColors(
        backgroundColor: Color = if (COUITheme.colorScheme.background.luminance() < 0.5f) {
            SegmentContainerDark
        } else {
            SegmentContainerLight
        },
        contentColor: Color = COUITheme.colorScheme.onSurface,
        selectedBackgroundColor: Color = if (COUITheme.colorScheme.background.luminance() < 0.5f) {
            SegmentIndicatorDark
        } else {
            SegmentIndicatorLight
        },
        selectedContentColor: Color = COUITheme.colorScheme.onSurface,
    ): TabRowColors = remember(backgroundColor, contentColor, selectedBackgroundColor, selectedContentColor) {
        TabRowColors(
            backgroundColor = backgroundColor,
            contentColor = contentColor,
            selectedBackgroundColor = selectedBackgroundColor,
            selectedContentColor = selectedContentColor,
        )
    }
}

@Immutable
data class TabRowColors(
    private val backgroundColor: Color,
    private val contentColor: Color,
    private val selectedBackgroundColor: Color,
    private val selectedContentColor: Color,
) {
    @Stable
    internal fun backgroundColor(selected: Boolean): Color = if (selected) selectedBackgroundColor else backgroundColor

    @Stable
    internal fun contentColor(selected: Boolean): Color = if (selected) selectedContentColor else contentColor
}

/**
 * COUISpringForce(response = 0.3f, bounce = 0f): a critically damped spring with stiffness
 * (2 * PI / 0.3)^2 ~= 438.65, used for the indicator springs and the press scale.
 */
private val SegmentButtonSpring = spring<Float>(dampingRatio = 1f, stiffness = 438.65f)

/** COUIMoveEaseInterpolator: PathInterpolator(0.3f, 0f, 0.1f, 1f). */
private val TextColorEasing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)

/** COUISegmentButtonLayout text color animator duration (COLOR_ANIMATOR_TIME = 300ms). */
private val TextColorAnimationDuration = 300

/** COUI `coui_segment_btn_padding_horizontal` (12dp) — horizontal text padding of a segment. */
private val SegmentPaddingHorizontal = 12.dp

/** COUI `coui_segment_shadow_offset` (2dp) — the vertical offset of the indicator shadow. */
private val IndicatorShadowOffsetY = 2.dp

/** COUI `coui_segment_shadow_radius` (1dp) — the blur radius of the indicator shadow. */
private val IndicatorShadowBlurRadius = 1.dp

/** COUI `coui_segment_button_shadow_color` in light themes (#0D000000). */
private val IndicatorShadowLight = Color(0x0D000000)

/** COUI `coui_segment_button_shadow_color` in dark themes (#1A000000). */
private val IndicatorShadowDark = Color(0x1A000000)

/** COUI `coui_color_segment_button_background` in light themes (#0F000000). */
private val SegmentContainerLight = Color(0x0F000000)

/** COUI `coui_color_segment_button_background` in dark themes (#1FFFFFFF). */
private val SegmentContainerDark = Color(0x1FFFFFFF)

/** COUI `coui_color_segment_button_indicator` in light themes (#FFFFFF). */
private val SegmentIndicatorLight = Color(0xFFFFFFFF)

/** COUI `coui_color_segment_button_indicator` in dark themes (#1AFFFFFF). */
private val SegmentIndicatorDark = Color(0x1AFFFFFF)

/** COUIInEaseInterpolator, used to map the segment area to the pressed end scale. */
private val PressedScaleEasing = CubicBezierEasing(0f, 0f, 0.1f, 1f)

/** COUIPressFeedbackHelper area bounds: coui_min_end_value_size (48dp) squared. */
private val PressedScaleMinAreaDp2 = 48f * 48f

/** COUIPressFeedbackHelper area bounds: coui_max_end_value_width x height (328dp x 220dp). */
private val PressedScaleMaxAreaDp2 = 328f * 220f

/** The pressed end scale of the smallest surfaces (COUIPressFeedbackHelper DEFAULT_SCALE_END_RATIO). */
private val PressedScaleMin = 0.92f

/** The pressed end scale of the largest surfaces (COUIPressFeedbackHelper MAX_SCALE_END_RATIO). */
private val PressedScaleMax = 0.98f

/**
 * The scale a segment of [size] shrinks to when fully pressed: [PressedScaleMin] for areas up to
 * 48 x 48dp, easing towards [PressedScaleMax] at 328 x 220dp
 * (COUIPressFeedbackHelper.getCardScaleRatio).
 */
private fun Density.pressedScaleEndRatio(size: Size): Float {
    val area = size.width.toDp().value * size.height.toDp().value
    return when {
        area <= PressedScaleMinAreaDp2 -> PressedScaleMin

        area >= PressedScaleMaxAreaDp2 -> PressedScaleMax

        else -> PressedScaleMin + (PressedScaleMax - PressedScaleMin) *
            PressedScaleEasing.transform((area - PressedScaleMinAreaDp2) / (PressedScaleMaxAreaDp2 - PressedScaleMinAreaDp2))
    }
}
