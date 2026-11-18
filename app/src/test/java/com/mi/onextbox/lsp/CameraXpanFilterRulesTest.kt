package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class CameraXpanFilterRulesTest {
    private val modern = listOf("default", "blackandwhite_HC_master.bin", "portra400_master.bin")
    @Test fun legacyMonochromeUsesAnActualSupportedEntry() {
        val selected = CameraXpanFilterRules.resolve(CameraXpanFilterRules.LEGACY_MONO, modern)
        assertEquals("blackandwhite_HC_master.bin", selected)
        assertTrue(modern.indexOf(selected) >= 0)
    }
    @Test fun nativeLegacyMaterialKeepsItsOriginalRendering() {
        val available = modern + CameraXpanFilterRules.LEGACY_MONO
        assertEquals(CameraXpanFilterRules.LEGACY_MONO, CameraXpanFilterRules.resolve(CameraXpanFilterRules.LEGACY_MONO, available))
    }
    @Test fun negativeAndStaleStoredIndicesRecoverWithoutLosingValidSelections() {
        assertEquals("blackandwhite_HC_master.bin", CameraXpanFilterRules.selected(-1, modern, true))
        assertEquals("default", CameraXpanFilterRules.selected(-1, modern, false))
        assertEquals("default", CameraXpanFilterRules.selected(20, modern, true))
        assertEquals("portra400_master.bin", CameraXpanFilterRules.selected(2, modern, true))
        assertEquals("default", CameraXpanFilterRules.selected(-1, emptyList(), true))
    }
}
