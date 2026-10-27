package com.mi.onextbox.lsp

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

internal class PinSwipeNativeClick {
    companion object {
        private var releasing: View? = null

        fun suppressesClick(view: Any?) = releasing != null && releasing === view

        fun releaseInitial(target: View, source: MotionEvent) {
            dispatchRelease(target, source.downTime, source.source, source.metaState, false)
        }

        private fun dispatchRelease(target: View, down: Long, inputSource: Int, meta: Int, cancelled: Boolean) {
            val event = MotionEvent.obtain(
                down, SystemClock.uptimeMillis(),
                if (cancelled) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP,
                target.width / 2f, target.height / 2f, meta,
            ).apply { source = inputSource }
            val previous = releasing
            releasing = target
            try {
                target.cancelPendingInputEvents()
                target.isPressed = false
                target.dispatchTouchEvent(event)
            } finally {
                target.cancelPendingInputEvents()
                releasing = previous
                event.recycle()
            }
        }
    }
    private var pressed: View? = null
    private var downTime = 0L
    private var inputSource = 0
    private var metaState = 0

    fun perform(target: View, source: MotionEvent, reuseInitialDown: Boolean) {
        if (!reuseInitialDown) {
            release()
            pressed = target
            downTime = source.eventTime
            inputSource = source.source
            metaState = source.metaState
            val press = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN,
                target.width / 2f, target.height / 2f, metaState,
            ).apply { this.source = inputSource }
            try {
                target.dispatchTouchEvent(press)
            } catch (error: Throwable) {
                release(cancelled = true)
                throw error
            } finally { press.recycle() }
        }
        try { target.performClick() }
        catch (error: Throwable) { release(cancelled = true); throw error }
    }

    fun release(cancelled: Boolean = false) {
        val target = pressed ?: return
        pressed = null
        dispatchRelease(target, downTime, inputSource, metaState, cancelled)
    }
}
