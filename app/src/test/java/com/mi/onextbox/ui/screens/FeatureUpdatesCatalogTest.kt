package com.mi.onextbox.ui.screens

import com.mi.onextbox.R
import com.mi.onextbox.lsp.SystemAdaptationVersion
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class FeatureUpdatesCatalogTest {
    @Test fun everyChangeHasOneCurrentDestinationAndIsListedOnce() {
        val changes = FeatureUpdatesCatalog.changes
        assertEquals(changes.size, changes.map { it.title }.distinct().size)
        val items = FeatureUpdatesCatalog.items(SystemAdaptationVersion.ColorOs17)
        assertEquals(changes.map { it.title }.toSet(), items.map { it.destination.title }.toSet())
        assertFalse(items.any { it.destination.page in setOf(FeaturePageMode.Main, FeaturePageMode.Updates) })
    }

    @Test fun existingFeatureAdjustmentsAreSeparatedFromNewFunctions() {
        val kinds = FeatureUpdatesCatalog.changes.associate { it.title to it.kind }
        assertEquals(FeatureUpdatesCatalog.Kind.Changed, kinds[R.string.launcher_custom_grid_title])
        assertFalse(kinds.containsKey(R.string.qs_tile_corner_title))
        assertFalse(kinds.containsKey(R.string.feature_settings_c15_about_layout_title))
        assertEquals(FeatureUpdatesCatalog.Kind.Changed, kinds[R.string.keyguard_swipe_pin_title])
        assertFalse(kinds.containsKey(R.string.hide_screenshot_floating_title))
        assertFalse(kinds.containsKey(R.string.power_indicator_title))
        assertEquals(FeatureUpdatesCatalog.Kind.Added, kinds[R.string.camera_default_gallery_title])
    }

    @Test fun colorOs16ShowsAuditedFeaturesAndOmitsUnsupportedPinInput() {
        val items = FeatureUpdatesCatalog.items(SystemAdaptationVersion.ColorOs16)
        assertTrue(items.any { it.destination.title == R.string.network_speed_refresh_second })
        assertFalse(items.any { it.destination.title == R.string.hide_screenshot_floating_title })
        assertFalse(items.any { it.destination.title == R.string.keyguard_swipe_pin_title })
        items.forEach {
            assertTrue(it.destination.title in ColorOs16FeatureRegistry.titlesForPage(it.destination.page))
        }
    }

    @Test fun collectionIsAvailableWithoutOfferingAnyScopeRestart() {
        assertTrue(FeaturePageMode.Updates.isNestedPage)
        assertTrue(isColorOs16FeaturePageAvailable(FeaturePageMode.Updates))
        SystemAdaptationVersion.entries.forEach {
            assertTrue(featureRestartPackages(FeaturePageMode.Updates, it).isEmpty())
        }
    }

    @Test fun completingAnOlderHighlightCannotClearANewerNavigationTarget() {
        val state = FeatureSpotlightState()
        state.show(FeatureSearchIndex.Entry(FeaturePageMode.DesktopLayout, R.string.launcher_custom_grid_title))
        val older = state.target!!
        state.show(FeatureSearchIndex.Entry(FeaturePageMode.SystemUiStatusBar, R.string.power_indicator_title))
        state.clear(older)
        assertEquals(R.string.power_indicator_title, state.target?.title)
        state.clear(state.target!!)
        assertNull(state.target)
    }

    @Test fun everyMaintainedLocaleIncludesTheCollectionLabelsAndFormatArguments() {
        val root = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val baseline = strings(parser.parse(File(root, "values/strings.xml")))
        assertEquals(11, baseline.size)
        root.listFiles()!!.filter { it.isDirectory && File(it, "strings.xml").isFile }.forEach { folder ->
            val localized = strings(parser.parse(File(folder, "strings.xml")))
            assertEquals(folder.name, baseline.keys, localized.keys)
            baseline.forEach { (key, value) ->
                assertEquals("${folder.name}: $key", Regex("%[12]\\$[ds]").findAll(value).map { it.value }.toSet(),
                    Regex("%[12]\\$[ds]").findAll(localized.getValue(key)).map { it.value }.toSet())
            }
        }
    }

    private fun strings(document: org.w3c.dom.Document): Map<String, String> {
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) }
            .filter { it.attributes.getNamedItem("name").nodeValue.startsWith("feature_updates_") }
            .associate { it.attributes.getNamedItem("name").nodeValue to it.textContent }
    }
}
