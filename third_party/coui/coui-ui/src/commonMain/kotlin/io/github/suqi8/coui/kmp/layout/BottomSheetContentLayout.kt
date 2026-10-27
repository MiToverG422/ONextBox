// Copyright 2025, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.suqi8.coui.kmp.anim.folmeSpring
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Internal shared layout logic for [OverlayBottomSheet] and [WindowBottomSheet].
 *
 * @param show Whether the bottom sheet is currently shown.
 * @param backgroundColor The background color of the bottom sheet.
 * @param cornerRadius The corner radius of the top corners of the bottom sheet.
 * @param sheetMaxWidth Additional maximum-width cap applied on top of the COUI responsive
 *   grid width (full width on compact windows, a 6-column grid span on medium/expanded ones).
 * @param outsideMargin The margin outside the bottom sheet.
 * @param insideMargin The margin inside the bottom sheet.
 * @param dragHandleColor The color of the drag handle.
 * @param popupHost A composable that provides the container (e.g., DialogLayout or Dialog).
 *   It receives the visibility state and the inner content composable.
 * @param modifier The modifier to be applied to the bottom sheet content.
 * @param title Optional title to display at the top of the bottom sheet.
 * @param startAction Optional [Composable] to display on the start side of the title.
 * @param endAction Optional [Composable] to display on the end side of the title.
 * @param enableWindowDim Whether to dim the window behind the bottom sheet.
 * @param onDismissRequest The callback when the user tries to dismiss the bottom sheet.
 * @param onDismissFinished Invoked when the hide animation completes; not invoked if the hide
 *   is cancelled mid-flight (e.g., by [show] toggling back to true).
 * @param defaultWindowInsetsPadding Whether to apply default window insets padding.
 * @param allowDismiss Whether to allow dismissing the sheet via drag or back gesture.
 * @param enableNestedScroll Whether to enable nested scrolling for the content.
 * @param topInset Optional top inset override. If null, calculated from window insets.
 * @param content The content of the bottom sheet.
 */
