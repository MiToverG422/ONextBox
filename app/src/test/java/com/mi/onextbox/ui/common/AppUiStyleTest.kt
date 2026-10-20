package com.mi.onextbox.ui.common

import org.junit.Assert.*
import org.junit.Test

class AppUiStyleTest {
    @Test fun thirdStyleIsAppendedWithoutChangingExistingSavedNames() {
        assertEquals(listOf("ColorOs", "Material3Expressive", "Miuix"), AppUiStyle.entries.map { it.name })
        assertEquals(AppUiStyle.ColorOs, AppUiStyle.entries[0])
        assertEquals(AppUiStyle.Material3Expressive, AppUiStyle.entries[1])
    }

    @Test fun everyStyleRoundTripsThroughTheStoredName() {
        AppUiStyle.entries.forEach { assertEquals(it, AppUiStyle.fromName(it.name)) }
    }

    @Test fun legacyMaterialNameAndDefaultFallbackArePreserved() {
        assertEquals(AppUiStyle.Material3Expressive, AppUiStyle.fromName("Clarity"))
        listOf(null, "", "unknown").forEach { assertEquals(AppUiStyle.ColorOs, AppUiStyle.fromName(it)) }
    }
}
