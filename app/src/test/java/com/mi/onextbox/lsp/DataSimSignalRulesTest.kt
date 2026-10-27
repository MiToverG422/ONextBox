package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DataSimSignalRulesTest {
    @Test fun followsEitherDataSimWithoutDependingOnSlotOrder() {
        assertEquals(listOf(7), DataSimSignalRules.select(listOf(7, 2), true, 7))
        assertEquals(listOf(2), DataSimSignalRules.select(listOf(7, 2), true, 2))
        assertEquals(listOf(7), DataSimSignalRules.select(listOf(2, 7), true, 7))
        assertEquals(listOf(2), DataSimSignalRules.select(listOf(7, 2, 9), true, 2))
    }
    @Test fun missingOrInvalidSelectionKeepsNativeSignals() {
        val source = listOf(7, 2)
        assertSame(source, DataSimSignalRules.select(source, false, 2))
        assertSame(source, DataSimSignalRules.select(source, true, -1))
        assertSame(source, DataSimSignalRules.select(source, true, 9))
        assertEquals(listOf(7), DataSimSignalRules.select(listOf(7), true, 2))
        assertEquals(emptyList<Int>(), DataSimSignalRules.select(emptyList(), true, 2))
    }
    @Test fun nativeInPlaceReverseCannotCorruptSourceOrNextDataSwitch() {
        val source = arrayListOf(7, 2)
        val nativeInput = ArrayList(DataSimSignalRules.select(source, true, 2))
        nativeInput.reverse()
        nativeInput.clear()
        assertEquals(listOf(7, 2), source)
        assertEquals(listOf(7), DataSimSignalRules.select(source, true, 7))
    }
    @Test fun independentOnlyNeverForcesNativeStackingOff() {
        assertTrue(DataSimSignalRules.forceIndependent(true, false, 2))
        assertFalse(DataSimSignalRules.forceIndependent(true, true, 2))
        assertFalse(DataSimSignalRules.forceIndependent(false, false, 2))
        assertFalse(DataSimSignalRules.forceIndependent(false, true, 2))
        assertFalse(DataSimSignalRules.forceIndependent(true, false, -1))
    }
    @Test fun nativeStackedEmptyInputIsNeverRepopulatedAndSeparateIconsStillFilter() {
        // C17 MobileUiAdapter passes an empty list while its separate stacked view
        // is active, and subscription IDs only while separate icons are in use.
        val stackedInput = emptyList<Int>()
        assertSame(stackedInput, DataSimSignalRules.select(stackedInput, true, 7))
        assertEquals(listOf(7), DataSimSignalRules.select(listOf(7, 2), true, 7))
        assertEquals(listOf(2), DataSimSignalRules.select(listOf(7, 2), true, 2))
    }
    @Test fun childPersistsSeparatelyAndUsesTheSharedJoinedExitAnimation() {
        fun source(path: String) = listOf(File("src/$path"), File("app/src/$path")).first { it.isFile }.readText()
        val settings = source("main/java/com/mi/onextbox/lsp/DataSimSignalSettings.kt")
        assertTrue(settings.contains("getBoolean(INDEPENDENT_ONLY, false)"))
        val write = settings.substringAfter("fun setIndependentOnly(").substringBefore("internal object DataSimSignalRules")
        assertTrue(write.contains("putBoolean(INDEPENDENT_ONLY, enabled)"))
        assertFalse(write.contains("putBoolean(KEY"))
        val row = source("main/java/com/mi/onextbox/ui/screens/DataSimSignalSettingsRow.kt")
        assertTrue(row.contains("FeatureAnimatedRows(hasDividerBelow = true, animateChanges = true)"))
        assertTrue(row.contains("row(\"independent-only\", visible = enabled)"))
        assertTrue(row.contains("interactionEnabled = savingKey != DataSimSignalSettings.INDEPENDENT_ONLY"))
        val hook = source("main/java/com/mi/onextbox/lsp/DataSimSignalHooker.kt")
        assertTrue(hook.contains("DataSimSignalRules.forceIndependent(DataSimSignalSettings.enabledXposed()"))
        assertTrue(hook.contains("DataSimSignalSettings.independentOnlyXposed(), dataSubId()"))
        val search = source("main/java/com/mi/onextbox/ui/screens/FeatureSearchIndex.kt")
        assertTrue(search.contains("Entry(FeaturePageMode.SystemUiStatusBarNetwork, R.string.feature_only_data_sim_independent_title)"))
    }
}
