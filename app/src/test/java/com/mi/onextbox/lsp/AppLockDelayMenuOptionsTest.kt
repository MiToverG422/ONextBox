package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AppLockDelayMenuOptionsTest {
    private val labels = arrayOf<CharSequence>("After leaving app", "After locking screen")
    @Test fun preservesOriginalModesAndAppendsOnlyOneAndFiveMinuteChoices() {
        val options = AppLockDelayMenuOptions.build(labels, labels, true) { "$it ms" }
        assertEquals(listOf("After leaving app", "After locking screen", "60000 ms", "300000 ms"), options.entries.toList())
        assertArrayEquals(labels, options.values.take(2).toTypedArray())
        assertEquals(listOf(60_000L, 300_000L), options.values.drop(2).map(AppLockDelaySettings::fromToken))
    }
    @Test fun disablingAndRepeatedRecreationNeverDuplicatesOrLosesNativeOptions() {
        repeat(30) {
            assertEquals(4, AppLockDelayMenuOptions.build(labels, labels, true) { "minute" }.entries.size)
            val restored = AppLockDelayMenuOptions.build(labels, labels, false) { error("Disabled must not resolve module resources") }
            assertArrayEquals(labels, restored.entries)
            assertArrayEquals(labels, restored.values)
        }
        assertEquals(2, labels.size)
    }
    @Test fun uninstallFallbackPersistsOnlyAnExistingNativeMode() {
        for (duration in AppLockDelaySettings.durations) {
            assertEquals(labels[0], AppLockDelayMenuOptions.persist(AppLockDelaySettings.token(duration), labels[0].toString()))
        }
        assertEquals(labels[1], AppLockDelayMenuOptions.persist(labels[1].toString(), labels[0].toString()))
    }
    @Test fun unknownDurationsAndTokensAreNotAccepted() {
        for (value in listOf(null, "60000", "onextbox_app_lock_after_0", "onextbox_app_lock_after_999999999")) {
            assertNull(AppLockDelaySettings.fromToken(value))
        }
        assertEquals(0L, AppLockDelaySettings.valid(Long.MAX_VALUE))
    }
    @Test fun scopeUiSearchAndAllLocalesIncludeTheFeature() {
        fun source(path: String) = File("src/main/java/com/mi/onextbox/$path").readText()
        assertTrue(File("src/main/resources/META-INF/xposed/scope.list").readLines().contains(AppLockDelaySettings.PACKAGE))
        assertTrue(source("ui/screens/SecurityPermissionFeaturesPage.kt").contains("AppLockDelaySettingsGroup()"))
        for (file in listOf("FeatureSearchIndex", "EnabledFeatureCatalog", "EnabledFeatureActions")) {
            assertTrue(source("ui/screens/$file.kt").contains("R.string.app_lock_delay_title"))
        }
        assertTrue(source("lsp/AppLockDelaySettings.kt").contains("getBoolean(ENABLED, false)"))
        for (locale in listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant", "values-ja", "values-ja-rCN", "values-ko", "values-ru", "values-vi")) {
            val strings = File("src/main/res/$locale/strings.xml").readText()
            for (key in listOf("app_lock_group", "app_lock_delay_title", "app_lock_delay_summary", "app_lock_delay_after_minutes", "app_lock_delay_save_failed")) {
                assertEquals("$locale: $key", 1, Regex("name=\"$key\"").findAll(strings).count())
            }
        }
    }
}
