package com.mi.onextbox.lsp

import kotlin.math.abs
import kotlin.math.roundToInt

// Horizontal gestures for the last-page category entry.
internal object LauncherCategoryPageRules {
    enum class Direction { Enter, Exit }

    fun showCategories(direction: Direction, complete: Boolean): Boolean = (direction == Direction.Enter) == complete

    fun handOffDesktopTouch(direction: Direction, complete: Boolean): Boolean = !showCategories(direction, complete)

    fun canClaimSwipe(categoryDragging: Boolean, heldMillis: Long, longPressMillis: Int): Boolean =
        !categoryDragging && heldMillis.coerceAtLeast(0) < longPressMillis.coerceAtLeast(1).toLong()

    fun retainAppReturn(panelActive: Boolean, enteredFromEdge: Boolean, launchedFromPanel: Boolean, transitioning: Boolean): Boolean =
        panelActive && enteredFromEdge && launchedFromPanel && !transitioning

    fun retainExpandedFolder(retainingAppReturn: Boolean, sameFolder: Boolean): Boolean =
        retainingAppReturn && sameFolder

    fun isPanelTouch(focused: Boolean, resumed: Boolean?, appClosing: Boolean?, returnGestureActive: Boolean?): Boolean =
        focused && resumed == true && appClosing == false && returnGestureActive == false

    fun categoryOrder(current: List<String>, saved: List<String>): List<String> {
        val remaining = current.toMutableSet()
        val ordered = saved.filter { remaining.remove(it) }
        return ordered + current.filter { it in remaining }
    }

    fun shouldBindApps(enabled: Boolean, standardLoader: Boolean, nativeBinding: Boolean): Boolean =
        nativeBinding || enabled && standardLoader

    data class Layout(val searchTop: Int, val contentTop: Int, val bottomInset: Int)

    fun layout(statusInset: Int, rootTop: Int, searchHeight: Int, navigationInset: Int, imeInset: Int, density: Float): Layout {
        val scale = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        val edge = (8 * scale).roundToInt()
        val searchTop = (statusInset.coerceAtLeast(0) - rootTop).coerceAtLeast(0) + edge
        return Layout(
            searchTop,
            searchTop + searchHeight.coerceAtLeast(0) + (4 * scale).roundToInt(),
            maxOf(0, navigationInset, imeInset) + edge,
        )
    }

    fun searchRowWidth(parentWidth: Int, folderGap: Int): Int =
        (parentWidth.coerceAtLeast(0) - folderGap.coerceIn(0, parentWidth.coerceAtLeast(0))).coerceAtLeast(0)

    fun contentPadding(contentTop: Int, viewportTop: Int): Int =
        (contentTop.coerceAtLeast(0) - viewportTop.coerceAtLeast(0)).coerceAtLeast(0)

    data class ColorLayout(val titleTop: Int, val listTop: Int)

    data class Fade(val start: Int, val end: Int)

    fun topFade(statusInset: Int, contentTop: Int, viewportTop: Int, viewportHeight: Int): Fade {
        val end = contentPadding(contentTop, viewportTop).coerceAtMost(viewportHeight.coerceAtLeast(0))
        return Fade(contentPadding(statusInset, viewportTop).coerceAtMost(end), end)
    }

    fun listImeInset(searchResults: Boolean, imeInset: Int): Int = if (searchResults) imeInset.coerceAtLeast(0) else 0

    fun isAtEntryEdge(scroll: Int, edgeScroll: Int, tolerance: Int): Boolean =
        abs(scroll.toLong() - edgeScroll.toLong()) <= tolerance.coerceAtLeast(0).toLong()

    fun colorLayout(contentTop: Int, titleHeight: Int, density: Float): ColorLayout {
        val scale = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        val titleTop = contentTop.coerceAtLeast(0) + (8 * scale).roundToInt()
        return ColorLayout(titleTop, titleTop + titleHeight.coerceAtLeast(0) + (12 * scale).roundToInt())
    }

    fun supportsMode(standard: Boolean, drawer: Boolean): Boolean = standard || drawer

    fun isRightmostPage(page: Int, count: Int, panels: Int, rtl: Boolean): Boolean {
        if (count <= 0 || page !in 0 until count || panels <= 0) return false
        return if (rtl) page == 0 else page >= (count - panels).coerceAtLeast(0)
    }

    fun entryPage(current: Int, next: Int, count: Int, panels: Int, rtl: Boolean): Int {
        val destination = if (next in 0 until count) next else current
        return if (isRightmostPage(destination, count, panels, rtl)) destination else -1
    }

    fun continuedDelta(dx: Float, startingFraction: Float, width: Float, direction: Direction): Float {
        if (!dx.isFinite() || !startingFraction.isFinite() || !width.isFinite() || width <= 0f) return 0f
        val distance = startingFraction.coerceIn(0f, 1f) * width
        return dx + if (direction == Direction.Enter) -distance else distance
    }

    fun sameAppSnapshot(source: Array<*>, target: Array<*>, sourceFlags: Int, targetFlags: Int): Boolean =
        sourceFlags == targetFlags && source.size == target.size && source.indices.all { source[it] === target[it] }

    fun forwardDistance(dx: Float, direction: Direction): Float =
        if (direction == Direction.Enter) -dx else dx

    fun canStart(dx: Float, dy: Float, direction: Direction, slop: Float): Boolean =
        dx.isFinite() && dy.isFinite() && slop.isFinite() &&
            forwardDistance(dx, direction) > slop.coerceAtLeast(1f) * 1.5f && abs(dx) > abs(dy) * 1.4f

    fun progress(dx: Float, width: Float, direction: Direction): Float {
        if (!dx.isFinite() || !width.isFinite() || width <= 0f) return 0f
        return (forwardDistance(dx, direction) / width).coerceIn(0f, 1f)
    }

    fun shouldComplete(dx: Float, width: Float, velocity: Float, direction: Direction, flingThreshold: Float): Boolean {
        if (!dx.isFinite() || !width.isFinite() || width <= 0f || !velocity.isFinite()) return false
        val distance = forwardDistance(dx, direction)
        val speed = forwardDistance(velocity, direction)
        if (abs(dx) > 25f && abs(speed) > flingThreshold.coerceAtLeast(1f)) return speed > 0f
        return distance > width * 0.4f
    }

    data class Offsets(val workspace: Float, val apps: Float)

    fun offsets(fraction: Float, width: Float, direction: Direction): Offsets {
        val progress = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
        val distance = width.takeIf { it.isFinite() && it > 0f } ?: 0f
        if (distance == 0f) return Offsets(0f, 0f)
        val open = if (direction == Direction.Enter) progress else 1f - progress
        return Offsets(if (open == 0f) 0f else -distance * open, distance * (1f - open))
    }

    fun settleDuration(nativeDuration: Int): Long = nativeDuration.coerceIn(120, 600).toLong()
}
