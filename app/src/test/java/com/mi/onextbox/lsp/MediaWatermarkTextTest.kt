package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class MediaWatermarkTextTest {
    @Test fun legacySavedContentWorksWithoutASeparateMasterPreference() {
        assertTrue(MediaWatermarkSettings.effectiveEnabled(null, true))
        assertFalse(MediaWatermarkSettings.effectiveEnabled(null, false))
    }
    @Test fun anExplicitlyDisabledWatermarkStaysDisabledWithSavedContent() {
        assertFalse(MediaWatermarkSettings.effectiveEnabled(false, true))
        assertTrue(MediaWatermarkSettings.effectiveEnabled(true, false))
    }
    @Test fun literalContentPreservesUnicodeAndDoesNotSplitEmoji() {
        assertEquals("OPPO 自定义 📷", MediaWatermarkSettings.normalize("  OPPO 自定义 📷\n"))
        val text = "📷".repeat(80)
        val normalized = MediaWatermarkSettings.normalize(text)
        assertEquals(64, normalized.codePointCount(0, normalized.length))
        assertEquals("📷".repeat(64), normalized)
        assertEquals("", MediaWatermarkSettings.normalize(null))
    }
}
