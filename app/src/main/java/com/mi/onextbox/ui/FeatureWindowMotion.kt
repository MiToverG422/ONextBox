package com.mi.onextbox.ui

import androidx.activity.BackEventCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import com.mi.onextbox.ui.layout.LocalChromeNativeBlurEnabled
import com.mi.onextbox.ui.common.LocalSegmentBackdropEnabled
import com.mi.onextbox.ui.common.LocalAppVisualEffects
import com.mi.onextbox.ui.layout.Page
import com.mi.onextbox.ui.screens.FeatureLaunchIcon
import com.mi.onextbox.ui.screens.FeatureLaunchOrigin
import com.mi.onextbox.ui.screens.FeaturePageMode
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.TopAppBarState
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// Page opening, closing and predictive-back motion.
internal const val C17_OPEN_GEOMETRY_STIFFNESS = 420f
internal const val C17_OPEN_GEOMETRY_DAMPING = 1.12f
internal const val C17_OPEN_RADIUS_STIFFNESS = 400f
internal const val C17_OPEN_RADIUS_DAMPING = 1f
internal const val C17_OPEN_WORKSPACE_STIFFNESS = 420f
internal const val C17_OPEN_WORKSPACE_DAMPING = 1f
internal const val C17_OPEN_BLUR_STIFFNESS = 240f
internal const val C17_OPEN_BLUR_DAMPING = 1f

internal const val C17_CLOSE_GEOMETRY_STIFFNESS = 160f
internal const val C17_CLOSE_GEOMETRY_DAMPING = 0.84f
internal const val C17_CLOSE_RADIUS_STIFFNESS = 300f
internal const val C17_CLOSE_RADIUS_DAMPING = 1f
internal const val C17_CLOSE_WORKSPACE_STIFFNESS = 200f
internal const val C17_CLOSE_WORKSPACE_DAMPING = 1f
internal const val C17_CLOSE_BLUR_STIFFNESS = 300f
internal const val C17_CLOSE_BLUR_DAMPING = 1f
// The 0.84 close spring is intentionally under-damped. Keep C17's small
// icon-end overshoot, but cap velocity-amplified rapid-interruption outliers.
internal const val C17_CLOSE_MIN_RENDER_PROGRESS = -0.012f

internal const val C17_SOURCE_SCALE = 0.9f
internal const val C17_SOURCE_BLUR_DP = 18f
internal const val C17_WINDOW_CORNER_DP = 30f
internal const val C17_ICON_CORNER_DP = 16f
internal const val C17_PREDICTIVE_MIN_WINDOW_SCALE = 0.4f
internal const val C17_PREDICTIVE_VERTICAL_MARGIN_DP = 10f
internal const val C17_PREDICTIVE_RESET_STIFFNESS = 400f
internal const val C17_PREDICTIVE_RESET_DAMPING = 1f

internal enum class FeatureLaunchDirection {
    Opening,
    Closing,
}

internal enum class FeatureWindowSurface {
    Icon,
    Task,
    Both,
}

internal data class FeatureLaunchSession(
    val mode: FeaturePageMode,
    val origin: FeatureLaunchOrigin,
    val pressToken: Long? = null,
)

internal data class FeatureLaunchAnimation(
    val session: FeatureLaunchSession,
    val direction: FeatureLaunchDirection,
    val motion: FeatureLaunchMotion,
    val returnOrigin: FeatureLaunchOrigin? = null,
    val interrupted: Boolean = false,
    val preserveWorkspace: Boolean = false,
    val initialVelocity: FeatureLaunchVelocity? = null,
    val initialWorkspaceVelocity: FeatureWorkspaceVelocity? = null,
    val predictiveBackMotion: FeaturePredictiveBackMotion? = null,
)

internal data class FeatureLaunchVelocity(
    val geometry: Float,
    val radius: Float,
)

internal data class FeatureWorkspaceVelocity(
    val scale: Float,
    val alpha: Float,
    val blur: Float,
)

/** Window geometry and corner-radius progress, retained across interrupted transitions. */
internal class FeatureLaunchMotion(
    initialGeometry: Float,
    initialRadius: Float = initialGeometry,
    val alphaDirection: FeatureLaunchDirection,
    val useReturnOrigin: Boolean,
) {
    val geometry = Animatable(initialGeometry)
    val radius = Animatable(initialRadius)

    fun velocitySnapshot(): FeatureLaunchVelocity = FeatureLaunchVelocity(
        geometry = geometry.velocity,
        radius = radius.velocity,
    )
}

