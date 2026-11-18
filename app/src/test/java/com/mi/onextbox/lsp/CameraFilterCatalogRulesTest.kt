package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class CameraFilterCatalogRulesTest {
    private fun row(id: String) = hashMapOf<Any?, Any?>(CameraFilterCatalogRules.TYPE to id,
        CameraFilterCatalogRules.NAME to 123, "native_parameter" to "keep")

    @Test fun onlyNativeSuppliedLutsAreAddedToRearPhotoAndOriginalDataIsUntouched() {
        val stock = row("default")
        val native = row("portra400_normal.bin")
        val masterOnly = row("portra400_master.bin")
        val original = hashMapOf<Any?, Any?>("photo-back" to listOf(stock), "photo-front" to listOf(stock),
            "photo-front-hdr" to listOf(stock), "master-back" to listOf(native, masterOnly), "unknown-mode" to "keep")
        val result = CameraFilterCatalogRules.merge(original) { it == "camera_unlock_filter_portra" }
        val rear = result["photo-back"] as List<*>
        assertEquals(2, rear.size)
        assertSame(stock, rear[0])
        assertEquals(native, rear[1])
        assertNotSame(native, rear[1])
        assertSame(original["photo-front"], result["photo-front"])
        assertSame(original["photo-front-hdr"], result["photo-front-hdr"])
        assertSame(original["master-back"], result["master-back"])
        assertEquals("keep", result["unknown-mode"])
        assertEquals(1, (original["photo-back"] as List<*>).size)
    }

    @Test fun unavailableResourcesNeverProduceFakeFilterRows() {
        val original = hashMapOf<Any?, Any?>("photo-back" to listOf(row("default")))
        assertSame(original, CameraFilterCatalogRules.merge(original) { true })
        assertSame(original, CameraFilterCatalogRules.merge(original) { false })
    }

    @Test fun selectedTypesDoNotAddOtherFamiliesOrDuplicateExistingRows() {
        val native = row("portra400_normal.bin")
        val original = hashMapOf<Any?, Any?>("photo-back" to listOf(native), "master-back" to listOf(native, row("tx400_normal.bin")))
        assertSame(original, CameraFilterCatalogRules.merge(original) { it == "camera_unlock_filter_portra" })
        val result = CameraFilterCatalogRules.merge(original) { it == "camera_unlock_filter_tx400" }
        assertEquals(2, (result["photo-back"] as List<*>).size)
    }
}
