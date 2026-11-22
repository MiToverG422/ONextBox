package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class CameraStreamCompatibilityTest {
    @Test fun grAndXpanRequireTheirOwnNativeCaptureStreams() {
        val professional = mapOf("0" to listOf("photo_mode", "professional_mode"), "1" to listOf("photo_mode"))
        assertFalse(CameraModeSupport.mayExpose("xpan_mode", null))
        assertFalse(CameraModeSupport.mayExpose("gr_mode", null))
        assertFalse(CameraModeSupport.mayExpose("xpan_mode", professional))
        assertFalse(CameraModeSupport.mayExpose("gr_mode", professional))
        val native = professional + ("0" to listOf("xpan_mode", "gr_mode", "professional_mode"))
        assertTrue(CameraModeSupport.mayExpose("gr_mode", native))
        assertTrue(CameraModeSupport.mayExpose("xpan_mode", native))
        assertTrue(CameraModeSupport.mayExpose("professional_mode", professional))
    }

    @Test fun lensChangesFallBackBeforeCaptureAndLeaveOldApsAlone() {
        assertTrue(CameraXpanFilterRules.canApply(null, 5, capturing = false))
        assertFalse(CameraXpanFilterRules.canApply(null, 5, capturing = true))
        assertTrue(CameraXpanFilterRules.canApply(arrayOf("aps_algo_filter"), 6, true))
        assertFalse(CameraXpanFilterRules.canApply(arrayOf("aps_algo_hdr"), 6, false))
        assertTrue(CameraXpanFilterRules.canApply(emptyArray<String>(), 4, true))
    }

    @Test fun partialXpanCatalogsNeverReachTheNativePositionalLabelLookup() {
        assertFalse(CameraXpanFilterRules.hasValidEntries(null, null))
        assertFalse(CameraXpanFilterRules.hasValidEntries(listOf("default", "film"), listOf(4)))
        assertFalse(CameraXpanFilterRules.hasValidEntries(listOf("default"), listOf(0)))
        assertFalse(CameraXpanFilterRules.hasValidEntries(listOf(null), listOf(4)))
        assertTrue(CameraXpanFilterRules.hasValidEntries(emptyList<String>(), emptyList<Int>()))
        assertTrue(CameraXpanFilterRules.hasValidEntries(listOf("film"), listOf(4)))
    }
}