/** Gesture transform retained while a window changes animation ownership. */
@Stable
internal class FeaturePredictiveBackMotion {
    val progress = Animatable(0f)
    var startTouchY by mutableFloatStateOf(Float.NaN)
    var touchY by mutableFloatStateOf(Float.NaN)
    var swipeEdge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
    var progressJob: Job? = null

    fun updatePointer(event: BackEventCompat) {
        if (startTouchY.isNaN()) {
            startTouchY = event.touchY
        }
        touchY = event.touchY
        swipeEdge = event.swipeEdge
    }
}

internal data class C17PredictiveBackTransform(
    val scale: Float,
    val translationX: Float,
    val translationY: Float,
)

/** Exact normalized OSpringInterpolator used by C17 Launcher. */
internal fun c17SpringInterpolation(
    input: Float,
    stiffness: Double,
    dampingRatio: Double,
): Float {
    fun origin(value: Double): Double {
        val undampedFrequency = Math.sqrt(stiffness)
        val decay = Math.exp(-dampingRatio * undampedFrequency * value)
        return if (dampingRatio < 1.0) {
            val angularFrequency = Math.sqrt(1.0 - dampingRatio * dampingRatio) *
                undampedFrequency
            val coefficient = dampingRatio * undampedFrequency / angularFrequency
            1.0 - (
                Math.sin(angularFrequency * value) * coefficient +
                    Math.cos(angularFrequency * value)
                ) * decay
        } else if (dampingRatio == 1.0) {
            1.0 - (1.0 + undampedFrequency * value) * decay
        } else {
            val angularFrequency = Math.sqrt(dampingRatio * dampingRatio - 1.0) *
                undampedFrequency
            1.0 - (
                Math.cosh(angularFrequency * value) * angularFrequency +
                    Math.sinh(angularFrequency * value) *
                    (dampingRatio * undampedFrequency)
                ) * (decay / angularFrequency)
        }
    }

    val boundedInput = input.coerceIn(0f, 1f).toDouble()
    val finalValue = origin(1.0)
    return if (abs(finalValue) < 1e-6) {
        boundedInput.toFloat()
    } else {
        (origin(boundedInput) / finalValue).toFloat()
    }
}

internal fun FeaturePredictiveBackMotion.c17Progress(): Float =
    c17SpringInterpolation(
        input = progress.value,
        stiffness = 100.0,
        dampingRatio = 4.0,
    ).coerceIn(0f, 1f)

internal fun FeaturePredictiveBackMotion.c17WindowScale(): Float = lerpFloat(
    1f,
    C17_PREDICTIVE_MIN_WINDOW_SCALE,
    c17Progress(),
)

internal fun FeaturePredictiveBackMotion.toC17Transform(
    viewportBounds: Rect,
    density: Float,
): C17PredictiveBackTransform {
    val progress = c17Progress()
    val scale = c17WindowScale()
    val viewportWidth = viewportBounds.width.coerceAtLeast(1f)
    val viewportHeight = viewportBounds.height.coerceAtLeast(1f)
    val scaledWidth = viewportWidth * scale
    val scaledHeight = viewportHeight * scale
    val targetCenterX = when (swipeEdge) {
        BackEventCompat.EDGE_LEFT -> viewportWidth
        BackEventCompat.EDGE_RIGHT -> 0f
        else -> viewportWidth / 2f
    }
    val centerX = lerpFloat(viewportWidth / 2f, targetCenterX, progress)
    val horizontalOffset = centerX - scaledWidth / 2f

    val touchDelta = if (startTouchY.isNaN() || touchY.isNaN()) {
        0f
    } else {
        touchY - startTouchY
    }
    val verticalInput = (
        abs(touchDelta) / (viewportHeight / 2f).coerceAtLeast(1f)
        ).coerceIn(0f, 1f)
    val verticalProgress = c17SpringInterpolation(
        input = verticalInput,
        stiffness = 50.0,
        dampingRatio = 5.0,
    ).coerceIn(0f, 1f)
    val centeredTop = (viewportHeight - scaledHeight) / 2f
    val verticalMargin = C17_PREDICTIVE_VERTICAL_MARGIN_DP * density
    // The touch delta is gesture-owned and intentionally remains frozen once
    // the finger lifts. Its edge margin must still decay with the predictive
    // progress, otherwise the outer leash reaches scale=1 with a non-zero Y
    // translation and the icon stays displaced until that leash is disposed.
    val animatedVerticalMargin = verticalMargin * progress
    val directionalTop = when {
        touchDelta > 0f ->
            viewportHeight - scaledHeight - animatedVerticalMargin * verticalProgress
        touchDelta < 0f -> animatedVerticalMargin * verticalProgress
        else -> centeredTop
    }
    val verticalOffset = lerpFloat(centeredTop, directionalTop, verticalProgress)

    return C17PredictiveBackTransform(
        scale = scale,
        translationX = horizontalOffset,
        translationY = verticalOffset,
    )
}

