package com.mi.onextbox.ui.screens

import com.mi.onextbox.lsp.SystemAdaptationVersion
import com.mi.onextbox.lsp.compat.AdaptationRuntimePolicy
import com.mi.onextbox.lsp.compat.ColorOs16Capabilities
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ColorOs16RegistryConsistencyTest {
    @Test fun visibleFeaturesAndInjectedScopesUseTheSameApprovedRegistry() {
        val entries = ColorOs16FeatureRegistry.entries
        assertEquals(entries.size, entries.map { it.id }.distinct().size)
        assertEquals(ColorOs16Capabilities.featureIds, entries.map { it.id }.toSet())
        assertEquals(ColorOs16Capabilities.packages, entries.flatMap { it.packages }.toSet())
        entries.forEach { entry ->
            assertTrue(entry.id, entry.titles.isNotEmpty())
            assertTrue(entry.id, entry.pages.isNotEmpty())
            entry.pages.forEach { assertTrue(entry.id, ColorOs16FeaturePolicy.isPageAvailable(it)) }
            entry.packages.forEach {
                assertTrue(it, AdaptationRuntimePolicy.allowsPackage(SystemAdaptationVersion.ColorOs16, it))
            }
        }
    }

    @Test fun everyApprovedAppScopeIsDeclaredToTheModuleFramework() {
        val file = listOf(File("src/main/resources/META-INF/xposed/scope.list"),
            File("app/src/main/resources/META-INF/xposed/scope.list")).first { it.isFile }
        val declared = file.readLines().map(String::trim).toSet() + "system"
        assertTrue((ColorOs16Capabilities.packages - declared).toString(),
            declared.containsAll(ColorOs16Capabilities.packages))
    }
}
