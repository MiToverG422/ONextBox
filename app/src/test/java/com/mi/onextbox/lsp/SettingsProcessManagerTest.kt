package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SettingsProcessManagerTest {
    @Test fun disabledRetainsEveryNativeAvailabilityResult() {
        for (status in 0..5) for (appPage in listOf(true, false)) {
            assertEquals(status, SettingsProcessManager.availability(status, false, appPage))
        }
    }
    @Test fun enabledShowsOnlyApplicationEntryWithoutDuplicates() {
        for (status in 0..5) {
            assertEquals(0, SettingsProcessManager.availability(status, true, true))
            assertEquals(3, SettingsProcessManager.availability(status, true, false))
        }
    }
    @Test fun toggleHasNoDescriptionAndEntryDoesNotReplaceNativePage() {
        val source = File("src/main/java/com/mi/onextbox/lsp/SettingsProcessManager.kt").readText()
        assertTrue(source.contains("getBoolean(KEY, false)"))
        assertTrue(source.contains("getAvailabilityStatus"))
        assertFalse(source.contains("isRLMDevice"))
        assertFalse(source.contains("startActivity"))
        val ui = File("src/main/java/com/mi/onextbox/ui/screens/SettingsRealmePage.kt").readText()
        // Only the two standalone-app features have dependency descriptions.
        assertNull(SettingsRealmeDependencies.packageName(SettingsRealmeFeature.ProcessManager))
        assertTrue(ui.contains("else -> \"\""))
        assertFalse(ui.contains("settings_show_process_manager_summary"))
        assertTrue(ui.contains("FeatureAnimatedGroup"))
    }
}
