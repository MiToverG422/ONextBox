package com.mi.onextbox.ui.common

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.mi.onextbox.ui.layout.LocalChromeNativeBlurEnabled
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

/** SearchBarBackgroundView's material/spotlight and passive COUIViewTouchAnimator path. */
@Composable
internal fun ColorOsSearchSurface(
    modifier: Modifier,
    background: Color,
    focused: Boolean,
    editorEmpty: Boolean = false,
    trailingActionWidth: androidx.compose.ui.unit.Dp = 0.dp,
    revealProgress: () -> Float = { 1f },
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = COUITheme.colorScheme.background.luminance() < .5f
    val nativeBlurEnabled = LocalChromeNativeBlurEnabled.current
    val fallbackColor = ColorOsToolbarGlass.fallbackColor(background, COUITheme.colorScheme.surface)
    val motion = remember { SearchMotion() }
    val editing = rememberUpdatedState(focused)
    val emptyEditor = rememberUpdatedState(editorEmpty)
    val trailingWidth = rememberUpdatedState(trailingActionWidth)
    val direction = rememberUpdatedState(LocalLayoutDirection.current)
    val host = LocalView.current
    val redraw = remember { mutableIntStateOf(0) }
    val light = remember(host) {
        ColorOsSpotlightRenderer(host, ColorOsSpotlightRenderer.Style.SearchBar, dark) { redraw.intValue++ }
    }
    SideEffect { light.updateAppearance(darkTheme = dark) }
    DisposableEffect(light) {
        onDispose { motion.cancel(); light.cancel() }
    }
    val wholeCapsuleMotion = Modifier.graphicsLayer {
        scaleX = motion.press.value * motion.xScale.value
        scaleY = motion.press.value * motion.yScale.value
        translationX = motion.x.value
        translationY = motion.y.value
    }
    Box(modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown(false, PointerEventPass.Initial)
                val origin = down.position
                var last = origin
                var emptyDragEligible = emptyEditor.value
                var slopChecked = false
                var decorativeEmptyDrag = false
                var verticalDrag = false
                val verticalDragEligible = if (direction.value == LayoutDirection.Rtl) {
                    origin.x >= trailingWidth.value.toPx()
                } else {
                    origin.x < size.width - trailingWidth.value.toPx()
                }
                fun lightPosition(x: Float, y: Float): Pair<Float, Float> = colorOsSearchLocalPosition(
                    x, y, size.width.toFloat(), size.height.toFloat(),
                    motion.press.value * motion.xScale.value, motion.press.value * motion.yScale.value,
                    motion.x.value, motion.y.value,
                )
                val elongated = size.width.toFloat() / size.height.coerceAtLeast(1) > 2f
                motion.press.to(colorOsSearchPressScale(size.width / density, size.height / density), 0f, if (elongated) .3f else .25f)
                lightPosition(origin.x, origin.y).let { light.onDown(it.first, it.second) }
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        last = change.position
                        if (!change.pressed) {
                            if (verticalDrag) change.consume()
                            break
                        }
                        val singlePointer = event.changes.none {
                            it.id != down.id && (it.pressed || it.previousPressed)
                        }
                        if (!singlePointer) {
                            verticalDrag = false
                            break
                        }
                        if (!emptyEditor.value) {
                            emptyDragEligible = false
                            if (decorativeEmptyDrag) motion.snapToRest()
                            decorativeEmptyDrag = false
                        }
                        val dx = last.x - origin.x
                        val dy = last.y - origin.y
                        lightPosition(last.x, last.y).let { light.onMove(it.first, it.second) }
                        val pastSlop = abs(dx) > 5f || abs(dy) > 5f
                        if (pastSlop && !slopChecked) {
                            slopChecked = true
                            verticalDrag = verticalDragEligible && abs(dy) > abs(dx) &&
                                (emptyEditor.value || change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis)
                            decorativeEmptyDrag = colorOsSearchEmptyDragAllowed(
                                editorEmpty = emptyDragEligible,
                                singlePointer = singlePointer,
                                horizontal = abs(dx) > abs(dy),
                                elapsedMillis = change.uptimeMillis - down.uptimeMillis,
                                longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis,
                            )
                        }
                        if (verticalDrag) change.consume()
                        val finalChange = awaitPointerEvent(PointerEventPass.Final).changes
                            .firstOrNull { it.id == down.id } ?: break
                        if (!verticalDrag && !colorOsSearchMotionAllowed(
                            editing.value, pastSlop, finalChange.isConsumed, decorativeEmptyDrag,
                        )) continue
                        val deform = colorOsSearchDeformation(dx, dy, size.width.toFloat(), size.height.toFloat(), density)
                        motion.x.to(colorOsSearchRubber(dx, .05f, 28f * density), .15f, .15f)
                        motion.y.to(colorOsSearchRubber(dy, .05f, 28f * density), .15f, .15f)
                        motion.xScale.to(deform.first, .15f, .15f)
                        motion.yScale.to(deform.second, .15f, .15f)
                    }
                } finally {
                    lightPosition(last.x, last.y).let { light.onUp(it.first, it.second) }
                    motion.press.to(1f, if (elongated) .5f else .6f, .5f)
                    motion.x.to(0f, .65f, .45f)
                    motion.y.to(0f, .65f, .45f)
                    motion.xScale.to(1f, .65f, .45f)
                    motion.yScale.to(1f, .65f, .45f)
                }
            }
        }
    }) {
        // One transform owns the material, icons, editor and clear button. Focus/layout
        // stays stable; only a render transform changes while the finger drags.
        Box(Modifier.matchParentSize().then(wholeCapsuleMotion)) {
            AndroidView(
                factory = {
                    ColorOsTopBarMaterialView(
                        it, dark, fallbackColor.toArgb(), searchCapsule = true,
                        nativeBlurEnabled = nativeBlurEnabled,
                    )
                },
                update = {
                    it.updateAppearance(dark, fallbackColor.toArgb(), nativeBlurEnabled)
                    it.updateSearchProgress(1f)
                    val progress = revealProgress().coerceIn(0f, 1f)
                    it.alpha = if (nativeBlurEnabled) (progress / .2f).coerceAtMost(1f) else progress
                },
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.matchParentSize().graphicsLayer {
                val progress = revealProgress().coerceIn(0f, 1f)
                alpha = if (nativeBlurEnabled) (progress / .2f).coerceAtMost(1f) * progress else progress
                val radius = (1f - progress) * 30f
                renderEffect = if (nativeBlurEnabled && radius > .01f) {
                    android.graphics.RenderEffect.createBlurEffect(
                        radius, radius, android.graphics.Shader.TileMode.DECAL,
                    ).asComposeRenderEffect()
                } else null
            }) {
                content()
            }
            // A draw-only overlay cannot claim touch input like the old AndroidView overlay.
            // It composites the native C17 shader after the translucent material and content.
            Canvas(Modifier.matchParentSize().clip(RoundedCornerShape(50)).onSizeChanged {
                light.onSizeChanged(it.width, it.height)
            }) {
                redraw.intValue // Subscribe in draw, not composition: don't restart the editor.
                if (revealProgress() > .99f) light.draw(drawContext.canvas.nativeCanvas)
            }
        }
    }
}

