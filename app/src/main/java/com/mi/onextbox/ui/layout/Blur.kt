package com.mi.onextbox.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.mi.onextbox.ui.common.ColorOsAppBarBlurView
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.ProgressiveBlur
import io.github.suqi8.coui.kmp.blur.isRuntimeShaderSupported
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.blur.progressiveTextureBlur
import io.github.suqi8.coui.kmp.theme.COUITheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Native background effects sample the window, so a moving task uses its own Compose layer. */
internal val LocalChromeNativeBlurEnabled = compositionLocalOf { true }

@Composable
fun rememberChromeBlurBackdrop(enabled: Boolean): LayerBackdrop? {
    if (!isRuntimeShaderSupported()) return null
    val surfaceColor = COUITheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    return if (enabled) backdrop else null
}

@Composable
fun BlurredChromeBar(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    effectAlpha: Float = 1f,
    effectExtension: Dp = 0.dp,
    effectTopOffsetPx: () -> Float = { 0f },
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    // ColorOS 17 uses the gradient AppBar path: 30 px at the top, 0 px at the lower edge,
    // with a speed/curve of 3. The Compose blur API accepts dp.
    val nativeBlurRadiusDp = 30f / density.density
    val surface = COUITheme.colorScheme.surface
    val isDark = surface.luminance() < 0.5f
    val nativeToolbarBase = if (isDark) Color.Black else Color(0xFFF0F1F2)
    val tintBrush = if (isDark) {
        Brush.verticalGradient(
            0f to nativeToolbarBase.copy(alpha = 0.4f),
            1f to nativeToolbarBase.copy(alpha = 0f),
        )
    } else {
        Brush.verticalGradient(
            0f to nativeToolbarBase,
            0.6f to nativeToolbarBase.copy(alpha = 0.7f),
            1f to nativeToolbarBase.copy(alpha = 0f),
        )
    }
    Box(
        modifier = modifier
            .background(backgroundColor),
    ) {
        if (backdrop != null && effectAlpha > 0f) {
            val effectModifier = Modifier
                .matchParentSize()
                .appBarEffectBounds(effectExtension, effectTopOffsetPx)
            if (LocalChromeNativeBlurEnabled.current && ColorOsAppBarBlurView.isSupported()) {
                // On ColorOS, use the same compositor background effect as Settings instead of
                // blurring a captured Compose texture. This is what produces the native sampling
                // across the status bar and the characteristic progressive lower edge.
                AndroidView(
                    factory = { context ->
                        ColorOsAppBarBlurView(
                            context = context,
                            darkTheme = isDark,
                            baseColor = surface.toArgb(),
                        ).apply {
                            updateAppearance(isDark, surface.toArgb(), effectAlpha)
                        }
                    },
                    update = { view ->
                        view.updateAppearance(isDark, surface.toArgb(), effectAlpha)
                    },
                    modifier = effectModifier,
                )
            } else {
                Box(
                    modifier = effectModifier
                        .graphicsLayer { alpha = effectAlpha.coerceIn(0f, 1f) }
                        .progressiveTextureBlur(
                            backdrop = backdrop,
                            shape = RectangleShape,
                            blurRadius = nativeBlurRadiusDp,
                            noiseCoefficient = 0f,
                            gradient = ProgressiveBlur(
                                angle = 90f,
                                startFraction = 0f,
                                endFraction = 1f,
                                curve = 3f,
                            ),
                        )
                        .drawWithContent {
                            drawContent()
                            drawRect(tintBrush)
                        },
                )
            }
        }
        content()
    }
}

/**
 * AppBarBlurOverlayBehavior follows the native app bar's full, offset bounds. The Compose
 * bar instead reduces its measured height while collapsing, so reconstruct the full bounds
 * here. Keeping the gradient's origin at the negative app-bar offset avoids squeezing it
 * into the remaining toolbar/status-bar area. The visible bottom still ends at bar + extra.
 */
private fun Modifier.appBarEffectBounds(
    extension: Dp,
    topOffsetPx: () -> Float,
): Modifier = layout { measurable, constraints ->
    val extensionPx = extension.roundToPx()
    val baseHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
    val offsetPx = topOffsetPx().roundToInt().coerceAtMost(0)
    val extendedHeight = (baseHeight - offsetPx + extensionPx).coerceAtLeast(0)
    val placeable = measurable.measure(
        constraints.copy(
            minHeight = extendedHeight,
            maxHeight = extendedHeight,
        ),
    )
    layout(
        width = constraints.maxWidth,
        height = baseHeight,
    ) {
        placeable.placeRelative(0, offsetPx)
    }
}
