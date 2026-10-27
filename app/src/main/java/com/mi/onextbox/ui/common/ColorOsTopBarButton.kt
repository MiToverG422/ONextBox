package com.mi.onextbox.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mi.onextbox.ui.layout.LocalChromeNativeBlurEnabled
import io.github.suqi8.coui.kmp.theme.COUITheme

/** ColorOS 17 toolbar button: one 40dp circle shared by material, state and motion. */
@Composable
fun ColorOsTopBarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current.density
    val motion = remember(density) { ColorOsTopBarMotion(density) }
    val darkTheme = COUITheme.colorScheme.background.luminance() < 0.5f
    val fallbackColor = ColorOsToolbarGlass.fallbackColor(
        COUITheme.colorScheme.surfaceContainer,
        COUITheme.colorScheme.surface,
    )
    val nativeBlurEnabled = LocalChromeNativeBlurEnabled.current
    val maskColor = if (darkTheme) {
        androidx.compose.ui.graphics.Color(0x33FFFFFF)
    } else {
        androidx.compose.ui.graphics.Color(0x1F000000)
    }
    val host = LocalView.current
    val redraw = remember { mutableIntStateOf(0) }
    val spotlight = remember(host) {
        ColorOsSpotlightRenderer(host, ColorOsSpotlightRenderer.Style.TopBarButton, darkTheme) {
            redraw.intValue++
        }
    }
    SideEffect { spotlight.updateAppearance(darkTheme = darkTheme) }
    DisposableEffect(motion, spotlight) {
        onDispose {
            motion.dispose()
            spotlight.cancel()
        }
    }

    // Input stays in a fixed hit box; only the visual subtree follows the finger.
    Box(
        modifier = modifier
            .size(40.dp)
            .pointerInput(motion, spotlight) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var lastPosition = down.position
                    var dragged = false
                    var releasedNormally = false
                    motion.begin()
                    motion.localPosition(lastPosition, size).let { spotlight.onDown(it.x, it.y) }
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            lastPosition = change.position
                            tracker.addPosition(change.uptimeMillis, lastPosition)
                            val singlePointer = event.changes.none {
                                it.id != down.id && (it.pressed || it.previousPressed)
                            }
                            if (!singlePointer) {
                                event.changes.forEach { it.consume() }
                                break
                            }
                            if (!change.pressed) {
                                releasedNormally = true
                                if (dragged) change.consume()
                                break
                            }
                            val delta = lastPosition - down.position
                            if (delta.getDistance() > viewConfiguration.touchSlop) dragged = true
                            if (dragged) change.consume()
                            motion.move(delta, size)
                            motion.localPosition(lastPosition, size).let { spotlight.onMove(it.x, it.y) }
                            if (!dragged) {
                                val finalChange = awaitPointerEvent(PointerEventPass.Final).changes
                                    .firstOrNull { it.id == down.id }
                                if (finalChange == null || finalChange.isConsumed) break
                            }
                        }
                    } finally {
                        val velocity = if (releasedNormally) tracker.calculateVelocity() else null
                        motion.release(size, velocity?.let { Offset(it.x, it.y) } ?: Offset.Zero)
                        motion.localPosition(lastPosition, size).let { spotlight.onUp(it.x, it.y) }
                    }
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = motion.scaleX
                scaleY = motion.scaleY
                translationX = motion.translationX
                translationY = motion.translationY
                clip = false
            },
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { context ->
                    ColorOsTopBarMaterialView(
                        context = context,
                        darkTheme = darkTheme,
                        fallbackColor = fallbackColor.toArgb(),
                        nativeBlurEnabled = nativeBlurEnabled,
                    )
                },
                update = { view ->
                    view.updateAppearance(darkTheme, fallbackColor.toArgb(), nativeBlurEnabled)
                },
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (motion.maskProgress > 0.001f) {
                    drawCircle(
                        color = maskColor.copy(alpha = maskColor.alpha * motion.maskProgress),
                        radius = size.minDimension / 2f,
                    )
                }
            }
            content()
            // Draw-only overlay preserves the spotlight without adding an Android touch target.
            Canvas(Modifier.fillMaxSize().clip(CircleShape).onSizeChanged {
                spotlight.onSizeChanged(it.width, it.height)
            }) {
                redraw.intValue
                spotlight.draw(drawContext.canvas.nativeCanvas)
            }
        }
    }
}
