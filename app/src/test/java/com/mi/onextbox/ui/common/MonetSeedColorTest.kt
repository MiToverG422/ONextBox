package com.mi.onextbox.ui.common

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class MonetSeedColorTest {
    @Test fun parsesSystemPaletteSeedsWithoutTreatingMissingValuesAsBlack() {
        assertEquals(0xFF123ABC.toInt(), parseMonetSeed("123abc"))
        assertEquals(0xFF123ABC.toInt(), parseMonetSeed(" #123ABC "))
        assertEquals(0xFF123ABC.toInt(), parseMonetSeed("ff123abc"))
        assertEquals(0xFF000000.toInt(), parseMonetSeed("000000"))
        listOf("", "null", "garbage", "123", "123456789", "-12345", "+12345").forEach { assertNull(parseMonetSeed(it)) }
    }

    @Test fun systemSeedAlsoFlowsThroughPaletteAndSpecGeneration() {
        val main = listOf(File("src/main/java/com/mi/onextbox/ui"), File("app/src/main/java/com/mi/onextbox/ui"))
            .first { it.isDirectory }
        val page = File(main, "settings/SettingsMainPage.kt").readText()
        assertFalse(page.contains("appThemeMode.isMonet && appThemeKeyColor != null"))
        val root = File(main, "Root.kt").readText()
        assertTrue(root.contains("keyColor = resolvedKeyColor"))
        assertTrue(root.contains("remember(appThemeMode, resolvedKeyColor, appThemePaletteStyle, appThemeColorSpec)"))
        assertTrue(root.contains("paletteStyle = paletteStyle"))
        assertTrue(root.contains("colorSpec = colorSpec"))
    }
}