@Suppress("ktlint:compose:modifier-not-used-at-root")
@Composable
internal fun BottomSheetContentLayout(
    show: Boolean,
    backgroundColor: Color,
    cornerRadius: Dp,
    sheetMaxWidth: Dp,
    outsideMargin: DpSize,
    insideMargin: DpSize,
    dragHandleColor: Color,
    popupHost: @Composable (visible: Boolean, content: @Composable () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    enableWindowDim: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    defaultWindowInsetsPadding: Boolean = true,
    allowDismiss: Boolean = true,
    enableNestedScroll: Boolean = true,
    topInset: Dp? = null,
    content: @Composable () -> Unit,
) {
    val animationProgress = remember { Animatable(0f, visibilityThreshold = 0.0001f) }
    val dragOffsetY = remember { Animatable(0f) }
    val currentOnDismissFinished by rememberUpdatedState(onDismissFinished)
    val internalVisible = remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val sheetHeightPx = remember { mutableIntStateOf(0) }

    LaunchedEffect(show) {
        // COUI samples the panel/container heights when the animation starts; mirror that
        // with a one-shot read (no recomposition subscription).
        val containerPx = with(density) { windowInfo.containerDpSize.height.toPx() }
        val peekPx = with(density) { PanelPeekHeight.toPx() }
        val travelPx = sheetHeightPx.intValue.takeIf { it > 0 }?.toFloat() ?: containerPx
        if (show) {
            internalVisible.value = true
            dragOffsetY.snapTo(0f)
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = couiPanelSlideSpring(
                    entering = true,
                    travelPx = travelPx,
                    containerPx = containerPx,
                    peekPx = peekPx,
                ),
            )
        } else {
            if (!internalVisible.value) return@LaunchedEffect
            if (dragOffsetY.value > 0f) {
                // Sheet already dragged off-screen — snap immediately
                animationProgress.snapTo(0f)
            } else {
                // Button/back dismiss — animate normally
                animationProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = couiPanelSlideSpring(
                        entering = false,
                        travelPx = travelPx,
                        containerPx = containerPx,
                        peekPx = peekPx,
                    ),
                )
            }
            internalVisible.value = false
            currentOnDismissFinished?.invoke()
        }
    }

    if (!show && !internalVisible.value) return

    val coroutineScope = rememberCoroutineScope()
    val dimAlpha = remember { mutableFloatStateOf(1f) }
    val dragSnapChannel = remember { Channel<Float>(capacity = Channel.CONFLATED) }
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)

    val requestDismiss: () -> Unit = remember {
        { currentOnDismissRequest?.invoke() }
    }

    val resetGesture: suspend () -> Unit = {
        dragOffsetY.animateTo(0f, animationSpec = tween(durationMillis = 150))
        animate(dimAlpha.floatValue, 1f, animationSpec = tween(durationMillis = 150)) { value, _ ->
            dimAlpha.floatValue = value
        }
    }

    LaunchedEffect(dragOffsetY) {
        for (target in dragSnapChannel) dragOffsetY.snapTo(target)
    }

    popupHost(internalVisible.value) {
        val navigationEventState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        NavigationBackHandler(
            state = navigationEventState,
            isBackEnabled = show,
            onBackCancelled = { coroutineScope.launch { resetGesture() } },
            onBackCompleted = {
                if (allowDismiss) {
                    coroutineScope.launch {
                        val windowHeightPx = with(density) { windowInfo.containerDpSize.height.toPx() }
                        animateDismissOffScreen(
                            dragOffsetY = dragOffsetY,
                            sheetHeightPx = sheetHeightPx.intValue,
                            windowHeightPx = windowHeightPx,
                            peekPx = with(density) { PanelPeekHeight.toPx() },
                            dimAlpha = dimAlpha,
                        ) {
                            requestDismiss()
                        }
                    }
                } else {
                    coroutineScope.launch { resetGesture() }
                }
            },
        )

        LaunchedEffect(allowDismiss) {
            // Collect inside a single coroutine so the per-frame `transitionState` ticks during a
            // back gesture do not cancel/relaunch the LaunchedEffect on every progress update.
            snapshotFlow { navigationEventState.transitionState }
                .collect { transitionState ->
                    if (
                        transitionState is NavigationEventTransitionState.InProgress &&
                        transitionState.direction == NavigationEventTransitionState.TRANSITIONING_BACK
                    ) {
                        val maxOffset = if (sheetHeightPx.intValue > 0) sheetHeightPx.intValue.toFloat() else 500f
                        val offset = transitionState.latestEvent.progress * maxOffset
                        val finalOffset = if (!allowDismiss) offset * 0.1f else offset
                        dragSnapChannel.trySend(finalOffset)
                        if (allowDismiss) dimAlpha.floatValue = 1f - transitionState.latestEvent.progress
                    }
                }
        }

        if (enableWindowDim) {
            // COUI drives the mask alpha with the same spring as the panel slide,
            // which multiplying by `animationProgress` reproduces here.
            val baseColor = COUITheme.colorScheme.windowDimming
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(baseColor.copy(alpha = baseColor.alpha * dimAlpha.floatValue * animationProgress.value))
                    },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // Key on `allowDismiss` so the gesture coroutine restarts with the latest closure
                // when the consumer toggles it; otherwise the in-flight detector keeps the stale value.
                .pointerInput(allowDismiss) {
                    detectTapGestures(
                        onTap = {
                            if (allowDismiss) {
                                requestDismiss()
                            }
                        },
                    )
                }
                .semantics {
                    if (allowDismiss) {
                        onClick(label = "Dismiss") {
                            requestDismiss()
                            true
                        }
                    }
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            val sheetModifier = modifier.graphicsLayer {
                val progress = animationProgress.value
                val currentHeight = sheetHeightPx.intValue.toFloat()
                val windowHeightPx = with(density) { windowInfo.containerDpSize.height.toPx() }
                val baseOffset = if (currentHeight > 0) currentHeight else windowHeightPx
                translationY = baseOffset * (1f - progress) + dragOffsetY.value
            }

            BottomSheetContent(
                title = title,
                backgroundColor = backgroundColor,
                cornerRadius = cornerRadius,
                sheetMaxWidth = sheetMaxWidth,
                outsideMargin = outsideMargin,
                insideMargin = insideMargin,
                defaultWindowInsetsPadding = defaultWindowInsetsPadding,
                dragHandleColor = dragHandleColor,
                allowDismiss = allowDismiss,
                sheetHeightPx = sheetHeightPx,
                dragOffsetY = dragOffsetY,
                dimAlpha = dimAlpha,
                dragSnapChannel = dragSnapChannel,
                onDismissRequest = {
                    if (allowDismiss) {
                        requestDismiss()
                    }
                },
                modifier = sheetModifier,
                topInset = topInset,
                enableNestedScroll = enableNestedScroll,
                startAction = startAction?.let { action ->
                    { CompositionLocalProvider(LocalDismissState provides requestDismiss) { action() } }
                },
                endAction = endAction?.let { action ->
                    { CompositionLocalProvider(LocalDismissState provides requestDismiss) { action() } }
                },
                content = {
                    CompositionLocalProvider(LocalDismissState provides requestDismiss) {
                        content()
                    }
                },
            )
        }
    }
}