// Editing taps need a stable handle anchor; unclaimed capsule drags still get rubber motion.
internal fun colorOsSearchMotionAllowed(
    editorFocused: Boolean,
    pastSlop: Boolean,
    consumed: Boolean,
    decorativeEmptyDrag: Boolean = false,
): Boolean = (!consumed || decorativeEmptyDrag) && (!editorFocused || pastSlop)

internal fun colorOsSearchEmptyDragAllowed(
    editorEmpty: Boolean,
    singlePointer: Boolean,
    horizontal: Boolean,
    elapsedMillis: Long,
    longPressTimeoutMillis: Long,
): Boolean = editorEmpty && singlePointer && horizontal &&
    elapsedMillis >= 0L && elapsedMillis < longPressTimeoutMillis

internal fun colorOsSearchLocalPosition(
    x: Float, y: Float, width: Float, height: Float,
    scaleX: Float, scaleY: Float, translationX: Float, translationY: Float,
): Pair<Float, Float> =
    (width / 2f + (x - width / 2f - translationX) / scaleX.coerceAtLeast(.001f)) to
        (height / 2f + (y - height / 2f - translationY) / scaleY.coerceAtLeast(.001f))

private class SearchMotion {
    val press = SearchSpring(1f)
    val xScale = SearchSpring(1f)
    val yScale = SearchSpring(1f)
    val x = SearchSpring(0f)
    val y = SearchSpring(0f)
    fun cancel() { listOf(press, xScale, yScale, x, y).forEach { it.cancel() } }
    fun snapToRest() {
        press.snapTo(1f)
        xScale.snapTo(1f)
        yScale.snapTo(1f)
        x.snapTo(0f)
        y.snapTo(0f)
    }
}

