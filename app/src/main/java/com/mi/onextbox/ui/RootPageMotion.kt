package com.mi.onextbox.ui

import androidx.compose.animation.core.spring

// One physical curve for entry, return, interruption and gesture release.
internal val RootPageSpring = spring<Float>(
    dampingRatio = 0.95f,
    stiffness = 300f,
    visibilityThreshold = 0.001f,
)

internal const val RootCoveredPageOffset = -0.25f

internal fun rootLayerFollowsGesture(key: Any, current: Any, previous: Any?, dragging: Boolean): Boolean =
    dragging && (key == current || key == previous)

internal fun rootGesturePageOffset(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress.coerceIn(0f, 1f)