@Suppress("ktlint:compose:modifier-not-used-at-root")
@Composable
internal fun BottomSheetContent(
    title: String?,
    backgroundColor: Color,
    cornerRadius: Dp,
    sheetMaxWidth: Dp,
    outsideMargin: DpSize,
    insideMargin: DpSize,
    defaultWindowInsetsPadding: Boolean,
    dragHandleColor: Color,
    allowDismiss: Boolean,
    sheetHeightPx: MutableIntState,
    dragOffsetY: Animatable<Float, *>,
    dimAlpha: MutableFloatState,
    dragSnapChannel: Channel<Float>,
    onDismissRequest: (() -> Unit)?,
    modifier: Modifier = Modifier,
    topInset: Dp? = null,
    enableNestedScroll: Boolean = true,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val windowHeight = windowInfo.containerDpSize.height
    // Do NOT use windowHeight as a remember key — IME resize would recreate handlers and trigger dismiss.
    val currentWindowHeight by rememberUpdatedState(windowHeight)
    val coroutineScope = rememberCoroutineScope()

    val settlingJob = remember { mutableStateOf<Job?>(null) }
    val isSettling = remember { mutableStateOf(false) }
    val calculatedTopInset = if (topInset != null) {
        topInset
    } else {
        val statusBars = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val captionBar = WindowInsets.captionBar.asPaddingValues().calculateTopPadding()
        val displayCutout = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
        maxOf(statusBars, captionBar, displayCutout)
    }

    val calculateNewOffset = remember(allowDismiss) {
        { current: Float, delta: Float ->
            val newOffset = current + delta
            if (newOffset < 0) {
                val dampingFactor = 0.1f
                (current + delta * dampingFactor).coerceAtMost(0f)
            } else if (newOffset >= 0 && !allowDismiss) {
                val dampingFactor = 0.1f
                val dampedAmount = if (delta > 0) delta * dampingFactor else delta
                (current + dampedAmount).coerceAtLeast(0f)
            } else {
                newOffset
            }
        }
    }

    val updateDimAlpha = remember(allowDismiss) {
        { offset: Float ->
            val thresholdPx = if (sheetHeightPx.intValue > 0) sheetHeightPx.intValue.toFloat() else 500f
            val alpha = if (offset >= 0 && allowDismiss) {
                1f - (offset / thresholdPx).coerceIn(0f, 1f)
            } else {
                1f
            }
            dimAlpha.floatValue = alpha
        }
    }

    // Settlement logic
    val performSettle: (Float) -> Unit = remember(allowDismiss, density) {
        { velocity ->
            settlingJob.value?.cancel()
            isSettling.value = true
            settlingJob.value = coroutineScope.launch {
                val currentOffset = dragOffsetY.value
                val dismissThresholdPx = with(density) { 150.dp.toPx() }
                // Velocity comes from gesture pipeline as px/s; the threshold is intentionally
                // expressed as a dp value converted to px so the trigger feels equivalent across
                // densities (higher-density screens both demand and produce proportionally larger
                // px/s values, which roughly cancels out).
                val velocityThresholdPx = with(density) { 800.dp.toPx() }
                val windowHeightPx = with(density) { currentWindowHeight.toPx() }

                val shouldDismiss = allowDismiss && (
                    (velocity > velocityThresholdPx) ||
                        (currentOffset > dismissThresholdPx && velocity > -velocityThresholdPx)
                    )

                try {
                    if (shouldDismiss) {
                        if (currentOffset >= windowHeightPx) {
                            onDismissRequest?.invoke()
                        } else {
                            animateDismissOffScreen(
                                dragOffsetY = dragOffsetY,
                                sheetHeightPx = sheetHeightPx.intValue,
                                windowHeightPx = windowHeightPx,
                                peekPx = with(density) { PanelPeekHeight.toPx() },
                                dimAlpha = dimAlpha,
                                velocity = velocity,
                            ) {
                                onDismissRequest?.invoke()
                            }
                        }
                    } else {
                        val effectiveVelocity = if (!allowDismiss && velocity > 0) 0f else velocity
                        dragOffsetY.animateTo(
                            targetValue = 0f,
                            animationSpec = folmeSpring(damping = 0.85f, response = 0.4f),
                            initialVelocity = effectiveVelocity,
                        ) {
                            updateDimAlpha(value)
                        }
                        dimAlpha.floatValue = 1f
                    }
                } catch (_: CancellationException) {
                    // Animation is interrupted
                } finally {
                    // Reset state after animation completes
                    isSettling.value = false
                }
            }
        }
    }

    // Nested scroll logic
    val nestedScrollConnection = remember(enableNestedScroll, allowDismiss, density) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!enableNestedScroll) return Offset.Zero

                // Allow interruption whenever settling
                if (isSettling.value) {
                    settlingJob.value?.cancel()
                    isSettling.value = false
                }

                val delta = available.y
                // If the sheet is offset, prioritize restoring its position
                if (delta < 0 && dragOffsetY.value > 0) {
                    val newOffset = calculateNewOffset(dragOffsetY.value, delta).coerceAtLeast(0f)
                    val consumedY = dragOffsetY.value - newOffset
                    if (consumedY != 0f) {
                        dragSnapChannel.trySend(newOffset)
                        updateDimAlpha(newOffset)
                        return Offset(0f, consumedY * -1f)
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!enableNestedScroll) return Offset.Zero

                val delta = available.y
                if (delta > 0) {
                    if (!allowDismiss) return Offset.Zero

                    if (isSettling.value) {
                        settlingJob.value?.cancel()
                        isSettling.value = false
                    }

                    val newOffset = calculateNewOffset(dragOffsetY.value, delta)
                    dragSnapChannel.trySend(newOffset)
                    updateDimAlpha(newOffset)

                    // Dismiss immediately if dragged beyond window height
                    val windowHeightPx = with(density) { currentWindowHeight.toPx() }
                    if (newOffset > windowHeightPx) {
                        performSettle(0f)
                        return available
                    }

                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!enableNestedScroll || isSettling.value) return Velocity.Zero

                // Take over fling if the sheet is offset
                if (dragOffsetY.value > 0) {
                    performSettle(available.y)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!enableNestedScroll || isSettling.value) return Velocity.Zero

                if (dragOffsetY.value > 0) {
                    performSettle(available.y)
                    return available
                }
                return super.onPostFling(consumed, available)
            }
        }
    }

    BottomSheetColumn(
        title = title,
        startAction = startAction,
        endAction = endAction,
        backgroundColor = backgroundColor,
        cornerRadius = cornerRadius,
        sheetMaxWidth = sheetMaxWidth,
        outsideMargin = outsideMargin,
        insideMargin = insideMargin,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        dragHandleColor = dragHandleColor,
        allowDismiss = allowDismiss,
        windowHeight = windowHeight,
        topInset = calculatedTopInset,
        enableNestedScroll = enableNestedScroll,
        sheetHeightPx = sheetHeightPx,
        dragOffsetY = dragOffsetY,
        nestedScrollConnection = nestedScrollConnection,
        coroutineScope = coroutineScope,
        dragSnapChannel = dragSnapChannel,
        onSettle = performSettle,
        onUpdateAlpha = updateDimAlpha,
        modifier = modifier,
        content = content,
    )
}

