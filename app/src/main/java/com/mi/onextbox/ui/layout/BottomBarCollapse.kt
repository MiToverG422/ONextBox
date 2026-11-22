package com.mi.onextbox.ui.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.AppIcons
import com.mi.onextbox.ui.common.ColorOsDialogButtonMotion
import com.mi.onextbox.ui.common.ColorOsTopBarButton
import kotlin.math.abs
import kotlin.math.roundToInt

@Stable
class BottomBarCollapseState internal constructor(
    private val scrollThresholdPx: Float,
    internal val mode: BottomBarAutoCollapse,
) {
    var collapsed by mutableStateOf(false)
        private set
    internal val animation = Animatable(0f)
    val progress: Float get() = if (mode.isSide) animation.value.coerceIn(0f, 1f) else 0f
    internal val folderCollapsed: Boolean get() = mode.isSide && collapsed
    internal val hiddenBelowScreen: Boolean get() = mode == BottomBarAutoCollapse.Bottom && collapsed
    val expandedContentAlpha: Float get() = (1f - progress * 2.5f).coerceIn(0f, 1f)
    val interactionReady: Boolean get() = !collapsed && progress == 0f
    private var scrollDistance = 0f

    fun collapse() { scrollDistance = 0f; collapsed = mode != BottomBarAutoCollapse.Off }
    fun expand() { scrollDistance = 0f; collapsed = false }

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (mode == BottomBarAutoCollapse.Off || source != NestedScrollSource.UserInput ||
                abs(available.y) <= abs(available.x)
            ) return Offset.Zero
            val delta = available.y
            if ((delta > 0f) != (scrollDistance > 0f)) scrollDistance = 0f
            scrollDistance += delta
            if (scrollDistance <= -scrollThresholdPx) collapse()
            else if (scrollDistance >= scrollThresholdPx) expand()
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            scrollDistance = 0f
            return Velocity.Zero
        }
    }
}

@Composable
internal fun rememberBottomBarCollapseState(
    mode: BottomBarAutoCollapse,
): BottomBarCollapseState {
    val density = LocalDensity.current
    val threshold = with(density) { 24.dp.toPx() }
    val state = remember(threshold, mode) { BottomBarCollapseState(threshold, mode) }
    if (mode.isSide) {
        LaunchedEffect(state, state.folderCollapsed) {
            state.animation.animateTo(
                if (state.folderCollapsed) 1f else 0f,
                tween(durationMillis = 420, easing = CubicBezierEasing(.2f, 0f, 0f, 1f)),
            )
        }
    }
    return state
}

internal fun Modifier.bottomBarCollapseGeometry(
    state: BottomBarCollapseState?,
    expandedWidth: Dp,
    diameter: Dp,
    hostWidth: Dp,
): Modifier = if (state == null || !state.mode.isSide) this else absoluteOffset {
    val halfTravel = ((hostWidth - diameter) / 2).coerceAtLeast(0.dp)
    val offset = (halfTravel - minOf(16.dp, halfTravel)) * state.progress
    IntOffset((if (state.mode == BottomBarAutoCollapse.Right) offset else -offset).roundToPx(), 0)
}.layout { measurable, constraints ->
    val progress = state.progress
    val width = (expandedWidth.toPx() + (diameter - expandedWidth).toPx() * progress).roundToInt()
        .coerceIn(1, constraints.maxWidth.coerceAtLeast(1))
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

internal fun Modifier.bottomBarCollapseGestures(
    state: BottomBarCollapseState?,
    startupRevealProgress: () -> Float = { 1f },
    expandedHoldMotion: ColorOsDialogButtonMotion? = null,
): Modifier = if (state == null) this else pointerInput(state, startupRevealProgress, expandedHoldMotion) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (startupRevealProgress() < 1f) return@awaitEachGesture
        val touchSlop = viewConfiguration.touchSlop
        var crossedSlop = false
        var axis = 0
        var handled = false
        var holdMotion: ColorOsDialogButtonMotion? = null
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val drag = change.position - down.position
                if (!crossedSlop && drag.getDistance() > touchSlop) {
                    crossedSlop = true
                    if (change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) {
                        // The standard bar and the folder already handle direct touch motion.
                        holdMotion = expandedHoldMotion?.takeIf { state.interactionReady }
                            ?: return@awaitEachGesture
                        holdMotion.begin()
                    }
                }
                if (holdMotion != null) {
                    change.consume()
                    if (change.pressed) holdMotion.drag(drag, size)
                    continue
                }
                if (axis == 0 && crossedSlop) {
                    if (abs(drag.y) > abs(drag.x) * 1.1f) axis = 1
                    else if (abs(drag.x) > abs(drag.y) * 1.1f) axis = 2
                }
                if (axis == 2 && abs(drag.y) > maxOf(touchSlop, abs(drag.x) * 1.4f)) axis = 1
                if (axis == 1 && state.mode != BottomBarAutoCollapse.Off) {
                    change.consume()
                    val threshold = if (change.pressed) maxOf(touchSlop, 12.dp.toPx()) else touchSlop
                    if (!handled && abs(drag.y) >= threshold) {
                        if (drag.y > 0f) state.collapse() else state.expand()
                        handled = true
                    }
                }
            } while (event.changes.any { it.pressed })
        } finally {
            holdMotion?.release(size)
        }
    }
}

@Composable
internal fun CollapsibleBottomBarSurface(
    state: BottomBarCollapseState?,
    background: @Composable BoxScope.() -> Unit,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    if (state == null || !state.mode.isSide) {
        Box(Modifier.fillMaxSize(), contentAlignment = contentAlignment) { background(); content() }
    } else ColorOsTopBarButton(
        onClick = state::expand,
        enabled = state.folderCollapsed,
        feedbackProgress = { state.progress },
        background = background,
        contentAlignment = contentAlignment,
        modifier = Modifier.fillMaxSize().then(if (state.folderCollapsed) Modifier.testTag("bottomBarFolder") else Modifier),
        content = content,
    )
}

@Composable
internal fun BottomBarFolderIcon(
    state: BottomBarCollapseState?,
    diameter: Dp,
    tint: Color,
    contentOpacity: () -> Float,
    modifier: Modifier = Modifier,
) {
    if (state == null || !state.mode.isSide) return
    val description = stringResource(R.string.bottom_bar_expand)
    Box(
        modifier = modifier.size(diameter)
            .graphicsLayer {
                val progress = state.progress
                alpha = ((progress - .3f) / .7f).coerceIn(0f, 1f) * contentOpacity()
                scaleX = .7f + .3f * progress
                scaleY = scaleX
            }
            .then(if (state.folderCollapsed) Modifier else Modifier.clearAndSetSemantics {}),
        contentAlignment = Alignment.Center,
    ) {
        Image(AppIcons.FolderOutline, description, Modifier.size(28.dp), colorFilter = ColorFilter.tint(tint))
    }
}
