package com.mi.onextbox.ui.common

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.UnplacedAwareModifierNode
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val LocalColorOsScrollEntrance = compositionLocalOf<ColorOsScrollEntranceState?> { null }
internal val LocalColorOsProgressiveCardAnimationEnabled = compositionLocalOf { true }

@Composable
internal fun rememberColorOsScrollEntrance(scrollState: ScrollState): ColorOsScrollEntranceState =
    remember(scrollState) { ColorOsScrollEntranceState { scrollState.isScrollInProgress } }

@Composable
internal fun rememberColorOsScrollEntrance(scrollState: LazyListState): ColorOsScrollEntranceState =
    remember(scrollState) { ColorOsScrollEntranceState { scrollState.isScrollInProgress } }

/** Cards entering a scroll viewport. */
@Composable
internal fun ColorOsScrollEntranceHost(
    state: ColorOsScrollEntranceState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!enabled || !LocalColorOsProgressiveCardAnimationEnabled.current ||
        LocalAppUiStyle.current != AppUiStyle.ColorOs
    ) {
        CompositionLocalProvider(LocalColorOsScrollEntrance provides null) {
            Box(modifier, content = content)
        }
        return
    }
    DisposableEffect(state) { onDispose { state.clear() } }
    CompositionLocalProvider(LocalColorOsScrollEntrance provides state) {
        Box(
            modifier.nestedScroll(state.connection).onGloballyPositioned { coordinates ->
                // Use the visible viewport, including the area behind floating bars.
                val bounds = coordinates.boundsInWindow()
                state.viewport(bounds.top, bounds.bottom)
            },
            content = content,
        )
    }
}

@Composable
internal fun Modifier.colorOsScrollEntrance(key: Any? = null): Modifier {
    val state = LocalColorOsScrollEntrance.current
    if (state == null || LocalAppUiStyle.current != AppUiStyle.ColorOs) return this
    val itemKey = remember(key) { key ?: Any() }
    return then(ColorOsScrollEntranceElement(state, itemKey))
}

@Stable
internal class ColorOsScrollEntranceState(private val scrolling: () -> Boolean) {
    private val items = mutableMapOf<Any, ColorOsScrollEntranceNode>()
    private var viewportTop = Float.NaN
    private var viewportBottom = Float.NaN
    private var direction = 0f

    internal val connection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (consumed.y != 0f && consumed.y.isFinite()) direction = consumed.y
            return Offset.Zero
        }
    }

    internal fun viewport(top: Float, bottom: Float) {
        if (top == viewportTop && bottom == viewportBottom) return
        viewportTop = top
        viewportBottom = bottom
        items.values.toList().forEach { check(it, baseline = true) }
    }

    private fun ready() = viewportTop.isFinite() && viewportBottom > viewportTop

    internal fun register(item: ColorOsScrollEntranceNode) { items[item.key] = item }

    internal fun remove(item: ColorOsScrollEntranceNode) {
        if (items[item.key] === item) items.remove(item.key)
    }

    internal fun check(item: ColorOsScrollEntranceNode, baseline: Boolean = false) {
        val coordinates = item.coordinates ?: return
        if (!ready() || !coordinates.isAttached) return
        val y = coordinates.positionInWindow().y
        val start = item.entry.update(
            y, y + coordinates.size.height,
            viewportTop, viewportBottom, direction, scrolling(), baseline,
        )
        if (start != null) item.transition(start)
    }

    internal fun offsetFor(key: Any): Float = items[key]?.offset ?: 0f

    internal fun clear() {
        items.values.toList().forEach { it.reset() }
        items.clear()
        viewportTop = Float.NaN
        viewportBottom = Float.NaN
        direction = 0f
    }
}

private data class ColorOsScrollEntranceElement(
    val state: ColorOsScrollEntranceState,
    val key: Any,
) : ModifierNodeElement<ColorOsScrollEntranceNode>() {
    override fun create() = ColorOsScrollEntranceNode(state, key)
    override fun update(node: ColorOsScrollEntranceNode) = node.update(state, key)
    override fun InspectorInfo.inspectableProperties() {
        name = "colorOsScrollEntrance"
        properties["key"] = key
    }
}

internal class ColorOsScrollEntranceNode(
    private var state: ColorOsScrollEntranceState,
    internal var key: Any,
) : Modifier.Node(), LayoutModifierNode, LayoutAwareModifierNode,
    GlobalPositionAwareModifierNode, UnplacedAwareModifierNode {
    internal var coordinates: LayoutCoordinates? = null
        private set
    internal var entry = ColorOsScrollEntranceEntry()
        private set
    internal var offset = 0f
        private set
    private var animation: Job? = null
    private var generation = 0

    override fun onAttach() { state.register(this) }

    override fun onDetach() {
        state.remove(this)
        reset()
        coordinates = null
    }

    internal fun update(nextState: ColorOsScrollEntranceState, nextKey: Any) {
        if (state === nextState && key == nextKey) return
        state.remove(this)
        reset()
        state = nextState
        key = nextKey
        entry = ColorOsScrollEntranceEntry()
        if (isAttached) state.register(this)
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        // Outer layout coordinates exclude the child's animated layer.
        this.coordinates = coordinates
        state.check(this)
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) { state.check(this) }

    override fun onUnplaced() {
        coordinates = null
        reset()
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val child = measurable.measure(constraints)
        return layout(child.width, child.height) {
            child.placeWithLayer(0, 0) { translationY = offset }
        }
    }

    internal fun transition(start: Float) {
        if (start == 0f) {
            stopAnimation()
            return
        }
        generation++
        val current = generation
        animation?.cancel()
        setOffset(start)
        animation = coroutineScope.launch {
            try {
                animate(
                    start, 0f,
                    animationSpec = tween(ColorOsScrollEntranceMotion.DurationMillis,
                        easing = ColorOsScrollEntranceMotion.Easing),
                ) { value, _ -> if (generation == current) setOffset(value) }
            } finally {
                if (generation == current) setOffset(0f)
            }
        }
    }

    internal fun reset() {
        stopAnimation()
        entry.leave()
    }

    private fun stopAnimation() {
        generation++
        animation?.cancel()
        animation = null
        setOffset(0f)
    }

    private fun setOffset(value: Float) {
        if (offset == value) return
        offset = value
        if (isAttached) invalidatePlacement()
    }
}
