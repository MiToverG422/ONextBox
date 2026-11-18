package com.mi.onextbox.lsp

import kotlin.math.PI
import kotlin.math.exp
import org.junit.Assert.*
import org.junit.Test

class LauncherIosBounceRecoveryTest {
    @Test fun normalHomeTargetsAndSmallIconAdjustmentsAreNotOpeningContinuations() {
        assertFalse(LauncherIosBounceRecovery.opensWindow(2_500_000.0, 28_000.0, 30_000.0))
        assertFalse(LauncherIosBounceRecovery.opensWindow(2_500_000.0, 28_000.0, 500_000.0))
        assertTrue(LauncherIosBounceRecovery.opensWindow(2_500_000.0, 28_000.0, 2_500_000.0))
        assertFalse(LauncherIosBounceRecovery.opensWindow(0.0, 28_000.0, 2_500_000.0))
    }

    @Test fun enteringAnAlreadyFullScreenAppDiscardsAnyRemainingReturnHomeVelocity() {
        assertEquals(0f, LauncherIosBounceRecovery.velocity(1080f, 1080f, 1500f, .24), 0f)
        assertEquals(0f, LauncherIosBounceRecovery.velocity(1080f, 1080f, -1500f, .24), 0f)
    }

    @Test fun validOpeningMomentumIsPreservedAndReverseBounceIsStopped() {
        assertEquals(500f, LauncherIosBounceRecovery.velocity(500f, 1080f, 500f, .24), 0f)
        assertEquals(0f, LauncherIosBounceRecovery.velocity(500f, 1080f, -500f, .24), 0f)
        assertEquals(-100f, LauncherIosBounceRecovery.velocity(10f, 0f, -100f, .24), 0f)
        assertEquals(0f, LauncherIosBounceRecovery.velocity(10f, 0f, 100f, .24), 0f)
    }

    @Test fun inheritedHighEnergyCannotCarryTheOpeningPastItsDestination() {
        for ((start, end, incoming) in listOf(Triple(1070f, 1080f, 4000f), Triple(10f, 0f, -4000f),
                Triple(1f, 2.2f, 100f), Triple(1f, 1f, 10f))) {
            val omega = 2.0 * PI / .24
            val v = LauncherIosBounceRecovery.velocity(start, end, incoming, .24)
            for (frame in 0..180) {
                val time = frame / 120.0
                val distance = start - end
                val value = end + (distance + (v + omega * distance) * time) * exp(-omega * time)
                assertTrue("$start -> $end at $frame: $value", value >= minOf(start, end) - .00001 && value <= maxOf(start, end) + .00001)
            }
        }
    }

    @Test fun invalidSamplesDoNotReplaceTheNativeState() {
        assertEquals(50f, LauncherIosBounceRecovery.velocity(Float.NaN, 1080f, 50f, .24), 0f)
        assertEquals(50f, LauncherIosBounceRecovery.velocity(1f, 1080f, 50f, 0.0), 0f)
    }

    @Test fun appEntrySupersedesAllPendingHomeBouncesAndLateCallbacksCannotReenableThem() {
        val lifecycle = LauncherIosBounceLifecycle<Any>()
        lifecycle.startDesktop(); lifecycle.resumeDesktop()
        val home1 = Any(); val home2 = Any(); val app = Any()
        lifecycle.bind(home1, LauncherIosMotionProfile.RouteKind.SwipeToHome)
        lifecycle.bind(home2, LauncherIosMotionProfile.RouteKind.SwipeToHome)
        assertTrue(lifecycle.allows(home1))
        assertEquals(setOf(home1, home2), lifecycle.bind(app, LauncherIosMotionProfile.RouteKind.Opening).toSet())
        repeat(10) {
            assertFalse(lifecycle.allows(home1))
            assertFalse(lifecycle.allows(home2))
            assertTrue(lifecycle.superseded(home1))
        }
        val nextHome = Any()
        lifecycle.startDesktop(); lifecycle.resumeDesktop()
        lifecycle.bind(nextHome, LauncherIosMotionProfile.RouteKind.SwipeToHome)
        assertTrue(lifecycle.allows(nextHome))
        assertFalse(lifecycle.allows(app))
    }

    @Test fun aReusedControllerMayBounceOnlyAfterItIsBoundToANewRealHomeTransition() {
        val lifecycle = LauncherIosBounceLifecycle<Any>()
        val owner = Any()
        lifecycle.bind(owner, LauncherIosMotionProfile.RouteKind.Opening)
        assertFalse(lifecycle.allows(owner))
        lifecycle.startDesktop(); lifecycle.resumeDesktop()
        lifecycle.bind(owner, LauncherIosMotionProfile.RouteKind.SwipeToHome)
        assertTrue(lifecycle.allows(owner))
        lifecycle.open(owner)
        assertFalse(lifecycle.allows(owner))
        lifecycle.home(owner)
        assertTrue(lifecycle.allows(owner))
        assertNull(lifecycle.opening(Any()))
    }

    @Test fun aHomeCallbackQueuedAfterTheDesktopIsHiddenCannotStartABounce() {
        val lifecycle = LauncherIosBounceLifecycle<Any>()
        lifecycle.startDesktop(); lifecycle.resumeDesktop()
        val old = Any()
        lifecycle.bind(old, LauncherIosMotionProfile.RouteKind.SwipeToHome)
        assertEquals(listOf(old), lifecycle.leaveDesktop())
        val delayed = Any()
        lifecycle.bind(delayed, LauncherIosMotionProfile.RouteKind.SwipeToHome)
        assertFalse(lifecycle.allows(delayed))
        assertTrue(lifecycle.superseded(delayed))
        lifecycle.startDesktop(); lifecycle.resumeDesktop()
        assertFalse(lifecycle.allows(delayed))
        val genuineExit = Any()
        lifecycle.bind(genuineExit, LauncherIosMotionProfile.RouteKind.SwipeToHome)
        assertTrue(lifecycle.allows(genuineExit))
    }

