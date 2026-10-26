package com.mi.onextbox.ui.common

import org.junit.Assert.*
import org.junit.Test

class AppLaunchRevealStateTest {
    @Test fun actualSplashFadeRequestsTheStartupAnimation() {
        val state = AppLaunchRevealState(restored = false)
        assertFalse(state.ready)
        assertTrue(state.animate)
        state.onExitStarted()
        assertTrue(state.ready)
        assertTrue(state.animate)
        state.onFinished()
        assertTrue(state.ready)
        assertTrue(state.animate)
    }

    @Test fun finishingWithoutASplashNeverQueuesAnAnimationForTheNextResume() {
        val state = AppLaunchRevealState(restored = false)
        state.onFinished()
        assertTrue(state.ready)
        assertFalse(state.animate)
        state.onExitStarted()
        state.onFinished()
        assertTrue(state.ready)
        assertFalse(state.animate)
    }

    @Test fun restoredActivityKeepsTheBottomBarComplete() {
        val state = AppLaunchRevealState(restored = true)
        assertTrue(state.ready)
        assertFalse(state.animate)
        state.onFinished()
        assertTrue(state.ready)
        assertFalse(state.animate)
    }

    @Test fun repeatedCompletionDoesNotDisableAnAlreadyStartedReveal() {
        val state = AppLaunchRevealState(restored = false)
        state.onExitStarted()
        repeat(3) { state.onFinished() }
        state.onExitStarted()
        assertTrue(state.ready)
        assertTrue(state.animate)
    }
}
