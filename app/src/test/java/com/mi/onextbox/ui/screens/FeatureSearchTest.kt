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
