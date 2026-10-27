package com.mi.onextbox.lsp

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class LauncherBlurContractTest {
    private val main = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private fun source(name: String) = File(main, "java/com/mi/onextbox/lsp/$name.kt").readText()

    @Test fun nativeRenderingAndSystemProtectionAreNotReplaced() {
        val hooks = source("LauncherBlurHooker")
        listOf("animationBlurrySwitch", "system_material_blur_enable", "low_power",
            "super_powersave_mode_state", "disable_window_blurs").forEach { assertTrue(hooks.contains(it)) }
        assertTrue(hooks.contains("LauncherFeature.ForceBlur"))
        assertTrue(hooks.contains("if (!enabled()) return"))
        assertTrue(hooks.contains("scope.isActive()"))
        assertTrue(hooks.contains("if (enabled()) false else chain.proceed()"))
        assertFalse(hooks.contains("Settings.System.put"))
        assertFalse(hooks.contains("Settings.Global.put"))
        assertFalse(hooks.contains("SystemProperties"))
        assertFalse(hooks.contains("setBackground("))
        assertFalse(hooks.contains("setRenderEffect("))
        assertTrue(source("LauncherFeaturesHooker").contains("LauncherBlurHooker.hook(classLoader, apkPath)"))
    }

    @Test fun obfuscatedTargetsAreDiscoveredFromNativeConsumersNotShortNames() {
        val targets = source("LauncherBlurTargets")
        assertTrue(targets.contains("PopupBlurView"))
        assertTrue(targets.contains("isSupportDynamicBlur"))
        assertTrue(targets.contains("getAnimLevelVersion e:"))
        assertTrue(targets.contains("current animLevel is too low"))
        assertTrue(targets.contains("com.oplus.graphics.OplusBlurManager"))
        assertTrue(targets.contains("\"setBackgroundBlurRadius\""))
        assertTrue(targets.contains("direct.flatMap { it.callers }"))
        listOf("\"ii.l\"", "\"pg.a\"", "\"pg.e\"").forEach { assertFalse(targets.contains(it)) }
    }

    @Test fun allLocalesKeepTitleAndGroupWithoutASummary() {
        val locales = listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant",
            "values-ja", "values-ja-rCN", "values-ko", "values-vi", "values-ru")
        locales.forEach { locale ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File(main, "res/$locale/strings.xml"))
            val nodes = doc.getElementsByTagName("string")
            listOf("launcher_group_effects", "launcher_force_blur_title").forEach { key ->
                val matches = (0 until nodes.length).map { nodes.item(it) }
                    .filter { it.attributes.getNamedItem("name").nodeValue == key }
                assertEquals("$locale:$key", 1, matches.size)
                val text = matches.single().textContent
                assertTrue(text.isNotBlank())
                assertFalse(text.endsWith("。").or(text.endsWith(".")))
            }
            val summaries = (0 until nodes.length).map { nodes.item(it) }
                .filter { it.attributes.getNamedItem("name").nodeValue == "launcher_force_blur_summary" }
            assertEquals("$locale:launcher_force_blur_summary", 1, summaries.size)
            assertEquals("$locale:launcher_force_blur_summary", "", summaries.single().textContent)
        }
    }
}
