package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class CameraNativeFilterEntriesTest {
    @Test fun anInstalledLutNeedsCaptureAndNameSupportBeforeItCanBeSelected() {
        val candidates = mapOf("good.bin" to "good", "previewOnly.bin" to "preview", "missing.bin" to "missing", "unnamed.bin" to "unnamed")
        val result = CameraNativeFilterEntries.eligible(candidates, setOf("good.bin", "missing.bin", "unnamed.bin"),
            { it != "missing.bin" }, { it != "unnamed" })
        assertEquals(mapOf("good.bin" to "good"), result)
    }

    @Test fun independentPacksDoNotAddDisabledClassicAndStreetEntries() {
        val selected = CameraNativeFilterEntries.candidates { it == "camera_unlock_filter_master" }
        assertEquals(setOf("Radiance.cube.rgb.bin", "Serenity.cube.rgb.bin", "Emerald.cube.rgb.bin"), selected.keys)
        assertTrue(CameraNativeFilterEntries.candidates { false }.isEmpty())
    }
}
