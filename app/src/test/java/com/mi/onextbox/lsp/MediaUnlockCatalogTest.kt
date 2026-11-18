package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class MediaUnlockCatalogTest {
    @Test fun allSwitchesOffDelegateEveryGateToStock() {
        MediaUnlockCatalog.App.entries.forEach { app ->
            MediaUnlockCatalog.gatesByApp.getValue(app).keys.forEach { key ->
                assertNull(key, MediaUnlockCatalog.overrideValue(app, key) { false })
            }
        }
    }

    @Test fun eachSwitchOverridesOnlyItsOwnGatesAndApp() {
        MediaUnlockCatalog.options.forEach { selected ->
            MediaUnlockCatalog.App.entries.forEach { app ->
                MediaUnlockCatalog.gatesByApp.getValue(app).keys.forEach { key ->
                    val actual = MediaUnlockCatalog.overrideValue(app, key) { it == selected.key }
                    assertEquals("${selected.key} -> $app/$key",
                        if (app == selected.app) selected.overrides[key]
                        else if (app == MediaUnlockCatalog.App.Camera && selected.styles.isNotEmpty() && key == MediaUnlockCatalog.CAMERA_MASTER_GATE) true
                        else null, actual)
                }
            }
        }
    }

    @Test fun missingAndUnknownKeysCannotBeUnlocked() {
        MediaUnlockCatalog.App.entries.forEach { app ->
            listOf(null, "", "com.oplus.unknown.feature.support", "feature_unknown", "feature_is_support_ai_retouch").forEach { key ->
                assertNull(MediaUnlockCatalog.overrideValue(app, key) { true })
            }
        }
    }

    @Test fun reversingDisableFlagsDoesNotTreatFalseAsNoOverride() {
        val camera = MediaUnlockCatalog.App.Camera
        listOf("com.oplus.disable.professional.histogram", "com.oplus.disable.master.video.histogram",
            "com.oplus.video.hide.sound.effect").forEach { key ->
            assertEquals(false, MediaUnlockCatalog.overrideValue(camera, key) { true })
            assertNull(MediaUnlockCatalog.overrideValue(camera, key) { false })
        }
    }

    @Test fun cameraAndGalleryWatermarkAndFilterPacksAreIndividuallySelectable() {
        val packs = MediaUnlockCatalog.options.filter { it.group in setOf(MediaUnlockCatalog.Group.Filters, MediaUnlockCatalog.Group.Watermarks) }
        assertEquals(packs.size, packs.map { it.key }.distinct().size)
        assertTrue(packs.count { it.app == MediaUnlockCatalog.App.Camera && it.group == MediaUnlockCatalog.Group.Filters } >= 8)
        assertTrue(packs.count { it.app == MediaUnlockCatalog.App.Gallery && it.group == MediaUnlockCatalog.Group.Watermarks } >= 10)
        packs.forEach { selected ->
            selected.overrides.keys.forEach { key ->
                assertNotNull(MediaUnlockCatalog.overrideValue(selected.app, key) { it == selected.key })
            }
        }
    }

    @Test fun protectionsAndUnverifiedHardwareCapabilitiesCannotBeOverridden() {
        val camera = MediaUnlockCatalog.App.Camera
        listOf("com.oplus.feature.temperature.protection.support", "com.oplus.temperature.control.stop_video_recording",
            "com.oplus.feature.video.8k.support", "com.oplus.feature.video.4k.120fps.support",
            "com.oplus.feature.microscope.support", "com.oplus.200m.supported.picturesize").forEach { key ->
            assertNull(key, MediaUnlockCatalog.overrideValue(camera, key) { true })
        }
        val gallery = MediaUnlockCatalog.App.Gallery
        listOf("feature_general_ai_switch_quota", "feature_ai_retouch_switch_quota", "feature_is_ai_matting_plugin_support",
            "feature_is_support_safe_box_album", "feature_is_support_show_ai_logo", "feature_privacy_is_support_caption").forEach { key ->
            assertNull(key, MediaUnlockCatalog.overrideValue(gallery, key) { true })
        }
    }

    @Test fun catalogHasNoDuplicatePreferenceOrGateAndRestoresAfterDisable() {
        assertEquals(MediaUnlockCatalog.options.size, MediaUnlockCatalog.keys.size)
        assertEquals(MediaUnlockCatalog.options.size, MediaUnlockCatalog.options.map { it.title }.distinct().size)
        MediaUnlockCatalog.options.forEach { option ->
            assertTrue(option.key.startsWith(option.app.name.lowercase() + "_unlock_"))
            option.overrides.forEach { (key, desired) ->
                var active = true
                assertEquals(desired, MediaUnlockCatalog.overrideValue(option.app, key) { active })
                active = false
                assertNull(MediaUnlockCatalog.overrideValue(option.app, key) { active })
            }
        }
    }
}
