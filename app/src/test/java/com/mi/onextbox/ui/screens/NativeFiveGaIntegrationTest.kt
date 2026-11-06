package com.mi.onextbox.ui.screens

import com.mi.onextbox.R
import com.mi.onextbox.lsp.NativeFiveGaSettings
import com.mi.onextbox.lsp.SystemAdaptationVersion
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class NativeFiveGaIntegrationTest {
    @Test fun nativeRecoveryHasOneEditableDestinationAndOnlyTheTwoRequiredScopes() {
        val title = R.string.native_5ga_title
        assertEquals(FeaturePageMode.MobileNetwork, FeatureSearchIndex.entries.single { it.title == title }.page)
        assertEquals(FeaturePageMode.MobileNetwork, EnabledFeatureCatalog.entries.single { it.title == title }.page)
        assertTrue(EnabledFeatureActions().supports(title))
        assertEquals(listOf("com.android.phone", "com.android.systemui"), NativeFiveGaSettings.restartPackages)
        assertFalse(FeatureRestartPlan(NativeFiveGaSettings.restartPackages).includesFramework)
    }

    @Test fun c16CannotExposeTheUnverifiedAdapterThroughSearchOrRelatedLinks() {
        val title = R.string.native_5ga_title
        assertFalse(featureSearchEntries(SystemAdaptationVersion.ColorOs16).any { it.title == title })
        assertTrue(FeatureRecommendations.destinations(FeaturePageMode.SystemUiStatusBarNetwork,
            SystemAdaptationVersion.ColorOs16).none { it.title == title })
        val related = FeatureRecommendations.destinations(FeaturePageMode.SystemUiStatusBarNetwork,
            SystemAdaptationVersion.ColorOs17)
        assertEquals(listOf(title), related.map { it.title })
        assertEquals(FeaturePageMode.MobileNetwork, related.single().page)
    }

    @Test fun everyLocaleHasCompleteNativeRecognitionAndRestartTextWithMatchingFormats() {
        val root = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        fun strings(locale: String): Map<String, String> {
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File(root, "$locale/strings.xml")).getElementsByTagName("string")
            val result = linkedMapOf<String, String>()
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val key = node.attributes.getNamedItem("name").nodeValue
                if (key.startsWith("native_5ga_")) assertNull("Duplicate $locale:$key", result.put(key, node.textContent))
            }
            return result
        }
        val original = strings("values")
        assertEquals(15, original.size)
        val formats = Regex("%[0-9]+\\$[ds]")
        for (locale in listOf("values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant", "values-ja",
            "values-ja-rCN", "values-ko", "values-ru", "values-vi")) {
            val translated = strings(locale)
            assertEquals(locale, original.keys, translated.keys)
            for ((key, value) in original) {
                assertTrue("$locale:$key", translated.getValue(key).isNotBlank())
                assertEquals("$locale:$key", formats.findAll(value).map { it.value }.toList(),
                    formats.findAll(translated.getValue(key)).map { it.value }.toList())
            }
        }
    }
}
