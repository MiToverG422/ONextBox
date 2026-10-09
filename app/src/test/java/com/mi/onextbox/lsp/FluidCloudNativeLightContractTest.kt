package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source contracts guard against globally suppressing the native edge/light pipeline again. */
class FluidCloudNativeLightContractTest {
    @Test fun templateOverridesDoNotWriteOrInterceptNativeLightFlags() {
        val source = source("FluidCloudTemplateMaterialHooks.kt")
        assertFalse(source.contains("\"enableLight\""))
        assertFalse(source.contains("Reflect.callMethod(wrapper, \"u\""))
        assertFalse(source.contains("Triple(\"u\""))
        assertTrue(source.contains("Bundle(original).apply"))
        assertTrue(source.contains("putInt(CONTENT, 1)"))
        assertTrue(source.contains("putBoolean(BACKGROUND, true)"))
    }

    @Test fun pluginMaterialHooksDoNotClearNativeLightListsOrForeground() {
        val source = source("FluidCloudMaterialHooker.kt")
        assertFalse(source.contains("setMultiLightParams"))
        assertFalse(source.contains("emptyList<Any>()"))
        assertFalse(source.contains("setForeground"))
        assertTrue(source.contains("expandedMaterial.install(loader, prefix)"))
        assertTrue(source.contains("installPanelMaterial(loader)"))
    }

    private fun source(name: String): String = listOf(
        File("src/main/java/com/mi/onextbox/lsp/$name"),
        File("app/src/main/java/com/mi/onextbox/lsp/$name"),
    ).first { it.isFile }.readText()
}