@Suppress("ktlint:compose:modifier-not-used-at-root")
@Composable
private fun BottomSheetColumn(
    title: String?,
    startAction: (@Composable () -> Unit)?,
    endAction: (@Composable () -> Unit)?,
    backgroundColor: Color,
    cornerRadius: Dp,
    sheetMaxWidth: Dp,
    outsideMargin: DpSize,
    insideMargin: DpSize,
    defaultWindowInsetsPadding: Boolean,
    dragHandleColor: Color,
    allowDismiss: Boolean,
    windowHeight: Dp,
    topInset: Dp,
    enableNestedScroll: Boolean,
    sheetHeightPx: MutableIntState,
    dragOffsetY: Animatable<Float, *>,
    nestedScrollConnection: NestedScrollConnection,
    coroutineScope: CoroutineScope,
    dragSnapChannel: Channel<Float>,
    onSettle: (velocity: Float) -> Unit,
    onUpdateAlpha: (Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    // COUI grid width plus `sheetMaxWidth` as an additional caller-supplied cap.
    val windowSize = LocalWindowInfo.current.containerDpSize
    val effectiveMaxWidth = remember(windowSize, sheetMaxWidth) {
        min(sheetMaxWidth, couiPanelMaxWidth(windowSize))
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        OverscrollBackground(
            dragOffsetY = dragOffsetY,
            sheetMaxWidth = effectiveMaxWidth,
            outsideMargin = outsideMargin,
            backgroundColor = backgroundColor,
        )

        Column(
            modifier = modifier
                .pointerInput(Unit) { detectTapGestures { /* Consume click */ } }
                .then(if (enableNestedScroll) Modifier.nestedScroll(nestedScrollConnection) else Modifier)
                .widthIn(max = effectiveMaxWidth)
                .fillMaxWidth()
                .wrapContentHeight()
                .heightIn(max = windowHeight - topInset)
                .onGloballyPositioned { coordinates ->
                    // Only capture the natural sheet height while the IME is closed; otherwise the
                    // bottom inset added by imePadding would inflate sheetHeightPx, shifting the
                    // dim threshold and causing translationY jumps mid-animation.
                    if (imeInsets.getBottom(density) == 0) {
                        sheetHeightPx.intValue = coordinates.size.height
                    }
                }
                .then(if (defaultWindowInsetsPadding) Modifier.imePadding() else Modifier)
                .padding(horizontal = outsideMargin.width)
                .squircleSurface(
                    color = backgroundColor,
                    topStart = cornerRadius,
                    topEnd = cornerRadius,
                    bottomEnd = 0.dp,
                    bottomStart = 0.dp,
                )
                .padding(horizontal = insideMargin.width)
                .padding(bottom = insideMargin.height),
        ) {
            DragHandleArea(
                dragHandleColor = dragHandleColor,
                allowDismiss = allowDismiss,
                dragOffsetY = dragOffsetY,
                coroutineScope = coroutineScope,
                dragSnapChannel = dragSnapChannel,
                onSettle = onSettle,
                onUpdateAlpha = onUpdateAlpha,
            )

            TitleAndActionsRow(
                title = title,
                startAction = startAction,
                endAction = endAction,
            )

            content()
        }
    }
}

/**
 * Renders the elastic background fill that follows the sheet upward when the user
 * drags past the top of its contents (negative `dragOffsetY`). Lives in its own
 * composable so the only state it reads — `dragOffsetY.value` — invalidates this
 * subtree alone, leaving the surrounding [BottomSheetColumn] free of per-frame
 * recomposition during a drag.
 */
@Composable
private fun BoxScope.OverscrollBackground(
    dragOffsetY: Animatable<Float, *>,
    sheetMaxWidth: Dp,
    outsideMargin: DpSize,
    backgroundColor: Color,
) {
    val density = LocalDensity.current
    val overscrollOffsetPx by remember { derivedStateOf { (-dragOffsetY.value).coerceAtLeast(0f) } }
    if (overscrollOffsetPx > 0f) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = sheetMaxWidth)
                .fillMaxWidth()
                .height(with(density) { overscrollOffsetPx.toDp() } + 1.dp)
                .padding(horizontal = outsideMargin.width)
                .background(backgroundColor),
        )
    }
}