internal fun lerpFloat(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction

internal fun FeatureLaunchOrigin.toLiveVisualOrigin(
    pressScale: Float,
    workspaceScale: Float,
    viewportBounds: Rect,
): FeatureLaunchOrigin {
    val boundedPressScale = pressScale.coerceIn(0.85f, 1f)
    val boundedWorkspaceScale = workspaceScale.coerceIn(C17_SOURCE_SCALE, 1f)
    val itemCenterX = hitLeft + hitWidth / 2f
    val itemCenterY = hitTop + hitHeight / 2f
    val pressedLeft = itemCenterX + (restingLeft - itemCenterX) * boundedPressScale
    val pressedTop = itemCenterY + (restingTop - itemCenterY) * boundedPressScale
    val pressedWidth = restingWidth * boundedPressScale
    val pressedHeight = restingHeight * boundedPressScale
    val workspaceCenterX = viewportBounds.left + viewportBounds.width / 2f
    val workspaceCenterY = viewportBounds.top + viewportBounds.height / 2f
    return copy(
        left = workspaceCenterX + (pressedLeft - workspaceCenterX) * boundedWorkspaceScale,
        top = workspaceCenterY + (pressedTop - workspaceCenterY) * boundedWorkspaceScale,
        width = pressedWidth * boundedWorkspaceScale,
        height = pressedHeight * boundedWorkspaceScale,
        pressScale = boundedPressScale,
    )
}

internal fun FeatureLaunchOrigin.toReturnOrigin(
    workspaceScale: Float,
    viewportBounds: Rect,
): FeatureLaunchOrigin {
    val boundedWorkspaceScale = workspaceScale.coerceIn(C17_SOURCE_SCALE, 1f)
    val workspaceCenterX = viewportBounds.left + viewportBounds.width / 2f
    val workspaceCenterY = viewportBounds.top + viewportBounds.height / 2f
    return copy(
        left = workspaceCenterX + (restingLeft - workspaceCenterX) * boundedWorkspaceScale,
        top = workspaceCenterY + (restingTop - workspaceCenterY) * boundedWorkspaceScale,
        width = restingWidth * boundedWorkspaceScale,
        height = restingHeight * boundedWorkspaceScale,
        pressScale = 1f,
    )
}

internal fun c17TaskSurfaceAlpha(
    direction: FeatureLaunchDirection,
    geometryProgress: Float,
): Float {
    val progress = geometryProgress.coerceIn(0f, 1f)
    return when (direction) {
        FeatureLaunchDirection.Opening -> ((progress - 0.2f) / 0.3f).coerceIn(0f, 1f)
        FeatureLaunchDirection.Closing -> ((progress - 0.3f) / 0.3f).coerceIn(0f, 1f)
    }
}

internal fun Modifier.c17LauncherSourceTransform(
    scaleProgress: () -> Float,
    alphaProgress: () -> Float,
    blurProgress: () -> Float,
): Modifier {
    val boundedBlurProgress = blurProgress().coerceIn(0f, 1f)
    val transform = this
        .graphicsLayer {
            val scale = lerpFloat(1f, C17_SOURCE_SCALE, scaleProgress().coerceIn(0f, 1f))
            scaleX = scale
            scaleY = scale
            alpha = 1f - alphaProgress().coerceIn(0f, 1f)
            transformOrigin = TransformOrigin.Center
        }
    return if (boundedBlurProgress > 0f) transform.blur((C17_SOURCE_BLUR_DP * boundedBlurProgress).dp)
    else transform
}

@Composable
internal fun C17StableBaseSceneLayer(
    transformActive: Boolean,
    workspaceScaleProgress: () -> Float,
    workspaceAlphaProgress: () -> Float,
    workspaceBlurProgress: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val animationBlur = LocalAppVisualEffects.current.animationBlur
    val blurProgress: () -> Float = if (animationBlur) workspaceBlurProgress else { { 0f } }
    val sourceModifier = if (transformActive) {
        Modifier.c17LauncherSourceTransform(
            scaleProgress = workspaceScaleProgress,
            alphaProgress = workspaceAlphaProgress,
            blurProgress = blurProgress,
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(sourceModifier),
    ) {
        content()
    }
}

internal data class C17FeatureWindowFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val cornerDp: Float,
    val taskAlpha: Float,
)

/** Identical geometry/alpha math; callers choose the layout or draw phase in which to read it. */
internal fun c17FeatureWindowFrame(
    sourceOrigin: FeatureLaunchOrigin,
    viewportBounds: Rect,
    geometryProgress: Float,
    radiusProgress: Float,
    direction: FeatureLaunchDirection,
    alphaDirection: FeatureLaunchDirection = direction,
): C17FeatureWindowFrame {
    val physicalProgress = if (direction == FeatureLaunchDirection.Closing)
        geometryProgress.coerceAtLeast(C17_CLOSE_MIN_RENDER_PROGRESS) else geometryProgress
    val sourceLeft = sourceOrigin.left - viewportBounds.left
    val sourceTop = sourceOrigin.top - viewportBounds.top
    val sourceWidth = sourceOrigin.width.coerceIn(1f, viewportBounds.width)
    val sourceHeight = sourceOrigin.height.coerceIn(1f, viewportBounds.height)
    val animatedCenterX = lerpFloat(sourceLeft + sourceWidth / 2f, viewportBounds.width / 2f, physicalProgress)
    val animatedTop = lerpFloat(sourceTop, 0f, physicalProgress)
    val animatedWidth = lerpFloat(sourceWidth, viewportBounds.width, physicalProgress).coerceAtLeast(1f)
    val animatedAspectRatio = lerpFloat(sourceHeight / sourceWidth,
        viewportBounds.height / viewportBounds.width, physicalProgress).coerceAtLeast(0.01f)
    return C17FeatureWindowFrame(
        left = animatedCenterX - animatedWidth / 2f,
        top = animatedTop,
        width = animatedWidth,
        height = (animatedWidth * animatedAspectRatio).coerceAtLeast(1f),
        cornerDp = lerpFloat(C17_ICON_CORNER_DP, C17_WINDOW_CORNER_DP, radiusProgress.coerceIn(0f, 1f)),
        taskAlpha = c17TaskSurfaceAlpha(alphaDirection, geometryProgress),
    )
}

@Composable
private fun C17FeatureWindowIcon(session: FeatureLaunchSession, frame: State<C17FeatureWindowFrame>) {
    val rect = frame.value
    val density = LocalDensity.current
    val size = with(density) { rect.width.toDp() }
    val height = with(density) { max(rect.height, rect.width).toDp() }
    // Keep the original extended icon painter, fixed halo and top-aligned plate.
    FeatureLaunchIcon(
        pageMode = session.mode, visualSize = size, surfaceHeight = height,
        modifier = Modifier.offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(size, height).graphicsLayer {
                alpha = if (rect.taskAlpha < 1f) 1f else 0f
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
    )
}

@Composable
internal fun C17FeatureWindowLayer(
    session: FeatureLaunchSession,
    direction: FeatureLaunchDirection,
    motion: FeatureLaunchMotion?,
    predictiveBackMotion: FeaturePredictiveBackMotion? = null,
    returnOrigin: (() -> FeatureLaunchOrigin?)? = null,
    viewportBounds: Rect,
    surface: FeatureWindowSurface = FeatureWindowSurface.Both,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (viewportBounds.width <= 0f || viewportBounds.height <= 0f) return
    val density = LocalDensity.current
    val frame = remember(session, direction, motion, returnOrigin, viewportBounds) {
        derivedStateOf {
            val origin = returnOrigin?.invoke() ?: if (motion?.useReturnOrigin == true) {
                session.origin.toReturnOrigin(workspaceScale = 1f, viewportBounds = viewportBounds)
            } else session.origin
            c17FeatureWindowFrame(origin, viewportBounds, motion?.geometry?.value ?: 1f,
                motion?.radius?.value ?: 1f, direction, motion?.alphaDirection ?: direction)
        }
    }
    val predictiveModifier = if (predictiveBackMotion != null) {
        Modifier.graphicsLayer {
            val transform = predictiveBackMotion.toC17Transform(viewportBounds, density.density)
            scaleX = transform.scale
            scaleY = transform.scale
            translationX = transform.translationX
            translationY = transform.translationY
            transformOrigin = TransformOrigin(0f, 0f)
            shape = RoundedCornerShape(C17_WINDOW_CORNER_DP.dp)
            clip = true
        }
    } else Modifier
    Box(modifier.fillMaxSize().then(predictiveModifier)) {
        if (surface != FeatureWindowSurface.Task) C17FeatureWindowIcon(session, frame)
        if (surface != FeatureWindowSurface.Icon) Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val rect = frame.value
                    // Use the same Dp round-trip / parent constraint rules as offset + size.
                    val width = constraints.constrainWidth(with(density) { rect.width.toDp().roundToPx() })
                    val height = constraints.constrainHeight(with(density) { rect.height.toDp().roundToPx() })
                    val child = measurable.measure(Constraints.fixed(width, height))
                    layout(width, height) {
                        child.placeRelativeWithLayer(rect.left.roundToInt(), rect.top.roundToInt())
                    }
                }
                .graphicsLayer {
                    val rect = frame.value
                    alpha = rect.taskAlpha
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .then(
                    if (direction == FeatureLaunchDirection.Closing) {
                        Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        }
                    } else Modifier,
                )
                .graphicsLayer {
                    shape = RoundedCornerShape(frame.value.cornerDp.dp)
                    clip = true
                }
                .background(COUITheme.colorScheme.surface),
        ) {
            Layout(
                content = {
                    Box(Modifier.fillMaxSize()) {
                        val stableSegmentBackdrop = LocalSegmentBackdropEnabled.current &&
                            motion == null && predictiveBackMotion == null
                        CompositionLocalProvider(
                            LocalChromeNativeBlurEnabled provides false,
                            LocalSegmentBackdropEnabled provides stableSegmentBackdrop,
                        ) { content() }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) { measurables, constraints ->
                val child = measurables.single().measure(Constraints.fixed(
                    viewportBounds.width.roundToInt().coerceAtLeast(1),
                    viewportBounds.height.roundToInt().coerceAtLeast(1),
                ))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    child.placeWithLayer(0, 0) {
                        val scale = frame.value.width / viewportBounds.width
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
            }
        }
    }
}

internal suspend fun animateFeatureLaunchMotion(
    motion: FeatureLaunchMotion,
    targetValue: Float,
    initialVelocity: FeatureLaunchVelocity? = null,
    geometryStiffness: Float,
    geometryDamping: Float,
    radiusStiffness: Float,
    radiusDamping: Float,
) = coroutineScope {
    val radiusJob = launch {
        motion.radius.animateTo(
            targetValue = targetValue,
            initialVelocity = initialVelocity?.radius ?: motion.radius.velocity,
            animationSpec = spring(
                dampingRatio = radiusDamping,
                stiffness = radiusStiffness,
                visibilityThreshold = 0.001f,
            ),
        )
    }
    try {
        motion.geometry.animateTo(
            targetValue = targetValue,
            initialVelocity = initialVelocity?.geometry ?: motion.geometry.velocity,
            animationSpec = spring(
                dampingRatio = geometryDamping,
                stiffness = geometryStiffness,
                visibilityThreshold = 0.0005f,
            ),
        )
    } finally {
        radiusJob.cancel()
    }
}

@Composable
internal fun C17ClosingFeatureWindowLayer(
    animation: FeatureLaunchAnimation,
    viewportBounds: Rect,
    workspaceScale: () -> Float,
    gridPosition: Offset?,
    onFinished: suspend (FeatureLaunchAnimation) -> Unit,
    surface: FeatureWindowSurface = FeatureWindowSurface.Both,
    content: @Composable () -> Unit,
) {
    val latestOnFinished by rememberUpdatedState(onFinished)
    if (surface != FeatureWindowSurface.Task) LaunchedEffect(animation, viewportBounds) {
        if (viewportBounds.width <= 0f || viewportBounds.height <= 0f) {
            return@LaunchedEffect
        }
        animateFeatureLaunchMotion(
            motion = animation.motion,
            targetValue = 0f,
            initialVelocity = animation.initialVelocity,
            geometryStiffness = C17_CLOSE_GEOMETRY_STIFFNESS,
            geometryDamping = C17_CLOSE_GEOMETRY_DAMPING,
            radiusStiffness = C17_CLOSE_RADIUS_STIFFNESS,
            radiusDamping = C17_CLOSE_RADIUS_DAMPING,
        )
        latestOnFinished(animation)
    }
    C17FeatureWindowLayer(
        session = animation.session,
        direction = FeatureLaunchDirection.Closing,
        motion = animation.motion,
        predictiveBackMotion = animation.predictiveBackMotion,
        // Keep the return target aligned with the moving grid.
        returnOrigin = {
            animation.returnOrigin?.atGridPosition(gridPosition)?.toReturnOrigin(
                workspaceScale = workspaceScale(),
                viewportBounds = viewportBounds,
            )
        },
        viewportBounds = viewportBounds,
        surface = surface,
        content = content,
    )
}

@Composable
internal fun FeatureLaunchQueueHitLayer(
    origins: Map<FeaturePageMode, FeatureLaunchOrigin>,
    viewportBounds: Rect,
    gridPosition: Offset?,
    scrollState: ScrollState,
    topAppBarState: TopAppBarState,
    onPressStart: (FeaturePageMode) -> Long,
    onPressEnd: (FeaturePageMode, Long) -> Unit,
    onOpen: (FeaturePageMode, FeatureLaunchOrigin?, Long) -> Unit,
    excludedBounds: Rect? = null,
    modifier: Modifier = Modifier,
) {
    if (viewportBounds.width <= 0f || viewportBounds.height <= 0f) return
    val density = LocalDensity.current
    val latestOnPressStart by rememberUpdatedState(onPressStart)
    val latestOnPressEnd by rememberUpdatedState(onPressEnd)
    val latestOnOpen by rememberUpdatedState(onOpen)
    val scrollBehavior = COUIScrollBehavior(state = topAppBarState)
    // This overlay is a sibling of the page, so its scroll events must explicitly
    // drive the same app bar while the closing window still owns hit testing.
    Box(modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
        origins.forEach { (mode, cachedOrigin) ->
            val origin = cachedOrigin.atGridPosition(gridPosition)
            val latestOrigin by rememberUpdatedState(origin)
            val restingHitWidth = origin.hitWidth.coerceAtLeast(origin.restingWidth)
            val restingHitHeight = origin.hitHeight.coerceAtLeast(origin.restingHeight)
            val hitBounds = Rect(origin.hitLeft, origin.hitTop,
                origin.hitLeft + restingHitWidth, origin.hitTop + restingHitHeight)
            featureLaunchHitRegions(hitBounds, viewportBounds, excludedBounds).forEachIndexed { regionIndex, region ->
                key(mode, regionIndex) {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (region.left - viewportBounds.left).roundToInt(),
                                    y = (region.top - viewportBounds.top).roundToInt(),
                                )
                            }
                            .size(
                                width = with(density) { region.width.toDp() },
                                height = with(density) { region.height.toDp() },
                            )
                            .scrollable(
                                state = scrollState,
                                orientation = Orientation.Vertical,
                                reverseDirection = true,
                            )
                            // Hit targets follow scrolling, not workspace press scaling.
                            .pointerInput(mode, regionIndex) {
                                detectTapGestures(
                                    onPress = {
                                        val pressToken = latestOnPressStart(mode)
                                        try {
                                            if (tryAwaitRelease()) {
                                                latestOnOpen(mode, latestOrigin, pressToken)
                                            }
                                        } finally {
                                            latestOnPressEnd(mode, pressToken)
                                        }
                                    },
                                )
                            },
                    )
                }
            }
        }
    }
}
