package com.mi.onextbox.ui.common

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AppUiDensityTest {
    @Test fun couiUsesSystemDensity() {
        assertEquals(1f, AppUiStyle.ColorOs.pageDensityScale, 0f)
    }

    @Test fun scaleIsRelativeToTheDisplayDensityRatherThanAFixedDeviceDpi() {
        listOf(1f, 2f, 2.75f, 3f, 4f).forEach { density ->
            assertEquals(density, density * AppUiStyle.ColorOs.pageDensityScale, 0.0001f)
        }
    }

    @Test fun activityKeepsSystemFontScalingAndUnscaledActivationWithoutRecreatingNavigation() {
        val app = listOf(File("."), File("app")).first { File(it, "src/main/java").isDirectory }
        val activity = File(app, "src/main/java/com/mi/onextbox/MainActivity.kt").readText()
        assertTrue(activity.contains("val scale = appUiStyle.pageDensityScale"))
        assertTrue(activity.contains("Density(systemDensity.density * scale, systemDensity.fontScale)"))
        assertTrue(activity.contains("CompositionLocalProvider(LocalDensity provides systemDensity)"))
        assertFalse(activity.contains("wm density"))
        assertFalse(activity.contains("fontScale = 1f"))
    }
}
