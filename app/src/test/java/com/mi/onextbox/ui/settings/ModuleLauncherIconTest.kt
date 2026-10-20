package com.mi.onextbox.ui.settings

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class ModuleLauncherIconTest {
    private val app = listOf(File("."), File("app")).first { File(it, "src/main/java").isDirectory }

    @Test fun onlyTheAliasOwnsTheLauncherFilterAndMainRemainsExported() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(app, "src/main/AndroidManifest.xml"))
        val activities = document.getElementsByTagName("activity")
        val main = (0 until activities.length).map { activities.item(it) }
            .single { it.attributes.getNamedItem("android:name").nodeValue == ".MainActivity" }
        assertEquals("true", main.attributes.getNamedItem("android:exported").nodeValue)
        val alias = document.getElementsByTagName("activity-alias").item(0)
        assertEquals(".LauncherAlias", alias.attributes.getNamedItem("android:name").nodeValue)
        assertEquals(".MainActivity", alias.attributes.getNamedItem("android:targetActivity").nodeValue)
        val categories = document.getElementsByTagName("category")
        val launcherFilters = (0 until categories.length).map { categories.item(it) }
            .filter { it.attributes.getNamedItem("android:name").nodeValue == "android.intent.category.LAUNCHER" }
            .filter { it.parentNode.parentNode.nodeName != "queries" }
        assertEquals(1, launcherFilters.size)
        assertEquals(alias, launcherFilters.single().parentNode.parentNode)
    }

    @Test fun toggleUsesPackageManagerStateWithoutDisablingMainOrKillingApp() {
        val source = File(app, "src/main/java/com/mi/onextbox/ui/settings/ModuleLauncherIcon.kt").readText()
        assertTrue(source.contains("getComponentEnabledSetting"))
        assertTrue(source.contains("PackageManager.DONT_KILL_APP"))
        assertTrue(source.contains("PackageManager.COMPONENT_ENABLED_STATE_DEFAULT"))
        assertFalse(source.contains(".MainActivity"))
        val entry = File(app, "src/main/java/com/mi/onextbox/lsp/SettingsHomeEntryHooker.kt").readText()
        assertTrue(entry.contains("ComponentName(MODULE, \"\$MODULE.MainActivity\")"))
    }
}
