package com.mi.onextbox.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FeatureAnimatedGroupTest {
    private fun keys(parent: Boolean, child: Boolean): List<Any> =
        FeatureAnimatedGroupScope().apply {
            row("main") { _, _ -> }
            row("child", visible = parent) { _, _ -> }
            row("nested", visible = parent && child) { _, _ -> }
            row("independent") { _, _ -> }
        }.rows.map { it.first }

    @Test fun disabledParentLeavesNoChildSlotsEvenWithSavedChildEnabled() {
        assertEquals(listOf("main", "independent"), keys(false, true))
        assertEquals(listOf("main", "independent"), keys(false, false))
    }

    @Test fun childrenReturnInStableOrderWhenParentReopens() {
        assertEquals(listOf("main", "child", "nested", "independent"), keys(true, true))
        assertEquals(listOf("main", "child", "independent"), keys(true, false))
    }
    @Test fun optInKeepsHiddenIdentitiesForExitAnimationsAndVisibilityReversal() {
        val scope = FeatureAnimatedGroupScope(retainHiddenRows = true).apply {
            row("main") { _, _ -> }
            row("child", visible = false) { _, _ -> }
            row("independent") { _, _ -> }
        }
        assertEquals(listOf("main", "child", "independent"), scope.rows.map { it.first })
        assertEquals(mapOf("main" to true, "child" to false, "independent" to true), scope.visibility)
    }
    @Test fun optInUsesKeyedBoundsAndRetainsContentUntilExitCompletesInBothUis() {
        val text = listOf(File("src/main/java/com/mi/onextbox/ui/screens/FeatureAnimatedGroup.kt"),
            File("app/src/main/java/com/mi/onextbox/ui/screens/FeatureAnimatedGroup.kt")).first { it.isFile }.readText()
        assertTrue(text.contains("Modifier.animateBounds(lookahead"))
        assertTrue(text.contains("transition.currentState || transition.targetState || !transition.isIdle"))
        assertTrue(text.contains("visibleState = transitions[rowIndex]"))
        assertTrue(text.contains("key(identity)"))
        org.junit.Assert.assertFalse(text.contains("Spacer(Modifier.height(2.dp))"))
        assertTrue(text.contains("if (below) SettingsDivider()"))
        assertTrue(text.indexOf("androidx.compose.animation.AnimatedVisibility(") < text.indexOf("Box(Modifier.animateBounds("))
    }
}
