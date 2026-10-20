package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class SettingsAppVolumeGroupTest {
    private open class Row(val key: String) {
        var parent: Group? = null
        var isVisible = true
    }
    private class Group(key: String) : Row(key) {
        val children = mutableListOf<Row>()
        fun findPreference(key: CharSequence): Row? = children.firstNotNullOfOrNull { row ->
            row.takeIf { it.key == key.toString() } ?: (row as? Group)?.findPreference(key)
        }
        fun addPreference(row: Row): Boolean {
            if (row in children) return false
            children += row
            row.parent = this
            return true
        }
        fun removePreference(row: Row): Boolean = children.remove(row).also { if (it) row.parent = null }
    }
    private class ModeController(@JvmField val mDialogPreference: Row)

    @Test fun restoresOriginalDetachedGroupAndRetainsNativeAppRow() {
        val screen = Group("screen")
        val group = Group(SettingsAppVolumeGroup.KEY)
        val mode = Row("volume_mode")
        val app = Row("multi_app_volume")
        val other = Row("ringer_mode")
        group.addPreference(mode)
        group.addPreference(app)
        screen.addPreference(group)
        screen.addPreference(other)
        screen.removePreference(group) // C17 non-Realme onCreate removes the entire group.
        val controller = ModeController(mode)

        repeat(30) {
            SettingsAppVolumeGroup.reconcile(screen, controller, forced = true, nativeRealme = false)
            assertSame(group, screen.findPreference(SettingsAppVolumeGroup.KEY))
            assertSame(app, screen.findPreference("multi_app_volume"))
            assertFalse(mode.isVisible)
            assertTrue(group.isVisible)
            assertEquals(2, screen.children.size)
            SettingsAppVolumeGroup.reconcile(screen, controller, forced = false, nativeRealme = false)
            assertNull(screen.findPreference(SettingsAppVolumeGroup.KEY))
            assertSame(other, screen.findPreference("ringer_mode"))
        }
    }

    @Test fun disabledOnNativeRealmePreservesStockGroupAndModeRow() {
        val screen = Group("screen")
        val group = Group(SettingsAppVolumeGroup.KEY)
        val mode = Row("volume_mode")
        group.addPreference(mode)
        screen.addPreference(group)
        SettingsAppVolumeGroup.reconcile(screen, ModeController(mode), forced = false, nativeRealme = true)
        assertSame(group, screen.findPreference(SettingsAppVolumeGroup.KEY))
        assertTrue(mode.isVisible)
    }

    @Test fun unrelatedOrMissingGroupIsNeverInserted() {
        val screen = Group("screen")
        val unrelated = Group("other_category")
        val mode = Row("volume_mode")
        unrelated.addPreference(mode)
        SettingsAppVolumeGroup.reconcile(screen, ModeController(mode), forced = true, nativeRealme = false)
        assertTrue(screen.children.isEmpty())
        assertSame(unrelated, mode.parent)
    }
}