/** Easing curve matching COUIEaseInterpolator (cubic-bezier 0.33, 0, 0.67, 1). */
private val CouiEaseEasing = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)

/**
 * Peek height used to normalize the panel travel fraction.
 * COUI `coui_panel_default_peek_height_in_gesture`.
 */
private val PanelPeekHeight = 82.dp

/**
 * Spring spec of the COUI panel slide: damping ratio 1, response interpolated between 0.25s
 * and 0.45s (enter) / 0.4s (exit) by the travel fraction `(travel - peek) / (container - peek)`.
 */
private fun <T> couiPanelSlideSpring(
    entering: Boolean,
    travelPx: Float,
    containerPx: Float,
    peekPx: Float,
): SpringSpec<T> {
    val base = if (entering) 0.45f else 0.4f
    val denominator = containerPx - peekPx
    val fraction = if (denominator > 0f) ((travelPx - peekPx) / denominator).coerceIn(0f, 1f) else 1f
    return folmeSpring(damping = 1f, response = 0.25f + (base - 0.25f) * fraction)
}

/**
 * Maximum panel width from the COUI responsive layout grid: full width on compact windows
 * (< 600dp), otherwise a 6-column span of the 8-column (margin 24dp, gutter 8dp) or
 * 12-column (margin 40dp, gutter 12dp) grid, chosen by the COUI window size classes.
 */
