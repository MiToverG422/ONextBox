package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherSearchModeTest {
    @Test
    fun migratesLegacyBoolean() {
        assertEquals(1, launcherSearchBarModeValue(true))
        assertEquals(0, launcherSearchBarModeValue(false))
    }

    @Test
    fun preservesCurrentModes() {
        for (mode in 0..2) assertEquals(mode, launcherSearchBarModeValue(mode))
    }

    @Test
    fun invalidValuesUseDefault() {
        for (value in listOf(null, -1, 3, "2")) {
            assertEquals(0, launcherSearchBarModeValue(value))
        }
    }
}
