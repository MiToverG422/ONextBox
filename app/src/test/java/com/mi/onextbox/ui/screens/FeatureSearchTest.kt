package com.mi.onextbox.ui.screens

import org.junit.Assert.*
import org.junit.Test

class FeatureSearchTest {
    @Test fun blankQueriesDoNotReturnEverything() {
        assertEquals(0, featureSearchScore("   ", "复制验证码", "通知"))
    }
    @Test fun matchesTitlesAndDescriptions() {
        assertEquals(3, featureSearchScore("复制验证码", "复制验证码", "通知"))
        assertEquals(2, featureSearchScore("验证码", "复制验证码", "通知"))
        assertEquals(1, featureSearchScore("短信", "复制验证码", "短信通知"))
        assertEquals(0, featureSearchScore("RCS", "复制验证码", "通知"))
    }
    @Test fun caseWidthAndMultipleWordsAreNormalized() {
        assertEquals(3, featureSearchScore(" ＧＥＭＩＮＩ ", "Gemini", ""))
        assertEquals(1, featureSearchScore("copy code", "Copy", "verification code"))
        assertEquals(0, featureSearchScore("copy rcs", "Copy", "verification code"))
    }
    @Test fun indexHasUniqueDestinationsAndNoMainPage() {
        val entries = FeatureSearchIndex.entries
        assertEquals(entries.size, entries.distinctBy { it.page to it.title }.size)
        assertFalse(entries.any { it.page == FeaturePageMode.Main })
        assertTrue(entries.any { it.page == FeaturePageMode.GoogleMessages })
        assertTrue(entries.any { it.page == FeaturePageMode.TouchSampling })
        assertTrue(entries.any { it.page == FeaturePageMode.SystemUiSmallWindow })
    }
    @Test fun brightnessAndRadiusSlidersAreSearchable() {
        for (title in listOf(
            com.mi.onextbox.R.string.feature_aod_dark_brightness_label,
            com.mi.onextbox.R.string.feature_aod_bright_brightness_label,
            com.mi.onextbox.R.string.feature_aod_multiplier_label,
        )) {
            assertTrue(FeatureSearchIndex.entries.any {
                it.page == FeaturePageMode.Aod && it.title == title
            })
        }
        assertTrue(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.Desktop && it.title == com.mi.onextbox.R.string.feature_recent_task_radius_label
        })
    }
    @Test fun recentTaskCardRadiusHasNoDescription() {
        val entry = FeatureSearchIndex.entries.single {
            it.page == FeaturePageMode.Desktop && it.title == com.mi.onextbox.R.string.feature_recent_task_radius_title
        }
        assertEquals(0, entry.description)
    }
    @Test fun bundledSettingsAndExpressFeaturesAreSearchable() {
        assertTrue(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.Settings && it.title == com.mi.onextbox.R.string.feature_settings_title_collapsed_title
        })
        assertTrue(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.QuickAppServices && it.title == com.mi.onextbox.R.string.feature_express_no_mini_program_title
        })
    }
    @Test fun expressServiceHasItsOwnTopLevelPage() {
        assertFalse(FeaturePageMode.QuickAppServices.isNestedPage)
        for (title in listOf(
            com.mi.onextbox.R.string.feature_quick_app_services_title,
            com.mi.onextbox.R.string.feature_group_express_service,
            com.mi.onextbox.R.string.feature_express_no_mini_program_title,
        )) {
            val destinations = FeatureSearchIndex.entries.filter { it.title == title }
            assertEquals(1, destinations.size)
            assertEquals(FeaturePageMode.QuickAppServices, destinations.single().page)
        }
    }
    @Test fun fluidCloudPageRemainsSearchableWithoutAnyControls() {
        assertTrue(FeaturePageMode.SystemUiFluidCloud.isNestedPage)
        val destinations = FeatureSearchIndex.entries.filter { it.page == FeaturePageMode.SystemUiFluidCloud }
        assertEquals(1, destinations.size)
        assertEquals(com.mi.onextbox.R.string.feature_fluid_cloud_title, destinations.single().title)
    }
    @Test fun fluidCloudBatteryIsSearchableOnlyUnderStatusBar() {
        val destinations = FeatureSearchIndex.entries.filter {
            it.title == com.mi.onextbox.R.string.feature_fluid_cloud_battery_title
        }
        assertEquals(1, destinations.size)
        assertEquals(FeaturePageMode.SystemUiStatusBar, destinations.single().page)
        assertEquals(com.mi.onextbox.R.string.feature_fluid_cloud_battery_summary, destinations.single().description)
    }
    @Test fun faceTapControlsAreSearchableOnlyUnderLockScreen() {
        assertTrue(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.SystemUiLockScreen && it.title == com.mi.onextbox.R.string.keyguard_face_tap_title
        })
        assertTrue(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.SystemUiLockScreen && it.title == com.mi.onextbox.R.string.keyguard_face_tap_animation_title
        })
        assertTrue(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.SystemUiLockScreen && it.title == com.mi.onextbox.R.string.keyguard_page_title
        })
        assertFalse(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.SystemUiNative &&
                (it.title == com.mi.onextbox.R.string.keyguard_face_tap_title ||
                    it.title == com.mi.onextbox.R.string.keyguard_face_tap_animation_title)
        })
    }
    @Test fun immersiveNavigationHasItsOwnDestination() {
        assertTrue(FeaturePageMode.SystemUiNavigationBar.isNestedPage)
        assertTrue(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.SystemUiNavigationBar &&
                it.title == com.mi.onextbox.R.string.feature_immersive_navigation_title &&
                it.description == com.mi.onextbox.R.string.feature_immersive_navigation_summary
        })
        assertTrue(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.SystemUiNavigationBar &&
                it.title == com.mi.onextbox.R.string.feature_navigation_handle_length_title &&
                it.description == 0
        })
        for (title in listOf(
            com.mi.onextbox.R.string.feature_navigation_handle_length_label,
            com.mi.onextbox.R.string.feature_navigation_handle_opacity_label,
        )) {
            assertTrue(FeatureSearchIndex.entries.any {
                it.page == FeaturePageMode.SystemUiNavigationBar && it.title == title
            })
        }
    }
    @Test fun captureControlsHaveTheirOwnDestinations() {
        assertTrue(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.Screenshot && it.title == com.mi.onextbox.R.string.keyguard_aod_screenshot_title
        })
        assertTrue(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.ScreenRecording && it.title == com.mi.onextbox.R.string.keyguard_screen_off_recording_title
        })
        assertFalse(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.SystemUiLockScreen &&
                (it.title == com.mi.onextbox.R.string.keyguard_aod_screenshot_title ||
                    it.title == com.mi.onextbox.R.string.keyguard_screen_off_recording_title)
        })
    }
    @Test fun autoHideHandleIsSearchableUnderNavigationBar() {
        assertTrue(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.SystemUiNavigationBar &&
                it.title == com.mi.onextbox.R.string.feature_navigation_handle_auto_hide_title &&
                it.description == com.mi.onextbox.R.string.feature_navigation_handle_auto_hide_summary
        })
    }
    @Test fun everySmallWindowControlIsSearchable() {
        val indexed = FeatureSearchIndex.entries.filter { it.page == FeaturePageMode.SystemUiSmallWindow }.map { it.title }.toSet()
        val controls = setOf(
            com.mi.onextbox.R.string.small_window_white_bar,
            com.mi.onextbox.R.string.small_window_safe_inset,
            com.mi.onextbox.R.string.small_window_hide_recents,
            com.mi.onextbox.R.string.small_window_keep_running,
            com.mi.onextbox.R.string.small_window_mute_stashed,
            com.mi.onextbox.R.string.small_window_landscape_ratio,
            com.mi.onextbox.R.string.small_window_larger_size,
            com.mi.onextbox.R.string.small_window_compact_caption,
            com.mi.onextbox.R.string.small_window_unlimited_count,
            com.mi.onextbox.R.string.small_window_unlimited_frame_rate,
        )
        assertTrue(indexed.containsAll(controls))
    }
}
