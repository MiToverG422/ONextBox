package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class SystemAdaptationVersionTest {
    @Test fun choiceOrderLabelsAndStoredIdsAreStable() {
        assertEquals(listOf(0, 1, 4), SystemAdaptationVersion.entries.map { it.value })
        assertEquals(
            listOf("ColorOS 17", "ColorOS 16", "HyperOS 3"),
            SystemAdaptationVersion.entries.map { it.label },
        )
    }

    @Test fun hyperOs3IsSelectableWhileColorOs16RemainsDisabled() {
        assertEquals(listOf(true, false, true), SystemAdaptationVersion.entries.map { it.selectable })
    }

    @Test fun colorOsCatalogDoesNotLeakIntoOtherProfiles() {
        assertTrue(SystemAdaptationVersion.ColorOs17.hasColorOs17Pages)
        assertFalse(SystemAdaptationVersion.ColorOs16.hasColorOs17Pages)
        assertFalse(SystemAdaptationVersion.HyperOs3.hasColorOs17Pages)
    }

    @Test fun explicitSelectionsOverrideSystemDetection() {
        assertEquals(SystemAdaptationVersion.ColorOs17, SystemAdaptationVersion.resolve(0, "V16.0"))
        assertEquals(SystemAdaptationVersion.ColorOs17, SystemAdaptationVersion.resolve(1, "V17.0"))
        for (system in listOf("V16.0", "V17.0", "HyperOS 3", null)) {
            assertEquals(SystemAdaptationVersion.HyperOs3, SystemAdaptationVersion.resolve(4, system, allowHyperOs = true))
        }
    }

    @Test fun detectedUnavailableVersionFallsBackToColorOs17() {
        for (version in listOf("V16.0", "16.0.0", "ColorOS 16", " v16.0 ")) {
            assertEquals(version, SystemAdaptationVersion.ColorOs17, SystemAdaptationVersion.resolve(null, version))
        }
        for (version in listOf("V17.0", "17", "ColorOS 17", null, "", "V18.0", "116.0")) {
            assertEquals(version, SystemAdaptationVersion.ColorOs17, SystemAdaptationVersion.resolve(null, version))
        }
    }

    @Test fun invalidOrImportedDisabledSelectionsCannotEnableAnUnavailableVersion() {
        for (value in listOf(-1, 1, 2, 3, 5, Int.MAX_VALUE)) {
            assertEquals(SystemAdaptationVersion.ColorOs17, SystemAdaptationVersion.resolve(value, "V16.0"))
            assertEquals(SystemAdaptationVersion.ColorOs17, SystemAdaptationVersion.resolve(value, "V17.0"))
        }
    }

    @Test fun hyperOsIsHiddenAndImportedSelectionFallsBackByDefault() {
        assertEquals(listOf(SystemAdaptationVersion.ColorOs17, SystemAdaptationVersion.ColorOs16),
            SystemAdaptationVersion.visibleVersions(false))
        assertEquals(SystemAdaptationVersion.entries, SystemAdaptationVersion.visibleVersions(true))
        for (system in listOf(null, "HyperOS 3", "V17.0")) {
            assertEquals(SystemAdaptationVersion.ColorOs17, SystemAdaptationVersion.resolve(4, system))
        }
    }

    @Test fun repeatedlyTogglingAccessAlwaysGatesTheSavedHyperOsVersion() {
        repeat(100) {
            assertEquals(SystemAdaptationVersion.HyperOs3, SystemAdaptationVersion.resolve(4, null, true))
            assertEquals(SystemAdaptationVersion.ColorOs17, SystemAdaptationVersion.resolve(4, null, false))
            assertEquals(SystemAdaptationVersion.ColorOs17, SystemAdaptationVersion.resolve(0, null, true))
        }
    }
}
