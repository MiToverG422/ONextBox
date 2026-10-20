package com.mi.onextbox.ui.settings

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class MiuixPreferenceValueLayoutTest {
    @Test fun trailingControlsCenterAgainstTitleAndDescriptionTogether() {
        val position = miuixPreferenceVerticalPlacement(24, 40, 24, false, 2, 4)
        assertEquals(66, position.height)
        assertEquals(0, position.titleTop)
        assertEquals(26, position.summaryTop)
        assertEquals(21, position.trailingTop)
    }

    @Test fun titleOnlyAndTallTrailingControlsRemainCentered() {
        val position = miuixPreferenceVerticalPlacement(24, null, 32, false, 2, 4)
        assertEquals(32, position.height)
        assertEquals(4, position.titleTop)
        assertEquals(0, position.trailingTop)
    }

    @Test fun longValuesGoBelowTheDescriptionWithoutOverlapping() {
        val position = miuixPreferenceVerticalPlacement(24, 40, 48, true, 2, 4)
        assertEquals(118, position.height)
        assertEquals(26, position.summaryTop)
        assertEquals(70, position.trailingTop)
    }

    @Test fun normalChineseAndEnglishSelectionsFitBesideTheTitle() {
        assertTrue(miuixPreferenceValueFitsInline(296, 68, 92, 12))
        assertTrue(miuixPreferenceValueFitsInline(296, 92, 156, 12))
    }

    @Test fun exactFitDoesNotNeedAnotherLine() {
        assertTrue(miuixPreferenceValueFitsInline(200, 80, 108, 12))
        assertFalse(miuixPreferenceValueFitsInline(199, 80, 108, 12))
    }

    @Test fun longTranslatedLabelsAndLargeAccessibilityFontsUseSeparateLines() {
        assertFalse(miuixPreferenceValueFitsInline(248, 180, 168, 12))
        assertFalse(miuixPreferenceValueFitsInline(248, 80, 360, 12))
        assertFalse(miuixPreferenceValueFitsInline(0, 80, 90, 12))
    }

    @Test fun intrinsicWidthArithmeticCannotOverflowAndChooseAnOverlappingLayout() {
        assertFalse(miuixPreferenceValueFitsInline(300, Int.MAX_VALUE, 100, 12))
    }

    @Test fun dropdownAndCardValuesShareFullWidthWrappingInsteadOfNestedEndActionFractions() {
        val app = listOf(File("."), File("app")).first { File(it, "src/main/java").isDirectory }
        fun source(name: String) = File(app, "src/main/java/com/mi/onextbox/ui/settings/$name.kt").readText()
        val dropdown = source("MiuixSettingsComponents")
        assertTrue(dropdown.contains("MiuixPreferenceValueLayout("))
        assertFalse(dropdown.contains("fillMaxWidth(0.32f)"))
        assertFalse(dropdown.contains("TextOverflow.Ellipsis"))
        assertTrue(source("SettingsComponents").contains("MiuixPreferenceValueLayout(title = title, summary = summary, value = trailing"))
        val layout = source("MiuixPreferenceValueLayout")
        assertTrue(layout.contains("placeRelative"))
        assertFalse(layout.contains("TextOverflow.Ellipsis"))
        assertFalse(layout.contains("maxLines = 1"))
    }
}
