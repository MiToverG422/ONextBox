package com.mi.onextbox.lsp

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

internal class PinSwipeNativeClick {
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
                release()
                throw error
            } finally { press.recycle() }
        }
        try { target.performClick() }
        catch (error: Throwable) { release(); throw error }
    }

    fun release() {
        val target = pressed ?: return
        pressed = null
        val cancel = MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL,
            target.width / 2f, target.height / 2f, metaState,
        ).apply { source = inputSource }
        try { target.dispatchTouchEvent(cancel) } finally { cancel.recycle() }
    }
}
