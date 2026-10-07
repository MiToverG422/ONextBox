package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LspDetectionPolicyTest {
    private val versionCode = 170002L
    private val systemUi = "com.android.systemui"
    private val optionalApp = "com.example.optional"

    @Test
    fun disabledConfigurationWinsOverCurrentAndStaleLiveTargets() {
        listOf(
            target(),
            target(current = false),
            target(loadedVersionCode = versionCode - 1),
        ).forEach { loaded ->
            val result = evaluate(LspModuleState.DISABLED, framework(targets = listOf(loaded)))
            assertEquals(LspModuleState.DISABLED, result.moduleState)
            assertEquals(LspStatus.DISABLED, result.status)
            assertTrue(result.serviceConnected)
        }
    }

    @Test
    fun disabledConfigurationAlsoWinsOverAnUnsupportedApi() {
        assertEquals(
            LspStatus.DISABLED,
            evaluate(LspModuleState.DISABLED, framework(apiVersion = 101)).status,
        )
    }

    @Test
    fun aLiveServiceCannotPromoteUnknownConfigurationToEnabledOrReady() {
        listOf(
            listOf(target()),
            listOf(target(current = false)),
            emptyList(),
        ).forEach { targets ->
            val result = evaluate(LspModuleState.UNKNOWN, framework(targets = targets))
            assertEquals(LspModuleState.UNKNOWN, result.moduleState)
            assertEquals(LspStatus.UNKNOWN, result.status)
            assertTrue(result.serviceConnected)
            assertEquals("config_unavailable", result.reason)
        }
    }

    @Test
    fun aConfirmedUnsupportedApiIsReportedEvenWhenConfigurationIsUnknown() {
        val result = evaluate(LspModuleState.UNKNOWN, framework(apiVersion = 101))
        assertEquals(LspModuleState.UNKNOWN, result.moduleState)
        assertEquals(LspStatus.API_UNSUPPORTED, result.status)
        assertEquals("api_unsupported", result.reason)
    }

    @Test
    fun supportedApiVersionsAcceptCurrentScopedTargets() {
        listOf(102, 103).forEach { api ->
            assertEquals(LspStatus.READY, evaluate(framework = framework(apiVersion = api)).status)
        }
    }

    @Test
    fun unsupportedApiTakesPriorityOverMissingScopeAndTargetQueries() {
        assertEquals(
            LspStatus.API_UNSUPPORTED,
            evaluate(framework = framework(apiVersion = 101, scopes = null, targets = null)).status,
        )
    }

    @Test
    fun enabledWithoutAServiceWaitsForConnectionRatherThanClaimingReady() {
        val result = evaluate(framework = null, configuredScopes = setOf(systemUi))
        assertEquals(LspModuleState.ENABLED, result.moduleState)
        assertEquals(LspStatus.WAITING_CONNECTION, result.status)
        assertFalse(result.serviceConnected)
        assertEquals(setOf(systemUi), result.scopes)
        assertNull(result.frameworkVersionText)
        assertEquals("config_db", result.source)
    }

    @Test
    fun absentServiceDoesNotMakeUnknownConfigurationDisabled() {
        val result = evaluate(LspModuleState.UNKNOWN, null)
        assertEquals(LspStatus.UNKNOWN, result.status)
        assertEquals(LspModuleState.UNKNOWN, result.moduleState)
        assertFalse(result.serviceConnected)
    }

    @Test
    fun failedScopeQueryIsUnknownButAnEmptyScopeIsMissing() {
        assertEquals(LspStatus.UNKNOWN, evaluate(framework = framework(scopes = null)).status)
        listOf(emptySet<String>(), setOf("", "  ")).forEach { scopes ->
            val result = evaluate(framework = framework(scopes = scopes))
            assertEquals(LspStatus.MISSING_SCOPE, result.status)
            assertEquals(emptySet<String>(), result.scopes)
        }
    }

    @Test
    fun scopeEntriesAreTrimmedBeforeMatching() {
        val result = evaluate(framework = framework(scopes = setOf("  $systemUi ", "")))
        assertEquals(LspStatus.READY, result.status)
        assertEquals(setOf(systemUi), result.scopes)
    }

    @Test
    fun failedTargetQueryIsUnknownButAnEmptyTargetListWaitsForRestart() {
        val unavailable = evaluate(framework = framework(targets = null))
        val empty = evaluate(framework = framework(targets = emptyList()))
        assertEquals(LspStatus.UNKNOWN, unavailable.status)
        assertEquals("targets_unavailable", unavailable.reason)
        assertEquals(LspStatus.WAITING_RESTART, empty.status)
        assertEquals("targets_not_loaded", empty.reason)
    }

    @Test
    fun aCurrentTargetMustMatchBothTheScopeAndCurrentUser() {
        val result = evaluate(
            framework = framework(scopes = setOf(optionalApp), targets = listOf(target(optionalApp, 1_010_001))),
            userId = 10,
        )
        assertEquals(LspStatus.READY, result.status)
    }

    @Test
    fun otherUsersTargetsCannotEstablishReadiness() {
        val result = evaluate(framework = framework(targets = listOf(target(uid = 1_010_001))))
        assertEquals(LspStatus.WAITING_RESTART, result.status)
    }

    @Test
    fun anotherUsersStaleTargetDoesNotInvalidateTheCurrentUsersTarget() {
        val result = evaluate(framework = framework(targets = listOf(
            target(),
            target(uid = 1_010_001, current = false, loadedVersionCode = versionCode - 1),
        )))
        assertEquals(LspStatus.READY, result.status)
    }

    @Test
    fun staleStateRequiresRestart() {
        val result = evaluate(framework = framework(targets = listOf(target(current = false))))
        assertEquals(LspStatus.WAITING_RESTART, result.status)
        assertEquals("target_stale", result.reason)
    }

    @Test
    fun currentStateIsAuthoritativeEvenWhenDiagnosticVersionDiffersOrIsUnknown() {
        listOf(0L, versionCode - 1, versionCode + 1).forEach { diagnosticVersion ->
            val result = evaluate(framework = framework(targets = listOf(target(loadedVersionCode = diagnosticVersion))))
            assertEquals(LspStatus.READY, result.status)
        }
    }

    @Test
    fun unopenedOptionalScopesDoNotBlockCurrentCoreTargets() {
        val result = evaluate(framework = framework(
            scopes = setOf("system", systemUi, optionalApp, "com.oplus.aod"),
            targets = listOf(target("system_server", 1000), target()),
        ))
        assertEquals(LspStatus.READY, result.status)
    }

    @Test
    fun unscopedAndInvalidUidTargetsAreIgnored() {
        val ignored = listOf(
            target(optionalApp, current = false),
            target(uid = -1, current = false),
        )
        assertEquals(
            LspStatus.READY,
            evaluate(framework = framework(targets = listOf(target()) + ignored)).status,
        )
        assertEquals(
            LspStatus.WAITING_RESTART,
            evaluate(framework = framework(targets = ignored)).status,
        )
    }

    @Test
    fun systemScopeRecognizesBothFrameworkSystemProcessNames() {
        listOf("system", "system_server").forEach { process ->
            val result = evaluate(
                framework = framework(scopes = setOf("system"), targets = listOf(target(process, 1000))),
                userId = 10,
            )
            assertEquals(LspStatus.READY, result.status)
        }
    }

    @Test
    fun aSystemServerNameWithoutSystemUidCannotSatisfyTheCoreScope() {
        listOf(10001, 1_010_001).forEach { uid ->
            assertEquals(
                LspStatus.WAITING_RESTART,
                evaluate(framework = framework(
                    scopes = setOf("system"),
                    targets = listOf(target("system_server", uid)),
                )).status,
            )
        }
    }

    @Test
    fun systemServerMustActuallyBeScopedToCount() {
        val result = evaluate(framework = framework(
            scopes = setOf(optionalApp),
            targets = listOf(target("system_server", 1000)),
        ))
        assertEquals(LspStatus.WAITING_RESTART, result.status)
    }

    @Test
    fun aScopedSystemServerMustBeLoadedEvenIfAnotherAppIsCurrent() {
        val result = evaluate(framework = framework(
            scopes = setOf("system", optionalApp),
            targets = listOf(target(optionalApp)),
        ))
        assertEquals(LspStatus.WAITING_RESTART, result.status)
        assertEquals("system_not_loaded", result.reason)
    }

    @Test
    fun androidScopeIsNotASystemServerAlias() {
        val result = evaluate(framework = framework(
            scopes = setOf("android"), targets = listOf(target("android:ui", 1000)),
        ))
        assertEquals(LspStatus.READY, result.status)
        assertEquals(LspStatus.WAITING_RESTART, evaluate(framework = framework(
            scopes = setOf("android"), targets = listOf(target("system", 1000)),
        )).status)
    }

    @Test
    fun aSecondaryUserCanUseSharedSystemUidTargets() {
        val result = evaluate(framework = framework(targets = listOf(target(uid = 1000))), userId = 10)
        assertEquals(LspStatus.READY, result.status)
    }

    @Test
    fun aScopedSystemUiMustHaveItsExactMainProcess() {
        val result = evaluate(framework = framework(
            scopes = setOf("system", systemUi),
            targets = listOf(target("system_server", 1000), target("$systemUi:service")),
        ))
        assertEquals(LspStatus.WAITING_RESTART, result.status)
        assertEquals("systemui_not_loaded", result.reason)
    }

    @Test
    fun aDifferentUsersSystemUiMainCannotReplaceTheCurrentUsersMain() {
        val result = evaluate(framework = framework(
            scopes = setOf("system", systemUi),
            targets = listOf(target("system_server", 1000), target(uid = 1_010_001)),
        ))
        assertEquals(LspStatus.WAITING_RESTART, result.status)
        assertEquals("systemui_not_loaded", result.reason)
    }

    @Test
    fun currentSystemUiMainAndSubprocessesCanCoexist() {
        val result = evaluate(framework = framework(targets = listOf(target(), target("$systemUi:service"))))
        assertEquals(LspStatus.READY, result.status)
    }

    @Test
    fun aLoadedStaleSubprocessRequiresRestartEvenWithACurrentMain() {
        val result = evaluate(framework = framework(targets = listOf(
            target(), target("$systemUi:service", current = false),
        )))
        assertEquals(LspStatus.WAITING_RESTART, result.status)
    }

    @Test
    fun liveVersionAndEvidenceSourceAreRetainedWithoutChangingConfiguration() {
        val result = evaluate(framework = framework())
        assertEquals("LSPosed test / API 102", result.frameworkVersionText)
        assertEquals("config_db+framework_service", result.source)
        assertEquals(LspModuleState.ENABLED, result.moduleState)
        assertEquals("ready", result.reason)
    }

    @Test
    fun currentConfigurationOverridesAStillConnectedServicesOldScopes() {
        val result = evaluate(configuredScopes = emptySet(), requiredScopes = setOf(systemUi))
        assertEquals(LspStatus.MISSING_SCOPE, result.status)
        assertEquals(setOf(systemUi), result.missingScopes)
        assertEquals(emptySet<String>(), result.scopes)
    }

    @Test
    fun partialMissingScopesAreReportedBeforeTargetLoadingOrConnection() {
        listOf(framework(targets = emptyList()), null).forEach { live ->
            val result = evaluate(framework = live, configuredScopes = setOf(systemUi),
                requiredScopes = setOf("system", systemUi, optionalApp))
            assertEquals(LspStatus.MISSING_SCOPE, result.status)
            assertEquals(setOf("system", optionalApp), result.missingScopes)
            assertEquals("scope_incomplete", result.reason)
        }
    }

    @Test
    fun unavailableConfiguredScopesCannotFallBackToServiceScopes() {
        val result = evaluate(configuredScopes = null)
        assertEquals(LspStatus.UNKNOWN, result.status)
        assertEquals("scope_unavailable", result.reason)
        assertNull(result.scopes)
    }

    @Test
    fun systemAndAndroidScopesDoNotSatisfyEachOthersRequirements() {
        val result = evaluate(configuredScopes = setOf("android"), requiredScopes = setOf("system"))
        assertEquals(setOf("system"), result.missingScopes)
        assertEquals(LspStatus.MISSING_SCOPE, result.status)
    }

    @Test
    fun disabledConfigurationStillWinsOverMissingScopes() {
        val result = evaluate(LspModuleState.DISABLED, configuredScopes = emptySet(), requiredScopes = setOf(systemUi))
        assertEquals(LspStatus.DISABLED, result.status)
    }

    private fun evaluate(
        state: LspModuleState = LspModuleState.ENABLED,
        framework: LspFrameworkSnapshot? = framework(),
        userId: Int = 0,
        configuredScopes: Set<String>? = framework?.scopes,
        requiredScopes: Set<String> = emptySet(),
    ) = LspDetectionPolicy.evaluate(state, framework, userId, configuredScopes, requiredScopes)

    private fun framework(
        apiVersion: Int = 102,
        scopes: Set<String>? = setOf(systemUi),
        targets: List<LspLoadedTarget>? = listOf(target()),
    ) = LspFrameworkSnapshot(apiVersion, "LSPosed test / API $apiVersion", scopes, targets)

    private fun target(
        processName: String = systemUi,
        uid: Int = 10001,
        current: Boolean = true,
        loadedVersionCode: Long = versionCode,
    ) = LspLoadedTarget(processName, uid, current, loadedVersionCode)
}