    @Test fun startingTheDesktopWithoutResumingItDoesNotPermitBackgroundBounce() {
        val lifecycle = LauncherIosBounceLifecycle<Any>()
        val owner = Any()
        lifecycle.startDesktop()
        lifecycle.bind(owner, LauncherIosMotionProfile.RouteKind.SwipeToHome)
        assertFalse(lifecycle.allows(owner))
        assertEquals(listOf(owner), lifecycle.resumeDesktop())
        assertTrue(lifecycle.allows(owner))
        lifecycle.leaveDesktop()
        assertFalse(lifecycle.allows(owner))
    }

    @Test fun tappingAnAppDuringBounceRevokesTheOldControllerBeforeItIsReversed() {
        val lifecycle = LauncherIosBounceLifecycle<Any>()
        val spring = Any()
        lifecycle.resumeDesktop()
        lifecycle.bind(spring, LauncherIosMotionProfile.RouteKind.SwipeToHome)
        assertTrue(lifecycle.allows(spring))

        assertEquals(listOf(spring), lifecycle.revokeHomeBounces())
        lifecycle.open(spring)
        assertTrue(lifecycle.wasHome(spring))
        assertEquals(LauncherIosMotionProfile.RouteKind.Opening, lifecycle.route(spring))

        lifecycle.resumeDesktop()
        lifecycle.bind(spring, LauncherIosMotionProfile.RouteKind.SwipeToHome)
        assertEquals(LauncherIosMotionProfile.RouteKind.Opening, lifecycle.route(spring))
        lifecycle.home(spring)
        assertFalse(lifecycle.allows(spring))
        val nextExit = Any()
        lifecycle.bind(nextExit, LauncherIosMotionProfile.RouteKind.SwipeToHome)
        assertTrue(lifecycle.allows(nextExit))
    }

    @Test fun copiedBounceSpeedMustBeDiscardedOnceBeforeTheFirstOpeningFrame() {
        val lifecycle = LauncherIosBounceLifecycle<Any>()
        val freshOpening = Any()
        val copiedOpening = Any()
        lifecycle.open(copiedOpening, seedFromBounce = true)
        lifecycle.bind(copiedOpening, LauncherIosMotionProfile.RouteKind.Opening)
        assertTrue(lifecycle.takeBounceSeed(copiedOpening))
        assertFalse(lifecycle.takeBounceSeed(copiedOpening))
        lifecycle.bind(freshOpening, LauncherIosMotionProfile.RouteKind.Opening)
        assertFalse(lifecycle.takeBounceSeed(freshOpening))
    }

    @Test fun aFreshNativeHomeRequestAfterRepeatedReversalsCanBounceBeforeDesktopResumes() {
        val lifecycle = LauncherIosBounceLifecycle<Any>()
        val home = LauncherIosMotionProfile.RouteKind.SwipeToHome
        val opening = LauncherIosMotionProfile.RouteKind.Opening
        lifecycle.resumeDesktop()
        repeat(4) {
            val leaving = Any()
            lifecycle.bind(leaving, home, lifecycle.homeRequestGeneration())
            assertTrue(lifecycle.allows(leaving))
            assertEquals(listOf(leaving), lifecycle.revokeHomeBounces())
            lifecycle.open(leaving)
            lifecycle.bind(Any(), opening)
            assertFalse(lifecycle.allows(leaving))
        }
        val finalExit = Any()
        lifecycle.bind(finalExit, home, lifecycle.homeRequestGeneration())
        assertTrue(lifecycle.allows(finalExit))
        assertEquals(listOf(finalExit), lifecycle.revokeHomeBounces())
        lifecycle.open(finalExit)
        assertFalse(lifecycle.allows(finalExit))
    }

    @Test fun aNativeHomeRequestFromBeforeAppEntryStaysExpiredEvenAfterDesktopResumes() {
        val lifecycle = LauncherIosBounceLifecycle<Any>()
        val home = LauncherIosMotionProfile.RouteKind.SwipeToHome
        val olderRequest = lifecycle.homeRequestGeneration()
        lifecycle.bind(Any(), LauncherIosMotionProfile.RouteKind.Opening)
        lifecycle.resumeDesktop()
        val oldExit = Any()
        lifecycle.bind(oldExit, home, olderRequest)
        assertTrue(lifecycle.superseded(oldExit))
        assertFalse(lifecycle.allows(oldExit))
        lifecycle.bind(oldExit, home, lifecycle.homeRequestGeneration())
        assertFalse(lifecycle.allows(oldExit))
    }

    @Test fun anOpeningListenerOverridesAStaleNativeHomeAnimationLabel() {
        val config = LauncherIosMotionProfile.Config(bounceStrength = 100)
        val spec = LauncherIosMotionProfile.spring("SWIPE_TO_HOME", config, LauncherIosMotionProfile.RouteKind.Opening)!!
        assertEquals(1f, spec.damping, 0f)
        assertEquals(LauncherIosMotionProfile.spring("LAUNCH_FROM_HOME", config)!!.stiffness, spec.stiffness, 0f)
        assertEquals(.8f, LauncherIosMotionProfile.spring("SWIPE_TO_HOME", config)!!.damping, 1e-6f)
    }
}
