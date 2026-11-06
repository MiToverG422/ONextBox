package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeFiveGaUiSafetyContractTest {
    private val source by lazy {
        listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
            .resolve("com/mi/onextbox/lsp/NativeFiveGaUiHooker.kt").readText()
    }

    @Test fun neverChangesNativeComposeSlotStructureOrReplaysScopes() {
        listOf("startRestartGroup", "endRestartGroup", "updateScope", "composeRevision", "mutableStateOf")
            .forEach { assertFalse("Native composition must own $it", source.contains(it)) }
        assertFalse(source.contains("args[10] ="))
    }

    @Test fun disabledAndSafeModeStartupDoNotInstallPresentationAdapters() {
        assertTrue(source.contains("if (!NativeFiveGaSettings.getEnabled() || SafeModeRuntime.blocked) return"))
    }

    @Test fun preservesNativeSharingPolicyAndParentScope() {
        assertFalse(source.contains("WhileSubscribed"))
        assertFalse(source.contains("Eagerly"))
        assertFalse(source.contains("zeroReplay"))
    }
}
