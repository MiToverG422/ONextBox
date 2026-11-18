package com.mi.onextbox.lsp

import java.io.File

internal object CameraNativeFilterEntries {
    val names = mapOf(
        "800t.bin" to "camera_filter_oplus_neon",
        "Cold.cube.rgb.bin" to "street_filter_refresh",
        "DL_A_MoneyBall.bin" to "camera_filter_oplus_basic_a_moneyball",
        "DL_B_Inception.bin" to "camera_filter_oplus_basic_b_inception",
        "DL_C_R3_80.bin" to "camera_filter_oplus_basic_c_r",
        "DL_D_G4.cube.rgb.bin" to "street_filter_xi_ju",
        "DL_L_B6.cube.rgb.bin" to "street_filter_guang_ying",
        "Emerald.cube.rgb.bin" to "camera_filter_oplus_master_emerald_new",
        "Martin.cube.rgb.bin" to "street_filter_90s_pop",
        "OPLUSR0604.cube.rgb.bin" to "street_filter_jie_pai",
        "Radiance.cube.rgb.bin" to "camera_filter_oplus_master_radiance_new",
        "Serenity.cube.rgb.bin" to "camera_filter_oplus_master_serenity",
        "Texture-70.cube.rgb.bin" to "street_filter_playback",
        "White.cube.rgb.bin" to "street_filter_silent",
        "blackandwhite_HC_normal.bin" to "camera_filter_fanchaheibai",
        "ccd_base_cold.bin" to "camera_filter_oplus_flash_cold",
        "ccd_base_warm.bin" to "camera_filter_oplus_flash_warm",
        "ceremony.rgba.bin" to "camera_filter_ceremony",
        "cola.bin" to "street_filter_80s_Cola",
        "festival.rgba.bin" to "camera_filter_festival",
        "fuji-nc.bin" to "camera_filter_oplus_fu_gu_new",
        "fuji-proNegHi.bin" to "camera_filter_oplus_tong_tou",
        "fuji_cc.bin" to "camera_filter_oplus_qing_xin",
        "gr.bw.rgba.bin" to "camera_gr_filter_bw",
        "gr.hi.bw.rgba.bin" to "camera_gr_filter_hi_bw",
        "gr.nega.rgba.bin" to "camera_gr_filter_nega",
        "gr.posi.rgba.bin" to "camera_gr_filter_posi",
        "gt-crisp-fall.cube.rgb.bin" to "camera_filter_gt_crisp_fall",
        "hu_po.bin" to "camera_filter_oplus_hu_po",
        "jzk-movie.cube.rgb.bin" to "camera_filter_type_director_joint_filter_JZK",
        "mono.cube.rgb.bin" to "camera_video_filter_danse",
        "morandi.cube.rgb.bin" to "camera_filter_oplus_style_morandi",
        "oplus-black-gold.cube.rgb.bin" to "camera_filter_oplus_night_black_gold",
        "oplus-cyberpunk.cube.rgb.bin" to "camera_filter_oplus_night_cyberpunk",
        "oplus-nightcity.cube.rgb.bin" to "night_filter_night_city",
        "oplus-yellow-blue.cube.rgb.bin" to "camera_filter_oplus_night_teal_orange",
        "portra400_normal.bin" to "camera_filter_oplus_he_guang",
        "positive_normal.bin" to "camera_filter_oplus_nong_yu",
        "qing_tou.bin" to "camera_filter_oplus_qing_tou",
        "tone-of-light.cube.rgb.bin" to "tol_filter_type",
        "vibe.rgba.bin" to "camera_filter_vibe",
        "vivid-lut.cube.rgb.bin" to "camera_filter_huidiao",
        "vivid-warm.cube.rgb.bin" to "camera_filter_oplus_basic_vividwarm",
    )

    fun candidates(enabled: (String) -> Boolean): Map<String, String> {
        val wanted = CameraFilterCatalogRules.idsByKey.filterKeys(enabled).values.flatten().toSet()
        return names.filterKeys(wanted::contains)
    }

    fun hasMaterial(id: String): Boolean = listOf("/odm/etc/camera/meishe_lut", "/odm/etc/camera/filters_lut").any {
        val file = File(it, id)
        file.isFile && file.canRead() && file.length() > 0L
    }

    fun eligible(candidates: Map<String, String>, captureIds: Set<String>, material: (String) -> Boolean, nameExists: (String) -> Boolean): Map<String, String> =
        candidates.filter { (id, name) -> id in captureIds && material(id) && nameExists(name) }
}
