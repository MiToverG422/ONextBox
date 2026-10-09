package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class FaceTapFeedbackRulesTest {
    private val state = FaceTapUnlockRules.State(0, 0, true, true, true, true, true, false)

    @Test fun childNeverRunsWithoutParentOrWithSystemAnimationsOff() {
        assertTrue(FaceTapFeedbackRules.enabled(true, true, true))
        assertFalse(FaceTapFeedbackRules.enabled(true, false, true))
        assertFalse(FaceTapFeedbackRules.enabled(false, true, true))
        assertFalse(FaceTapFeedbackRules.enabled(true, true, false))
    }

    @Test fun playsOnlyRecognizedOemAnimationsAndRespectsNone() {
        listOf(FaceTapFeedbackRules.BASE, FaceTapFeedbackRules.WATER, FaceTapFeedbackRules.HY_WATER).forEach {
            assertTrue(FaceTapFeedbackRules.supported(it, 40, "native_frames"))
            assertFalse(FaceTapFeedbackRules.supported(it, 0, "native_frames"))
            assertFalse(FaceTapFeedbackRules.supported(it, 40, null))
            assertFalse(FaceTapFeedbackRules.supported(it, 40, ""))
        }
        assertFalse(FaceTapFeedbackRules.supported("android.graphics.drawable.AnimationDrawable", 40, "native_frames"))
        assertFalse(FaceTapFeedbackRules.supported("future.unknown.OemDrawable", 40, "native_frames"))
    }

    @Test fun copiedOptionsPreserveOemPlaybackSpeed() {
        listOf(64, 48, 33, 20, 42).forEachIndexed { speed, duration ->
            assertEquals(speed, FaceTapFeedbackRules.speedForDuration(duration))
        }
        assertNull(FaceTapFeedbackRules.speedForDuration(0))
        assertNull(FaceTapFeedbackRules.speedForDuration(16))
    }

    @Test fun onlyConstructorEliminatedHyRippleUsesTheBaseConstructor() {
        assertTrue(FaceTapFeedbackRules.needsBaseConstructor(FaceTapFeedbackRules.HY_WATER, false))
        assertFalse(FaceTapFeedbackRules.needsBaseConstructor(FaceTapFeedbackRules.HY_WATER, true))
        assertFalse(FaceTapFeedbackRules.needsBaseConstructor(FaceTapFeedbackRules.BASE, false))
        assertFalse(FaceTapFeedbackRules.needsBaseConstructor(FaceTapFeedbackRules.WATER, false))
        assertFalse(FaceTapFeedbackRules.needsBaseConstructor("future.unknown.OemDrawable", false))
    }

    @Test fun queuedCommitRequiresLiveAuthenticationAndTheExactSession() {
        assertTrue(FaceTapFeedbackRules.mayCommit(0, 1000L, state, 1000L))
        assertFalse(FaceTapFeedbackRules.mayCommit(0, 1000L, null, 1000L))
        assertFalse(FaceTapFeedbackRules.mayCommit(0, 1000L, state.copy(faceAuthenticated = false), 1000L))
        assertFalse(FaceTapFeedbackRules.mayCommit(0, 1000L, state.copy(faceAllowed = false), 1000L))
        assertFalse(FaceTapFeedbackRules.mayCommit(0, 1000L, state.copy(blocked = true), 1000L))
        assertFalse(FaceTapFeedbackRules.mayCommit(0, 1000L, state.copy(interactive = false), 1000L))
        assertFalse(FaceTapFeedbackRules.mayCommit(0, 1000L, state.copy(sessionUser = null), 1000L))
        assertFalse(FaceTapFeedbackRules.mayCommit(0, 1000L, state.copy(sessionUser = 10, currentUser = 10), 1000L))
        assertFalse(FaceTapFeedbackRules.mayCommit(0, 1000L, state, 1100L))
        assertFalse(FaceTapFeedbackRules.mayCommit(0, 0L, state, 0L))
    }

    @Test fun unlockDoesNotRestartOrWaitForTheWholeNativeEffect() {
        assertTrue(FaceTapFeedbackRules.COMMIT_DELAY_MS in 1L..100L)
        assertEquals(80L, FaceTapFeedbackRules.commitDelay(0L))
        assertEquals(60L, FaceTapFeedbackRules.commitDelay(20L))
        assertEquals(0L, FaceTapFeedbackRules.commitDelay(80L))
        assertEquals(0L, FaceTapFeedbackRules.commitDelay(500L))
        assertEquals(80L, FaceTapFeedbackRules.commitDelay(-1L))
    }

    @Test fun onlyCommittedVisualsMayFinishAfterNormalKeyguardHide() {
        assertTrue(FaceTapFeedbackRules.cancelForHide(false, false))
        assertFalse(FaceTapFeedbackRules.cancelForHide(true, false))
        assertTrue(FaceTapFeedbackRules.cancelForHide(false, true))
        assertTrue(FaceTapFeedbackRules.cancelForHide(true, true))
    }

    @Test fun cleanupWaitsForTheNativeRippleNotJustItsIconFrames() {
        assertEquals(900L, FaceTapFeedbackRules.cleanupDelay(300L, true))
        assertEquals(1200L, FaceTapFeedbackRules.cleanupDelay(1100L, true))
        assertEquals(400L, FaceTapFeedbackRules.cleanupDelay(300L, false))
        assertEquals(5000L, FaceTapFeedbackRules.cleanupDelay(8000L, false))
    }
}
