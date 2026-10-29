package com.mi.onextbox.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

internal fun Modifier.swipeBack(enabled: Boolean): Modifier =
    if (!enabled) this else composed {
        val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        Modifier.pointerInput(dispatcher) {
            if (dispatcher == null) return@pointerInput
            var distance = 0f
            var started = false
            var touchX = 0f
            var touchY = 0f
            val threshold = 72.dp.toPx()
            fun event(progress: Float) = BackEventCompat(
                touchX, touchY, progress, BackEventCompat.EDGE_LEFT,
            )
            fun cancel() {
                if (started) dispatcher.dispatchOnBackCancelled()
                started = false
                distance = 0f
            }
            try {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    distance = 0f
                    touchX = down.position.x
                    touchY = down.position.y
                    started = false
                    val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                        if (overSlop > 0f) {
                            distance = overSlop
                            change.consume()
                        }
                    } ?: return@awaitEachGesture
                    touchX = drag.position.x
                    touchY = drag.position.y
                    dispatcher.dispatchOnBackStarted(event(0f))
                    started = true
                    dispatcher.dispatchOnBackProgressed(
                        event((distance / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)),
                    )
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == drag.id }
                        if (change == null) {
                            cancel()
                            break
                        }
                        if (!change.pressed) {
                            if (distance >= threshold) {
                                started = false
                                dispatcher.onBackPressed()
                            } else {
                                cancel()
                            }
                            change.consume()
                            break
                        }
                        distance = (distance + change.positionChange().x).coerceAtLeast(0f)
                        touchX = change.position.x
                        touchY = change.position.y
                        dispatcher.dispatchOnBackProgressed(
                            event((distance / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)),
                        )
                        change.consume()
                    }
                }
            } finally {
                cancel()
            }
        }
    }
