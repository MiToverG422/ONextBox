package com.mi.onextbox.ui.screens

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeNetworkToggleUiContractTest {
    private val source = listOf(
        File("src/main/java/com/mi/onextbox/ui/screens/LargeNetworkTypeSettingsRow.kt"),
        File("app/src/main/java/com/mi/onextbox/ui/screens/LargeNetworkTypeSettingsRow.kt"),
    ).first { it.isFile }.readText()

    @Test fun savingDoesNotDimTheGroupButStillGuardsEveryWrite() {
        assertFalse(source.contains("enabled = !saving"))
        assertEquals(5, Regex("if \\(!saving\\)").findAll(source).count())
        assertEquals(6, Regex("finally \\{").findAll(source).count())
    }

    @Test fun resumeCannotOverwriteAnOptimisticPendingChange() {
        assertTrue(source.contains("event == Lifecycle.Event.ON_RESUME && !saving"))
        listOf("enabled", "artwork", "lte", "followFont", "bold").forEach {
            assertTrue(source.contains("$it = checked"))
        }
    }
}
