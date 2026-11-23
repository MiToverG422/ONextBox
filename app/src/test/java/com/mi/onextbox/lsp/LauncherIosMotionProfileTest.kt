package com.mi.onextbox.lsp

import kotlin.math.PI
import org.junit.Assert.*
import org.junit.Test

class LauncherIosMotionProfileTest {
    @Test fun fullscreenDeformationAcceptsBothDisplayOrientations() {
        for ((width, height) in listOf(1080 to 2400, 2400 to 1080, 2800 to 2000)) {
            assertTrue(LauncherIosMotionProfile.supportsFullscreenFrame(
                width, height, fullscreen = true, widget = false, rotating = false,
            ))
        }
    }

    @Test fun unsupportedLandscapeFramesUseTheNativeTransform() {
        assertFalse(LauncherIosMotionProfile.supportsFullscreenFrame(
            2400, 1080, fullscreen = true, widget = false, rotating = true,
        ))
        assertFalse(LauncherIosMotionProfile.supportsFullscreenFrame(
            2400, 1080, fullscreen = false, widget = false, rotating = false,
        ))
        assertFalse(LauncherIosMotionProfile.supportsFullscreenFrame(
            2400, 1080, fullscreen = true, widget = true, rotating = false,
        ))
        assertFalse(LauncherIosMotionProfile.supportsFullscreenFrame(
            2400, 0, fullscreen = true, widget = false, rotating = false,
        ))
    }

    @Test fun nativeSpringsMatchSuppliedPluginAtFullBounce() {
        val config = LauncherIosMotionProfile.Config(bounceStrength = 100)
        val open = LauncherIosMotionProfile.spring("LAUNCH_FROM_HOME", config)!!
        val close = LauncherIosMotionProfile.spring("REMOTE_CLOSE_TO_HOME", config)!!
        val swipe = LauncherIosMotionProfile.spring("SWIPE_TO_HOME", config)!!
        assertEquals(((2 * PI / .24).let { it * it }).toFloat(), open.stiffness, .001f)
        assertEquals(((2 * PI / .336).let { it * it }).toFloat(), close.stiffness, .001f)
        assertEquals(close.stiffness, swipe.stiffness, 0f)
        assertEquals(1f, open.damping, 0f)
        assertEquals(.8f, swipe.damping, 1e-6f)
    }

    @Test fun existingSpeedSlowdownAndBounceControlsRemainEffective() {
        val config = LauncherIosMotionProfile.Config(openSpeed = 200, closeSpeed = 50, slowMotion = 200)
        assertEquals(.24, LauncherIosMotionProfile.response(true, config), 1e-12)
        assertEquals(1.344, LauncherIosMotionProfile.response(false, config), 1e-12)
        assertEquals(1f, LauncherIosMotionProfile.spring("SWIPE_TO_HOME", config)!!.damping, 0f)
        val partial = LauncherIosMotionProfile.spring("SWIPE_TO_HOME", config.copy(bounceStrength = 16))!!
        assertEquals(.968f, partial.damping, 1e-6f)
        assertNull(LauncherIosMotionProfile.spring("UNKNOWN_ROUTE", config))
    }

    @Test fun iconFadeKeepsBothDirectionsInTheSameWindowOpacityDomain() {
        assertEquals(.7f, LauncherIosMotionProfile.iconAlpha(.7f, 0f, true), 0f)
        assertEquals(.7f, LauncherIosMotionProfile.iconAlpha(.7f, .08f, true), 0f)
        assertEquals(0f, LauncherIosMotionProfile.iconAlpha(.7f, .53f, true), 1e-6f)
        assertEquals(0f, LauncherIosMotionProfile.iconAlpha(.7f, 1f, false), 0f)
        assertEquals(.7f, LauncherIosMotionProfile.iconAlpha(.7f, 0f, false), 0f)
        assertEquals(.35f, LauncherIosMotionProfile.iconAlpha(.7f, .305f, false), 1e-6f)
        assertEquals(0f, LauncherIosMotionProfile.iconAlpha(0f, .2f, true), 0f)
        assertEquals(.7f, LauncherIosMotionProfile.iconAlpha(.7f, Float.NaN, true), 0f)
    }

    @Test fun pendingSurfaceIsHiddenBeforeFitInBothDirectionsAndFallbackOnlyOnOpening() {
        assertEquals(0f, LauncherIosMotionProfile.iconAlphaBeforeFit(.7f, .006f, true, true), 0f)
        assertEquals(0f, LauncherIosMotionProfile.iconAlphaBeforeFit(.7f, .006f, false, true), 0f)
        assertEquals(0f, LauncherIosMotionProfile.iconAlphaBeforeFit(.7f, .006f, true, false), 0f)
        assertEquals(.7f, LauncherIosMotionProfile.iconAlphaBeforeFit(.7f, .006f, false, false), 0f)
        assertEquals(0f, LauncherIosMotionProfile.iconAlphaBeforeFit(.7f, .005f, true, true), 0f)
        assertEquals(0f, LauncherIosMotionProfile.iconAlphaBeforeFit(.7f, 0f, false, true), 0f)
        assertEquals(.7f, LauncherIosMotionProfile.iconAlphaBeforeFit(.7f, Float.NaN, true, true), 0f)
    }

    @Test fun iconAttachmentLandsOnNativeGeometryWithoutAnOpacityThresholdSnap() {
        assertEquals(0f, LauncherIosMotionProfile.iconAttachment(1.0), 0f)
        assertEquals(.5f, LauncherIosMotionProfile.iconAttachment(.94), 1e-6f)
        assertEquals(1f, LauncherIosMotionProfile.iconAttachment(.88), 1e-6f)
        assertTrue(LauncherIosMotionProfile.iconAttachment(.999) < .001f)
    }

    @Test fun dropletFollowsReferenceTimingAndActualCropLimit() {
        assertEquals(0f, LauncherIosDeformation.droplet(0f, true), 0f)
        assertEquals(0f, LauncherIosDeformation.droplet(1f, true), 0f)
        assertEquals(1f, LauncherIosDeformation.droplet(.3f, true), 0f)
        assertEquals(1f, LauncherIosDeformation.droplet(.5f, false), 0f)
        assertEquals(100f, LauncherIosMotionProfile.cornerRadius(10f, 200, 400, 1f), 0f)
        assertEquals(10f, LauncherIosMotionProfile.cornerRadius(10f, 200, 400, 0f), 0f)
    }

    @Test fun nativeAliasesAndGenerationProtectTheNewAnimation() {
        data class Surface(val native: Int, val wrapper: Int)
        val owners = LauncherIosSurfaceOwners<Surface> { a, b -> a.native == b.native }
        val older = LauncherIosSurfaceOwners.Lease(1)
        val newer = LauncherIosSurfaceOwners.Lease(2)
        assertTrue(owners.acquire(Surface(7, 1), older))
        assertFalse(owners.isEmpty)
        assertTrue(owners.acquire(Surface(7, 2), newer))
        assertTrue(owners.hasNewerOwner(Surface(7, 3), older))
        assertFalse(owners.hasNewerOwner(Surface(7, 3), newer))
        assertFalse(owners.canAcquire(Surface(7, 3), older))
        assertTrue(owners.detachLease(older).isEmpty())
        assertFalse(owners.releaseIfOwned(Surface(7, 1), older))
        assertEquals(listOf(Surface(7, 2)), owners.detachLease(newer))
        assertTrue(owners.isEmpty)
    }
}
