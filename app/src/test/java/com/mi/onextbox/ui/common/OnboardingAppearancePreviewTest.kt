package com.mi.onextbox.ui.common

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class OnboardingAppearancePreviewTest {
    @Test fun miniatureMaterialDoesNotSampleTheActualWindowTheme() {
        val popup = listOf(
            File("src/main/java/com/mi/onextbox/ui/common/ColorOsPopup.kt"),
            File("app/src/main/java/com/mi/onextbox/ui/common/ColorOsPopup.kt"),
        ).first { it.isFile }.readText()
            .substringAfter("internal fun ColorOsPopupMiniatureMaterial(")
            .substringBefore("private fun ColorOsPopupSurface(")
        assertFalse(popup.contains("applyOplusPopupBlur("))
        assertTrue(popup.contains("view.isForceDarkAllowed = false"))
        assertTrue(popup.contains("if (darkTheme) 0xFF19191B.toInt() else 0xFFFDFDFE.toInt()"))
    }

    @Test fun rowArrowIsAnIconRatherThanAFontGlyph() {
        val row = source.substringAfter("private fun MiniAppearanceRow(")
            .substringBefore("private fun MiniAppearanceDivider(")
        assertFalse(row.contains("⌄"))
        assertTrue(row.contains("imageVector = Icons.Filled.ExpandMore"))
        assertTrue(row.contains("verticalAlignment = Alignment.CenterVertically"))
    }

    private val source = listOf(
        File("src/main/java/com/mi/onextbox/ui/onboarding/OnboardingScreen.kt"),
        File("app/src/main/java/com/mi/onextbox/ui/onboarding/OnboardingScreen.kt"),
    ).first { it.isFile }.readText()

    @Test fun themeTransitionUsesItsOwnSnapshotInsteadOfTheRemovedStyleSelector() {
        val preview = source.substringAfter("private fun AppearanceStylePreview(")
            .substringBefore("private fun MiniAppearanceSettings(")
        assertTrue(preview.contains("targetState = OnboardingAppearance.brightnessOf(themeMode) to dark"))
        assertTrue(preview.contains("MiniAppearanceSettings(dark = previewDark, themeMode = previewMode)"))
        assertFalse(preview.contains("uiStyle"))
    }

    @Test fun miniatureHasRoomForAllThreeMenuOptionsAndShadow() {
        val page = source.substringAfter("private fun AppearancePage(")
            .substringBefore("private fun AppearanceStylePreview(")
        val miniature = source.substringAfter("private fun MiniAppearanceSettings(")
            .substringBefore("private fun MiniAppearanceRow(")
        assertTrue(page.contains(".height(224.dp)"))
        assertTrue(miniature.contains("top = 104.dp"))
        assertTrue(miniature.contains(".height(30.dp)"))
        assertTrue(104 + 3 * 30 + 2 * 5 + 8 < 224)
        assertTrue(miniature.contains("ColorOsPopupMiniatureMaterial("))
        assertFalse(miniature.contains("AppUiStyle."))
    }
}