private fun couiPanelMaxWidth(windowSize: DpSize): Dp {
    val width = windowSize.width.value
    val height = windowSize.height.value
    if (width < 600f) return Dp.Infinity
    val eightColumns = width < 840f || height < 480f || (height >= 900f && height > width && width < 960f)
    return if (eightColumns) {
        ((width - 2f * 24f - 7f * 8f) / 8f * 6f + 5f * 8f).dp
    } else {
        ((width - 2f * 40f - 11f * 12f) / 12f * 6f + 5f * 12f).dp
    }
}

private fun CoroutineScope.animateHandlePressDown(pressAlpha: Animatable<Float, *>) {
    // COUIPanelPressHelper.startAnim: bgAlpha 0 -> 1, 200ms, COUIEaseInterpolator.
    launch { pressAlpha.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 200, easing = CouiEaseEasing)) }
}

private fun CoroutineScope.animateHandlePressRelease(pressAlpha: Animatable<Float, *>) {
    // COUIPanelPressHelper.endAnim: bgAlpha -> 0, 200ms, COUIEaseInterpolator.
    launch { pressAlpha.animateTo(targetValue = 0f, animationSpec = tween(durationMillis = 200, easing = CouiEaseEasing)) }
}

/**
 * Drag handle strip at the top of the sheet, matching the COUI panel drag view: a fixed
 * capsule bar that never deforms; pressing or dragging fades in a rounded press background
 * centered behind it. Drag amounts are forwarded into [dragSnapChannel].
 *
 * Press/release fading is delegated to [animateHandlePressDown] / [animateHandlePressRelease]
 * so the same launch is not rewritten in each of the four entry points
 * (tap-press, tap-release, drag-start, drag-stop).
 */
@Composable
private fun DragHandleArea(
    dragHandleColor: Color,
    allowDismiss: Boolean,
    dragOffsetY: Animatable<Float, *>,
    coroutineScope: CoroutineScope,
    dragSnapChannel: Channel<Float>,
    onSettle: (velocity: Float) -> Unit,
    onUpdateAlpha: (Float) -> Unit,
) {
    val pressAlpha = remember { Animatable(0f) }
    val handleShape = remember { RoundedCornerShape(2.dp) }
    val pressShape = remember { RoundedCornerShape(16.dp) }
    // COUI couiColorPress has no dedicated scheme slot; derive it from onSurface.
    val pressColor = COUITheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        coroutineScope.animateHandlePressDown(pressAlpha)
                        val released = tryAwaitRelease()
                        if (released) {
                            coroutineScope.animateHandlePressRelease(pressAlpha)
                        }
                    },
                )
            }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { dragAmount ->
                    val newOffset = dragOffsetY.value + dragAmount
                    val finalOffset = if (newOffset < 0) {
                        // Damping up
                        (dragOffsetY.value + dragAmount * 0.1f).coerceAtMost(0f)
                    } else if (newOffset >= 0 && !allowDismiss) {
                        // Damping down if not dismissible
                        val dampedAmount = if (dragAmount > 0) dragAmount * 0.1f else dragAmount
                        (dragOffsetY.value + dampedAmount).coerceAtLeast(0f)
                    } else {
                        newOffset
                    }

                    dragSnapChannel.trySend(finalOffset)
                    onUpdateAlpha(finalOffset)
                },
                onDragStarted = {
                    coroutineScope.animateHandlePressDown(pressAlpha)
                },
                onDragStopped = { velocity ->
                    coroutineScope.animateHandlePressRelease(pressAlpha)
                    // Delegate the settle logic to the shared function
                    onSettle(velocity)
                },
            )
            // COUI coui_panel_drag_view_shadow_margin_top: aligns the band center with the bar center.
            .padding(top = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        // COUI press feedback background, faded in/out while the handle is touched.
        Box(
            modifier = Modifier
                .width(70.dp)
                .height(20.dp)
                .clip(pressShape)
                .drawBehind {
                    drawRect(pressColor.copy(alpha = pressColor.alpha * pressAlpha.value))
                },
        )
        // COUI drag bar: a fixed capsule, no press deformation.
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(handleShape)
                .drawBehind {
                    drawRect(dragHandleColor)
                },
        )
    }
}