private class SearchSpring(initial: Float) {
    var value by mutableFloatStateOf(initial)
        private set
    private val holder = FloatValueHolder(initial)
    private val animation = SpringAnimation(holder).apply {
        minimumVisibleChange = .0001f
        spring = SpringForce(initial)
        addUpdateListener { _, position, _ -> value = position }
    }
    fun to(target: Float, bounce: Float, response: Float) {
        animation.spring.dampingRatio = 1f - bounce
        animation.spring.stiffness = (2f * Math.PI.toFloat() / response).pow(2)
        animation.animateToFinalPosition(target)
    }
    fun cancel() = animation.cancel()
    fun snapTo(target: Float) {
        animation.cancel()
        holder.value = target
        value = target
    }
}

// C17 TouchMotionRubberBand and default OliveLink tier parameters.
internal fun colorOsSearchRubber(offset: Float, ratio: Float, limit: Float): Float =
    if (limit <= 0f) 0f else (1f - 1f / (ratio * abs(offset) / limit + 1f)) * limit * sign(offset)

// PressCalc's area/aspect blend; cubic(0,0,0,1) has x=t³, y=3t²-2t³.
internal fun colorOsSearchPressScale(widthDp: Float, heightDp: Float): Float {
    if (widthDp <= 0f || heightDp <= 0f) return 1f
    fun bezier(input: Float): Float { val t = input.coerceIn(0f, 1f).pow(1f / 3f); return 3f * t * t - 2f * t * t * t }
    val areaScale = .85f + .12f * bezier((widthDp * heightDp - 400f) / (22500f - 400f))
    val aspect = max(widthDp / heightDp, heightDp / widthDp)
    return if (aspect <= 2f) areaScale else (areaScale + .85f + .15f * bezier((aspect - 2f) / 13f)) / 2f
}

internal fun colorOsSearchDeformation(dx: Float, dy: Float, width: Float, height: Float, density: Float): Pair<Float, Float> {
    if (width <= 0f || height <= 0f || density <= 0f || abs(dx) + abs(dy) <= 5f * density) return 1f to 1f
    val area = width * height
    val attenuation = if (area <= 38416f) 0f else
        (.35f * ln(area / 38416f) + .015f * (max(width, height) / min(width, height) - 1f)).coerceAtMost(1f)
    val maxScale = 1.03f + .17f * (1f - attenuation)
    val x = 1f + colorOsSearchRubber(abs(dx), .55f, 150f * density) / (150f * density) * (maxScale - 1f)
    val y = 1f + colorOsSearchRubber(abs(dy), .55f, 150f * density) / (150f * density) * (maxScale - 1f)
    val weight = abs(dx) / (abs(dx) + abs(dy))
    val scale = exp(ln(x) * weight - ln(y) * (1f - weight))
    return scale to 1f / scale
}
