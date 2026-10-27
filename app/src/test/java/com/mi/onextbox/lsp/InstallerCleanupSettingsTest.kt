package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class InstallerCleanupSettingsTest {
    @Test fun appInstallerInheritsExistingChoicesWithoutChangingTheNativeUiChoice() {
        val values = mutableMapOf(InstallerCleanupSettings.SKIP_SCAN to true, InstallerCleanupSettings.REMOVE_ADS to true,
            InstallerCleanupSettings.NATIVE_UI to true)
        fun read(key: String) = InstallerCleanupSettings.inherited(key) { name, fallback -> values[name] ?: fallback }
        assertTrue(listOf(InstallerCleanupSettings.APP_SKIP_SCAN, InstallerCleanupSettings.APP_REMOVE_ADS,
            InstallerCleanupSettings.NATIVE_UI).all(::read))
        assertFalse(read(InstallerCleanupSettings.APP_HIDE_COLLECTION_NOTICE))
        assertFalse(read(InstallerCleanupSettings.APP_BLOCK_NETWORK))
        values[InstallerCleanupSettings.APP_SKIP_SCAN] = false
        values[InstallerCleanupSettings.APP_HIDE_COLLECTION_NOTICE] = true
        assertFalse(read(InstallerCleanupSettings.APP_SKIP_SCAN))
        assertTrue(read(InstallerCleanupSettings.APP_REMOVE_ADS))
        assertTrue(read(InstallerCleanupSettings.NATIVE_UI))
        assertTrue(read(InstallerCleanupSettings.APP_HIDE_COLLECTION_NOTICE))
        assertFalse(read(InstallerCleanupSettings.APP_BLOCK_NETWORK))
        assertTrue(values.getValue(InstallerCleanupSettings.SKIP_SCAN))
        assertTrue(values.getValue(InstallerCleanupSettings.REMOVE_ADS))
    }

    @Test fun unavailableOrEmptyConfigurationKeepsEverySwitchOff() {
        assertTrue(InstallerCleanupSettings.keys.all { key -> !InstallerCleanupSettings.inherited(key) { _, fallback -> fallback } })
        assertTrue(InstallerCleanupSettings.keys.all { key -> !InstallerCleanupSettings.inherited(key) { _, _ -> null } })
    }

    @Test fun detectionAndAdChoicesBelongOnlyToAppInstaller() {
        assertEquals(setOf(InstallerCleanupSettings.APP_SKIP_SCAN, InstallerCleanupSettings.APP_REMOVE_ADS,
            InstallerCleanupSettings.APP_HIDE_COLLECTION_NOTICE, InstallerCleanupSettings.APP_BLOCK_NETWORK), InstallerCleanupSettings.appKeys)
        assertEquals(setOf(InstallerCleanupSettings.NATIVE_UI), InstallerCleanupSettings.packageKeys)
        assertEquals(5, InstallerCleanupSettings.keys.size)
    }
}
