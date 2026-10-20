package com.mi.onextbox.ui.common

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAppearanceTransitionStateTest {
    private val animationsDisabled = object : MotionDurationScale {
        override val scaleFactor = 0f
    }

    // Hold the outgoing animation without real delays or a device display clock.
    private val pausedFrames = object : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
            suspendCancellableCoroutine { }
    }

    @Test
    fun disabledAnimationsCommitImmediatelyAndLeaveNoCover() = runBlocking(animationsDisabled) {
        val state = AppAppearanceTransitionState()
        var commits = 0

        state.change { commits++ }

        assertEquals(1, commits)
        assertIdle(state)
    }

    @Test
    fun failedCommitRestoresInteractionAndAllowsAnotherChange() = runBlocking(animationsDisabled) {
        val state = AppAppearanceTransitionState()
        val failure = IllegalStateException("write failed")
        try {
            state.change { throw failure }
            throw AssertionError("Expected the commit failure")
        } catch (actual: IllegalStateException) {
            assertTrue(actual === failure)
        }
        assertIdle(state)

        var committed = false
        state.change { committed = true }
        assertTrue(committed)
        assertIdle(state)
    }

    @Test
    fun concurrentRequestIsIgnoredAndCancellationResetsAllLayers() = runBlocking(pausedFrames) {
        val state = AppAppearanceTransitionState()
        var commits = 0
        val outgoing = launch(start = CoroutineStart.UNDISPATCHED) {
            state.change { commits++ }
        }
        assertTrue(state.isChanging)

        state.change { commits++ }
        assertEquals(0, commits)
        outgoing.cancelAndJoin()
        assertEquals(0, commits)
        assertIdle(state)

        withContext(animationsDisabled) { state.change { commits++ } }
        assertEquals(1, commits)
        assertIdle(state)
    }

    @Test
    fun successiveAppearanceChangesCommitOnlyBehindTheCoverAndRestoreEveryLayer() {
        val acceleratedFrames = object : MonotonicFrameClock {
            private var frameNanos = 0L
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                yield()
                frameNanos += 1_000_000_000L
                return onFrame(frameNanos)
            }
        }
        runBlocking(acceleratedFrames) {
            withTimeout(5_000) {
                val state = AppAppearanceTransitionState()
                val applied = mutableListOf<String>()
                listOf("language", "uiStyle", "themeMode", "colorSpec").forEach { option ->
                    state.change {
                        assertTrue(state.isChanging)
                        assertEquals(1f, state.coverAlpha.value, 0f)
                        assertEquals(0f, state.contentAlpha.value, 0f)
                        assertEquals(0.92f, state.contentScale.value, 0f)
                        assertEquals(150f, state.bottomOffsetDp.value, 0f)
                        assertEquals(0.6f, state.bottomScale.value, 0f)
                        applied += option
                    }
                    assertIdle(state)
                }
                assertEquals(listOf("language", "uiStyle", "themeMode", "colorSpec"), applied)
            }
        }
    }

    private fun assertIdle(state: AppAppearanceTransitionState) {
        assertFalse(state.isChanging)
        assertEquals(1f, state.contentAlpha.value, 0f)
        assertEquals(1f, state.contentScale.value, 0f)
        assertEquals(0f, state.coverAlpha.value, 0f)
        assertEquals(0f, state.bottomOffsetDp.value, 0f)
        assertEquals(1f, state.bottomScale.value, 0f)
    }
}
