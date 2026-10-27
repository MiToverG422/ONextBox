package com.mi.onextbox.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationBatchActionAppearanceTest {
    @Test fun actionsUseOnlyCouiButtonsAndPreserveEnabledState() {
        val buttons = source().substringAfter("private fun BatchButton(").substringBefore("private fun BatchToolbarButton(")
        assertFalse(buttons.contains("ButtonDefaults."))
        assertTrue(buttons.contains("ColorOs17Button("))
        assertTrue(buttons.contains("enabled = enabled"))
    }

    @Test fun couiBackingIsOpaqueInBothStatesAndMatchesTheNativeCapsule() {
        val buttons = source().substringAfter("private fun BatchButton(").substringBefore("private fun BatchToolbarButton(")
        assertTrue(buttons.contains("Box(modifier.background(COUITheme.colorScheme.surface.copy(alpha = 1f), CircleShape))"))
        assertTrue(buttons.contains("ColorOs17Button(label, primary = !secondary"))
        assertTrue(buttons.contains("enabled = enabled, buttonModifier = Modifier.testTag(tag)"))
        assertFalse(buttons.contains("modifier.alpha"))
    }

    @Test fun floatingActionsRemainAboveTheListWithoutARectangularFooterBackground() {
        val text = source()
        assertTrue(text.contains("Modifier.align(Alignment.BottomCenter).zIndex(1f).onSizeChanged"))
        val actions = text.substringAfter("internal fun NotificationBatchActions(").substringBefore("private fun BatchButton(")
        assertFalse(actions.contains(".background("))
        assertFalse(actions.contains("batchFooter"))
        assertTrue(text.contains("actionsHeightPx.toDp() } + 8.dp"))
    }

    private fun source(): String = listOf(
        File("src/main/java/com/mi/onextbox/ui/screens/NotificationBatchLayout.kt"),
        File("app/src/main/java/com/mi/onextbox/ui/screens/NotificationBatchLayout.kt"),
    ).first { it.isFile }.readText()
}
