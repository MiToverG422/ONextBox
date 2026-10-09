package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class FluidCloudNotificationMaterialRulesTest {
    @Test fun usesNotificationCenterThemeInsteadOfForcedPluginTheme() {
        assertEquals("SHADE_LIGHT", FluidCloudMaterialRules.notificationMode(false))
        assertEquals("SHADE_DARK", FluidCloudMaterialRules.notificationMode(true))
    }

    @Test fun wallpaperMaterialIsNotReplacedByFixedDarkShadeRecipe() {
        for (mode in listOf("WALLPAPER_A", "WALLPAPER_B", "SHADE_LIGHT", "SHADE_DARK")) {
            assertEquals(mode, FluidCloudMaterialRules.notificationMode(true, mode))
            assertEquals(mode, FluidCloudMaterialRules.notificationMode(false, mode))
        }
    }

    @Test fun unavailablePanelModeUsesSystemThemeWithoutHeadsUpRecipe() {
        for (mode in listOf(null, "UNINITIATED", "NO_BLUR_LIGHT", "NO_BLUR_DARK", "HEADS_UP_MATERIAL_DARK")) {
            assertEquals("SHADE_DARK", FluidCloudMaterialRules.notificationMode(true, mode))
            assertEquals("SHADE_LIGHT", FluidCloudMaterialRules.notificationMode(false, mode))
        }
    }

    @Test fun onlyExpandedAndPanelContentsUseNativeBackgroundOverride() {
        for (size in listOf("VIEW_SIZE_MD", "VIEW_SIZE_LG", "VIEW_SIZE_IMMERSIVE")) {
            assertTrue(FluidCloudMaterialRules.isExpandedTemplate(size))
        }
        for (size in listOf(null, "VIEW_SIZE_NONE", "VIEW_SIZE_SM", "UNKNOWN")) {
            assertFalse(FluidCloudMaterialRules.isExpandedTemplate(size))
        }
    }

    @Test fun onlyCustomNotificationRowsChangeMaterial() {
        assertTrue(FluidCloudMaterialRules.shouldUseNotificationMaterial(true, "CUSTOM"))
        for (type in listOf(null, "NOTIFICATION", "CAPSULE", "QS_TILE", "FULLSCREENBANNER")) {
            assertFalse(FluidCloudMaterialRules.shouldUseNotificationMaterial(true, type))
        }
    }

    @Test fun disabledFeatureLeavesEveryNativeCardUnchanged() {
        assertFalse(FluidCloudMaterialRules.shouldUseNotificationMaterial(false, "CUSTOM"))
        assertFalse(FluidCloudMaterialRules.shouldClearMiniBackground(false, 1))
        assertFalse(FluidCloudMaterialRules.canClearTemplate(false, true, false))
    }

    @Test fun miniCardActionCollectorIsNotModified() {
        assertFalse(FluidCloudMaterialRules.shouldClearMiniBackground(true, 0))
        assertTrue(FluidCloudMaterialRules.shouldClearMiniBackground(true, 1))
    }

    @Test fun capsuleTemplateNeverMatchesExpandedBackgroundTargets() {
        assertFalse(FluidCloudMaterialRules.SINGLE_BACKGROUND.contains("CapsulePage"))
        assertNotEquals("${FluidCloudMaterialRules.PAGE_PACKAGE}.d", FluidCloudMaterialRules.MINI_BACKGROUND)
    }

    @Test fun nativeAnimationAlphaIsForwardedToBlurAmount() {
        assertEquals(0f, FluidCloudMaterialRules.blurAmount(0), 0f)
        assertEquals(1f, FluidCloudMaterialRules.blurAmount(255), 0f)
        assertEquals(128f / 255f, FluidCloudMaterialRules.blurAmount(128), 0.00001f)
    }

    @Test fun alphaCannotOverflowNativeBlurAmount() {
        assertEquals(0f, FluidCloudMaterialRules.blurAmount(-100), 0f)
        assertEquals(1f, FluidCloudMaterialRules.blurAmount(500), 0f)
    }

    @Test fun templateStaysIntactWhenBlurOrNativeRendererIsUnavailable() {
        assertFalse(FluidCloudMaterialRules.canClearTemplate(true, false, false))
        assertFalse(FluidCloudMaterialRules.canClearTemplate(true, true, true))
        assertTrue(FluidCloudMaterialRules.canClearTemplate(true, true, false))
    }

    @Test fun matchingPlainNotificationIsAValidMaterialReference() {
        for (mode in listOf("SHADE_LIGHT", "SHADE_DARK", "WALLPAPER_A", "WALLPAPER_B")) {
            assertTrue(FluidCloudMaterialRules.canUseReference(mode, mode, false, false))
        }
    }

    @Test fun brandedAndStackedCardsCannotChangeTheCommonRecipe() {
        assertFalse(FluidCloudMaterialRules.canUseReference("SHADE_DARK", "SHADE_DARK", true, false))
        assertFalse(FluidCloudMaterialRules.canUseReference("SHADE_DARK", "SHADE_DARK", false, true))
    }

    @Test fun staleThemeAndHeadsUpReferenceAreRejected() {
        assertFalse(FluidCloudMaterialRules.canUseReference("SHADE_LIGHT", "SHADE_DARK", false, false))
        assertFalse(FluidCloudMaterialRules.canUseReference("WALLPAPER_A", "WALLPAPER_B", false, false))
        assertFalse(FluidCloudMaterialRules.canUseReference("HEADS_UP_MATERIAL_DARK", "HEADS_UP_MATERIAL_DARK", false, false))
        assertFalse(FluidCloudMaterialRules.canUseReference(null, null, false, false))
    }

    @Test fun unchangedFrameReusesGeometry() {
        val key = FluidCloudMaterialRules.StyleKey("SHADE_DARK", true, true, 1312, 400, 4, 5)
        assertEquals(key, key.copy())
    }

    @Test fun themeWallpaperMotionAndBoundsRefreshMaterial() {
        val key = FluidCloudMaterialRules.StyleKey("SHADE_DARK", true, true, 1312, 400, 4, 5)
        assertNotEquals(key, key.copy(mode = "WALLPAPER_B"))
        assertNotEquals(key, key.copy(night = false))
        assertNotEquals(key, key.copy(motion = false))
        assertNotEquals(key, key.copy(width = 1310))
        assertNotEquals(key, key.copy(height = 600))
    }

    @Test fun nativeResourceAndStyleChangesRefreshEvenWithSameBounds() {
        val key = FluidCloudMaterialRules.StyleKey("SHADE_LIGHT", false, false, 1312, 400, 4, 5)
        assertNotEquals(key, key.copy(resources = 6))
        assertNotEquals(key, key.copy(revision = 7))
    }

}
