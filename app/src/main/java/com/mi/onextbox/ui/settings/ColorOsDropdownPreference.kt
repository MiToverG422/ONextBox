package com.mi.onextbox.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.onextbox.ui.common.ColorOsPopup
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.DropdownColors
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.squircleBackground
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.floor

private val PopupItemPressSpring = spring<Float>(
    dampingRatio = 1f,
    stiffness = 438.65f,
)
private val PopupSharedMaskSpring = spring<Float>(
    // C17 COUITouchRecyclerView shared-mask spring: bounce 0, response .15.
    dampingRatio = 1f,
    stiffness = 1_754.22f,
    visibilityThreshold = 0.1f,
)

@Composable
internal fun ColorOsWindowDropdownPreference(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    dropdownColors: DropdownColors,
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
    interactionEnabled: Boolean = true,
) {
    val entry = remember(items, selectedIndex, onSelectedIndexChange) {
        DropdownEntry(
            items = items.mapIndexed { index, text ->
                DropdownItem(
                    text = text,
                    selected = index == selectedIndex,
                    onClick = { onSelectedIndexChange?.invoke(index) },
                )
            },
        )
    }
    ColorOsWindowDropdownPreference(
        entry = entry,
        title = title,
        modifier = modifier,
        summary = summary,
        dropdownColors = dropdownColors,
        startAction = startAction,
        bottomAction = bottomAction,
        maxHeight = maxHeight,
        enabled = enabled,
        showValue = showValue,
        onExpandedChange = onExpandedChange,
        interactionEnabled = interactionEnabled,
    )
}

@Composable
internal fun ColorOsWindowDropdownPreference(
    entry: DropdownEntry,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    dropdownColors: DropdownColors,
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    collapseOnSelection: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    interactionEnabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var expanded by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val currentEntry by rememberUpdatedState(entry)
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val actualEnabled = enabled && entry.items.isNotEmpty()
    var componentLeftInWindow by remember { mutableFloatStateOf(0f) }
    var componentWidth by remember { mutableFloatStateOf(0f) }
    var lastTouchXInWindow by remember { mutableFloatStateOf(Float.NaN) }

    fun setExpanded(value: Boolean) {
        if (expanded != value) {
            expanded = value
            currentOnExpandedChange?.invoke(value)
        }
    }

    val actionColor = if (actualEnabled) {
        COUITheme.colorScheme.onSurfaceVariantActions
    } else {
        COUITheme.colorScheme.disabledOnSecondaryVariant
    }
    val valueColor = if (actualEnabled) {
        COUITheme.colorScheme.onSurfaceSecondary
    } else {
        COUITheme.colorScheme.disabledOnSecondaryVariant
    }

    BasicComponent(
        modifier = modifier.settingsInteractionGate(interactionEnabled)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                componentLeftInWindow = bounds.left
                componentWidth = bounds.width
            }
            .pointerInput(actualEnabled) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        if (actualEnabled) {
                            lastTouchXInWindow = componentLeftInWindow + down.position.x
                        }
                    }
                }
            },
        interactionSource = interactionSource,
        insideMargin = SettingsTokens.RowInsideMargin,
        title = title,
        summary = summary,
        startAction = startAction,
        endActions = {
            val selectedText = entry.items.firstOrNull { it.selected }?.text
            if (showValue && !selectedText.isNullOrEmpty()) {
                Text(
                    text = selectedText,
                    modifier = Modifier
                        .padding(start = 8.dp, end = 4.dp)
                        .align(Alignment.CenterVertically)
                        .weight(1f, fill = false),
                    fontSize = COUITheme.textStyles.body2.fontSize,
                    color = valueColor,
                    textAlign = TextAlign.End,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SettingsPopupArrow(tint = actionColor)
            ColorOsDropdownPopup(
                show = expanded,
                entry = entry,
                maxHeight = maxHeight,
                anchorCenterX = if (lastTouchXInWindow.isFinite()) {
                    lastTouchXInWindow
                } else {
                    componentLeftInWindow + componentWidth / 2f
                },
                onDismissRequest = { setExpanded(false) },
                onItemClick = { index ->
                    if (interactionEnabled && actualEnabled) {
                        currentEntry.items.getOrNull(index)?.takeIf { it.enabled }?.let { item ->
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            item.onClick?.invoke()
                            if (collapseOnSelection) setExpanded(false)
                        }
                    }
                },
            )
        },
        bottomAction = bottomAction,
        onClick = {
            if (actualEnabled && interactionEnabled) {
                setExpanded(!expanded)
                if (expanded) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
            }
        },
        role = Role.DropdownList,
        enabled = actualEnabled,
    )
}

