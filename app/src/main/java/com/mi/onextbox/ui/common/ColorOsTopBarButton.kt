package com.mi.onextbox.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

/** ColorOS 17 toolbar button: one 40dp circle shared by material, state and motion. */
@Composable
fun ColorOsTopBarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = remember { Animatable(1f) }
    val pressMask = remember { Animatable(0f) }
    val spotlightViewHolder = remember { TopBarSpotlightViewHolder() }

    val darkTheme = COUITheme.colorScheme.background.luminance() < 0.5f
    val fallbackColor = COUITheme.colorScheme.surfaceContainer
    val maskColor = if (darkTheme) {
        androidx.compose.ui.graphics.Color(0x33FFFFFF)
    } else {
        androidx.compose.ui.graphics.Color(0x1F000000)
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    launch {
                        pressScale.animateTo(
                            targetValue = 0.88858957f,
                            animationSpec = spring(
                                dampingRatio = 1f,
                                stiffness = 631.655f,
                            ),
                        )
                    }
                    launch {
                        pressMask.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = 1f,
                                stiffness = 438.649f,
                            ),
                        )
                    }
                }

                is PressInteraction.Release,
                is PressInteraction.Cancel,
                -> {
                    launch {
                        pressScale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = 0.4f,
                                stiffness = 157.914f,
                            ),
                        )
                    }
                    launch {
                        pressMask.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = 1f,
                                stiffness = 438.649f,
                            ),
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            spotlightViewHolder.view?.cancelSpotlight()
            spotlightViewHolder.view = null
        }
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = pressScale.value
                scaleY = pressScale.value
                clip = false
            }
            .pointerInput(Unit) {
                fun spotlightLocalPosition(position: Offset): Offset {
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val scale = pressScale.value.coerceAtLeast(0.001f)
                    return Offset(
                        x = centerX + (position.x - centerX) / scale,
                        y = centerY + (position.y - centerY) / scale,
                    )
                }
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        var lastPosition = down.position
                        spotlightLocalPosition(lastPosition).let { position ->
                            spotlightViewHolder.view?.spotlightDown(position.x, position.y)
                        }
                        try {
                            var tracking = true
                            while (tracking) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change != null) {
                                    lastPosition = change.position
                                }
                                if (change == null || !change.pressed) {
                                    tracking = false
                                } else {
                                    spotlightLocalPosition(change.position).let { position ->
                                        spotlightViewHolder.view?.spotlightMove(position.x, position.y)
                                    }
                                }
                            }
                        } finally {
                            spotlightLocalPosition(lastPosition).let { position ->
                                spotlightViewHolder.view?.spotlightUp(position.x, position.y)
                            }
                        }
                    }
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { context ->
                ColorOsTopBarMaterialView(
                    context = context,
                    darkTheme = darkTheme,
                    fallbackColor = fallbackColor.toArgb(),
                )
            },
            update = { view ->
                view.updateAppearance(darkTheme, fallbackColor.toArgb())
            },
            modifier = Modifier.fillMaxSize(),
        )

        // COUIMaskEffectDrawable is a background layer in C17, so the icon remains crisp.
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (pressMask.value > 0.001f) {
                drawCircle(
                    color = maskColor.copy(alpha = maskColor.alpha * pressMask.value),
                    radius = size.minDimension / 2f,
                )
            }
        }

        content()

        // C17 uses ViewOverlay for TOOLBAR_BUTTON / MENU_OVERFLOW_BUTTON spotlights, so the
        // light is composited after both the pressed-state mask and the icon.
        AndroidView(
            factory = { context ->
                ColorOsTopBarSpotlightView(
                    context = context,
                    darkTheme = darkTheme,
                ).also { spotlightViewHolder.view = it }
            },
            update = { view ->
                view.updateAppearance(darkTheme)
                spotlightViewHolder.view = view
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private class TopBarSpotlightViewHolder {
    var view: ColorOsTopBarSpotlightView? = null
}
