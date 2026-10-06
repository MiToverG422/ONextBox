package com.mi.onextbox.lsp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaceTapUnlockRulesTest {
    private val authenticated = FaceTapUnlockRules.State(
        sessionUser = 0, currentUser = 0, faceAuthenticated = true, faceAllowed = true,
        canSkipBouncer = true, showing = true, interactive = true, blocked = false,
    )

    @Test fun queuedTouchesFromBeforeAuthenticationCannotBegin() {
        assertFalse(FaceTapUnlockRules.mayBegin(900L, 1000L))
        assertFalse(FaceTapUnlockRules.mayBegin(1000L, 0L))
        assertTrue(FaceTapUnlockRules.mayBegin(1100L, 1000L))
    }

    @Test fun emptyVisibleNativeContainerMustNotSuppressTheRetainedIcon() {
        assertFalse(FaceTapUnlockRules.nativeIconVisible(true, 1f, false))
        assertTrue(FaceTapUnlockRules.nativeIconVisible(true, 1f, true))
        assertFalse(FaceTapUnlockRules.nativeIconVisible(false, 1f, true))
        assertFalse(FaceTapUnlockRules.nativeIconVisible(true, 0f, true))
        assertFalse(FaceTapUnlockRules.nativeIconVisible(true, Float.NaN, true))
    }

    @Test fun screenWideAnimationContainerUsesCompactDrawableBounds() {
        // Find X9 Ultra C17, 1440px animation container, 221px real fingerprint icon.
        val bounds = FaceTapUnlockRules.nativeIconBounds(-240, 1401, 1440, 1440, 166, 166, 1.3333334f, 1440, 3168)
        assertEquals(FaceTapUnlockRules.IconBounds(610, 2251, 831, 2472), bounds)
    }

    @Test fun normalOriginAndNegativeScaledOriginMatchTheStockHitArea() {
        val scaled = FaceTapUnlockRules.nativeIconBounds(-240, 1401, 1440, 1440, 166, 166, 1.3333334f, 1440, 3168)
        val normal = FaceTapUnlockRules.nativeIconBounds(0, 1641, 1440, 1440, 166, 166, 1.3333334f, 1440, 3168)
        assertEquals(normal, scaled)
    }

    @Test fun largeAnimationFrameNeverBecomesTheUnlockTapTarget() {
        assertNull(FaceTapUnlockRules.nativeIconBounds(0, 1500, 1440, 1440, 1440, 1440, 1f, 1440, 3168))
    }

    @Test fun invalidOrOffscreenGeometryCannotMakeAButton() {
        assertNull(FaceTapUnlockRules.nativeIconBounds(0, 1500, 1440, 1440, 0, 166, 1f, 1440, 3168))
        assertNull(FaceTapUnlockRules.nativeIconBounds(0, 1500, 0, 1440, 166, 166, 1f, 1440, 3168))
        assertNull(FaceTapUnlockRules.nativeIconBounds(0, 1500, 1440, 1440, 166, 166, Float.NaN, 1440, 3168))
        assertNull(FaceTapUnlockRules.nativeIconBounds(0, 1500, 1440, 1440, 166, 166, 0f, 1440, 3168))
        assertNull(FaceTapUnlockRules.nativeIconBounds(2000, 1500, 1440, 1440, 166, 166, 1f, 1440, 3168))
        assertNull(FaceTapUnlockRules.nativeIconBounds(Int.MIN_VALUE, Int.MAX_VALUE, 1440, 1440, 166, 166, 1f, 1440, 3168))
    }

    @Test fun onlyTheAuthenticatedCurrentUserIsEligible() {
        assertTrue(FaceTapUnlockRules.eligible(authenticated))
        assertFalse(FaceTapUnlockRules.eligible(authenticated.copy(sessionUser = null)))
        assertFalse(FaceTapUnlockRules.eligible(authenticated.copy(sessionUser = 10)))
        assertFalse(FaceTapUnlockRules.eligible(authenticated.copy(currentUser = 10)))
    }

    @Test fun cachedFaceOrTrustCannotReplaceLiveFaceAuthentication() {
        assertFalse(FaceTapUnlockRules.eligible(authenticated.copy(faceAuthenticated = false)))
        assertFalse(FaceTapUnlockRules.eligible(authenticated.copy(faceAllowed = false)))
        assertFalse(FaceTapUnlockRules.eligible(authenticated.copy(canSkipBouncer = false)))
    }

    @Test fun blockedSleepingOrDismissedLockscreenCannotBeUsed() {
        assertFalse(FaceTapUnlockRules.eligible(authenticated.copy(showing = false)))
        assertFalse(FaceTapUnlockRules.eligible(authenticated.copy(interactive = false)))
        assertFalse(FaceTapUnlockRules.eligible(authenticated.copy(blocked = true)))
    }

    @Test fun shortStationaryTapIsAccepted() {
        val tap = FaceTapUnlockRules.Tap(8f)
        tap.begin(100f, 200f, 1000L, 0)
        assertTrue(tap.finish(103f, 204f, 1150L, 0, true))
        assertFalse(tap.active)
        assertFalse(tap.finish(103f, 204f, 1200L, 0, true))
    }

    @Test fun dragDoesNotBecomeTapWhenFingerReturns() {
        val tap = FaceTapUnlockRules.Tap(8f)
        tap.begin(100f, 200f, 1000L, 0)
        tap.move(120f, 200f)
        tap.move(100f, 200f)
        assertFalse(tap.finish(100f, 200f, 1150L, 0, true))
    }

    @Test fun cancelMultitouchOrAuthRevocationIsIrreversible() {
        val tap = FaceTapUnlockRules.Tap(8f)
        tap.begin(100f, 200f, 1000L, 0)
        tap.invalidate()
        assertTrue(tap.active)
        assertFalse(tap.finish(100f, 200f, 1150L, 0, true))
        tap.begin(100f, 200f, 2000L, 0)
        tap.reset()
        assertFalse(tap.finish(100f, 200f, 2150L, 0, true))
        tap.begin(100f, 200f, 3000L, 0)
        assertFalse(tap.finish(100f, 200f, 3150L, 0, false))
    }

    @Test fun stationaryHoldIsAcceptedOnlyWithLiveAuthenticationOnRelease() {
        val tap = FaceTapUnlockRules.Tap(8f)
        listOf(601L, 2000L, 5000L).forEach { duration ->
            tap.begin(100f, 200f, 1000L, 0)
            assertTrue(tap.finish(100f, 200f, 1000L + duration, 0, true))
        }
        tap.begin(100f, 200f, 1000L, 0)
        assertFalse(tap.finish(100f, 200f, 6000L, 0, false))
        tap.begin(100f, 200f, 1000L, 0)
        tap.move(130f, 200f)
        assertFalse(tap.finish(100f, 200f, 6000L, 0, true))
    }

    @Test fun changedUserOrInvalidTimeCannotDismiss() {
        val tap = FaceTapUnlockRules.Tap(8f)
        tap.begin(100f, 200f, 1000L, 0)
        assertFalse(tap.finish(100f, 200f, 1150L, 10, true))
        tap.begin(100f, 200f, 3000L, 0)
        assertFalse(tap.finish(100f, 200f, 2999L, 0, true))
    }

    @Test fun holdEntersAt600MillisecondsWithoutWaitingForReleaseAndOnlyOnce() {
        val tap = FaceTapUnlockRules.Tap(8f)
        tap.begin(100f, 200f, 1000L, 0)
        assertFalse(tap.finishHold(1599L, 0, true))
        assertTrue(tap.finishHold(1600L, 0, true))
        assertFalse(tap.finishHold(1700L, 0, true))
        assertFalse(tap.finish(100f, 200f, 1800L, 0, true))
    }

    @Test fun holdCannotEnterAfterMovementCancellationUserChangeOrRevocation() {
        val tap = FaceTapUnlockRules.Tap(8f)
        tap.begin(100f, 200f, 1000L, 0)
        assertFalse(tap.finishHold(1600L, 10, true))
        assertFalse(tap.finishHold(1600L, 0, false))
        assertFalse(tap.finishHold(999L, 0, true))
        tap.move(120f, 200f)
        assertFalse(tap.finishHold(1600L, 0, true))
        tap.begin(100f, 200f, 2000L, 0)
        tap.invalidate()
        assertFalse(tap.finishHold(2600L, 0, true))
        tap.reset()
        assertFalse(tap.finishHold(2600L, 0, true))
    }

    @Test fun failedHoldCannotBeReusedAfterAuthenticationReturns() {
        val tap = FaceTapUnlockRules.Tap(8f)
        tap.begin(100f, 200f, 1000L, 0)
        assertFalse(tap.finishHold(1600L, 0, false))
        assertFalse(tap.finishHold(1800L, 0, true))
        assertFalse(tap.finish(100f, 200f, 2000L, 0, true))
    }

    @Test fun nonFiniteCoordinatesCannotBeATap() {
        val tap = FaceTapUnlockRules.Tap(8f)
        tap.begin(100f, 200f, 1000L, 0)
        assertFalse(tap.finish(Float.NaN, 200f, 1150L, 0, true))
    }

    private val fingerArea = FaceTapUnlockRules.IconBounds(80, 180, 180, 280)

    @Test fun fingerprintHoldAllowsContactDriftWithinTheNativeIconArea() {
        val tap = FaceTapUnlockRules.Tap(8f)
        tap.begin(100f, 200f, 1000L, 0, fingerArea)
        tap.move(145f, 235f) // Beyond click slop, still inside OPPO's actual finger area.
        assertTrue(tap.validForFeedback)
        assertTrue(tap.finishHold(1600L, 0, true))
        assertFalse(tap.finish(145f, 235f, 1800L, 0, true))
    }

    @Test fun shortFingerprintPressUsesTheSameNativeHitArea() {
        val tap = FaceTapUnlockRules.Tap(8f)
        tap.begin(100f, 200f, 1000L, 0, fingerArea)
        tap.move(145f, 235f)
        assertTrue(tap.finish(145f, 235f, 1100L, 0, true))
    }

    @Test fun exitingTheFingerprintAreaCannotBeRearmedByReturning() {
        val tap = FaceTapUnlockRules.Tap(8f)
        tap.begin(100f, 200f, 1000L, 0, fingerArea)
        tap.move(180f, 200f)
        tap.move(100f, 200f)
        assertFalse(tap.finishHold(1600L, 0, true))
        assertFalse(tap.finish(100f, 200f, 1800L, 0, true))
    }

    @Test fun pointerDownTimeIdentifiesTheOriginalPressAcrossWindowForwarding() {
        val tap = FaceTapUnlockRules.Tap(8f)
        tap.begin(100f, 200f, 1000L, 0, fingerArea)
        assertTrue(tap.matchesPress(1000L))
        assertFalse(tap.matchesPress(1001L))
        tap.invalidate()
        assertTrue(tap.matchesPress(1000L)) // A duplicate cannot make this press valid again.
        assertFalse(tap.validForFeedback)
        tap.begin(100f, 200f, 2000L, 0, fingerArea)
        assertFalse(tap.matchesPress(1000L)) // An old timer cannot complete the new press.
        assertTrue(tap.matchesPress(2000L))
        assertFalse(tap.finishHold(2600L, 0, false))
        tap.reset()
        assertFalse(tap.matchesPress(2000L))
    }

    @Test fun invalidCoordinatesOrOutOfAreaDownCannotStartAnAuthorizedPress() {
        val tap = FaceTapUnlockRules.Tap(8f)
        listOf(Float.NaN to 200f, Float.POSITIVE_INFINITY to 200f, 79f to 200f,
            180f to 200f, 100f to 280f).forEach { (x, y) ->
            tap.begin(x, y, 1000L, 0, fingerArea)
            assertFalse(tap.validForFeedback)
            assertFalse(tap.finishHold(1600L, 0, true))
            tap.reset()
        }
    }

    @Test fun leavingTheNativeAreaOnReleaseDoesNotUnlock() {
        val tap = FaceTapUnlockRules.Tap(8f)
        tap.begin(100f, 200f, 1000L, 0, fingerArea)
        assertFalse(tap.finish(200f, 200f, 1200L, 0, true))
    }
}
