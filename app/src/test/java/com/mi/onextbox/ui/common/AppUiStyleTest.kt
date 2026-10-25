package com.mi.onextbox.ui.common

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AppUiStyleTest {
    @Test fun onlyCouiIsAvailable() {
        assertEquals(listOf("ColorOs"), AppUiStyle.entries.map { it.name })
        assertEquals(AppUiStyle.ColorOs, AppUiStyle.entries[0])
    }

    @Test fun everyStyleRoundTripsThroughTheStoredName() {
        AppUiStyle.entries.forEach { assertEquals(it, AppUiStyle.fromName(it.name)) }
    }

    @Test fun legacyMaterialNameAndDefaultFallbackArePreserved() {
        listOf(null, "", "unknown", "Clarity", "Material3Expressive", "Miuix").forEach {
            assertEquals(AppUiStyle.ColorOs, AppUiStyle.fromName(it))
        }
    }

    @Test fun removedStylesCannotBeSelectedOrLoaded() {
        val app = listOf(File("."), File("app")).first { File(it, "src/main/java").isDirectory }
        val sources = File(app, "src/main/java/com/mi/onextbox/ui").walkTopDown()
            .filter { it.extension == "kt" }.map { it.readText() }.joinToString("\n")
        listOf("AppUiStyle.Material3Expressive", "AppUiStyle.Miuix", "top.yukonga.miuix",
            "Material3ExpressiveAppTheme(", "SettingsUiStyleDropdown(").forEach {
            assertFalse(it, sources.contains(it))
        }
        assertFalse(File(app, "build.gradle.kts").readText().contains("libs.miuix.ui"))
        assertTrue(sources.contains("ColorOs17AppTheme("))
    }
}
