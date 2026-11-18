package com.mi.onextbox.ui.screens

import org.junit.Assert.*
import org.junit.Test

class FeatureSearchDocumentsTest {
    @Test fun exactTitleAndDescriptionTiesRetainTheOriginalCatalogOrder() {
        val documents = listOf(
            FeatureSearchDocument("description-first", "Other", "COPY code"),
            FeatureSearchDocument("partial-first", "Copy code", ""),
            FeatureSearchDocument("exact-first", "Copy", ""),
            FeatureSearchDocument("exact-second", "ＣＯＰＹ", ""),
            FeatureSearchDocument("partial-second", "Copy text", ""),
            FeatureSearchDocument("excluded", "Other", "nothing"),
        )
        assertEquals(listOf("exact-first", "exact-second", "partial-first", "partial-second", "description-first"),
            searchFeatureDocuments("  copy  ", documents))
        assertTrue(searchFeatureDocuments("  ", documents).isEmpty())
    }

    @Test fun normalizedIndexPreservesMultiTokenAndUnicodeSearchAcrossQueries() {
        val document = FeatureSearchDocument("result", "Ｃｏｐｙ Café", "verification code 核心破解（实验性）")
        assertEquals(1, FeatureSearchQuery("copy\tcode").score(document))
        assertEquals(0, FeatureSearchQuery("copy rcs").score(document))
        assertEquals(2, FeatureSearchQuery("cafe\u0301").score(document))
        assertEquals(1, FeatureSearchQuery("核心破解(实验性)").score(document))
        assertEquals(3, FeatureSearchQuery("copy café").score(document))
    }
}
