package com.mi.onextbox.ui.screens

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class RemovedRefreshRateToolContractTest {
    @Test fun toolHasNoPageRouteSearchEntryOrImplementation() {
        for (path in listOf(
            "java/com/mi/onextbox/ui/Root.kt",
            "java/com/mi/onextbox/ui/screens/FeaturesScreen.kt",
            "java/com/mi/onextbox/ui/screens/FeatureSearchIndex.kt",
        )) {
            val source = main(path).readText()
            assertFalse(path, source.contains("FeaturePageMode.RefreshRate"))
            assertFalse(path, source.contains("FeatureRefreshRate"))
            assertFalse(path, source.contains("tab_refresh_rate"))
        }
        for (path in listOf(
            "java/com/mi/onextbox/refresh",
            "aidl/com/mi/onextbox/refresh",
        )) {
            assertFalse(path, main(path).walkTopDown().any { it.isFile })
        }
        assertFalse(main("java/com/mi/onextbox/ui/screens/RefreshRatePage.kt").exists())
        assertFalse(main("java/com/mi/onextbox/tools/SurfaceFlingerModeTool.java").exists())
    }

    @Test fun bootBackupAndShrinkerNoLongerReferenceTheRemovedTool() {
        val manifest = main("AndroidManifest.xml").readText()
        assertFalse(manifest.contains(".refresh.RefreshRateBootJobService"))
        val receiver = main("java/com/mi/onextbox/boot/BootSyncReceiver.kt").readText()
        assertFalse(receiver.contains("RefreshRate"))
        assertTrue(receiver.contains("LspConfig.syncTogglesForBoot"))
        assertTrue(receiver.contains("TouchSamplingController.applyOnBootIfEnabled"))
        val backup = main("java/com/mi/onextbox/ui/common/ConfigBackup.kt").readText()
        assertFalse(backup.contains("RefreshRate"))
        assertTrue(backup.contains("TouchSamplingPreferences.PREFS_NAME"))
        assertFalse(File("proguard-rules.pro").readText().contains("SurfaceFlingerModeTool"))
    }

    @Test fun retiredStringsAreRemovedWithoutRemovingTheSeparateSettingsFeatures() {
        val locales = main("res").listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values") }
            .map { File(it, "strings.xml") }
            .filter { it.isFile }
        assertEquals(9, locales.size)
        for (file in locales) {
            val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val nodes = xml.getElementsByTagName("string")
            val names = (0 until nodes.length).map {
                nodes.item(it).attributes.getNamedItem("name").nodeValue
            }
            assertFalse(file.path, names.any { it == "tab_refresh_rate" || it.startsWith("refresh_rate_") })
            assertTrue(file.path, "feature_settings_unlock_refresh_rate_title" in names)
            assertTrue(file.path, "feature_settings_force_global_extreme_refresh_rate_title" in names)
        }
        assertTrue(main("java/com/mi/onextbox/lsp/SettingsRefreshRateHooker.kt").exists())
        assertTrue(main("java/com/mi/onextbox/lsp/SettingsGlobalExtremeRefreshRateHooker.kt").exists())
    }

    private fun main(path: String): File = File("src/main/$path")
}
