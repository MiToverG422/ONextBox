package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImmersiveNavigationRulesTest {
    @Test fun immersiveNavigationRequiresTheCustomHandleSwitch() {
        assertEquals(false, ImmersiveNavigationRules.isHandleOptionActive(false, false))
        assertEquals(false, ImmersiveNavigationRules.isHandleOptionActive(false, true))
        assertEquals(false, ImmersiveNavigationRules.isHandleOptionActive(true, false))
        assertEquals(true, ImmersiveNavigationRules.isHandleOptionActive(true, true))
    }

    @Test fun disablingParentLeavesNativeResourcesEvenWithImmersivePreferenceOn() {
        val active = ImmersiveNavigationRules.isHandleOptionActive(false, true)
        for ((pkg, name) in listOf(
            "android" to "navigation_bar_height",
            "com.android.systemui" to "navigation_handle_height",
            "oplus" to "navigation_gesture_view_width",
        )) {
            assertNull(ImmersiveNavigationRules.dimension(
                pkg, name, 360, true, immersiveEnabled = active, customLengthEnabled = false,
            ))
        }
    }

    @Test fun autoHideUsesFadedWhiteAndBlackWithTenPercentAlpha() {
        assertEquals(0x1affffff, fadedColor("navigation_bar_home_handle_dark_color_faded"))
        assertEquals(0x1a000000, fadedColor("navigation_bar_home_handle_light_color_faded"))
    }

    @Test fun autoHideDoesNotChangeBrightColorsOrOtherPackages() {
        for (name in listOf(
            "navigation_bar_home_handle_dark_color",
            "navigation_bar_home_handle_light_color",
            "navigation_bar_home_handle_dark_color_bright",
            "navigation_bar_home_handle_split_screen_color",
        )) {
            assertNull(fadedColor(name))
        }
        for (pkg in listOf("android", "example.app", "oplus")) {
            assertNull(ImmersiveNavigationRules.fadedHandleColor(
                pkg, "navigation_bar_home_handle_dark_color_faded", true, true,
            ))
        }
    }

    @Test fun disabledAutoHideAndThreeButtonNavigationKeepNativeColors() {
        for (name in listOf(
            "navigation_bar_home_handle_dark_color_faded",
            "navigation_bar_home_handle_light_color_faded",
        )) {
            assertNull(ImmersiveNavigationRules.fadedHandleColor("com.android.systemui", name, true, false))
            assertNull(ImmersiveNavigationRules.fadedHandleColor("com.android.systemui", name, false, true))
        }
    }

    @Test fun autoHideCannotRunWhenCustomHandleIsDisabled() {
        assertNull(ImmersiveNavigationRules.fadedHandleColor(
            "com.android.systemui", "navigation_bar_home_handle_dark_color_faded", true,
            autoHideEnabled = ImmersiveNavigationRules.isHandleOptionActive(false, true),
        ))
    }

    @Test fun autoHideComposesWithCustomOpacityWithoutLosingNativeFade() {
        val nativeAlpha = fadedColor("navigation_bar_home_handle_dark_color_faded")!! ushr 24
        assertEquals(26, ImmersiveNavigationRules.handleAlpha(nativeAlpha, -1))
        assertEquals(26, ImmersiveNavigationRules.handleAlpha(nativeAlpha, 100))
        assertEquals(13, ImmersiveNavigationRules.handleAlpha(nativeAlpha, 50))
        assertEquals(0, ImmersiveNavigationRules.handleAlpha(nativeAlpha, 0))
    }

    private fun fadedColor(name: String) = ImmersiveNavigationRules.fadedHandleColor(
        "com.android.systemui", name, gestureNavigation = true, autoHideEnabled = true,
    )

    @Test fun phoneNavigationInsetsUsePixels() {
        for (name in listOf("navigation_bar_height", "navigation_bar_height_landscape", "navigation_bar_width")) {
            assertEquals(
                ImmersiveNavigationRules.Dimension(1f, true),
                ImmersiveNavigationRules.dimension("android", name, 360, true),
            )
        }
    }

    @Test fun largeTabletRetainsNavigationSpace() {
        assertEquals(ImmersiveNavigationRules.Dimension(16f), dimension("android", "navigation_bar_height", 900))
        assertEquals(ImmersiveNavigationRules.Dimension(16f), dimension("android", "navigation_bar_height", 1000))
        assertEquals(ImmersiveNavigationRules.Dimension(1f, true), dimension("android", "navigation_bar_height", 899))
    }

    @Test fun gestureTouchAreaRemainsIndependentOfInsets() {
        assertEquals(ImmersiveNavigationRules.Dimension(32f), dimension("android", "navigation_bar_gesture_height"))
        assertNull(dimension("android", "navigation_bar_frame_height_gestural"))
    }

    @Test fun handleMatchesDimensions() {
        for ((name, value) in mapOf(
            "navigation_handle_radius" to 20f,
            "navigation_handle_height" to 4f,
            "navigation_handle_bottom" to 2.199982f,
            "navigation_home_handle_width" to 92f,
        )) {
            assertEquals(ImmersiveNavigationRules.Dimension(value), dimension("com.android.systemui", name))
        }
    }

    @Test fun threeButtonNavigationIsUntouched() {
        assertNull(ImmersiveNavigationRules.dimension("android", "navigation_bar_height", 360, false))
        assertNull(ImmersiveNavigationRules.dimension("com.android.systemui", "navigation_handle_height", 360, false))
        assertNull(ImmersiveNavigationRules.boolean("android", "config_navBarNeedsScrim", false))
    }

    @Test fun otherPackagesAndResourcesAreUntouched() {
        assertNull(dimension("example.app", "navigation_bar_height"))
        assertNull(dimension("android", "navigation_handle_height"))
        assertNull(dimension("com.android.systemui", "navigation_bar_height"))
        assertNull(dimension("android", "status_bar_height"))
        assertNull(dimension("android", "input_method_navigation_bar_height"))
        assertNull(dimension("android", "taskbar_frame_height"))
    }

    @Test fun removesOnlyNavigationScrimAndTransitionAttachment() {
        assertEquals(false, ImmersiveNavigationRules.boolean("android", "config_navBarNeedsScrim", true))
        assertEquals(false, ImmersiveNavigationRules.boolean("android", "config_attachNavBarToAppDuringTransition", true))
        assertEquals(true, ImmersiveNavigationRules.boolean("android", "config_navBarTapThrough", true))
        assertNull(ImmersiveNavigationRules.boolean("example.app", "config_navBarTapThrough", true))
    }

    @Test fun doesNotOverrideRotationImeOrNavigationMode() {
        assertNull(ImmersiveNavigationRules.boolean("android", "config_allowSeamlessRotationDespiteNavBarMoving", true))
        assertNull(ImmersiveNavigationRules.boolean("android", "config_imeDrawsImeNavBar", true))
        assertNull(ImmersiveNavigationRules.boolean("android", "config_navBarInteractionMode", true))
    }

    @Test fun customLengthWorksWithoutImmersiveNavigation() {
        for ((pkg, name) in listOf(
            "oplus" to "navigation_gesture_view_width",
            "oplus" to "navigation_gesture_view_landscape_width",
            "com.android.systemui" to "navigation_home_handle_width",
        )) {
            assertEquals(ImmersiveNavigationRules.Dimension(144f), customLength(pkg, name, 144))
        }
    }

    @Test fun lengthCannotCollapseOrGrowBeyondRange() {
        assertEquals(40, ImmersiveNavigationRules.normalizeLength(Int.MIN_VALUE))
        assertEquals(40, ImmersiveNavigationRules.normalizeLength(0))
        assertEquals(200, ImmersiveNavigationRules.normalizeLength(Int.MAX_VALUE))
        assertEquals(92, ImmersiveNavigationRules.normalizeLength(ImmersiveNavigationRules.DEFAULT_LENGTH_DP))
        assertEquals(ImmersiveNavigationRules.Dimension(200f), customLength("oplus", "navigation_gesture_view_width", 201))
    }

    @Test fun lengthDoesNotChangeInsetsHeightOrOtherPackages() {
        for ((pkg, name) in listOf(
            "android" to "navigation_bar_height",
            "android" to "navigation_bar_width",
            "com.android.systemui" to "navigation_handle_height",
            "com.android.systemui" to "navigation_handle_bottom",
            "example.app" to "navigation_home_handle_width",
            "com.android.systemui" to "navigation_gesture_view_width",
        )) {
            assertNull(customLength(pkg, name, 144))
        }
    }

    @Test fun customLengthDoesNotTouchThreeButtonNavigation() {
        assertNull(ImmersiveNavigationRules.dimension(
            "oplus", "navigation_gesture_view_width", 360, gestureNavigation = false,
            customLengthEnabled = true, lengthDp = 144,
        ))
    }

    @Test fun disabledCustomLengthKeepsImmersiveDefaultsAndNativeOplusWidth() {
        assertEquals(ImmersiveNavigationRules.Dimension(92f), dimension("com.android.systemui", "navigation_home_handle_width"))
        assertNull(dimension("oplus", "navigation_gesture_view_width"))
        assertNull(ImmersiveNavigationRules.dimension(
            "com.android.systemui", "navigation_home_handle_width", 360, true, immersiveEnabled = false,
        ))
    }

    @Test fun customLengthTakesPrecedenceWithoutChangingImmersiveInsets() {
        assertEquals(ImmersiveNavigationRules.Dimension(144f), ImmersiveNavigationRules.dimension(
            "com.android.systemui", "navigation_home_handle_width", 360, true,
            customLengthEnabled = true, lengthDp = 144,
        ))
        assertEquals(ImmersiveNavigationRules.Dimension(1f, true), ImmersiveNavigationRules.dimension(
            "android", "navigation_bar_height", 360, true,
            customLengthEnabled = true, lengthDp = 144,
        ))
    }

    @Test fun systemDefaultLengthKeepsEachNativeResource() {
        for ((pkg, name) in listOf(
            "oplus" to "navigation_gesture_view_width",
            "oplus" to "navigation_gesture_view_landscape_width",
            "com.android.systemui" to "navigation_home_handle_width",
        )) {
            for (immersive in listOf(false, true)) {
                assertNull(ImmersiveNavigationRules.dimension(
                    pkg, name, 360, true, immersiveEnabled = immersive,
                    customLengthEnabled = true,
                ))
            }
        }
    }

    @Test fun lengthPreferencePreservesSystemDefaultAndExistingCustomValues() {
        assertEquals(-1, ImmersiveNavigationRules.normalizeLengthPreference(-1))
        assertEquals(92, ImmersiveNavigationRules.normalizeLengthPreference(92))
        assertEquals(120, ImmersiveNavigationRules.normalizeLengthPreference(120))
        assertEquals(40, ImmersiveNavigationRules.normalizeLengthPreference(0))
        assertEquals(200, ImmersiveNavigationRules.normalizeLengthPreference(Int.MAX_VALUE))
    }

    @Test fun opacityPreferencePreservesSystemDefaultAndClampsPercentages() {
        assertEquals(-1, ImmersiveNavigationRules.normalizeOpacity(-1))
        assertEquals(0, ImmersiveNavigationRules.normalizeOpacity(Int.MIN_VALUE))
        assertEquals(0, ImmersiveNavigationRules.normalizeOpacity(0))
        assertEquals(60, ImmersiveNavigationRules.normalizeOpacity(60))
        assertEquals(100, ImmersiveNavigationRules.normalizeOpacity(Int.MAX_VALUE))
    }

    @Test fun slidingLengthBackToNativeValueRestoresSystemDefault() {
        assertEquals(144, ImmersiveNavigationRules.lengthPreferenceFromSlider(144, 120))
        assertEquals(-1, ImmersiveNavigationRules.lengthPreferenceFromSlider(120, 120))
        assertEquals(-1, ImmersiveNavigationRules.lengthPreferenceFromSlider(-1, 120))
        assertNull(customLength("oplus", "navigation_gesture_view_width",
            ImmersiveNavigationRules.lengthPreferenceFromSlider(120, 120)))
    }

    @Test fun sliderDefaultTracksTheCurrentOrientationNotAFixedLength() {
        assertEquals(-1, ImmersiveNavigationRules.lengthPreferenceFromSlider(88, 88))
        assertEquals(120, ImmersiveNavigationRules.lengthPreferenceFromSlider(120, 88))
        assertEquals(92, ImmersiveNavigationRules.lengthPreferenceFromSlider(92, 120))
        assertEquals(-1, ImmersiveNavigationRules.lengthPreferenceFromSlider(40, 40))
        assertEquals(-1, ImmersiveNavigationRules.lengthPreferenceFromSlider(200, 200))
    }

    @Test fun unknownOrUnreachableNativeLengthIsNotGuessedFromTheSlider() {
        assertEquals(92, ImmersiveNavigationRules.lengthPreferenceFromSlider(92, null))
        assertEquals(40, ImmersiveNavigationRules.lengthPreferenceFromSlider(40, 20))
        assertEquals(200, ImmersiveNavigationRules.lengthPreferenceFromSlider(200, 240))
        assertEquals(-1, ImmersiveNavigationRules.lengthPreferenceFromSlider(-1, null))
    }

    @Test fun slidingOpacityToFullRestoresNativeOpacity() {
        assertEquals(0, ImmersiveNavigationRules.opacityPreferenceFromSlider(0))
        assertEquals(75, ImmersiveNavigationRules.opacityPreferenceFromSlider(75))
        assertEquals(99, ImmersiveNavigationRules.opacityPreferenceFromSlider(99))
        assertEquals(-1, ImmersiveNavigationRules.opacityPreferenceFromSlider(100))
        assertEquals(-1, ImmersiveNavigationRules.opacityPreferenceFromSlider(-1))
        assertEquals(26, ImmersiveNavigationRules.handleAlpha(
            26, ImmersiveNavigationRules.opacityPreferenceFromSlider(100),
        ))
    }

    @Test fun defaultAndFullOpacityLeaveSystemAlphaUnchanged() {
        for (alpha in listOf(0, 32, 128, 200, 255)) {
            assertEquals(alpha, ImmersiveNavigationRules.handleAlpha(alpha, -1))
            assertEquals(alpha, ImmersiveNavigationRules.handleAlpha(alpha, 100))
        }
    }

    @Test fun opacityScalesNativeFadedAndBrightColorsWithoutAccumulating() {
        assertEquals(128, ImmersiveNavigationRules.handleAlpha(255, 50))
        assertEquals(64, ImmersiveNavigationRules.handleAlpha(128, 50))
        assertEquals(32, ImmersiveNavigationRules.handleAlpha(64, 50))
        assertEquals(128, ImmersiveNavigationRules.handleAlpha(255, 50))
        assertEquals(255, ImmersiveNavigationRules.handleAlpha(255, -1))
    }

    @Test fun zeroOpacityHidesOnlyTheHandlePaint() {
        for (alpha in listOf(0, 128, 255)) {
            assertEquals(0, ImmersiveNavigationRules.handleAlpha(alpha, 0))
        }
        assertNull(customLength("android", "navigation_bar_gesture_height", ImmersiveNavigationRules.SYSTEM_DEFAULT))
    }

    @Test fun opacityResultNeverExceedsNativeAlpha() {
        for (alpha in 0..255) {
            for (opacity in 0..100) {
                val result = ImmersiveNavigationRules.handleAlpha(alpha, opacity)
                org.junit.Assert.assertTrue(result in 0..alpha)
            }
        }
    }

    private fun customLength(pkg: String, name: String, value: Int) = ImmersiveNavigationRules.dimension(
        pkg, name, 360, true, immersiveEnabled = false, customLengthEnabled = true, lengthDp = value,
    )

    private fun dimension(pkg: String, name: String, width: Int = 360) =
        ImmersiveNavigationRules.dimension(pkg, name, width, true)
}
