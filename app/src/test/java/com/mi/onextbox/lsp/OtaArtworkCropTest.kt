package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class OtaArtworkCropTest {
    private val id = "6344ffdb-cf9b-4052-afed-60c34fa492aa"

    @Test fun cropPersistsWithTheOriginalImageIdAndClampsRestoredValues() {
        val selection = OtaArtwork(id, OtaCrop(.3f, .9f, 2.75f))
        assertEquals(selection, OtaArtwork.parse(selection.encode()))
        assertEquals(OtaCrop(0f, 1f, 4f), OtaArtwork.parse("$id|-2|20|7")?.crop)
        assertEquals(1f, OtaArtwork.parse("$id|.5|.5|0")!!.crop.zoom, 0f)
    }
    @Test fun invalidKeysAndNonfiniteCropValuesCannotReachViewMatrix() {
        for (bad in listOf(null, "", "../$id|0|0|1", "$id|0|0", "$id|NaN|.5|1", "$id|0|0|Infinity"))
            assertNull(OtaArtwork.parse(bad))
    }
}
