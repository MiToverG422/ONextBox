package com.mi.onextbox.ui.screens

import com.mi.onextbox.lsp.SoftwareUpdateFeature
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class SoftwareUpdatePageContractTest {
    @Test fun everyFeatureHasExactlyOneRowAndFourJoinedGroups() {
        assertEquals(SoftwareUpdateFeature.entries.toSet(), SoftwareUpdateFeatureItems.all.map { it.feature }.toSet())
        assertEquals(listOf(3, 2, 4, 1), listOf(SoftwareUpdateFeatureItems.checks.size, SoftwareUpdateFeatureItems.blocking.size,
            SoftwareUpdateFeatureItems.automatic.size, SoftwareUpdateFeatureItems.information.size))
        assertEquals(10, SoftwareUpdateFeatureItems.all.map { it.title }.distinct().size)
        assertEquals(10, SoftwareUpdateFeatureItems.all.map { it.summary }.distinct().size)
    }

    @Test fun sharedRowsProvideJoinedCardsAndDividersForBothUiStyles() {
        val text = file("main/java/com/mi/onextbox/ui/screens/SoftwareUpdateFeaturesPage.kt").readText()
        for (required in listOf("SettingsGroup {", "if (index > 0) SettingsDivider()", "SettingsToggleRow(",
            "hasDividerAbove = index > 0", "hasDividerBelow = index < items.lastIndex", "FeatureSettingWriteQueue.application.enqueue")) {
            assertTrue(required, text.contains(required))
        }
        assertFalse(text.contains("LocalAppUiStyle"))
    }

    @Test fun theNewPageHasNavigationScopeAndPackageVisibility() {
        val root = file("main/java/com/mi/onextbox/ui/Root.kt").readText()
        assertTrue(root.contains("data object FeatureSoftwareUpdate : RootRoute"))
        assertTrue(root.contains("subclass(RootRoute.FeatureSoftwareUpdate::class)"))
        assertTrue(root.contains("entry<RootRoute.FeatureSoftwareUpdate>"))
        assertTrue(file("main/resources/META-INF/xposed/scope.list").readLines().contains("com.oplus.ota"))
        assertTrue(file("main/AndroidManifest.xml").readText().contains("<package android:name=\"com.oplus.ota\" />"))
        assertTrue(file("main/resources/META-INF/xposed/scope.list").readLines().contains("com.oplus.sau"))
        assertTrue(file("main/AndroidManifest.xml").readText().contains("<package android:name=\"com.oplus.sau\" />"))
        assertTrue(file("main/java/com/mi/onextbox/ui/screens/FeaturesScreen.kt").readText()
            .contains("SoftwareUpdateSettings.PACKAGE_NAME, SoftwareUpdateSettings.SYSTEM_FILES_PACKAGE_NAME"))
    }

    @Test fun translatedStringsAndFormatArgumentsMatchInAllExistingCoreLocales() {
        val baseline = strings("values")
        assertEquals(46, baseline.size)
        assertTrue(baseline.keys.containsAll(listOf("ota_details_source_links", "ota_details_link_failed_code",
            "ota_details_link_auth_unavailable", "ota_details_link_timeout", "ota_details_link_network", "ota_details_link_failed")))
        listOf("values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant", "values-ja", "values-ja-rCN",
            "values-ko", "values-vi", "values-ru").forEach { locale ->
            val translated = strings(locale)
            assertEquals(locale, baseline.keys, translated.keys)
            val arguments = Regex("%[0-9]\\\$[sd]")
            baseline.forEach { (key, value) ->
                assertEquals("$locale:$key", arguments.findAll(value).map { it.value }.toList(),
                    arguments.findAll(translated.getValue(key)).map { it.value }.toList())
            }
        }
    }

    @Test fun featureDescriptionsOmitRetainedBehaviorExplanations() {
        val omitted = mapOf(
            "values" to listOf("retain", "keep", "manual downloads remain available"),
            "values-zh-rCN" to listOf("保留"),
            "values-zh-rTW" to listOf("保留"),
            "values-b+yue+Hant" to listOf("保留"),
            "values-ja" to listOf("維持"),
            "values-ja-rCN" to listOf("保留"),
            "values-ko" to listOf("유지"),
            "values-vi" to listOf("giữ"),
            "values-ru" to listOf("сохран"),
        )
        omitted.forEach { (locale, phrases) ->
            strings(locale).filterKeys { it.endsWith("_summary") }.forEach { (key, value) ->
                assertTrue("$locale:$key", value.isNotBlank())
                phrases.forEach { phrase -> assertFalse("$locale:$key:$phrase", value.contains(phrase, ignoreCase = true)) }
            }
        }
        assertEquals("阻止夜间定时安装，不中断已经开始的安装", strings("values-zh-rCN")["ota_disable_night_summary"])
    }

    @Test fun updateSettingsSingleActionUsesCouiPopup() {
        val source = file("main/java/com/mi/onextbox/ui/settings/SettingsMainPage.kt").readText()
        val action = source.substringAfter("fun SoftwareUpdateTopBarActions(")
            .substringBefore("return@Box")
        assertFalse(action.contains("AppUiStyle.Material3Expressive"))
        assertTrue(action.contains("ColorOsPopup("))
        assertTrue(action.contains("ColorOsTopBarButton("))
        assertFalse(action.contains("shape = MenuDefaults.standaloneItemShape"))
    }

    private fun strings(locale: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(file("main/res/$locale/strings.xml"))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }.filterKeys { it.startsWith("ota_") }
    }

    private fun file(path: String): File = listOf(File("src/$path"), File("app/src/$path")).first { it.isFile }
}
