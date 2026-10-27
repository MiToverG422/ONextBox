package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class InstallerCleanupRulesTest {
    @Test fun collectionNoticeOverrideOnlyAppliesInsideItsParsingDecision() {
        val factory = "com.oplus.appdetail.modelv2.parsing.reository.Repository" to "checkInstall"
        fun frame(method: Pair<String, String>) = StackTraceElement(method.first, method.second, null, -1)
        val helper = frame("com.oplus.appdetail.model.entrance.ChannelWhiteListHelper" to "check")
        assertTrue(InstallerCleanupRules.noticeCaller(listOf(helper, frame(factory)), factory))
        assertFalse(InstallerCleanupRules.noticeCaller(listOf(helper), factory))
        assertFalse(InstallerCleanupRules.noticeCaller(listOf(frame(factory.first to "other")), factory))
        assertFalse(InstallerCleanupRules.noticeCaller(listOf(frame("other.Repository" to factory.second)), factory))
    }

    @Test fun noScanRouteDoesNotChangeOtherMarketAvailabilityQueries() {
        val factory = "com.oplus.appdetail.Factory" to "create"
        fun frame(method: Pair<String, String>) = StackTraceElement(method.first, method.second, null, -1)
        val bridge = frame("com.mi.onextbox.lsp.InstallerCleanupHooker" to "invoke")
        assertTrue(InstallerCleanupRules.routeCaller(listOf(bridge, frame(factory)), factory))
        assertFalse(InstallerCleanupRules.routeCaller(listOf(bridge), factory))
        assertFalse(InstallerCleanupRules.routeCaller(listOf(frame("com.oplus.appdetail.Other" to "render"), frame(factory)), factory))
    }

    @Test fun childrenAndMinorsModesKeepTheirExistingInstallationFlow() {
        assertTrue(InstallerCleanupRules.maySkip(false, false))
        assertFalse(InstallerCleanupRules.maySkip(true, false))
        assertFalse(InstallerCleanupRules.maySkip(false, true))
        assertFalse(InstallerCleanupRules.maySkip(true, true))
    }
    @Test fun onlyLegacyFileUrisUseTheLegacyNativeInstaller() {
        assertTrue(InstallerCleanupRules.legacyFile("file", true))
        assertFalse(InstallerCleanupRules.legacyFile("content", true))
        assertFalse(InstallerCleanupRules.legacyFile("package", true))
        assertFalse(InstallerCleanupRules.legacyFile(null, true))
        assertFalse(InstallerCleanupRules.legacyFile("file", false))
    }
}
