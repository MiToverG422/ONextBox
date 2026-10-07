package com.mi.onextbox.lsp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LspStatusSnapshotTest {
    @Test
    fun anInitialOrInvalidatedSnapshotIsNeverEnabledOrReady() {
        val snapshot = LsposedScopeRequester.StatusSnapshot()
        assertFalse(snapshot.moduleEnabled)
        assertFalse(snapshot.moduleReady)
        assertFalse(snapshot.isReady)
        assertFalse(snapshot.canContinue)
    }

    @Test
    fun aBoundServiceAndScopesCannotEnableUnknownConfiguration() {
        val snapshot = LsposedScopeRequester.StatusSnapshot(
            serviceConnected = true,
            status = LspStatus.UNKNOWN,
            verifiedScopes = setOf("system", "com.android.systemui"),
        )
        assertTrue(snapshot.hasRequiredScopes)
        assertFalse(snapshot.moduleEnabled)
        assertFalse(snapshot.isReady)
        assertFalse(snapshot.canContinue)
    }

    @Test
    fun enabledConfigurationDoesNotImplyReadyProcesses() {
        val snapshot = LsposedScopeRequester.StatusSnapshot(
            moduleState = LspModuleState.ENABLED,
            status = LspStatus.WAITING_RESTART,
        )
        assertTrue(snapshot.moduleEnabled)
        assertTrue(snapshot.canContinue)
        assertFalse(snapshot.moduleReady)
        assertFalse(snapshot.isReady)
    }

    @Test
    fun checkingAndIncompatibleApisCannotCompleteActivation() {
        listOf(LspStatus.CHECKING, LspStatus.API_UNSUPPORTED).forEach { status ->
            assertFalse(
                LsposedScopeRequester.StatusSnapshot(
                    moduleState = LspModuleState.ENABLED, status = status,
                ).canContinue,
            )
        }
    }

    @Test
    fun readyRequiresExplicitEnabledConfiguration() {
        val enabled = LsposedScopeRequester.StatusSnapshot(
            moduleState = LspModuleState.ENABLED, status = LspStatus.READY,
        )
        assertTrue(enabled.isReady)
        assertTrue(enabled.moduleReady)
        listOf(LspModuleState.UNKNOWN, LspModuleState.DISABLED).forEach { state ->
            assertFalse(LsposedScopeRequester.StatusSnapshot(moduleState = state, status = LspStatus.READY).isReady)
        }
    }

    @Test
    fun androidAndSystemScopesAreNotAliases() {
        val android = LsposedScopeRequester.StatusSnapshot(verifiedScopes = setOf("android", "com.android.systemui"))
        assertTrue(android.hasAndroidScope)
        assertFalse(android.hasSystemScope)
        assertFalse(android.hasRequiredScopes)
        val system = LsposedScopeRequester.StatusSnapshot(verifiedScopes = setOf("system", "com.android.systemui"))
        assertTrue(system.hasSystemScope)
        assertTrue(system.hasRequiredScopes)
        assertFalse(system.hasAndroidScope)
    }

    @Test
    fun missingScopeDetailsAreRetainedWithoutBlockingConfigurationActivation() {
        val missing = setOf("com.android.settings", "com.oplus.athena")
        val snapshot = LsposedScopeRequester.StatusSnapshot(
            moduleState = LspModuleState.ENABLED,
            status = LspStatus.MISSING_SCOPE,
            missingScopes = missing,
        )
        assertEquals(missing, snapshot.missingScopes)
        assertTrue(snapshot.canContinue)
        assertFalse(snapshot.isReady)
    }

    @Test
    fun unknownScopeEvidenceDoesNotCountAsRequiredScopesPresent() {
        assertFalse(LsposedScopeRequester.StatusSnapshot().hasRequiredScopes)
        assertTrue(LsposedScopeRequester.StatusSnapshot().missingScopes.isEmpty())
    }

    @Test
    fun refreshingKeepsPreviousMissingScopeDisplayButCannotCompleteActivation() {
        val verified = LsposedScopeRequester.StatusSnapshot(
            moduleState = LspModuleState.ENABLED,
            status = LspStatus.MISSING_SCOPE,
            verifiedScopes = setOf("system"),
            missingScopes = setOf("com.android.systemui"),
        )
        val refreshing = verified.copy(isRefreshing = true)
        assertEquals(verified.moduleState, refreshing.moduleState)
        assertEquals(verified.status, refreshing.status)
        assertEquals(verified.verifiedScopes, refreshing.verifiedScopes)
        assertEquals(verified.missingScopes, refreshing.missingScopes)
        assertFalse(refreshing.canContinue)
        assertTrue(verified.canContinue)
    }

    @Test
    fun refreshingDoesNotClearReadyDisplayAndFinishingReenablesActivation() {
        val refreshing = LsposedScopeRequester.StatusSnapshot(
            moduleState = LspModuleState.ENABLED, status = LspStatus.READY, isRefreshing = true,
        )
        assertEquals(LspStatus.READY, refreshing.status)
        assertTrue(refreshing.isReady)
        assertFalse(refreshing.canContinue)
        assertTrue(refreshing.copy(isRefreshing = false).canContinue)
    }
}
