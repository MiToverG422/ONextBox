package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class RotationSuggestionContractTest {
    private val root = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    @Test fun hideUsesNativeCleanupWithoutChangingOrientationSettings() {
        val source = File(root, "java/com/mi/onextbox/lsp/RotationSuggestionHooker.kt").readText()
        assertTrue(source.contains("getBoolean(KEY, false)"))
        assertTrue(source.contains("setRotateSuggestionButtonState"))
        assertTrue(source.contains("if (RotationSuggestionSettings.enabledXposed())"))
        assertTrue(source.contains("param.args[0] = false"))
        assertTrue(source.contains("param.args[1] = true"))
        assertFalse(source.contains("param.result ="))
        assertFalse(source.contains("Settings.System"))
        assertFalse(source.contains("Settings.Secure"))
        assertFalse(source.contains("setRotationLock"))
    }
    @Test fun translatedLabelsExist() {
        val expectedKeys = setOf("rotation_suggestion_hide_title", "rotation_suggestion_hide_summary")
        for (locale in listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant", "values-ja", "values-ja-rCN", "values-ko", "values-ru", "values-vi")) {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(root, "res/$locale/strings.xml"))
            val nodes = doc.getElementsByTagName("string")
            val entries = (0 until nodes.length).map(nodes::item)
                .filter { it.attributes.getNamedItem("name").nodeValue in expectedKeys }
            assertEquals(locale, expectedKeys.size, entries.size)
            assertEquals(locale, expectedKeys, entries.map { it.attributes.getNamedItem("name").nodeValue }.toSet())
            entries.forEach { assertTrue(locale, it.textContent.isNotBlank()) }
        }
    }
}
