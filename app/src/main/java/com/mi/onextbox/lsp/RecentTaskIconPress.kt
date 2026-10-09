package com.mi.onextbox.lsp

import kotlin.math.abs

/** Tracks only a stationary press; taps, scrolling, and canceled gestures stay native. */
internal class RecentTaskIconPress(private val downX: Float, private val downY: Float, private val slop: Int) {
    private var tracking = true
    var handled = false
        private set

    fun move(x: Float, y: Float): Boolean {
        if (!handled && (abs(x - downX) > slop || abs(y - downY) > slop)) tracking = false
        return tracking
    }

    fun trigger(enabled: Boolean, sameTask: Boolean, ready: Boolean): Boolean {
        if (!tracking || handled || !enabled || !sameTask || !ready) {
            tracking = false
            return false
        }
        handled = true
        tracking = false
        return true
    }

    fun cancel() {
        tracking = false
    }
}
