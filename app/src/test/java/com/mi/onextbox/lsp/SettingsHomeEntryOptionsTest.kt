package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class SettingsHomeEntryOptionsTest {
    @Test fun emptyConfigIsDisabledWithSystemStyle() {
        val options = SettingsHomeEntryOptions.resolve(null, false, null)
        assertFalse(options.enabled)
        assertEquals(SettingsEntryPosition.Disabled, options.position)
        assertEquals(SettingsEntryIconStyle.System, options.iconStyle)
    }

    @Test fun legacyEnabledConfigKeepsTheExistingTopAndSystemStyle() {
        val options = SettingsHomeEntryOptions.resolve(null, true, null)
        assertEquals(SettingsEntryPosition.Top, options.position)
        assertEquals(SettingsEntryIconStyle.System, options.iconStyle)
    }

    @Test fun explicitPositionAlwaysOverridesTheLegacyBoolean() {
        SettingsEntryPosition.entries.forEach { position ->
            listOf(false, true).forEach { legacy ->
                val options = SettingsHomeEntryOptions.resolve(position.value, legacy, 1)
                assertEquals(position, options.position)
                assertEquals(position != SettingsEntryPosition.Disabled, options.enabled)
                assertEquals(SettingsEntryIconStyle.App, options.iconStyle)
            }
        }
    }

    @Test fun invalidValuesFailClosedAndStyleFallsBackToSystem() {
        val options = SettingsHomeEntryOptions.resolve(99, true, 99)
        assertFalse(options.enabled)
        assertEquals(SettingsEntryIconStyle.System, options.iconStyle)
    }

    @Test fun persistedIdsAndChoiceOrderStayStable() {
        assertEquals(listOf(0, 1, 2, 3), SettingsEntryPosition.entries.map { it.value })
        assertEquals(listOf(0, 1), SettingsEntryIconStyle.entries.map { it.value })
    }

    @Test fun disablingDoesNotForgetTheChosenIconStyle() {
        assertEquals(SettingsEntryIconStyle.App, SettingsHomeEntryOptions.resolve(0, true, 1).iconStyle)
        assertEquals(SettingsEntryIconStyle.App, SettingsHomeEntryOptions.resolve(2, true, 1).iconStyle)
    }
}
