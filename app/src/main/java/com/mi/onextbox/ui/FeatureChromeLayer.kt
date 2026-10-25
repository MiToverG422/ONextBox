package com.mi.onextbox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

internal val LocalBottomBarContentOpacity = staticCompositionLocalOf<() -> Float> { { 1f } }

@Composable
internal fun C17StableChromeSceneLayer(
    transformActive: Boolean,
    workspaceScaleProgress: Float,
    workspaceAlphaProgress: Float,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
    routeVisibility: Float = 1f,
    content: @Composable () -> Unit,
) {
    val latestContentOpacity by rememberUpdatedState(
        (if (transformActive) 1f - workspaceAlphaProgress.coerceIn(0f, 1f) else 1f) *
            routeVisibility.coerceIn(0f, 1f),
    )
    val contentOpacity = remember { { latestContentOpacity } }
    CompositionLocalProvider(LocalBottomBarContentOpacity provides contentOpacity) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = if (transformActive) {
                        lerpFloat(1f, C17_SOURCE_SCALE, workspaceScaleProgress.coerceIn(0f, 1f))
                    } else 1f
                    scaleX = scale
                    scaleY = scale
                    alpha = if (hidden) 0f else 1f
                    translationY = if (hidden) size.height else 0f
                    transformOrigin = TransformOrigin.Center
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
        ) {
            content()
        }
    }
}
