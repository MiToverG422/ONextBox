package com.mi.onextbox.lsp

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class SettingsHomeEntryAppearanceTest {
    private fun mainFile(path: String): File = listOf(File("src/main/$path"), File("app/src/main/$path")).first { it.isFile }

    @Test fun entryUsesTheSameVectorAsUpdateNotificationsAndNotTheLauncherIcon() {
        val appearance = mainFile("java/com/mi/onextbox/lsp/SettingsHomeEntryAppearance.kt").readText()
        val hook = mainFile("java/com/mi/onextbox/lsp/SettingsHomeEntryHooker.kt").readText()
        val notifications = mainFile("java/com/mi/onextbox/ui/settings/UpdateNotificationScheduler.kt").readText()
        assertTrue(appearance.contains("getIdentifier(\"ic_update_notification\", \"drawable\", MODULE)"))
        assertTrue(notifications.contains("setSmallIcon(R.drawable.ic_update_notification)"))
        assertTrue(hook.contains("SettingsHomeEntryAppearance.createRow"))
        assertFalse(hook.contains("getApplicationIcon"))
        assertTrue(appearance.contains(".createConfigurationContext(context.resources.configuration)"))
        assertTrue(appearance.contains("requireNotNull(module.getDrawable(id)).mutate()"))
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val vector = factory.newDocumentBuilder().parse(mainFile("res/drawable/ic_update_notification.xml")).documentElement
        assertEquals("vector", vector.nodeName)
        val android = "http://schemas.android.com/apk/res/android"
        assertEquals("24dp", vector.getAttributeNS(android, "width"))
        assertEquals("24dp", vector.getAttributeNS(android, "height"))
    }

    @Test fun nativeWifiAttributesAndThemeAreUsedWithoutHardCodedColors() {
        val source = mainFile("java/com/mi/onextbox/lsp/SettingsHomeEntryAppearance.kt").readText()
        assertTrue(source.contains("\"top_level_settings_oplus\", \"xml\", SETTINGS"))
        assertTrue(source.contains("parser.getAttributeValue(ANDROID, \"key\") != \"wifi_settings\""))
        assertTrue(source.contains("ModernReflect.newInstance(rowClass, context, parser)"))
        assertTrue(source.contains("getWrapperOrPrimaryContext"))
        assertTrue(source.contains("\"tintType\", \"attr\", SETTINGS"))
        assertTrue(source.contains("\"couiColorContainerTheme\", \"attr\", SETTINGS"))
        assertTrue(source.contains("icon::setTintList"))
        listOf("Color.parseColor", "Color.rgb", "Color.WHITE", "Color.BLACK", "R.drawable.", "R.mipmap.").forEach {
            assertFalse(it, source.contains(it))
        }
    }

    @Test fun appIconKeepsItsColorsButUsesTheSameSmallIconBounds() {
        val source = mainFile("java/com/mi/onextbox/lsp/SettingsHomeEntryAppearance.kt").readText()
        assertTrue(source.contains("SettingsEntryIconStyle.App ->"))
        assertTrue(source.contains("ModernReflect.callMethod(row, \"setShowColorTintIcon\", 0)"))
        assertTrue(source.contains("getApplicationIcon(MODULE).mutate()"))
        assertTrue(source.contains("setLayerSize(0, template.intrinsicWidth, template.intrinsicHeight)"))
        assertTrue(source.contains("SettingsEntryIconStyle.System -> template.also"))
    }
}
