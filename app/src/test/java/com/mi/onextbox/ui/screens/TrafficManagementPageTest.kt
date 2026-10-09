package com.mi.onextbox.ui.screens

import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficManagementPageTest {
    @Test fun everySwitchHasOneRowAndOneSearchDestination() {
        val items = TrafficFeatureItems.all
        assertEquals(LspConfig.TrafficFeature.entries.toSet(), items.map { it.feature }.toSet())
        assertEquals(7, items.size)
        items.forEach { item ->
            val entry = FeatureSearchIndex.entries.single { it.title == item.title }
            assertEquals(FeaturePageMode.TrafficManagement, entry.page)
            assertEquals(item.summary, entry.description)
            assertTrue(item.summary != 0)
        }
    }

    @Test fun trafficManagementIsAnIndependentSearchablePage() {
        assertFalse(FeaturePageMode.TrafficManagement.isNestedPage)
        assertEquals(FeaturePageMode.TrafficManagement,
            FeatureSearchIndex.entries.single { it.title == R.string.traffic_page_title }.page)
        TrafficFeatureItems.groups.forEach { (title, _) ->
            assertEquals(FeaturePageMode.TrafficManagement,
                FeatureSearchIndex.entries.single { it.title == title }.page)
        }
    }
}
