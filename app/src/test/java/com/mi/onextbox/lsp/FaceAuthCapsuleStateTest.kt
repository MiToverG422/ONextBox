package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class FaceAuthCapsuleStateTest {
    @Test fun idleLockedStateDoesNotRequireBiometricsAndHasNoScanTimeout() {
        val state = FaceAuthCapsuleState()
        state.showLocked()
        state.tick(600_000, allowed = true, keyguardShowing = true)
        assertEquals(FaceAuthCapsuleState.Phase.Locked, state.phase)
        state.tick(600_001, allowed = true, keyguardShowing = false)
        assertEquals(FaceAuthCapsuleState.Phase.Hidden, state.phase)
        state.showLocked()
        state.tick(600_002, allowed = false, keyguardShowing = true)
        assertEquals(FaceAuthCapsuleState.Phase.Hidden, state.phase)
    }

    @Test fun fingerprintUnlockAnimatesFromTheIdleLockWithoutFaceEnrollment() {
        val state = FaceAuthCapsuleState()
        state.showLocked()
        assertTrue(state.fingerprintAuthenticated(20_000))
        state.tick(20_500, allowed = true, keyguardShowing = false)
        assertEquals(FaceAuthCapsuleState.Phase.Success, state.phase)
        state.animationFinished()
        state.showLocked()
        assertEquals(FaceAuthCapsuleState.Phase.Hidden, state.phase)
    }

    @Test fun onlyAnAcceptedCredentialCanUnlockTheIdleLock() {
        val state = FaceAuthCapsuleState()
        state.showLocked()
        assertFalse(state.credentialAuthenticated(100, accepted = false))
        assertEquals(FaceAuthCapsuleState.Phase.Locked, state.phase)
        assertTrue(state.credentialAuthenticated(200, accepted = true))
        state.tick(500, allowed = true, keyguardShowing = false)
        assertEquals(FaceAuthCapsuleState.Phase.Success, state.phase)
        assertFalse(state.fingerprintAuthenticated(600))
        assertFalse(state.finish(true, 650, freshNativeSuccess = true))
        state.animationFinished()
        assertFalse(state.credentialAuthenticated(2000, accepted = true))
        state.showLocked()
        assertEquals(FaceAuthCapsuleState.Phase.Hidden, state.phase)
    }

    @Test fun aFreshAcceptedCredentialCanRecoverAMissedLockScreenFrame() {
        val state = FaceAuthCapsuleState()
        assertTrue(state.credentialAuthenticated(100, accepted = true, animationDuration = 3040))
        state.tick(2600, allowed = true, keyguardShowing = false)
        assertEquals(FaceAuthCapsuleState.Phase.Success, state.phase)
        state.animationFinished()
        assertEquals(FaceAuthCapsuleState.Phase.Hidden, state.phase)
    }

    @Test fun faceCancellationReturnsToThePersistentLockAndOtherUnlockMethodsStillWork() {
        val state = FaceAuthCapsuleState()
        state.showLocked()
        state.running(100)
        state.stopped(200)
        state.tick(600, true)
        state.showLocked()
        assertEquals(FaceAuthCapsuleState.Phase.Locked, state.phase)
        assertTrue(state.credentialAuthenticated(700, accepted = true))
        assertEquals(FaceAuthCapsuleState.Phase.Success, state.phase)
    }

    @Test fun aFailedFaceScanDoesNotRemoveTheBaselineLockOrPreventFingerprintUnlock() {
        val state = FaceAuthCapsuleState()
        state.showLocked()
        state.running(100)
        state.finish(false, 200)
        state.showLocked()
        assertEquals(FaceAuthCapsuleState.Phase.Failed, state.phase)
        state.tick(950, true)
        state.showLocked()
        assertEquals(FaceAuthCapsuleState.Phase.Locked, state.phase)
        assertTrue(state.fingerprintAuthenticated(1000))
    }

    @Test fun relockingAfterCredentialUnlockStartsANewIdleLockAndAnimationSession() {
        val state = FaceAuthCapsuleState()
        state.showLocked()
        assertTrue(state.credentialAuthenticated(100, accepted = true))
        state.animationFinished()
        state.resetSession()
        state.showLocked()
        state.tick(50_000, true)
        assertEquals(FaceAuthCapsuleState.Phase.Locked, state.phase)
        assertTrue(state.credentialAuthenticated(50_100, accepted = true))
    }

    @Test fun staleResultsCannotCreateAnAuthenticationAnimation() {
        val state = FaceAuthCapsuleState()
        state.finish(true, 100)
        assertEquals(FaceAuthCapsuleState.Phase.Hidden, state.phase)
        state.start(200)
        state.cancel()
        state.finish(true, 300)
        assertEquals(FaceAuthCapsuleState.Phase.Hidden, state.phase)
    }

    @Test fun completionSurvivesAutomaticUnlockButNeverDisablingTheFeature() {
        val state = FaceAuthCapsuleState()
        state.start(0)
        state.finish(true, 100)
        state.tick(300, true)
        assertEquals(FaceAuthCapsuleState.Phase.Success, state.phase)
        state.tick(310, true, keyguardShowing = false)
        assertEquals(FaceAuthCapsuleState.Phase.Success, state.phase)
        state.tick(320, false)
        assertEquals(FaceAuthCapsuleState.Phase.Hidden, state.phase)
    }

    @Test fun acceptedNativeSuccessRecoversAnOmittedStartButCachedResultsDoNot() {
        val state = FaceAuthCapsuleState()
        state.finish(true, 100)
        assertEquals(FaceAuthCapsuleState.Phase.Hidden, state.phase)
        state.finish(true, 200, freshNativeSuccess = true)
        assertEquals(FaceAuthCapsuleState.Phase.Success, state.phase)
        state.start(210)
        assertEquals(FaceAuthCapsuleState.Phase.Success, state.phase)
        state.tick(200 + FaceAuthCapsuleMotion.SUCCESS_DURATION + 500, true, false)
        assertEquals(FaceAuthCapsuleState.Phase.Hidden, state.phase)
    }

    @Test fun stopBeforeTheAcceptedResultDoesNotDropTheCompletion() {
        val state = FaceAuthCapsuleState()
        state.start(100)
        state.stopped(200)
        state.tick(400, true)
        assertEquals(FaceAuthCapsuleState.Phase.Scanning, state.phase)
        state.finish(true, 450)
        state.tick(610, true, false)
        assertEquals(FaceAuthCapsuleState.Phase.Success, state.phase)
    }

    @Test fun cancellationGraceCannotKeepAnOrphanedCapsuleVisible() {
        val state = FaceAuthCapsuleState()
        state.start(100)
        state.stopped(200)
        state.tick(600, true)
        assertEquals(FaceAuthCapsuleState.Phase.Hidden, state.phase)
        state.running(700)
        state.stopped(800)
        state.running(900)
        state.tick(1300, true)
        assertEquals(FaceAuthCapsuleState.Phase.Scanning, state.phase)
        state.tick(1310, false)
        assertEquals(FaceAuthCapsuleState.Phase.Hidden, state.phase)
    }

    @Test fun failureAndScanningHaveBoundedLifetimes() {
        val state = FaceAuthCapsuleState()
        state.start(0)
        state.start(9000)
        state.tick(10_000, true)
        assertEquals(FaceAuthCapsuleState.Phase.Hidden, state.phase)
        state.start(11_000)
        state.finish(false, 11_100)
        state.tick(11_849, true)
        assertEquals(FaceAuthCapsuleState.Phase.Failed, state.phase)
        state.tick(11_850, true)
        assertEquals(FaceAuthCapsuleState.Phase.Hidden, state.phase)
    }

    @Test fun rendererCompletionEndsSuccessAndSlowAnimationsAreNotCutOffAtTheDefaultTime() {
        val state = FaceAuthCapsuleState()
        state.finish(true, 0, freshNativeSuccess = true, animationDuration = 3040L)
        state.tick(2500, true, false)
        assertEquals(FaceAuthCapsuleState.Phase.Success, state.phase)
        state.animationFinished()
        assertEquals(FaceAuthCapsuleState.Phase.Hidden, state.phase)
        state.resetSession()
        state.start(2600)
        state.animationFinished()
        assertEquals(FaceAuthCapsuleState.Phase.Scanning, state.phase)
    }

    @Test fun fingerprintUnlockCompletesThePendingFaceAnimationAndRejectsDuplicateFaceResults() {
        val state = FaceAuthCapsuleState()
        state.start(100)
        assertTrue(state.fingerprintAuthenticated(150))
        assertEquals(FaceAuthCapsuleState.Phase.Success, state.phase)
        assertFalse(state.finish(true, 180, freshNativeSuccess = true))
        state.animationFinished()
        state.running(190)
        assertFalse(state.finish(true, 200, freshNativeSuccess = true))
        assertEquals(FaceAuthCapsuleState.Phase.Hidden, state.phase)
        state.resetSession()
        state.finish(true, 300, freshNativeSuccess = true)
        assertEquals(FaceAuthCapsuleState.Phase.Success, state.phase)
    }

    @Test fun aFaceResultWhichAlreadyWonIsNotReplacedByAConcurrentFingerprintResult() {
        val state = FaceAuthCapsuleState()
        state.finish(true, 100, freshNativeSuccess = true)
        assertFalse(state.fingerprintAuthenticated(110))
        assertEquals(FaceAuthCapsuleState.Phase.Success, state.phase)
        state.animationFinished()
        assertEquals(FaceAuthCapsuleState.Phase.Hidden, state.phase)
    }

    @Test fun fingerprintSuccessDoesNotRequireAPrecedingFaceScan() {
        val state = FaceAuthCapsuleState()
        assertTrue(state.fingerprintAuthenticated(100))
        assertEquals(FaceAuthCapsuleState.Phase.Success, state.phase)
        state.tick(150, true, keyguardShowing = false)
        assertEquals(FaceAuthCapsuleState.Phase.Success, state.phase)
        assertFalse(state.finish(false, 160))
        state.animationFinished()
        assertFalse(state.fingerprintAuthenticated(180))
        assertEquals(FaceAuthCapsuleState.Phase.Hidden, state.phase)
        state.resetSession()
        assertTrue(state.fingerprintAuthenticated(300))
    }

    @Test fun completedFaceAuthenticationDoesNotReplayWhenFingerprintArrivesLater() {
        val state = FaceAuthCapsuleState()
        assertTrue(state.finish(true, 100, freshNativeSuccess = true))
        state.animationFinished()
        assertFalse(state.fingerprintAuthenticated(1800))
        assertEquals(FaceAuthCapsuleState.Phase.Hidden, state.phase)
    }
}
