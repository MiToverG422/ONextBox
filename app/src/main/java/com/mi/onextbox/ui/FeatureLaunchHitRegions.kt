package com.mi.onextbox.ui

import androidx.compose.ui.geometry.Rect

internal fun featureLaunchHitRegions(hitBounds: Rect, viewport: Rect, excluded: Rect?): List<Rect> {
    if (!hitBounds.hasPositiveFiniteArea() || !viewport.hasPositiveFiniteArea()) return emptyList()
    val visible = hitBounds.intersect(viewport)
    if (!visible.hasPositiveFiniteArea()) return emptyList()
    if (excluded == null || !excluded.hasPositiveFiniteArea()) return listOf(visible)
    val cut = visible.intersect(excluded)
    if (!cut.hasPositiveFiniteArea()) return listOf(visible)
    return listOf(
        Rect(visible.left, visible.top, visible.right, cut.top),
        Rect(visible.left, cut.bottom, visible.right, visible.bottom),
        Rect(visible.left, cut.top, cut.left, cut.bottom),
        Rect(cut.right, cut.top, visible.right, cut.bottom),
    ).filter(Rect::hasPositiveFiniteArea)
}

private fun Rect.hasPositiveFiniteArea(): Boolean =
    left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
        width.isFinite() && height.isFinite() && width > 0f && height > 0f
