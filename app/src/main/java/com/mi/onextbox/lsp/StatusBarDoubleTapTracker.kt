package com.mi.onextbox.lsp

/** A pair of completed single-pointer taps; drags, long presses, and cancellation clear it. */
internal class StatusBarDoubleTapTracker {
    private data class Down(val time: Long, val x: Float, val y: Float)
    private data class Tap(val upTime: Long, val x: Float, val y: Float)
    private var down: Down? = null
    private var previous: Tap? = null

    fun onDown(time: Long, x: Float, y: Float) {
        if (down != null) cancel()
        down = Down(time, x, y)
    }

    fun onMove(x: Float, y: Float, touchSlop: Float) {
        val start = down ?: return
        if (moved(start, x, y, touchSlop)) cancel()
    }

    fun onUp(
        time: Long,
        x: Float,
        y: Float,
        touchSlop: Float,
        longPressTimeout: Long,
        doubleTapTimeout: Long,
        doubleTapSlop: Float,
    ): Boolean {
        val start = down
        down = null
        if (start == null || time - start.time !in 0L..longPressTimeout || moved(start, x, y, touchSlop)) {
            cancel()
            return false
        }
        val first = previous
        val matches = first != null && start.time - first.upTime in 1L..doubleTapTimeout &&
            squaredDistance(start.x, start.y, first.x, first.y) <= doubleTapSlop * doubleTapSlop
        previous = if (matches) null else Tap(time, start.x, start.y)
        return matches
    }

    fun cancel() {
        down = null
        previous = null
    }

    private fun moved(start: Down, x: Float, y: Float, slop: Float): Boolean =
        squaredDistance(start.x, start.y, x, y) > slop * slop

    private fun squaredDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return dx * dx + dy * dy
    }
}
