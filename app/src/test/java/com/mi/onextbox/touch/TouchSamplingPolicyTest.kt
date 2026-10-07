package com.mi.onextbox.touch

import org.junit.Assert.*
import org.junit.Test

class TouchSamplingPolicyTest {
    @Test fun acceptsOneObservedPanelDespiteOtherConfigurationPanels() {
        assertEquals(1, panel(listOf(1, 1), listOf(profile(0), profile(1))))
    }

    @Test fun rejectsConflictingObservedPanels() {
        assertNull(panel(listOf(0, 1), listOf(profile(0))))
        assertNull(panel(listOf(0, 9), listOf(profile(0))))
    }

    @Test fun rejectsUnsupportedObservedPanel() {
        assertNull(panel(listOf(-1), listOf(profile(0))))
        assertNull(panel(listOf(2), listOf(profile(0))))
    }

    @Test fun acceptsOnlyOneExplicitKnownPanelWhenObservationIsAbsent() {
        assertEquals(1, panel(emptyList(), listOf(profile(1), profile(1))))
        assertNull(panel(emptyList(), listOf(profile(0), profile(1))))
        assertNull(panel(emptyList(), listOf(profile(2))))
    }

    @Test fun doesNotGuessPanelForAnUnknownUnscopedConfiguration() {
        assertNull(panel(emptyList(), listOf(profile(null, path = "/vendor/etc/touchconfig/other.xml"))))
        assertNull(panel(emptyList(), listOf(profile(null, path = null))))
        assertNull(panel(emptyList(), emptyList()))
    }

    @Test fun legacyFallbackRequiresBothKnownDefaultServiceAndExactPath() {
        assertEquals(0, panel(emptyList(), listOf(profile(null, path = LEGACY_CONFIG))))
        assertNull(panel(emptyList(), listOf(profile(null, path = LEGACY_CONFIG)), emptyList()))
        assertNull(panel(emptyList(), listOf(profile(null, path = LEGACY_CONFIG)), listOf(OTHER_SERVICE)))
        assertNull(panel(emptyList(), listOf(profile(null, path = LEGACY_CONFIG)), listOf(DEFAULT_SERVICE, OTHER_SERVICE)))
        assertNull(panel(emptyList(), listOf(profile(null, path = "/vendor/etc/touchconfig/vnd_custom_config_main.xml"))))
    }

    @Test fun explicitProfilesNeverFallBackToLegacyPanelZero() {
        assertNull(panel(emptyList(), listOf(profile(0), profile(1), profile(null, path = LEGACY_CONFIG))))
        assertEquals(1, panel(emptyList(), listOf(profile(1), profile(null, path = LEGACY_CONFIG))))
    }

    @Test fun alternateNodesNeverSupplyAPanelOrLegacyFallback() {
        assertNull(panel(emptyList(), listOf(profile(1, node = 183))))
        assertNull(panel(emptyList(), listOf(profile(null, node = 183, path = LEGACY_CONFIG))))
        assertEquals(0, panel(emptyList(), listOf(profile(0), profile(1, node = 183))))
    }

    @Test fun checksCurrentIndexAndChipIncludingTheDefault() {
        val profile = profile(0)
        assertTrue(TouchSamplingPolicy.matches(profile, 0 to 3))
        assertTrue(TouchSamplingPolicy.matches(profile, 1 to 7))
        assertFalse(TouchSamplingPolicy.matches(profile, null))
        assertFalse(TouchSamplingPolicy.matches(profile, 0 to 0))
        assertFalse(TouchSamplingPolicy.matches(profile, 1 to 3))
        assertFalse(TouchSamplingPolicy.matches(profile, 2 to 7))
        assertFalse(TouchSamplingPolicy.matches(profile, -1 to 7))
    }

    @Test fun requiresBothBindingsAndExactEquality() {
        assertTrue(TouchSamplingPolicy.bindingMatches("binding-a", "binding-a"))
        assertFalse(TouchSamplingPolicy.bindingMatches("binding-a", "binding-b"))
        assertFalse(TouchSamplingPolicy.bindingMatches(null, "binding-a"))
        assertFalse(TouchSamplingPolicy.bindingMatches("binding-a", null))
        assertFalse(TouchSamplingPolicy.bindingMatches(null, null))
    }

    private fun panel(
        observed: List<Int>,
        profiles: List<TouchConfigProfile>,
        services: List<String> = listOf(DEFAULT_SERVICE),
    ): Int? = TouchSamplingPolicy.panelIndex(observed, profiles, services)

    private fun profile(panel: Int?, node: Int = 182, path: String? = null) = TouchConfigProfile(
        panelIndex = panel,
        node = node,
        presets = listOf(TouchRatePreset(1, null, 7, false)),
        defaultChipValue = 3,
        sourceLabel = path,
    )

    private companion object {
        const val LEGACY_CONFIG = "/data/vendor/touchconfig/vnd_custom_config_main.xml"
        const val DEFAULT_SERVICE = "vendor.oplus.hardware.touch.IOplusTouch/default"
        const val OTHER_SERVICE = "vendor.oplus.hardware.touch.IOplusTouch/secondary"
    }
}
