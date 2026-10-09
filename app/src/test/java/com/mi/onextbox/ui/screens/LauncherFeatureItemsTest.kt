package com.mi.onextbox.ui.screens

import com.mi.onextbox.lsp.LspConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherFeatureItemsTest {
    @Test fun `task auto focus and icon details belong only to recent tasks`() {
        for (feature in listOf(
            LspConfig.LauncherFeature.DisablePreviousTaskAutoFocus,
            LspConfig.LauncherFeature.RecentIconAppDetails,
        )) {
            val item = LauncherFeatureItems.recent.single { it.feature == feature }
            assertEquals(FeaturePageMode.DesktopRecent,
                FeatureSearchIndex.entries.single { it.title == item.title }.page)
            assertTrue((LauncherFeatureItems.badges + LauncherFeatureItems.folder).none { it.feature == feature })
        }
    }

    @Test fun `every new launcher feature has exactly one settings row`() {
        val items = LauncherFeatureItems.all
        assertEquals(LspConfig.LauncherFeature.entries.toSet(), items.map { it.feature }.toSet())
        assertEquals(items.size, items.map { it.feature }.distinct().size)
        assertTrue(items.all { it.title != 0 })
        assertEquals(
            LauncherFeatureItems.recent.map { it.feature },
            items.filter { it.summary == 0 }.map { it.feature },
        )
    }

    @Test fun `recent task features have no descriptions in settings or search`() {
        LauncherFeatureItems.recent.forEach { item ->
            assertEquals(0, item.summary)
            assertEquals(0, FeatureSearchIndex.entries.single { it.title == item.title }.description)
        }
    }

    @Test fun `every launcher row is searchable on a desktop category page`() {
        val pages = setOf(FeaturePageMode.DesktopPages, FeaturePageMode.DesktopRecent,
            FeaturePageMode.DesktopIcons, FeaturePageMode.DesktopLayout)
        LauncherFeatureItems.all.forEach { item ->
            assertTrue(FeatureSearchIndex.entries.any {
                it.page in pages && it.title == item.title && it.description == item.summary
            })
        }
    }

    @Test fun `launcher preference keys are unique and property names fit Android limits`() {
        val features = LspConfig.LauncherFeature.entries
        assertEquals(features.size, features.map { it.key }.distinct().size)
        assertTrue(features.all { it.persistPropertyKey.length < 92 })
    }
}
