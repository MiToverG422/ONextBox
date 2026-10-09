package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficManagementRulesTest {
    @Test fun roamingChooserIsMatchedByNativeAdapterAndLocalizedLabels() {
        listOf(listOf("从不", "总是", "仅在漫游时"), listOf("Never", "Always", "Only when roaming")).forEach { labels ->
            assertTrue(TrafficManagementRules.isRoamingChoiceAdapter(
                "androidx.appcompat.app.AlertController\$d", labels, labels,
            ))
        }
    }

    @Test fun unrelatedDialogsAndPartialListsAreNotRecolored() {
        val labels = listOf("Never", "Always", "Only when roaming")
        val adapter = "androidx.appcompat.app.AlertController\$d"
        assertFalse(TrafficManagementRules.isRoamingChoiceAdapter("android.widget.ArrayAdapter", labels, labels))
        assertFalse(TrafficManagementRules.isRoamingChoiceAdapter(adapter, labels.reversed(), labels))
        assertFalse(TrafficManagementRules.isRoamingChoiceAdapter(adapter, labels.take(2), labels))
        assertFalse(TrafficManagementRules.isRoamingChoiceAdapter(adapter, listOf(null, null, null), labels))
        assertFalse(TrafficManagementRules.isRoamingChoiceAdapter(adapter, emptyList(), emptyList()))
        assertFalse(TrafficManagementRules.isRoamingChoiceAdapter(adapter, listOf("", "", ""), listOf("", "", "")))
    }

    @Test fun onlyOrdinaryApplicationUidsAreEligible() {
        listOf(10_000, 19_999, 110_000, 219_999).forEach {
            assertTrue("UID $it", TrafficManagementRules.isApplicationUid(it))
        }
        listOf(-1, 0, 1000, 9999, 20_000, 29_999, 90_000, 99_000, 101_000, 209_999).forEach {
            assertFalse("UID $it", TrafficManagementRules.isApplicationUid(it))
        }
    }

    @Test fun unconfiguredAndInvalidModesPreserveExistingRestriction() {
        listOf(null, -1, 3, Int.MAX_VALUE).forEach { saved ->
            assertEquals(0, TrafficManagementRules.roamingMode(saved, false))
            assertEquals(1, TrafficManagementRules.roamingMode(saved, true))
        }
    }

    @Test fun explicitRoamingSelectionIsNotOverwrittenByCurrentPolicy() {
        (0..2).forEach { saved ->
            assertEquals(saved, TrafficManagementRules.roamingMode(saved, false))
            assertEquals(saved, TrafficManagementRules.roamingMode(saved, true))
        }
    }

    @Test fun preferencesAndMirrorsDoNotCollide() {
        val features = LspConfig.TrafficFeature.entries
        assertEquals(7, features.size)
        assertEquals(features.size, features.map { it.key }.toSet().size)
        assertEquals(features.size, features.map { it.propertyKey }.toSet().size)
        assertEquals(features.size, features.map { it.settingsKey }.toSet().size)
        assertTrue(features.all { it.key.startsWith("traffic_") })
    }
}
