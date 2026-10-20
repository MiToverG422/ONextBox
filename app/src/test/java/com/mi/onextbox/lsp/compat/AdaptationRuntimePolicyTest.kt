package com.mi.onextbox.lsp.compat

import com.mi.onextbox.lsp.SystemAdaptationVersion
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AdaptationRuntimePolicyTest {
    @Test fun onlyMatchingProfileHooksCanRun() {
        val color = SystemAdaptationVersion.ColorOs17
        val hyper = SystemAdaptationVersion.HyperOs3
        assertTrue(AdaptationRuntimePolicy.allowsHook(color, color))
        assertFalse(AdaptationRuntimePolicy.allowsHook(color, hyper))
        assertFalse(AdaptationRuntimePolicy.allowsHook(hyper, color))
        assertTrue(AdaptationRuntimePolicy.allowsHook(hyper, hyper))
    }

    @Test fun unknownOrDisabledProfilesCannotActivateHyperOs() {
        assertEquals(SystemAdaptationVersion.ColorOs17, AdaptationRuntimePolicy.selected(null))
        assertEquals(SystemAdaptationVersion.ColorOs17, AdaptationRuntimePolicy.selected(mapOf("system_adaptation_version" to 4)))
        assertEquals(SystemAdaptationVersion.HyperOs3, AdaptationRuntimePolicy.selected(mapOf(
            "system_adaptation_version" to 4, "developer_allow_hyperos3" to true)))
    }

    @Test fun bothHookPathsAndAsyncVendorWritesAreGuardedButLifecycleDispatchRemainsAvailable() {
        fun source(path: String) = File("src/main/java/com/mi/onextbox/$path").readText()
        val bridge = source("lsp/compat/ModernHookBridge.kt")
        assertEquals(2, Regex("if \\(!AdaptationHookGate.isActive\\(profile\\)\\)").findAll(bridge).count())
        assertTrue(source("lsp/compat/HotReloadLifecycle.kt").contains("AdaptationHookGate.isBoundProfileActive()"))
        assertTrue(source("lsp/OosLspModuleEntry.kt").contains("HotReloadLifecycle.withInfrastructureHook("))
        for (path in listOf("lsp/hyperos/Force185.java", "lsp/hyperos/TouchRateLock.java")) {
            assertTrue(source(path).contains("AdaptationHookGate.isHyperOsActive()"))
        }
    }

    @Test fun profileChangesStaySilentButSaveFailuresRemainVisible() {
        for (name in listOf("SettingsSystemAdaptationGroup", "DeveloperHyperOsSupportGroup")) {
            val source = File("src/main/java/com/mi/onextbox/ui/settings/$name.kt").readText()
            assertFalse(source.contains("R.string.system_adaptation_runtime_restart"))
            assertTrue(source.contains("R.string.setting_system_adaptation_save_failed"))
        }
    }
}
