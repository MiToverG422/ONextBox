package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class SettingsRealmeLabEntriesTest {
    private open class Row(var key: String = "") {
        var order = 0
        var isVisible = true
        var bound: SettingsRealmeFeature? = null
    }
    private class Group(key: String = "") : Row(key) {
        val children = mutableListOf<Row>()
        fun findPreference(key: CharSequence): Row? = children.firstNotNullOfOrNull {
            it.takeIf { row -> row.key == key.toString() } ?: (it as? Group)?.findPreference(key)
        }
        fun getPreferenceCount() = children.size
        fun addPreference(row: Row) = children.add(row)
        fun removePreference(row: Row) = children.remove(row)
    }
    private fun reconcile(screen: Group, requested: List<SettingsRealmeLabEntries.Entry>) =
        SettingsRealmeLabEntries.reconcile(screen, requested, ::Group, ::Row) { row, entry -> (row as Row).bound = entry.feature }

    @Test fun emptyC17LaboratoryGetsOnlySelectedNativeEntriesWithoutDuplicates() {
        val screen = Group("screen")
        val selected = SettingsRealmeLabEntries.entries.filter { it.feature in setOf(SettingsRealmeFeature.DcDimming, SettingsRealmeFeature.DualMusic) }
        repeat(30) {
            reconcile(screen, selected)
            reconcile(screen, selected)
            assertEquals(1, screen.children.size)
            assertEquals(2, (screen.children.single() as Group).children.size)
            assertEquals(SettingsRealmeFeature.DualMusic, screen.findPreference("lab_double_ear")?.bound)
            assertNull(screen.findPreference("lab_well_being"))
            assertNull(screen.findPreference("heart_rate_detection"))
            reconcile(screen, emptyList())
            assertTrue(screen.children.isEmpty())
        }
    }

    @Test fun disabledFeaturesAndMissingDependenciesNeverCreateEmptyOrBrokenEntries() {
        assertTrue(SettingsRealmeLabEntries.requested({ false }, { true }).isEmpty())
        val missingApps = SettingsRealmeLabEntries.requested({ true }) {
            it != SettingsRealmeFeature.SleepCapsule && it != SettingsRealmeFeature.HeartRate
        }
        assertEquals(setOf(SettingsRealmeFeature.DcDimming, SettingsRealmeFeature.DualMusic), missingApps.map { it.feature }.toSet())
        val onlyFace = SettingsRealmeLabEntries.requested({ it == SettingsRealmeFeature.MultipleFaces }, { true })
        assertTrue(onlyFace.isEmpty())
        val screen = Group()
        reconcile(screen, onlyFace)
        assertTrue(screen.children.isEmpty())
    }

    @Test fun existingStockRowsAndOtherCategoriesAreNeverReplacedOrRemoved() {
        val screen = Group()
        val stock = Group("stock")
        val nativeRow = Row("lab_double_ear")
        stock.addPreference(nativeRow)
        screen.addPreference(stock)
        reconcile(screen, SettingsRealmeLabEntries.entries)
        assertSame(nativeRow, screen.findPreference("lab_double_ear"))
        assertNull(nativeRow.bound)
        SettingsRealmeLabEntries.removeOwnedGroup(screen)
        assertEquals(listOf(stock), screen.children)
        assertSame(nativeRow, screen.findPreference("lab_double_ear"))
    }

    @Test fun retiringGenerationOnlyRemovesItsOwnRowsAndNewGenerationCanRestoreThem() {
        val screen = Group()
        val unrelated = Row("stock")
        screen.addPreference(unrelated)
        reconcile(screen, SettingsRealmeLabEntries.entries)
        SettingsRealmeLabEntries.removeOwnedGroup(screen)
        assertEquals(listOf(unrelated), screen.children)
        reconcile(screen, SettingsRealmeLabEntries.entries)
        assertEquals(2, screen.children.size)
        assertEquals(4, (screen.children.last() as Group).children.size)
    }
}
