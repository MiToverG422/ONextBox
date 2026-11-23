package com.mi.onextbox.ui.common

import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.CardDefaults
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AppUiStyleTest {
    @Test fun colorOs17RemainsDefaultAndBothGenerationsAreSelectable() {
        assertEquals(listOf("ColorOS 17", "ColorOS 16"), AppUiStyle.entries.map { it.label })
        assertEquals(AppUiStyle.ColorOs, AppUiStyle.fromName("ColorOs"))
    }

    @Test fun everyStyleRoundTripsThroughTheStoredName() {
        AppUiStyle.entries.forEach { assertEquals(it, AppUiStyle.fromName(it.name)) }
    }

    @Test fun colorOs17CardCornerStylesUseTheNativeAndPreviousRadii() {
        val standard = AppCardCornerStyle.resolve(AppUiStyle.ColorOs, smoother = false)
        assertEquals(17.dp, standard.cardRadius)
        assertEquals(24.dp, standard.preferenceRadius)

        val smoother = AppCardCornerStyle.resolve(AppUiStyle.ColorOs, smoother = true)
        assertEquals(20.dp, smoother.cardRadius)
        assertEquals(20.dp, smoother.preferenceRadius)
    }

    @Test fun smootherCornerPreferenceOnlyAppliesToColorOs17() {
        for (smoother in listOf(false, true)) {
            val corners = AppCardCornerStyle.resolve(AppUiStyle.ColorOs16, smoother)
            assertEquals(AppCardCornerStyle.ColorOs16, corners)
            assertEquals(CardDefaults.CornerRadius, corners.cardRadius)
            assertEquals(CardDefaults.CornerRadius, corners.preferenceRadius)
        }
    }

    @Test fun homeCardsUseTheGroupedCardOutline() {
        val app = listOf(File("."), File("app")).first { File(it, "src/main/java").isDirectory }
        val homeSources = File(app, "src/main/java/com/mi/onextbox/ui/home")
        val cards = listOf("HomeHeroCard", "HomeInfoCard", "HomeRuntimeStatus", "HomeScreen", "HomeSupportNotice")
        cards.forEach { card ->
            val source = File(homeSources, "$card.kt").readText()
            assertTrue(card, source.contains("AppUiTokens.PreferenceCardCornerRadius"))
            assertTrue(card, source.contains("preferenceGroup = true"))
            assertFalse(card, source.contains("AppUiTokens.CardCornerRadius"))
        }
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
