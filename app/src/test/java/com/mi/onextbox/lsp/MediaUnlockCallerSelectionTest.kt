package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class MediaUnlockCallerSelectionTest {
    @Test fun aWatermarkSwitchDoesNotDeoptimizeDisabledFilterRenderers() {
        val app = MediaUnlockCatalog.App.Camera
        val watermark = MediaUnlockCatalog.options.first { it.app == app && it.group == MediaUnlockCatalog.Group.Watermarks && it.overrides.isNotEmpty() }
        val filter = MediaUnlockCatalog.options.first { it.app == app && it.group == MediaUnlockCatalog.Group.Filters }
        val own = String::class.java.getMethod("substring", Int::class.javaPrimitiveType)
        val unrelated = String::class.java.getMethod("charAt", Int::class.javaPrimitiveType)
        val targets = mapOf("gate:${watermark.overrides.keys.first()}" to arrayOf(own),
            "gate:${filter.overrides.keys.first()}" to arrayOf(unrelated))
        assertEquals(listOf(own), MediaUnlockTargets.callersFor(targets, app) { it == watermark.key })
        assertTrue(MediaUnlockTargets.callersFor(targets, app) { false }.isEmpty())
    }
}
