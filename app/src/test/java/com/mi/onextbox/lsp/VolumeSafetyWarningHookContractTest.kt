package com.mi.onextbox.lsp

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeSafetyWarningHookContractTest {
    @Test
    fun preferenceIsDefaultOffAndResettable() {
        val settings = source("lsp/VolumeSafetyWarningSettings.kt")
        assertTrue(settings.contains("getBoolean(KEY, false)"))
        assertTrue(settings.contains("HookConfigSnapshot.boolean(KEY, false) == true"))
        assertTrue(source("lsp/LspConfig.kt").contains("VolumeSafetyWarningSettings.set(context, false)"))
    }

    @Test
    fun systemUiCallbackDisablesSafeVolumeOnlyWhenEnabled() {
        val hooker = source("lsp/VolumeSafetyWarningHooker.kt")
        assertTrue(hooker.contains("OplusVolumeRepository\\\$mControllerCallbackH\\\$1"))
        assertTrue(hooker.contains("\"onShowSafetyWarning\""))
        assertTrue(hooker.contains("VolumeSafetyWarningSettings.enabledXposed()"))
        assertTrue(hooker.contains("\"disableSafeMediaVolume\""))
        assertTrue(hooker.contains("chain.proceed()"))
        assertTrue(source("lsp/OosLspModuleEntry.kt").contains(
            "VolumeSafetyWarningHooker.hook(classLoader)",
        ))
    }

    @Test
    fun volumePagePrecedesDynamicColorAndIsSearchable() {
        val categories = source("ui/screens/FeatureCategoriesPage.kt")
        val controlCenter = categories.indexOf("FeaturePageMode.SystemUiControlCenter")
        val volume = categories.indexOf("FeaturePageMode.SystemUiVolume")
        val dynamicColor = categories.indexOf("FeaturePageMode.SystemUiDynamicColor")
        assertTrue(controlCenter < volume && volume < dynamicColor)
        assertTrue(source("ui/screens/FeatureSearchIndex.kt").contains(
            "SharedFeatureDefinitions.volumeSafetyWarning.searchEntry()",
        ))
    }

    @Test
    fun everySupportedLanguageIncludesVolumeStrings() {
        val root = listOf(File("src/main/res"), File("app/src/main/res"))
            .first { it.isDirectory }
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val expected = setOf(
            "feature_group_volume",
            "feature_system_ui_volume_entry_summary",
            "volume_block_hearing_safety_warning_title",
            "volume_block_hearing_safety_warning_summary",
        )
        for (folder in listOf(
            "values",
            "values-zh-rCN",
            "values-zh-rTW",
            "values-b+yue+Hant",
            "values-ja",
            "values-ja-rCN",
            "values-ko",
            "values-ru",
            "values-vi",
        )) {
            val document = parser.parse(File(root, "$folder/strings.xml"))
            val strings = document.getElementsByTagName("string")
            val names = (0 until strings.length)
                .map { strings.item(it).attributes.getNamedItem("name").nodeValue }
                .toSet()
            assertTrue("$folder missing volume strings", names.containsAll(expected))
        }
    }

    private fun source(path: String): String = listOf(
        File("src/main/java/com/mi/onextbox/$path"),
        File("app/src/main/java/com/mi/onextbox/$path"),
    ).first { it.isFile }.readText()
}