@Composable
internal fun ColorOsDropdownPopup(
    show: Boolean,
    entry: DropdownEntry,
    maxHeight: Dp? = null,
    anchorCenterX: Float? = null,
    selectionMenu: Boolean = true,
    onDismissRequest: () -> Unit,
    onItemClick: (Int) -> Unit,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val dragHaptic = LocalHapticFeedback.current
    val maskAnimationScope = rememberCoroutineScope()
    val gestureItems = entry.items.map { Triple(it.text, it.enabled, it.selected) }
    var pressedIndex by remember(gestureItems) { mutableIntStateOf(-1) }
    var maskAnimationJob by remember { mutableStateOf<Job?>(null) }
    val sharedMaskOffset = remember(gestureItems) { Animatable(0f) }
    val currentOnItemClick by rememberUpdatedState(onItemClick)
    val currentGestureItems by rememberUpdatedState(gestureItems)
    val currentShow by rememberUpdatedState(show)
    val rowHeightPx = with(density) { 40.dp.roundToPx().toFloat() }
    val pressProgress by animateFloatAsState(
        targetValue = if (pressedIndex >= 0) 1f else 0f,
        animationSpec = PopupItemPressSpring,
        label = "colorOsPopupSharedMaskPress",
    )
    val darkTheme = COUITheme.colorScheme.background.luminance() < 0.5f
    val pressColor = if (darkTheme) {
        androidx.compose.ui.graphics.Color(0x26FFFFFF)
    } else {
        androidx.compose.ui.graphics.Color(0x14000000)
    }

    fun itemIndexAt(y: Float): Int {
        val contentY = y + scrollState.value
        if (contentY < 0f || rowHeightPx <= 0f) return -1
        return floor(contentY / rowHeightPx).toInt().takeIf { it in entry.items.indices } ?: -1
    }

    ColorOsPopup(
        show = show,
        onDismissRequest = onDismissRequest,
        maxHeight = maxHeight,
        anchorCenterX = anchorCenterX,
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .clipToBounds(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .graphicsLayer { translationY = sharedMaskOffset.value }
                    .padding(horizontal = 8.dp)
                    .squircleBackground(
                        color = pressColor.copy(alpha = pressColor.alpha * pressProgress),
                        cornerRadius = 20.dp,
                        extension = 1f,
                    ),
            )
            Column(
                modifier = Modifier
                .pointerInput(show, gestureItems, scrollState, rowHeightPx) {
                    if (!show) return@pointerInput
                    awaitEachGesture {
                        fun enabledIndexAt(position: Offset): Int {
                            if (position.x < 0f || position.x >= size.width ||
                                position.y < 0f || position.y >= size.height
                            ) return -1
                            return itemIndexAt(position.y)
                                .takeIf { it in entry.items.indices && entry.items[it].enabled }
                                ?: -1
                        }

                        try {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Main,
                            )
                            if (down.isConsumed) return@awaitEachGesture
                            val downIndex = enabledIndexAt(down.position)
                            val downScroll = scrollState.value
                            val scrollable = scrollState.maxValue > 0
                            var trackedIndex = downIndex
                            if (trackedIndex >= 0) {
                                val initialIndex = trackedIndex
                                val targetOffset = initialIndex * rowHeightPx - scrollState.value
                                maskAnimationJob?.cancel()
                                maskAnimationJob = maskAnimationScope.launch(
                                    start = CoroutineStart.UNDISPATCHED,
                                ) {
                                    sharedMaskOffset.snapTo(targetOffset)
                                    pressedIndex = initialIndex
                                }
                            } else {
                                pressedIndex = -1
                            }
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || event.changes.size > 1 || change.isConsumed ||
                                    !currentShow || currentGestureItems != gestureItems
                                ) return@awaitEachGesture
                                if (scrollable && (
                                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop ||
                                        scrollState.value != downScroll || scrollState.isScrollInProgress
                                    )
                                ) {
                                    return@awaitEachGesture
                                }

                                val nextIndex = enabledIndexAt(change.position)
                                if (change.changedToUpIgnoreConsumed()) {
                                    if (nextIndex >= 0 && (!scrollable || nextIndex == downIndex)) {
                                        change.consume()
                                        currentOnItemClick(nextIndex)
                                    }
                                    return@awaitEachGesture
                                }

                                if (!change.pressed) return@awaitEachGesture

                                if (!scrollable) {
                                    if (nextIndex != trackedIndex) {
                                        trackedIndex = nextIndex
                                        pressedIndex = nextIndex
                                        if (nextIndex >= 0) {
                                            val targetOffset = nextIndex * rowHeightPx - scrollState.value
                                            maskAnimationJob?.cancel()
                                            maskAnimationJob = maskAnimationScope.launch {
                                                sharedMaskOffset.animateTo(
                                                    targetOffset,
                                                    PopupSharedMaskSpring,
                                                )
                                            }
                                            dragHaptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        }
                                    }
                                }
                            }
                        } finally {
                            maskAnimationJob?.cancel()
                            pressedIndex = -1
                        }
                    }
                }
                .verticalScroll(scrollState, enabled = scrollState.maxValue > 0),
            ) {
                entry.items.forEachIndexed { index, item ->
                val selected = item.selected
                val titleColor = if (darkTheme) {
                    if (item.enabled) Color(0xE6FFFFFF) else Color(0x4DFFFFFF)
                } else {
                    if (item.enabled) Color(0xE6000000) else Color(0x42000000)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .semantics(mergeDescendants = true) {
                            role = if (selectionMenu) Role.RadioButton else Role.Button
                            if (selectionMenu) this.selected = selected
                            if (item.enabled && show) {
                                onClick {
                                    currentOnItemClick(index)
                                    true
                                }
                            } else {
                                disabled()
                            }
                        },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.text,
                            color = titleColor,
                            style = COUITheme.textStyles.body1.copy(
                                fontSize = 15.sp,
                                lineHeight = 17.sp,
                            ),
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (selectionMenu && selected) {
                            Spacer(Modifier.width(8.dp))
                            ColorOsPopupCheck(
                                tint = if (darkTheme) Color(0xE6FFFFFF) else Color(0xE6000000),
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun ColorOsPopupCheck(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val scaleX = size.width / 24f
        val scaleY = size.height / 24f
        val path = Path().apply {
            moveTo(9f * scaleX, 12.435f * scaleY)
            lineTo(13.388f * scaleX, 17f * scaleY)
            lineTo(23f * scaleX, 7f * scaleY)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(
                width = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