@Composable
private fun TitleAndActionsRow(
    title: String?,
    startAction: (@Composable () -> Unit)?,
    endAction: (@Composable () -> Unit)?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 12.dp),
    ) {
        // Start action (e.g. close button)
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            startAction?.invoke()
        }

        // Title text
        title?.let {
            Text(
                text = it,
                modifier = Modifier.align(Alignment.Center),
                fontSize = COUITheme.textStyles.title4.fontSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = COUITheme.colorScheme.onSurface,
            )
        }

        // End action (e.g. submit button)
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            endAction?.invoke()
        }
    }
}

/**
 * Animate the sheet off-screen, updating dim alpha along the way.
 * Dismisses as soon as the sheet leaves the viewport (offset >= sheetHeight).
 */
private suspend fun CoroutineScope.animateDismissOffScreen(
    dragOffsetY: Animatable<Float, *>,
    sheetHeightPx: Int,
    windowHeightPx: Float,
    peekPx: Float,
    dimAlpha: MutableFloatState,
    velocity: Float = 0f,
    onDismiss: () -> Unit,
) {
    val sheetHeight = sheetHeightPx.toFloat()
    val thresholdPx = if (sheetHeight > 0) sheetHeight else 500f
    val settleJob = launch {
        dragOffsetY.animateTo(
            targetValue = windowHeightPx,
            animationSpec = couiPanelSlideSpring(
                entering = false,
                travelPx = if (sheetHeight > 0) sheetHeight else windowHeightPx,
                containerPx = windowHeightPx,
                peekPx = peekPx,
            ),
            initialVelocity = velocity,
        ) {
            dimAlpha.floatValue = (1f - (value / thresholdPx).coerceIn(0f, 1f))
        }
    }
    snapshotFlow { dragOffsetY.value }
        .first { sheetHeight > 0 && it >= sheetHeight }
    settleJob.cancel()
    onDismiss()
}

object BottomSheetDefaults {

    /**
     * The default background color of the bottom sheet. COUI tints the panel with
     * `couiColorSurface`, which the `background` slot matches in both modes.
     */
    @Composable
    fun backgroundColor() = COUITheme.colorScheme.background

    /**
     * The default color of the drag handle.
     */
    @Composable
    fun dragHandleColor() = COUITheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.2f)

    /**
     * The default corner radius of the top corners of the bottom sheet. COUI `couiRoundCornerXL`,
     * with the squircle surface supplying the smooth-corner shape; the bottom corners are square,
     * matching the outline COUI extends off-screen.
     */
    val cornerRadius = 20.dp

    /**
     * The default maximum width cap of the bottom sheet. Unbounded by default: the effective
     * width is governed by the COUI responsive layout grid (full width on compact windows,
     * a 6-column grid span on medium/expanded windows). Pass a finite value to cap it further.
     */
    val maxWidth = Dp.Infinity

    /**
     * The default margin outside the bottom sheet.
     */
    val outsideMargin = DpSize(0.dp, 0.dp)

    /**
     * The default margin inside the bottom sheet.
     */
    val insideMargin = DpSize(24.dp, 0.dp)
}
