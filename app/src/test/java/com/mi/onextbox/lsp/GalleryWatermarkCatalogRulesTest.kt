package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class GalleryWatermarkCatalogRulesTest {
    @Test fun brandPacksUseOnlyTheirOwnAvailableTemplatesInMatchingNativeCategories() {
        val option = GalleryWatermarkCatalogRules.options.single { it.key == "gallery_unlock_watermark_realme_film" }
        val available = option.styles.take(1).toSet()
        assertEquals(available.toList(), GalleryWatermarkCatalogRules.selected("getBuiltInPersonalStyles", available) { it == option.key })
        assertEquals(available.toList(), GalleryWatermarkCatalogRules.selected("getBuiltInFilmStyles", available) { it == option.key })
        assertTrue(GalleryWatermarkCatalogRules.selected("getBuiltInHasselStyles", available) { it == option.key }.isEmpty())
        assertTrue(GalleryWatermarkCatalogRules.selected("getAllBuiltInStyles", available) { false }.isEmpty())
        assertTrue(GalleryWatermarkCatalogRules.selected("getAllBuiltInStyles", emptySet()) { true }.isEmpty())
    }

    @Test fun separatelyBuiltVisibleListsKeepStockThumbnailsAndPairEachExtraIdWithItsPreview() {
        val ids = listOf("personalize_film_1", "personalize_film_2", "personalize_film_realme_1", "personalize_film_realme_2")
        val stock = intArrayOf(11, 12)
        val result = GalleryWatermarkCatalogRules.completeChoices(ids, stock) {
            when (it) { "personalize_film_realme_1" -> 21; "personalize_film_realme_2" -> 22; else -> 0 }
        }
        assertEquals(ids, result.ids)
        assertArrayEquals(intArrayOf(11, 12, 21, 22), result.previews)
        assertArrayEquals(intArrayOf(11, 12), stock)
        assertSame(stock, GalleryWatermarkCatalogRules.completeChoices(ids.take(2), stock) { error("stock must not be rebuilt") }.previews)
        val missing = GalleryWatermarkCatalogRules.completeChoices(ids, stock) { 0 }
        assertEquals(ids.take(2), missing.ids)
        assertSame(stock, missing.previews)
        assertEquals("sketch_personalize_camera_film_realme_1",
            GalleryWatermarkCatalogRules.previewName(ids[2], camera = true, landscape = false))
        assertEquals("sketch_personalize_film_realme_1_land",
            GalleryWatermarkCatalogRules.previewName(ids[2], camera = false, landscape = true))
        assertEquals("sketch_personalize_brand_3",
            GalleryWatermarkCatalogRules.previewName("personalize_brand_2", camera = false, landscape = false))
        assertEquals("sketch_personalize_camera_retro_camera_1",
            GalleryWatermarkCatalogRules.previewName("personalize_retroCamera_1", camera = true, landscape = false))
    }

    @Test fun nativeListsArePreservedWhenDisabledAndExtendedWithoutDuplicatesOrMutation() {
        val first = "stock" to "native/path.json"
        val original = listOf(first)
        fun id(value: Any) = (value as Pair<*, *>).first as? String
        fun create(id: String): Any = id to "$id.json"
        assertSame(original, GalleryWatermarkCatalogRules.merge(original, emptyList(), ::id, ::create))
        assertSame(original, GalleryWatermarkCatalogRules.merge(original, listOf("stock"), ::id, ::create))
        val result = GalleryWatermarkCatalogRules.merge(original, listOf("stock", "extra", "extra"), ::id, ::create)
        assertEquals(2, result.size)
        assertSame(first, result[0])
        assertEquals(listOf(first), original)
    }

    @Test fun templatePacksOpenTheSharedEntryWithoutEnablingOtherTypesOrChangingDeviceIdentity() {
        val chosen = GalleryWatermarkCatalogRules.options.single { it.key == "gallery_unlock_watermark_realme_film" }
        val enabled: (String) -> Boolean = { it == chosen.key }
        assertEquals(true, MediaUnlockCatalog.overrideValue(MediaUnlockCatalog.App.Gallery, "feature_is_support_photo_editor_watermark", enabled))
        assertEquals(true, MediaUnlockCatalog.overrideValue(MediaUnlockCatalog.App.Camera, MediaUnlockCatalog.CAMERA_MASTER_GATE, enabled))
        assertNull(MediaUnlockCatalog.overrideValue(MediaUnlockCatalog.App.Gallery, "feature_is_support_lumo_watermark", enabled))
        listOf("is_realme_brand", "is_oneplus_brand", "is_oppo_brand").forEach {
            assertNull(MediaUnlockCatalog.overrideValue(MediaUnlockCatalog.App.Gallery, it, enabled))
        }
    }
}
