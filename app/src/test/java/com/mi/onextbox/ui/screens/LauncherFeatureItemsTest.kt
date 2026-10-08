package com.mi.onextbox.ui.screens

import com.mi.onextbox.lsp.LspConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherFeatureItemsTest {
    @Test fun `every new launcher feature has exactly one settings row`() {
        val items = LauncherFeatureItems.all
        assertEquals(LspConfig.LauncherFeature.entries.toSet(), items.map { it.feature }.toSet())
        assertEquals(items.size, items.map { it.feature }.distinct().size)
        assertTrue(items.all { it.title != 0 })
        assertEquals(
            listOf(LspConfig.LauncherFeature.HideClearButton),
            items.filter { it.summary == 0 }.map { it.feature },
        )
    }

    @Test fun `every launcher row is searchable on the desktop page`() {
        LauncherFeatureItems.all.forEach { item ->
            assertTrue(FeatureSearchIndex.entries.any {
                it.page == FeaturePageMode.Desktop && it.title == item.title && it.description == item.summary
            })
        }
    }

    @Test fun `launcher preference keys are unique and property names fit Android limits`() {
        val features = LspConfig.LauncherFeature.entries
        assertEquals(features.size, features.map { it.key }.distinct().size)
        assertTrue(features.all { it.persistPropertyKey.length < 92 })
    }
}
