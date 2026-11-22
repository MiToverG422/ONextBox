package com.mi.onextbox.ui.screens

import com.mi.onextbox.R
import com.mi.onextbox.lsp.SystemAdaptationVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureRecommendationsTest {
    @Test fun lockScreenHandleOpensTheLockScreenRowFromRelatedPages() {
        val sources = listOf(FeaturePageMode.SystemUiNavigationBar, FeaturePageMode.SystemUiBackGesture, FeaturePageMode.Aod)
        for (version in listOf(SystemAdaptationVersion.ColorOs16, SystemAdaptationVersion.ColorOs17)) {
            for (source in sources) {
                val entry = FeatureRecommendations.destinations(source, version).single { it.title == R.string.keyguard_gesture_handle_title }
                assertEquals(FeaturePageMode.SystemUiLockScreen, entry.page)
            }
        }
    }

    @Test fun mobileNetworkRecommendsNativeFiveGaOnlyOnSupportedVersions() {
        fun recommendsFiveGa(version: SystemAdaptationVersion) =
            FeatureRecommendations.destinations(FeaturePageMode.MobileNetwork, version).any {
                it.title == R.string.native_5ga_title && it.page == FeaturePageMode.GlobalNetwork
            }
        assertTrue(recommendsFiveGa(SystemAdaptationVersion.ColorOs17))
        assertFalse(recommendsFiveGa(SystemAdaptationVersion.ColorOs16))
    }

    @Test fun glassAndVolumeLinksOpenTheirMatchingControls() {
        val links = listOf(
            Triple(FeaturePageMode.DesktopLayout, R.string.soft_light_glass_title, FeaturePageMode.SoftLightGlass),
            Triple(FeaturePageMode.SoftLightGlass, R.string.launcher_dock, FeaturePageMode.DesktopLayout),
            Triple(FeaturePageMode.SoftLightGlass, R.string.qs_tile_corner_title, FeaturePageMode.SystemUiControlCenter),
            Triple(FeaturePageMode.SystemUiControlCenter, R.string.side_volume_percentage_title, FeaturePageMode.SystemUiVolume),
            Triple(FeaturePageMode.SystemUiVolume, R.string.qs_slider_percentage_title, FeaturePageMode.SystemUiControlCenter),
        )
        for ((source, title, target) in links) {
            val recommendations = FeatureRecommendations.destinations(source, SystemAdaptationVersion.ColorOs17)
            assertEquals(target, recommendations.single { it.title == title }.page)
        }
    }
}
