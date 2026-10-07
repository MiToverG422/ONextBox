package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LspDbRulesTest {
    private val modern = setOf("module_pkg_name", "user_id", "enabled", "scope_request_blocked")
    private val legacyUser = setOf("module_pkg_name", "user_id", "enabled", "apk_path")
    private val legacyOwner = setOf("module_pkg_name", "enabled", "apk_path")
    private val scope = setOf("module_pkg_name", "app_pkg_name", "user_id")

    @Test
    fun modernConfigurationTakesPrecedenceOverLegacyEnabledColumns() {
        assertEquals(
            LspDbSchema.MODERN,
            LspDbRules.schema(mapOf("modules_state" to modern, "modules" to legacyUser), 0),
        )
    }

    @Test
    fun modernConfigurationSupportsSecondaryUsers() {
        assertEquals(LspDbSchema.MODERN, LspDbRules.schema(mapOf("modules_state" to modern), 10))
        assertTrue(LspDbRules.query(LspDbSchema.MODERN).contains("AND user_id = ?"))
    }

    @Test
    fun aMissingCurrentUserRecordIsDisabledNotALegacyFallback() {
        val schema = LspDbRules.schema(mapOf("modules_state" to modern, "modules" to legacyOwner), 10)
        assertEquals(LspDbSchema.MODERN, schema)
        assertEquals(LspModuleState.DISABLED, LspDbRules.moduleState(emptyList()))
    }

    @Test
    fun anIncompleteModernSchemaCannotFallBackToLegacy() {
        modern.forEach { omitted ->
            if (omitted == "scope_request_blocked") return@forEach
            assertNull(
                LspDbRules.schema(
                    mapOf("modules_state" to (modern - omitted), "modules" to legacyUser), 0,
                ),
            )
        }
    }

    @Test
    fun modulePathsAreNotEnabledConfiguration() {
        assertNull(LspDbRules.schema(mapOf("modules" to setOf("module_pkg_name", "apk_path")), 0))
    }

    @Test
    fun unrelatedGuessedTablesAndColumnsAreNotAccepted() {
        assertNull(LspDbRules.schema(mapOf("module" to modern), 0))
        assertNull(LspDbRules.schema(mapOf("modules" to setOf("package_name", "enable", "user")), 0))
    }

    @Test
    fun perUserLegacyConfigurationAlwaysConstrainsTheUser() {
        assertEquals(LspDbSchema.LEGACY_USER, LspDbRules.schema(mapOf("modules" to legacyUser), 10))
        assertEquals(
            "SELECT enabled FROM modules WHERE module_pkg_name = ? AND user_id = ? LIMIT 2",
            LspDbRules.query(LspDbSchema.LEGACY_USER),
        )
    }

    @Test
    fun ownerOnlyLegacyConfigurationNeverAppliesToSecondaryUsers() {
        assertEquals(LspDbSchema.LEGACY_OWNER, LspDbRules.schema(mapOf("modules" to legacyOwner), 0))
        assertNull(LspDbRules.schema(mapOf("modules" to legacyOwner), 1))
        assertNull(LspDbRules.schema(mapOf("modules" to legacyOwner), 10))
        assertFalse(LspDbRules.query(LspDbSchema.LEGACY_OWNER).contains("user_id"))
    }

    @Test
    fun onlyExplicitBooleanValuesAreAccepted() {
        listOf("1", "true", " TRUE ").forEach {
            assertEquals(LspModuleState.ENABLED, LspDbRules.moduleState(listOf(it)))
        }
        listOf("0", "false", " FALSE ").forEach {
            assertEquals(LspModuleState.DISABLED, LspDbRules.moduleState(listOf(it)))
        }
    }

    @Test
    fun ambiguousOrMalformedValuesDoNotBecomeEnabled() {
        listOf(null, "", " ", "-1", "2", "01", "1.0", "enabled", "yes").forEach {
            assertEquals(LspModuleState.UNKNOWN, LspDbRules.moduleState(listOf(it)))
        }
        assertEquals(LspModuleState.UNKNOWN, LspDbRules.moduleState(listOf("1", "1")))
        assertEquals(LspModuleState.UNKNOWN, LspDbRules.moduleState(listOf("0", "1")))
    }

    @Test
    fun actualScopeQueriesConstrainBothTheModuleAndCurrentUser() {
        assertEquals(
            "SELECT DISTINCT app_pkg_name FROM scope WHERE module_pkg_name = ?" +
                " AND (user_id = ? OR app_pkg_name = 'system')",
            LspDbRules.scopeQuery(mapOf("scope" to scope)),
        )
    }

    @Test
    fun missingOrIncompleteScopeSchemasAreUnknownRatherThanEmpty() {
        assertNull(LspDbRules.scopeQuery(emptyMap()))
        scope.forEach { omitted ->
            assertNull(LspDbRules.scopeQuery(mapOf("scope" to (scope - omitted))))
        }
        assertNull(LspDbRules.scopeQuery(mapOf("scopes" to scope)))
        assertNull(
            LspDbRules.scopeQuery(mapOf("scope" to setOf("module_pkg_name", "app_pkg_name", "app_user_id"))),
        )
    }

    @Test
    fun noCurrentUserScopeRowsMeansAnExplicitlyEmptyScope() {
        assertEquals(emptySet<String>(), LspDbRules.scopes(emptyList()))
    }

    @Test
    fun removingAllScopesDoesNotPreservePreviousScopeState() {
        assertEquals(setOf("android", "com.android.systemui"), LspDbRules.scopes(listOf("android", "com.android.systemui")))
        assertEquals(emptySet<String>(), LspDbRules.scopes(emptyList()))
    }

    @Test
    fun aPartialScopeIsKeptAsPartialWithoutInventingMissingTargets() {
        assertEquals(setOf("com.android.systemui"), LspDbRules.scopes(listOf("com.android.systemui")))
        assertEquals(setOf("android"), LspDbRules.scopes(listOf("android")))
    }

    @Test
    fun scopeRowsAreNormalizedWithoutDroppingUnverifiableEntries() {
        assertEquals(
            setOf("android", "com.android.systemui"),
            LspDbRules.scopes(listOf(" android ", "com.android.systemui", "android")),
        )
        listOf(null, "", " ", "\t\n").forEach { malformed ->
            assertNull(LspDbRules.scopes(listOf("android", malformed)))
        }
    }

    @Test
    fun unavailableScopeEvidenceDoesNotEraseExplicitEnabledConfiguration() {
        val configuration = LspDbConfiguration(LspDbRules.moduleState(listOf("1")), null)
        assertEquals(LspModuleState.ENABLED, configuration.moduleState)
        assertNull(configuration.scopes)
    }

    @Test
    fun anUnverifiedConfigurationDoesNotHaveAssumedScopes() {
        val configuration = LspDbConfiguration()
        assertEquals(LspModuleState.UNKNOWN, configuration.moduleState)
        assertNull(configuration.scopes)
        assertEquals("config_unavailable", configuration.unavailableReason)
    }

    @Test
    fun anUninitializedRootShellIsNotAConfigurationFailure() {
        val configuration = LspDbConfiguration(unavailableReason = "root_shell_unavailable")
        assertEquals(LspModuleState.UNKNOWN, configuration.moduleState)
        assertNull(configuration.scopes)
        assertEquals("root_shell_unavailable", configuration.unavailableReason)
    }

    @Test
    fun confirmedMidScopeSchemaUsesTheExactModuleJoinAndGlobalSystemException() {
        assertEquals(
            "SELECT DISTINCT scope.app_pkg_name FROM scope" +
                " INNER JOIN modules ON scope.mid = modules.mid WHERE modules.module_pkg_name = ?" +
                " AND (scope.user_id = ? OR scope.app_pkg_name = 'system')",
            LspDbRules.scopeQuery(
                mapOf("scope" to setOf("mid", "app_pkg_name", "user_id"), "modules" to setOf("mid", "module_pkg_name")),
            ),
        )
    }

    @Test
    fun midScopeRowsCannotBeMatchedThroughUnknownModuleColumns() {
        val scopeByMid = setOf("mid", "app_pkg_name", "user_id")
        assertNull(LspDbRules.scopeQuery(mapOf("scope" to scopeByMid)))
        assertNull(LspDbRules.scopeQuery(mapOf("scope" to scopeByMid, "modules" to setOf("module_id", "module_pkg_name"))))
        assertNull(LspDbRules.scopeQuery(mapOf("scope" to scopeByMid, "modules" to setOf("mid", "package_name"))))
    }

    @Test
    fun systemIsGlobalButAndroidIsNeverAnUnrestrictedUserException() {
        val query = LspDbRules.scopeQuery(mapOf("scope" to scope))!!
        assertTrue(query.contains("(user_id = ? OR app_pkg_name = 'system')"))
        assertFalse(query.contains("'android'"))
    }
}
