package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class CameraPaletteAlgorithmsTest {
    @Test fun palettePrecedesFaceAndPostProcessingAndRetainsAllExistingNodes() {
        assertArrayEquals(arrayOf("aps_algo_hdr", CameraPaletteAlgorithms.ID, "aps_algo_face_info", "aps_algo_post_proc"),
            CameraPaletteAlgorithms.include(arrayOf("aps_algo_hdr", "aps_algo_face_info", "aps_algo_post_proc")))
        assertArrayEquals(arrayOf("aps_algo_hdr", CameraPaletteAlgorithms.ID), CameraPaletteAlgorithms.include(arrayOf("aps_algo_hdr")))
    }
    @Test fun anExistingNativePaletteNodeIsKeptInPlaceWithoutDuplication() {
        val native = arrayOf("aps_algo_hdr", CameraPaletteAlgorithms.ID, "aps_algo_face_info")
        assertSame(native, CameraPaletteAlgorithms.include(native))
    }
}
