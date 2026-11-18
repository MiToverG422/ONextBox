package com.mi.onextbox.lsp

internal object GalleryWatermarkCatalogRules {
    const val OWNER = "com.oplus.gallery.framework.abilities.watermark.masterstyle.WatermarkMasterStyle\$Companion"
    const val ASSET_ROOT = "watermark_master_styles/"
    const val VIEW_MODEL = "com.oplus.gallery.photoeditor.editingvvm.watermarkmaster.WatermarkMasterVM"
    val options = MediaUnlockCatalog.options.filter { it.styles.isNotEmpty() }
    val keys = options.map { it.key }
    val methods = mapOf("getAllBuiltInStyles" to 4, "getBuiltInPersonalStyles" to 6,
        "getBuiltInBrandStyles" to 4, "getBuiltInHasselStyles" to 2, "getBuiltInLumoStyles" to 1,
        "getBuiltInFilmStyles" to 1)

    fun selected(method: String, available: Set<String>, enabled: (String) -> Boolean): List<String> =
        options.asSequence().filter { enabled(it.key) }.flatMap { it.styles.asSequence() }
            .filter { it in available }.filter { id -> when (method) {
                "getAllBuiltInStyles" -> true
                "getBuiltInPersonalStyles" -> id.startsWith("personalize_") || id.startsWith("realme_brand_imprint_")
                "getBuiltInFilmStyles" -> id.startsWith("personalize_film_")
                "getBuiltInBrandStyles" -> id.startsWith("personalize_brand_")
                "getBuiltInHasselStyles" -> id.startsWith("hassel_style_")
                "getBuiltInLumoStyles" -> id.startsWith("lumo_style_")
                else -> false
            } }.distinct().toList()

    /** Never mutate a cached host list; preserve stock order and host Pair instances. */
    fun merge(original: List<*>, selected: List<String>, id: (Any) -> String?, create: (String) -> Any): List<*> {
        if (selected.isEmpty()) return original
        val known = original.filterNotNull().mapNotNull(id).toMutableSet()
        val extra = selected.filter(known::add)
        if (extra.isEmpty()) return original
        return ArrayList<Any?>(original).apply { extra.forEach { add(create(it)) } }
    }

    /** Factories append IDs, but native chooser thumbnails are separate fixed-size arrays. */
    data class VisibleChoices(val ids: List<String>, val previews: IntArray)

    fun completeChoices(ids: List<String>, original: IntArray, preview: (String) -> Int): VisibleChoices {
        if (ids.size <= original.size) return VisibleChoices(ids, original)
        val displayed = ArrayList(ids.take(original.size))
        val extra = ArrayList<Int>()
        ids.drop(original.size).forEach { id ->
            val resource = preview(id)
            // Never pass an added ID without a drawable to the native adapter.
            // Missing material removes just that extra entry, retaining stock IDs.
            if (resource != 0) {
                displayed.add(id)
                extra.add(resource)
            }
        }
        return VisibleChoices(displayed, if (extra.isEmpty()) original else original + extra.toIntArray())
    }

    fun previewName(id: String, camera: Boolean, landscape: Boolean): String? {
        val suffix = if (landscape && !camera) "_land" else ""
        return when {
            id.startsWith("personalize_masterSign_") ->
                "sketch_personalize_${if (camera) "camera_" else ""}master_sign_${id.substringAfterLast('_')}$suffix"
            id.startsWith("personalize_retroCamera_") ->
                "sketch_personalize_${if (camera) "camera_" else ""}retro_camera_${id.substringAfterLast('_')}$suffix"
            id.startsWith("personalize_brand_") -> {
                // Native brand style 2 intentionally shares preview resource 3.
                val index = id.substringAfterLast('_').let { if (it == "2") "3" else it }
                "sketch_personalize_${if (camera) "camera_" else ""}brand_$index$suffix"
            }
            id.startsWith("personalize_film_realme_") ->
                "sketch_personalize_${if (camera) "camera_" else ""}film_realme_${id.substringAfterLast('_')}$suffix"
            id.startsWith("realme_brand_imprint_inspirationPhoto_") ->
                "sketch_${if (camera) "camera_" else ""}realme_brand_inspation_photo_${id.substringAfterLast('_')}$suffix"
            id == "hassel_style_xpan_1" -> "sketch_hassel_style_xpan_1"
            id == "hassel_style_master_mode_1" -> if (camera) "sketch_camera_legend_skeatro_style_1"
                else "sketch_hassel_no_legend_maestro_style_1$suffix"
            id == "lumo_style_master_mode_1" -> if (camera) "sketch_camera_lumo_legend_skeatro_style_1"
                else "sketch_lumo_no_legend_maestro_style_1$suffix"
            else -> null
        }
    }
}
