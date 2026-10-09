package com.mi.onextbox.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.NavigationBackHandler
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplayTransitionEffects
import androidx.navigation3.ui.getRoundedCorner
import androidx.navigation3.ui.isInMultiWindowMode
import androidx.navigationevent.NavigationEventTransitionState.InProgress
import io.github.suqi8.coui.kmp.squircle.absoluteSquircleClip
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

private class RootPageLayer<T : Any>(scene: Scene<T>, start: Float, order: Int) {
    var scene by mutableStateOf(scene)
    val offset = Animatable(start)
    var target by mutableFloatStateOf(start)
    var order by mutableIntStateOf(order)
    // Animatable clears its velocity on cancellation; keep it for the next motion.
    var velocity = 0f
}

/** Single-pane root pages, with physical offsets instead of eased timeline fractions. */
@Composable
internal fun <T : Any> RootNavDisplay(
    entries: List<NavEntry<T>>,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    transitionEffects: NavDisplayTransitionEffects = NavDisplayTransitionEffects.Default,
    onBack: () -> Unit,
) {
    val strategies = remember { listOf(SinglePaneSceneStrategy<T>()) }
    val sceneState = rememberSceneState(entries, strategies, onBack = onBack)
    val navigationState = rememberNavigationEventState(sceneState)
    NavigationBackHandler(sceneState, navigationState, onBack)

    val scene = sceneState.currentScene
    val previousScene = sceneState.previousScenes.lastOrNull()
    val dragging by remember(previousScene != null) {
        derivedStateOf { previousScene != null && navigationState.transitionState is InProgress }
    }
    val currentKey by rememberUpdatedState(scene.key)
    val currentDragging by rememberUpdatedState(dragging)
    val layers = remember { mutableStateListOf(RootPageLayer(scene, 0f, entries.lastIndex)) }
    val stackKeys = sceneState.entries.map { it.contentKey }
    val previousKeys = remember { mutableListOf<Any>().apply { addAll(stackKeys) } }

    fun ensureLayer(page: Scene<T>, start: Float): RootPageLayer<T> =
        layers.firstOrNull { it.scene.key == page.key } ?: RootPageLayer(
            page, start, stackKeys.indexOf(page.key).coerceAtLeast(0),
        ).also { layers.add(it) }

    SideEffect {
        val knownScenes = sceneState.previousScenes + scene
        layers.forEach { layer ->
            knownScenes.firstOrNull { it.key == layer.scene.key }?.let { layer.scene = it }
        }
        val start = when {
            !animate -> 0f
            scene.key in previousKeys -> RootCoveredPageOffset
            else -> 1f
        }
        ensureLayer(scene, start)
        if (!dragging) {
            layers.forEach { layer ->
                val index = stackKeys.indexOf(layer.scene.key)
                if (index >= 0) layer.order = index
                layer.target = when {
                    layer.scene.key == scene.key -> 0f
                    index >= 0 -> RootCoveredPageOffset
                    else -> 1f
                }
            }
            if (!animate) layers.removeAll { it.scene.key != scene.key }
        }
        previousKeys.clear()
        previousKeys.addAll(stackKeys)
    }

    fun removeFinishedLayers() {
        if (currentDragging) return
        val current = layers.firstOrNull { it.scene.key == currentKey } ?: return
        val currentSettled = !current.offset.isRunning && abs(current.offset.value) < 0.001f
        val outgoingMoving = layers.any { it.target == 1f && it.offset.isRunning }
        layers.removeAll { layer ->
            layer !== current && !layer.offset.isRunning && (
                (layer.target == 1f && abs(layer.offset.value - 1f) < 0.001f) ||
                    (layer.target == RootCoveredPageOffset && currentSettled && !outgoingMoving)
                )
        }
    }

    LaunchedEffect(dragging, sceneState) {
        if (!dragging || previousScene == null) return@LaunchedEffect
        val front = ensureLayer(scene, 0f)
        val behind = ensureLayer(previousScene, RootCoveredPageOffset)
        front.offset.stop()
        behind.offset.stop()
        val frontStart = front.offset.value
        val behindStart = behind.offset.value
        var lastTime = System.nanoTime()
        snapshotFlow { navigationState.transitionState }.collectLatest { state ->
            if (state !is InProgress) return@collectLatest
            val progress = state.latestEvent.progress
            val frontNext = rootGesturePageOffset(frontStart, 1f, progress)
            val behindNext = rootGesturePageOffset(behindStart, 0f, progress)
            val now = System.nanoTime()
            val elapsed = (now - lastTime) / 1_000_000_000f
            if (elapsed >= 0.001f) {
                front.velocity = ((frontNext - front.offset.value) / elapsed).coerceIn(-6f, 6f)
                behind.velocity = ((behindNext - behind.offset.value) / elapsed).coerceIn(-6f, 6f)
            }
            lastTime = now
            front.offset.snapTo(frontNext)
            behind.offset.snapTo(behindNext)
        }
    }

    val corner = if (transitionEffects.enableCornerClip && !isInMultiWindowMode()) {
        getRoundedCorner()
    } else 0.dp
    BoxWithConstraints(modifier = modifier) {
        val width = with(LocalDensity.current) { maxWidth.toPx() }
        // A newly opened sibling must be above a sibling that is still closing.
        val orderedLayers = layers.sortedBy { it.order }
        val top = orderedLayers.lastOrNull()
        orderedLayers.forEachIndexed { drawOrder, layer ->
            key(layer.scene.key) {
                LaunchedEffect(layer.target, dragging, animate) {
                    if (dragging) return@LaunchedEffect
                    if (animate) {
                        layer.offset.animateTo(
                            layer.target,
                            animationSpec = RootPageSpring,
                            initialVelocity = layer.velocity,
                        ) { layer.velocity = velocity }
                    } else {
                        layer.offset.snapTo(layer.target)
                    }
                    layer.velocity = 0f
                    removeFinishedLayers()
                }
                val lifecycle = rememberLifecycleOwner(
                    maxLifecycle = if (layer.scene.key == scene.key &&
                        !dragging && !layer.offset.isRunning) Lifecycle.State.RESUMED
                    else Lifecycle.State.STARTED,
                )
                val rounded = if (corner > 0.dp && layer === top &&
                    (dragging || layer.offset.isRunning)) {
                    Modifier.absoluteSquircleClip(corner, 0.dp, 0.dp, corner)
                } else Modifier
                val input = if (transitionEffects.blockInputDuringTransition &&
                    layer.scene.key != scene.key) {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        }
                    }
                } else Modifier
                Box(
                    Modifier.fillMaxSize()
                        .zIndex(drawOrder.toFloat())
                        .graphicsLayer { translationX = if (animate) layer.offset.value * width else 0f }
                        .then(rounded)
                        .then(input),
                ) {
                    CompositionLocalProvider(LocalLifecycleOwner provides lifecycle) {
                        layer.scene.content()
                    }
                    if (top != null && layer !== top && transitionEffects.dimAmount > 0f) {
                        Box(
                            Modifier.fillMaxSize()
                                .graphicsLayer {
                                    alpha = (1f - top.offset.value.coerceIn(0f, 1f)) *
                                        transitionEffects.dimAmount
                                }
                                .background(Color.Black),
                        )
                    }
                }
            }
        }
    }
}
