package com.mi.onextbox.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.mi.onextbox.ui.screens.FeatureLaunchOrigin

internal fun unscaledFeatureGridPosition(
    position: Offset,
    viewport: Rect,
    workspaceScale: Float,
): Offset {
    val scale = workspaceScale.coerceIn(C17_SOURCE_SCALE, 1f)
    return viewport.center + (position - viewport.center) / scale
}

// The grid moves with scrolling and the collapsing toolbar.
internal fun FeatureLaunchOrigin.atGridPosition(position: Offset?): FeatureLaunchOrigin {
    if (position == null) return this
    val deltaX = position.x - gridLeft
    val deltaY = position.y - gridTop
    return copy(
        left = left + deltaX,
        top = top + deltaY,
        restingLeft = restingLeft + deltaX,
        restingTop = restingTop + deltaY,
        hitLeft = hitLeft + deltaX,
        hitTop = hitTop + deltaY,
        gridLeft = position.x,
        gridTop = position.y,
    )
}
