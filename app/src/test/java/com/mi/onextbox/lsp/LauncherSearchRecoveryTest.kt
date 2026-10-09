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
    fun internationalSupportIncludesTabletsAndBothFoldableScreens() {
        for (foldScreen in listOf(false, true)) {
            for (tablet in listOf(false, true)) {
                val supported = LauncherSearchRecoveryRules.supportsInternationalDevice(foldScreen, tablet)
                assertEquals(tablet || foldScreen, supported)
            }
        }
    }

    @Test
    fun unfoldedColdStartDoesNotDependOnAnEarlierCoverLayout() {
        assertTrue(LauncherSearchRecoveryRules.supportsInternationalDevice(foldScreen = true, tablet = false))
        val state = LauncherSearchRecovery()
        assertFalse(state.isStable(inner))
        assertTrue(state.isStable(inner))
        assertTrue(LauncherSearchPage(true, false, true, true).canRebind)
    }

    @Test
    fun internationalLandscapeSupportsFoldablesWithoutUnlockingOtherModesOrPhones() {
        for (foldScreen in listOf(false, true)) {
            for (tablet in listOf(false, true)) {
                for (landscape in listOf(false, true)) {
                    for (drawerOrStandard in listOf(false, true)) {
                        assertEquals(
                            (foldScreen || tablet) && landscape && drawerOrStandard,
                            LauncherSearchRecoveryRules.supportsInternationalLayout(
                                foldScreen, tablet, landscape, drawerOrStandard,
                            ),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun pageRulesOnlyRebindOnResumedSettledHome() {
        for (resumed in listOf(false, true)) {
            for (transitioning in listOf(false, true)) {
                for (normal in listOf(false, true)) {
                    for (hotseatVisible in listOf(false, true)) {
                        val page = LauncherSearchPage(resumed, transitioning, normal, hotseatVisible)
                        assertEquals(resumed && !transitioning && normal, page.canRebind)
                        assertEquals(!resumed || (!transitioning && !hotseatVisible), page.hideBoundView)
                    }
                }
            }
        }
    }

    @Test
    fun drawerRoundTripUsesNativeAnimationThenSettledVisibility() {
        val home = LauncherSearchPage(true, false, true, true)
        val entering = home.copy(transitioning = true, normal = false, hotseatVisible = false)
        val drawer = entering.copy(transitioning = false)
        val returning = home.copy(transitioning = true)
        assertTrue(home.canRebind)
        assertFalse(home.hideBoundView)
        assertFalse(entering.canRebind)
        assertFalse(entering.hideBoundView)
        assertFalse(drawer.canRebind)
        assertTrue(drawer.hideBoundView)
        assertFalse(returning.canRebind)
        assertFalse(returning.hideBoundView)
        assertTrue(home.canRebind)
    }

    @Test
    fun delayedBindingCannotExposeSearchInDrawerOrPausedLauncher() {
        val home = LauncherSearchPage(true, false, true, true)
        for (page in listOf(
            home.copy(normal = false, hotseatVisible = false),
            home.copy(resumed = false),
            home.copy(resumed = false, transitioning = true),
        )) {
            assertFalse(page.canRebind)
            assertTrue(page.hideBoundView)
        }
    }

    @Test
    fun canceledDrawerTransitionCanRestoreHomeWithoutRecreatingDuringAnimation() {
        val interrupted = LauncherSearchPage(true, true, false, false)
        val reversing = interrupted.copy(normal = true, hotseatVisible = true)
        assertFalse(interrupted.canRebind)
        assertFalse(reversing.canRebind)
        assertFalse(reversing.hideBoundView)
        assertTrue(reversing.copy(transitioning = false).canRebind)
    }

    @Test
    fun otherPagesMayKeepNativeHotseatVisibleWithoutCreatingNewSearch() {
        val page = LauncherSearchPage(true, false, false, true)
        assertFalse(page.canRebind)
        assertFalse(page.hideBoundView)
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
