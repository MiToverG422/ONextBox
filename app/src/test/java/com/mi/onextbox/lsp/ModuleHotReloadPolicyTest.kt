package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleHotReloadPolicyTest {
    private val supported = ModuleHotReloadPolicy.supportedPackages - "system"

    @Test
    fun everyDeclaredAppScopeIsSupported() {
        val file = listOf(File("src/main/resources/META-INF/xposed/scope.list"),
            File("app/src/main/resources/META-INF/xposed/scope.list")).first { it.isFile }
        assertEquals(file.readLines().filter { it.isNotBlank() }.toSet(), ModuleHotReloadPolicy.supportedPackages)
        supported.forEach { process ->
            assertTrue(process, ModuleHotReloadPolicy.supportsProcess(process, false))
            assertTrue(process, canReload(process))
        }
    }

    @Test
    fun eligibilityHasNoUserToggleParameter() {
        val method = ModuleHotReloadPolicy::class.java.declaredMethods.single { it.name == "canReload" }
        assertEquals(4, method.parameterCount)
        assertEquals(String::class.java, method.parameterTypes.first())
    }

    @Test
    fun failedOrIncompleteHookGenerationRejectsReload() {
        supported.forEach { process ->
            assertFalse(process, canReload(process, healthy = false))
        }
    }

    @Test
    fun subprocessesAreSupportedButSimilarPackageNamesAreNot() {
        supported.forEach { assertTrue(canReload("$it:remote", packages = setOf(it))) }
        supported.flatMap { packageName ->
            listOf("$packageName.debug", "$packageName ")
        }.forEach { process ->
            assertFalse(process, ModuleHotReloadPolicy.supportsProcess(process, false))
            assertFalse(process, canReload(process))
        }
    }

    @Test
    fun unknownProcessesAreNotGuessed() {
        listOf(
            "", "android", "system", "system_server", "com.example.app",
        ).forEach { process ->
            assertFalse(process, ModuleHotReloadPolicy.supportsProcess(process, false))
            assertFalse(process, canReload(process))
        }
    }

    @Test
    fun systemServerFlagRejectsEvenAnOtherwiseSupportedName() {
        supported.forEach { process ->
            assertFalse(process, ModuleHotReloadPolicy.supportsProcess(process, true))
            assertFalse(process, canReload(process, systemServer = true))
        }
    }

    @Test
    fun systemServerHasAnExplicitFrameworkLifecycleRecord() {
        for (name in listOf("system", "system_server")) {
            assertTrue(canReload(name, systemServer = true, packages = setOf("system")))
            assertFalse(canReload(name, systemServer = true, packages = setOf("com.android.systemui")))
            assertFalse(canReload(name, systemServer = true, packages = emptySet()))
        }
    }

    @Test
    fun packageLifecycleMustIncludeTheRealPrimaryPackage() {
        supported.forEach { process ->
            assertFalse(process, canReload(process, packages = emptySet()))
            assertFalse(process, canReload(process, packages = setOf("com.example.app")))
            assertFalse(process, canReload(process, packages = supported - process))
        }
    }

    @Test
    fun knownSharedPackagesAndNonScopeCompanionContextsAreReplayed() {
        supported.forEach { process ->
            assertTrue(process, canReload(process, packages = supported))
            assertTrue(process, canReload(process, packages = setOf(process, "com.example.host.plugin")))
            assertTrue(process, canReload("$process:remote", packages = setOf(process, "com.oplus.theme.resources")))
        }
    }

    @Test
    fun companionContextsDoNotTurnAnUnknownProcessIntoASupportedScope() {
        val companions = supported + "com.example.app"
        assertFalse(canReload("com.example.app", packages = companions))
        assertFalse(canReload("com.example.app:remote", packages = companions))
        assertFalse(canReload("com.oplus.theme.resources", packages = supported + "com.oplus.theme.resources"))
    }

    @Test
    fun companionContextsCannotReplaceThePrimaryLifecycleRecord() {
        supported.forEach { process ->
            assertFalse(process, canReload(process, packages = setOf("com.oplus.theme.resources", "com.example.host.plugin")))
            assertFalse(process, canReload("$process:remote", packages = setOf("com.example.host.plugin")))
        }
        assertFalse(canReload("system", systemServer = true, packages = setOf("com.oplus.theme.resources")))
    }

    @Test
    fun systemServerCanReplayItsFrameworkRecordAndResourceContexts() {
        for (name in listOf("system", "system_server")) {
            assertTrue(canReload(name, systemServer = true,
                packages = setOf("system", "com.oplus.theme.resources", "com.example.host.plugin")))
            assertTrue(canReload(name, systemServer = true, packages = supported + "system" + "com.oplus.theme.resources"))
        }
    }

    @Test
    fun frameworkAndInvalidLifecycleRecordsStillRejectReload() {
        supported.forEach { process ->
            assertFalse(process, canReload(process, packages = setOf(process, "android")))
            assertFalse(process, canReload(process, packages = setOf(process, "system")))
            assertFalse(process, canReload(process, packages = setOf(process, "system_server")))
            assertFalse(process, canReload(process, packages = setOf(process, "")))
        }
        for (name in listOf("system", "system_server")) {
            for (invalid in listOf("android", "system_server", "", " ")) {
                assertFalse(canReload(name, systemServer = true, packages = setOf("system", invalid)))
            }
        }
    }

    private fun canReload(
        process: String,
        healthy: Boolean = true,
        systemServer: Boolean = false,
        packages: Set<String> = setOf(process),
    ): Boolean = ModuleHotReloadPolicy.canReload(process, systemServer, packages, healthy)
}
