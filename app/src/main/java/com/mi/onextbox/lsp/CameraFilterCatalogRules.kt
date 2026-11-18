package com.mi.onextbox.lsp

/** Import only ordinary LUT rows already supplied by the installed native IPU catalog. */
internal object CameraFilterCatalogRules {
    const val SDK = "com.oplus.camera.filter.ipu.IpuFilterSdk"
    const val TYPE = "drawing_item_filter_type"
    const val NAME = "drawing_item_filter_name"
    val idsByKey = mapOf(
        "camera_unlock_filter_portra" to setOf("portra400_normal.bin"),
        "camera_unlock_filter_tx400" to setOf("blackandwhite_HC_normal.bin", "tx400_normal.bin"),
        "camera_unlock_filter_new" to setOf("fuji-proNegHi.bin", "fuji-nc.bin", "fuji_cc.bin", "800t.bin",
            "ccd_base_cold.bin", "ccd_base_warm.bin", "positive_normal.bin"),
        "camera_unlock_filter_clear_amber" to setOf("qing_tou.bin", "hu_po.bin"),
        "camera_unlock_filter_mono" to setOf("blackandwhite_HC_normal.bin"),
        "camera_unlock_filter_master" to setOf("Radiance.cube.rgb.bin", "Serenity.cube.rgb.bin", "Emerald.cube.rgb.bin"),
        "camera_unlock_filter_jiangwen" to setOf("drjw1994.bin", "drjw2010.bin", "jw_sun.CUBE.rgb.bin", "jw_surprise.CUBE.rgb.bin"),
        "camera_unlock_filter_jzk" to setOf("jzk-movie.cube.rgb.bin"),
        "camera_unlock_filter_tone" to setOf("tone-of-light.cube.rgb.bin"),
        "camera_unlock_filter_realme_classic" to setOf("DL_B_Inception.bin", "DL_C_R3_80.bin", "DL_A_MoneyBall.bin", "gt-crisp-fall.cube.rgb.bin"),
        "camera_unlock_filter_classic" to setOf("morandi.cube.rgb.bin", "mono.cube.rgb.bin", "vivid-warm.cube.rgb.bin", "vivid-lut.cube.rgb.bin"),
        "camera_unlock_filter_street" to setOf("Cold.cube.rgb.bin", "DL_D_G4.cube.rgb.bin", "DL_L_B6.cube.rgb.bin", "OPLUSR0604.cube.rgb.bin", "Martin.cube.rgb.bin", "Texture-70.cube.rgb.bin", "White.cube.rgb.bin", "cola.bin"),
        "camera_unlock_filter_night" to setOf("oplus-black-gold.cube.rgb.bin", "oplus-cyberpunk.cube.rgb.bin", "oplus-nightcity.cube.rgb.bin", "oplus-yellow-blue.cube.rgb.bin"),
        "camera_unlock_filter_gr" to setOf("gr.posi.rgba.bin", "gr.nega.rgba.bin", "gr.bw.rgba.bin", "gr.hi.bw.rgba.bin"),
        "camera_unlock_filter_festival" to setOf("vibe.rgba.bin", "festival.rgba.bin", "ceremony.rgba.bin"),
        "camera_unlock_filter_lively" to setOf("vibe.rgba.bin"),
        "camera_unlock_filter_realme_black_gold" to setOf("oplus-r-black-gold-1.1-RGB.CUBE.rgb.bin", "realme-black-gold-1.1-RGB.CUBE.rgb.bin"),
        "camera_unlock_filter_realme_cyberpunk" to setOf("oplus-r-cyberpunk-RGB.CUBE.rgb.bin", "realme-cyberpunk-RGB.CUBE.rgb.bin"),
        "camera_unlock_filter_realme_infrared" to setOf("oplus-r-infra-RGB.CUBE.rgb.bin", "realme-infra-RGB.CUBE.rgb.bin"),
    )

    fun merge(original: HashMap<*, *>, enabled: (String) -> Boolean): HashMap<*, *> {
        val wanted = idsByKey.filterKeys(enabled).values.flatten().toSet()
        if (wanted.isEmpty()) return original
        val sources = linkedMapOf<String, Map<*, *>>()
        original.entries.sortedBy { it.key as? String ?: "" }.forEach { (_, rows) ->
            (rows as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach { row ->
                val id = row[TYPE] as? String
                if (id in wanted && (row[NAME] as? Number)?.toInt()?.let { it > 0 } == true)
                    sources.putIfAbsent(id!!, row)
            }
        }
        var changed: HashMap<Any?, Any?>? = null
        original.forEach { (mode, value) ->
            if (mode !is String || mode.substringBefore('-') != "photo" || mode.substringAfter('-', "back").substringBefore('-') == "front") return@forEach
            val rows = value as? List<*> ?: return@forEach
            val existing = rows.filterIsInstance<Map<*, *>>().map { it[TYPE] }.toSet()
            val extra = sources.filterKeys { it !in existing }.values
            if (extra.isNotEmpty()) {
                if (changed == null) changed = HashMap(original)
                changed!![mode] = ArrayList<Any?>(rows).apply { extra.forEach { add(HashMap(it)) } }
            }
        }
        return changed ?: original
    }
}
