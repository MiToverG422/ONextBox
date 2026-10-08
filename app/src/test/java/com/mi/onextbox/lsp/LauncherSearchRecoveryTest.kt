package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherSearchRecoveryTest {
    private val cover = LauncherSearchLayout(true, 1080, 2520, 1, 1)
    private val inner = LauncherSearchLayout(false, 2240, 2268, 1, 2)

    @Test
    fun constructingBootstrapDoesNotProbeOemFeatures() {
        var probes = 0
        LauncherSearchBootstrap { probes++ }
        assertEquals(0, probes)
    }

    @Test
    fun missingContextDoesNotProbeOemFeatures() {
        var probes = 0
        val bootstrap = LauncherSearchBootstrap { probes++ }
        repeat(3) { bootstrap.onApplicationReady(contextReady = false) }
        assertEquals(0, probes)
        bootstrap.onApplicationReady(contextReady = true)
        assertEquals(1, probes)
    }

    @Test
    fun applicationReadyOnlyInstallsOnce() {
        var probes = 0
        val bootstrap = LauncherSearchBootstrap { probes++ }
        repeat(3) { bootstrap.onApplicationReady(contextReady = true) }
        assertEquals(1, probes)
    }

    @Test
    fun failedProbeIsNotRepeated() {
        var probes = 0
        val bootstrap = LauncherSearchBootstrap {
            probes++
            error("OEM feature probe failed")
        }
        assertTrue(runCatching { bootstrap.onApplicationReady(true) }.isFailure)
        bootstrap.onApplicationReady(true)
        assertEquals(1, probes)
    }

    @Test
    fun waitsForTwoMatchingLayouts() {
        val state = LauncherSearchRecovery()
        assertFalse(state.isStable(cover))
        assertTrue(state.isStable(cover))
    }

    @Test
    fun unfoldAndFoldBackEachRequireStability() {
        val state = LauncherSearchRecovery()
        for (layout in listOf(cover, inner, cover, inner, cover)) {
            assertFalse(state.isStable(layout))
            assertTrue(state.isStable(layout))
        }
    }

    @Test
    fun fastFoldChangesNeverUsePreviousScreen() {
        val state = LauncherSearchRecovery()
        assertFalse(state.isStable(cover))
        assertFalse(state.isStable(inner))
        assertFalse(state.isStable(cover))
        assertTrue(state.isStable(cover))
    }

    @Test
    fun transitionOrUnattachedLayerClearsStability() {
        val state = LauncherSearchRecovery()
        assertFalse(state.isStable(cover))
        assertFalse(state.isStable(null))
        assertFalse(state.isStable(cover))
        assertTrue(state.isStable(cover))
    }

    @Test
    fun ignoresUnmeasuredLayouts() {
        val state = LauncherSearchRecovery()
        for (layout in listOf(cover.copy(width = 0), cover.copy(height = 0))) {
            assertFalse(state.isStable(layout))
            assertFalse(state.isStable(layout))
        }
    }

    @Test
    fun recreatedContainerRequiresNewLayoutCheck() {
        val state = LauncherSearchRecovery()
        state.isStable(cover)
        assertTrue(state.isStable(cover))
        assertFalse(state.isStable(cover.copy(containerId = 2)))
        assertTrue(state.isStable(cover.copy(containerId = 2)))
    }

    @Test
    fun profileChangeRequiresNewLayoutCheck() {
        val state = LauncherSearchRecovery()
        state.isStable(cover)
        assertTrue(state.isStable(cover))
        assertFalse(state.isStable(cover.copy(profileId = 3)))
        assertTrue(state.isStable(cover.copy(profileId = 3)))
    }

    @Test
    fun internationalSupportIncludesTabletsAndFoldedPhones() {
        for (foldScreen in listOf(false, true)) {
            for (tablet in listOf(false, true)) {
                for (folded in listOf(false, true)) {
                    val supported = LauncherSearchRecoveryRules.supportsInternationalDevice(foldScreen, tablet, folded)
                    if (tablet || (foldScreen && folded)) assertTrue(supported) else assertFalse(supported)
                }
            }
        }
    }

    @Test
    fun repairRequiresBothParentModeAndOwnSwitch() {
        for (mode in listOf(-1, 0, 1, 2, 3)) {
            assertFalse(LauncherSearchRecoveryRules.isEnabled(mode, requested = false))
            assertEquals(mode == 1 || mode == 2, LauncherSearchRecoveryRules.isEnabled(mode, requested = true))
        }
    }

    @Test
    fun disablingParentDoesNotForgetChildChoice() {
        val childEnabled = true
        assertTrue(LauncherSearchRecoveryRules.isEnabled(2, childEnabled))
        assertFalse(LauncherSearchRecoveryRules.isEnabled(0, childEnabled))
        assertTrue(LauncherSearchRecoveryRules.isEnabled(1, childEnabled))
    }

    @Test
    fun tabletLandscapeFixDoesNotUnlockOtherModesOrPhones() {
        for (tablet in listOf(false, true)) {
            for (landscape in listOf(false, true)) {
                for (drawerOrStandard in listOf(false, true)) {
                    assertEquals(
                        tablet && landscape && drawerOrStandard,
                        LauncherSearchRecoveryRules.supportsTabletLayout(tablet, landscape, drawerOrStandard),
                    )
                }
            }
        }
    }

    @Test
    fun tabletRotationRequiresFreshStabilityEvenWithSameSquareBounds() {
        val portrait = LauncherSearchLayout(false, 2000, 2000, 1, 1, landscape = false)
        val landscape = portrait.copy(landscape = true)
        val state = LauncherSearchRecovery()
        for (layout in listOf(portrait, landscape, portrait)) {
            assertFalse(state.isStable(layout))
            assertTrue(state.isStable(layout))
        }
    }

    @Test
    fun neverOverridesNativeSwitchProviderOrLayoutRestrictions() {
        for (enabled in listOf(false, true)) {
            for (provider in listOf(false, true)) {
                for (layout in listOf(false, true)) {
                    val restore = LauncherSearchRecoveryRules.canRestore(enabled, provider, layout)
                    if (enabled && provider && layout) assertTrue(restore) else assertFalse(restore)
                }
            }
        }
    }
}
