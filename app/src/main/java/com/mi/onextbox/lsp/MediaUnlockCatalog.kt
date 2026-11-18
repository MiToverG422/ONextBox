package com.mi.onextbox.lsp

import com.mi.onextbox.R

/** Exact client eligibility gates verified against camera 7.006.303 and gallery 17.24.79.
 * No stream specifications, HAL, temperature, conflict, security, plugin or service-quota checks are overridden.
 */
internal object MediaUnlockCatalog {
    enum class App(val packageName: String) {
        Camera("com.oplus.camera"), Gallery("com.coloros.gallery3d"),
    }
    enum class Group(val title: Int) {
        Modes(R.string.media_unlock_group_modes),
        Controls(R.string.media_unlock_group_controls),
        Filters(R.string.media_unlock_group_filters),
        Watermarks(R.string.media_unlock_group_watermarks),
        Editing(R.string.media_unlock_group_editing),
        Live(R.string.media_unlock_group_live),
        AI(R.string.media_unlock_group_ai),
    }
    data class Option(val key: String, val app: App, val group: Group, val title: Int,
        val overrides: Map<String, Boolean>, val styles: List<String> = emptyList())
    data class Gate(val optionKeys: List<String>, val desired: Boolean)

    private val declaredOptions = listOf(
        Option("camera_unlock_xpan", App.Camera, Group.Modes, R.string.camera_unlock_xpan, mapOf("com.oplus.feature.xpan.mode.support" to true)),
        Option("camera_unlock_gr", App.Camera, Group.Modes, R.string.camera_unlock_gr, mapOf("com.oplus.gr.mode.support" to true)),
        Option("camera_unlock_vibe", App.Camera, Group.Modes, R.string.camera_unlock_vibe, mapOf("com.oplus.vibe.mode.support" to true)),
        Option("camera_unlock_street", App.Camera, Group.Modes, R.string.camera_unlock_street, mapOf("com.oplus.feature.street.mode.support" to true)),
        Option("camera_unlock_professional", App.Camera, Group.Modes, R.string.camera_unlock_professional, mapOf("com.oplus.feature.professional.mode.support" to true)),
        Option("camera_unlock_movie", App.Camera, Group.Modes, R.string.camera_unlock_movie, mapOf("com.oplus.feature.movie.mode.support" to true)),
        Option("camera_unlock_tilt_shift", App.Camera, Group.Modes, R.string.camera_unlock_tilt_shift, mapOf("com.oplus.feature.tilt.shift.photo.support" to true)),
        Option("camera_unlock_street_lock", App.Camera, Group.Controls, R.string.camera_unlock_street_lock, mapOf("com.oplus.feature.street.lock.focus.support" to true)),
        Option("camera_unlock_parameter_memory", App.Camera, Group.Controls, R.string.camera_unlock_parameter_memory, mapOf("com.oplus.feature.professional.parameter.saving.support" to true)),
        Option("camera_unlock_hasselblad_pro", App.Camera, Group.Controls, R.string.camera_unlock_hasselblad_pro, mapOf("com.oplus.professional.use.hasselblad.style.support" to true)),
        Option("camera_unlock_hasselblad_style", App.Camera, Group.Controls, R.string.camera_unlock_hasselblad_style, mapOf("com.oplus.use.hasselblad.style.support" to true)),
        Option("camera_unlock_xpan_legacy", App.Camera, Group.Controls, R.string.camera_unlock_xpan_legacy, mapOf("com.oplus.xpan.legacy.ui.style" to true)),
        Option("camera_unlock_ev", App.Camera, Group.Controls, R.string.camera_unlock_ev, mapOf("com.oplus.global.ev.support" to true)),
        Option("camera_unlock_portrait_ev", App.Camera, Group.Controls, R.string.camera_unlock_portrait_ev, mapOf("com.oplus.portrait.global.ev.support" to true)),
        Option("camera_unlock_video_ev", App.Camera, Group.Controls, R.string.camera_unlock_video_ev, mapOf("com.oplus.video.global.ev.support" to true)),
        Option("camera_unlock_street_ev", App.Camera, Group.Controls, R.string.camera_unlock_street_ev, mapOf("com.oplus.street.global.ev.support" to true)),
        Option("camera_unlock_vibe_ev", App.Camera, Group.Controls, R.string.camera_unlock_vibe_ev, mapOf("com.oplus.vibe.global.ev.support" to true)),
        Option("camera_unlock_video_sound", App.Camera, Group.Controls, R.string.camera_unlock_video_sound, mapOf("com.oplus.video.hide.sound.effect" to false)),
        Option("camera_unlock_pro_histogram", App.Camera, Group.Controls, R.string.camera_unlock_pro_histogram, mapOf("com.oplus.disable.professional.histogram" to false)),
        Option("camera_unlock_video_histogram", App.Camera, Group.Controls, R.string.camera_unlock_video_histogram, mapOf("com.oplus.disable.master.video.histogram" to false)),
        Option("camera_unlock_focus_peaking", App.Camera, Group.Controls, R.string.camera_unlock_focus_peaking, mapOf("com.oplus.feature.master.video.focus.peaking.histogram.oplus.r.support" to true)),
        Option("camera_unlock_aspect_ratio", App.Camera, Group.Controls, R.string.camera_unlock_aspect_ratio, mapOf("com.oplus.change.picture.ratio.support" to true)),
        Option("camera_unlock_filter_portra", App.Camera, Group.Filters, R.string.camera_unlock_filter_portra, mapOf("com.oplus.feature.portra.filter.support" to true)),
        Option("camera_unlock_filter_tx400", App.Camera, Group.Filters, R.string.camera_unlock_filter_tx400, mapOf("com.oplus.feature.tx400.filter.support" to true)),
        Option("camera_unlock_filter_new", App.Camera, Group.Filters, R.string.camera_unlock_filter_new, mapOf("com.oplus.feature.os15.new.filter.support" to true)),
        Option("camera_unlock_filter_clear_amber", App.Camera, Group.Filters, R.string.camera_unlock_filter_clear_amber, mapOf("com.oplus.feature.qingtou.hupo.filter.support" to true)),
        Option("camera_unlock_filter_soft_light", App.Camera, Group.Filters, R.string.camera_unlock_filter_soft_light, mapOf("com.oplus.feature.soft.light.filter.support" to true)),
        Option("camera_unlock_filter_mono", App.Camera, Group.Filters, R.string.camera_unlock_filter_mono, mapOf("com.oplus.mono.filter.support" to true)),
        Option("camera_unlock_filter_portrait_bw", App.Camera, Group.Filters, R.string.camera_unlock_filter_portrait_bw, mapOf("com.oplus.mono.portrait.filter.heibai" to true)),
        Option("camera_unlock_filter_grand_tour", App.Camera, Group.Filters, R.string.camera_unlock_filter_grand_tour, mapOf("com.oplus.support.grand.tour.filter" to true)),
        Option("camera_unlock_filter_street_tour", App.Camera, Group.Filters, R.string.camera_unlock_filter_street_tour, mapOf("com.oplus.street.grand.tour.filter.type.support" to true)),
        Option("camera_unlock_color_palette", App.Camera, Group.Filters, R.string.camera_unlock_color_palette, mapOf("com.oplus.feature.color.palette.support" to true)),
        Option("camera_unlock_filter_master", App.Camera, Group.Filters, R.string.camera_unlock_filter_master, emptyMap()),
        Option("camera_unlock_filter_jiangwen", App.Camera, Group.Filters, R.string.camera_unlock_filter_jiangwen, mapOf("com.oplus.director.filter.support" to true, "com.oplus.director.filter.rus" to true, "com.oplus.director.filter.upgrade.support" to true)),
        Option("camera_unlock_filter_jzk", App.Camera, Group.Filters, R.string.camera_unlock_filter_jzk, mapOf("com.oplus.support.jzk.movie.filter" to true)),
        Option("camera_unlock_filter_tone", App.Camera, Group.Filters, R.string.camera_unlock_filter_tone, mapOf("com.oplus.tol.style.filter.support" to true)),
        Option("camera_unlock_filter_800t", App.Camera, Group.Filters, R.string.camera_unlock_filter_800t, mapOf("com.oplus.feature.800t.filter.support" to true)),
        Option("camera_unlock_filter_flash", App.Camera, Group.Filters, R.string.camera_unlock_filter_flash, mapOf("com.oplus.feature.flash.filter.support" to true, "com.oplus.feature.flash.warm.filter.support" to true, "com.oplus.feature.flash.filter.portrait.support" to true)),
        Option("camera_unlock_filter_classic", App.Camera, Group.Filters, R.string.camera_unlock_filter_classic, mapOf("com.oplus.feature.fewer.users.filter.support" to true)),
        Option("camera_unlock_filter_realme_classic", App.Camera, Group.Filters, R.string.camera_unlock_filter_realme_classic, emptyMap()),
        Option("camera_unlock_filter_street", App.Camera, Group.Filters, R.string.camera_unlock_filter_street, emptyMap()),
        Option("camera_unlock_filter_night", App.Camera, Group.Filters, R.string.camera_unlock_filter_night, emptyMap()),
        Option("camera_unlock_filter_gr", App.Camera, Group.Filters, R.string.camera_unlock_filter_gr, emptyMap()),
        Option("camera_unlock_filter_festival", App.Camera, Group.Filters, R.string.camera_unlock_filter_festival, mapOf("com.oplus.filter.festival.support" to true)),
        Option("camera_unlock_filter_lively", App.Camera, Group.Filters, R.string.camera_unlock_filter_lively, mapOf("com.oplus.filter.lively.support" to true)),
        Option("camera_unlock_lighting", App.Camera, Group.Filters, R.string.camera_unlock_lighting, mapOf("com.ocs.camera.ipu.lighting.photo.mode.support" to true)),
        Option("camera_unlock_ai_scene", App.Camera, Group.Controls, R.string.camera_unlock_ai_scene, mapOf("com.oplus.ai.scene.preset.support" to true)),
        Option("camera_unlock_ai_composition_inspiration", App.Camera, Group.Controls, R.string.camera_unlock_ai_composition_inspiration,
            mapOf("com.oplus.feature.aicomposition.inspiration.support" to true)),
        Option("camera_unlock_ai_composition_scene", App.Camera, Group.Controls, R.string.camera_unlock_ai_composition_scene,
            mapOf("com.oplus.feature.aicomposition.realscene.support" to true)),
        Option("camera_unlock_scale_focus", App.Camera, Group.Controls, R.string.camera_unlock_scale_focus, mapOf("com.oplus.camera.feature.scale.focus" to true)),
        Option("camera_unlock_focal_length", App.Camera, Group.Controls, R.string.camera_unlock_focal_length, mapOf("com.oplus.switch.lens.focal.length.support" to true)),
        Option("camera_unlock_video_lock_lens", App.Camera, Group.Controls, R.string.camera_unlock_video_lock_lens, mapOf("com.oplus.video.lock.lens.support" to true)),
        Option("camera_unlock_video_lock_wb", App.Camera, Group.Controls, R.string.camera_unlock_video_lock_wb, mapOf("com.oplus.video.lock.wb.support" to true)),
        Option("camera_unlock_video_auto_fps", App.Camera, Group.Controls, R.string.camera_unlock_video_auto_fps, mapOf("com.oplus.video.auto.fps.setting.support" to true)),
        Option("camera_unlock_watermark_custom", App.Camera, Group.Watermarks, R.string.media_watermark_custom, emptyMap()),
        Option("camera_unlock_watermark_master", App.Camera, Group.Watermarks, R.string.camera_unlock_watermark_master, mapOf("com.oplus.camera.support.ai.master.watermark" to true)),
        Option("camera_unlock_watermark_hasselblad", App.Camera, Group.Watermarks, R.string.camera_unlock_watermark_hasselblad, mapOf("com.oplus.camera.support.custom.hasselblad.watermark" to true)),
        Option("camera_unlock_watermark_color", App.Camera, Group.Watermarks, R.string.camera_unlock_watermark_color, mapOf("com.oplus.camera.support.custom.color.watermark" to true)),
        Option("camera_unlock_watermark_lonely_planet", App.Camera, Group.Watermarks, R.string.camera_unlock_watermark_lonely_planet, mapOf("com.oplus.camera.support.custom.lonely.planet.watermark" to true)),
        Option("camera_unlock_watermark_frame", App.Camera, Group.Watermarks, R.string.camera_unlock_watermark_frame, mapOf("com.oplus.camera.support.frame.watermark" to true)),
        Option("camera_unlock_watermark_new_master", App.Camera, Group.Watermarks, R.string.camera_unlock_watermark_new_master, mapOf("com.oplus.support.new.master.watermark" to true)),
        Option("camera_unlock_live_square", App.Camera, Group.Live, R.string.camera_unlock_live_square, mapOf("com.oplus.1.1.size.livephoto.complete.support" to true)),
        Option("camera_unlock_live_four_three", App.Camera, Group.Live, R.string.camera_unlock_live_four_three, mapOf("com.oplus.4.3.size.livephoto.complete.support" to true)),
        Option("gallery_unlock_filter_ai", App.Gallery, Group.Filters, R.string.gallery_unlock_filter_ai, mapOf("feature_is_support_ai_filter" to true, "pref_ai_filter_enable" to true)),
        Option("gallery_unlock_filter_ipu", App.Gallery, Group.Filters, R.string.gallery_unlock_filter_ipu, mapOf("feature_is_support_ipu_filter" to true)),
        Option("gallery_unlock_palette_ipu", App.Gallery, Group.Filters, R.string.gallery_unlock_palette_ipu, mapOf("feature_is_support_ipu_color_palette" to true)),
        Option("gallery_unlock_watermark_editor", App.Gallery, Group.Watermarks, R.string.gallery_unlock_watermark_editor, mapOf("feature_is_support_photo_editor_watermark" to true)),
        Option("gallery_unlock_watermark_frame", App.Gallery, Group.Watermarks, R.string.gallery_unlock_watermark_frame, mapOf("feature_is_support_photo_editor_frame_watermark" to true)),
        Option("gallery_unlock_watermark_hasselblad", App.Gallery, Group.Watermarks, R.string.gallery_unlock_watermark_hasselblad, mapOf("feature_is_support_hassel_watermark" to true)),
        Option("gallery_unlock_watermark_lumo", App.Gallery, Group.Watermarks, R.string.gallery_unlock_watermark_lumo, mapOf("feature_is_support_lumo_watermark" to true)),
        Option("gallery_unlock_watermark_master", App.Gallery, Group.Watermarks, R.string.gallery_unlock_watermark_master, mapOf("feature_is_support_watermark_master" to true)),
        Option("gallery_unlock_watermark_color", App.Gallery, Group.Watermarks, R.string.gallery_unlock_watermark_color, mapOf("feature_is_support_color_watermark" to true)),
        Option("gallery_unlock_watermark_lonely_planet", App.Gallery, Group.Watermarks, R.string.gallery_unlock_watermark_lonely_planet, mapOf("feature_is_support_lonely_planet_watermark" to true)),
        Option("gallery_unlock_watermark_spring", App.Gallery, Group.Watermarks, R.string.gallery_unlock_watermark_spring, mapOf("feature_is_support_spring_festival_watermark" to true)),
        Option("gallery_unlock_watermark_street", App.Gallery, Group.Watermarks, R.string.gallery_unlock_watermark_street, mapOf("feature_is_support_street_watermark" to true)),
        Option("gallery_unlock_watermark_privacy", App.Gallery, Group.Watermarks, R.string.gallery_unlock_watermark_privacy, mapOf("feature_is_support_privacy_watermark" to true)),
        Option("gallery_unlock_watermark_video_text", App.Gallery, Group.Watermarks, R.string.gallery_unlock_watermark_video_text, mapOf("feature_is_support_video_watermark_extra_large_text_size" to true)),
        Option("gallery_unlock_creation_tab", App.Gallery, Group.Editing, R.string.gallery_unlock_creation_tab, mapOf("feature_is_creation_tab_enabled" to true)),
        Option("gallery_unlock_gradient_blur", App.Gallery, Group.Editing, R.string.gallery_unlock_gradient_blur, mapOf("feature_is_gradient_blur_supported" to true)),
        Option("gallery_unlock_gradient_stroke", App.Gallery, Group.Editing, R.string.gallery_unlock_gradient_stroke, mapOf("feature_is_gradient_stroke_supported" to true)),
        Option("gallery_unlock_beverage_sticker", App.Gallery, Group.Editing, R.string.gallery_unlock_beverage_sticker, mapOf("feature_is_support_beverage_sticker" to true)),
        Option("gallery_unlock_blur", App.Gallery, Group.Editing, R.string.gallery_unlock_blur, mapOf("feature_is_support_blurring" to true)),
        Option("gallery_unlock_caption", App.Gallery, Group.Editing, R.string.gallery_unlock_caption, mapOf("feature_is_support_caption" to true)),
        Option("gallery_unlock_collage_live", App.Gallery, Group.Editing, R.string.gallery_unlock_collage_live, mapOf("feature_is_support_collage_live_photo" to true)),
        Option("gallery_unlock_collage_live_sticker", App.Gallery, Group.Editing, R.string.gallery_unlock_collage_live_sticker, mapOf("feature_is_support_collage_live_sticker" to true)),
        Option("gallery_unlock_collage_multilayer", App.Gallery, Group.Editing, R.string.gallery_unlock_collage_multilayer, mapOf("feature_is_support_collage_multilayer_sticker" to true)),
        Option("gallery_unlock_collage_popout", App.Gallery, Group.Editing, R.string.gallery_unlock_collage_popout, mapOf("feature_is_support_collage_popout" to true)),
        Option("gallery_unlock_collage_direction", App.Gallery, Group.Editing, R.string.gallery_unlock_collage_direction, mapOf("feature_is_support_collage_popout_direction" to true)),
        Option("gallery_unlock_collage_video", App.Gallery, Group.Editing, R.string.gallery_unlock_collage_video, mapOf("feature_is_support_collage_video_sticker" to true)),
        Option("gallery_unlock_live_sticker", App.Gallery, Group.Editing, R.string.gallery_unlock_live_sticker, mapOf("feature_is_support_live_sticker" to true)),
        Option("gallery_unlock_palette", App.Gallery, Group.Editing, R.string.gallery_unlock_palette, mapOf("feature_is_support_color_palette" to true)),
        Option("gallery_unlock_project_save", App.Gallery, Group.Editing, R.string.gallery_unlock_project_save, mapOf("feature_is_support_project_save" to true)),
        Option("gallery_unlock_side_pane", App.Gallery, Group.Editing, R.string.gallery_unlock_side_pane, mapOf("feature_is_support_side_pane" to true)),
        Option("gallery_unlock_light_theme", App.Gallery, Group.Editing, R.string.gallery_unlock_light_theme, mapOf("feature_is_support_photo_page_light_theme" to true)),
        Option("gallery_unlock_thumb_line", App.Gallery, Group.Editing, R.string.gallery_unlock_thumb_line, mapOf("feature_is_support_photo_thumb_line" to true)),
        Option("gallery_unlock_multi_view", App.Gallery, Group.Editing, R.string.gallery_unlock_multi_view, mapOf("feature_is_support_timeline_page_multi_view_switch" to true)),
        Option("gallery_unlock_video_wallpaper", App.Gallery, Group.Editing, R.string.gallery_unlock_video_wallpaper, mapOf("feature_is_support_video_wallpaper" to true)),
        Option("gallery_unlock_playback_speed", App.Gallery, Group.Editing, R.string.gallery_unlock_playback_speed, mapOf("feature_support_playback_speed" to true)),
        Option("gallery_unlock_gif", App.Gallery, Group.Editing, R.string.gallery_unlock_gif, mapOf("feature_is_support_gif_synthesis" to true)),
        Option("gallery_unlock_live_creation", App.Gallery, Group.Live, R.string.gallery_unlock_live_creation, mapOf("feature_is_support_live_creation" to true)),
        Option("gallery_unlock_live_edit", App.Gallery, Group.Live, R.string.gallery_unlock_live_edit, mapOf("feature_is_support_live_content_edit" to true)),
        Option("gallery_unlock_live_slow", App.Gallery, Group.Live, R.string.gallery_unlock_live_slow, mapOf("feature_is_support_live_photo_slow_motion" to true)),
        Option("gallery_unlock_live_4k", App.Gallery, Group.Live, R.string.gallery_unlock_live_4k, mapOf("feature_is_support_olive_4k_edit" to true)),
        Option("gallery_unlock_live_cover", App.Gallery, Group.Live, R.string.gallery_unlock_live_cover, mapOf("feature_is_support_olive_cover_frame_enhancer" to true)),
        Option("gallery_unlock_live_duration", App.Gallery, Group.Live, R.string.gallery_unlock_live_duration, mapOf("feature_is_support_olive_export_dynamic_duration" to true)),
        Option("gallery_unlock_video_to_live", App.Gallery, Group.Live, R.string.gallery_unlock_video_to_live, mapOf("feature_is_support_video_extract_olive" to true)),
        Option("gallery_unlock_raw_edit", App.Gallery, Group.Live, R.string.gallery_unlock_raw_edit, mapOf("feature_is_support_raw_edit" to true)),
        Option("gallery_unlock_live_heif", App.Gallery, Group.Live, R.string.gallery_unlock_live_heif, mapOf("feature_is_support_olive_heif" to true)),
        Option("gallery_unlock_ai_eliminate", App.Gallery, Group.AI, R.string.gallery_unlock_ai_eliminate, mapOf("feature_is_support_ai_eliminate" to true, "feature_is_device_support_ai_eliminate" to true)),
        Option("gallery_unlock_ai_eliminate_local", App.Gallery, Group.AI, R.string.gallery_unlock_ai_eliminate_local, mapOf("feature_is_support_ai_eliminate_local" to true)),
        Option("gallery_unlock_ai_matting", App.Gallery, Group.AI, R.string.gallery_unlock_ai_matting, mapOf("feature_is_support_ai_matting" to true)),
        Option("gallery_unlock_ai_deblur", App.Gallery, Group.AI, R.string.gallery_unlock_ai_deblur, mapOf("feature_is_support_ai_deblur" to true)),
        Option("gallery_unlock_ai_dereflection", App.Gallery, Group.AI, R.string.gallery_unlock_ai_dereflection, mapOf("feature_is_support_ai_dereflection" to true)),
        Option("gallery_unlock_ai_lighting", App.Gallery, Group.AI, R.string.gallery_unlock_ai_lighting, mapOf("feature_is_support_ai_lighting" to true)),
        Option("gallery_unlock_ai_lighting_local", App.Gallery, Group.AI, R.string.gallery_unlock_ai_lighting_local, mapOf("feature_is_support_ai_lighting_local" to true)),
        Option("gallery_unlock_ai_best_take", App.Gallery, Group.AI, R.string.gallery_unlock_ai_best_take, mapOf("feature_is_support_ai_best_take" to true)),
        Option("gallery_unlock_ai_composition", App.Gallery, Group.AI, R.string.gallery_unlock_ai_composition, mapOf("feature_is_support_ai_composition" to true)),
        Option("gallery_unlock_ai_quality", App.Gallery, Group.AI, R.string.gallery_unlock_ai_quality, mapOf("feature_is_support_image_quality_enhance" to true)),
        Option("gallery_unlock_ai_id_photo", App.Gallery, Group.AI, R.string.gallery_unlock_ai_id_photo, mapOf("feature_is_support_ai_id_photo" to true)),
        Option("gallery_unlock_portrait_blur", App.Gallery, Group.AI, R.string.gallery_unlock_portrait_blur, mapOf("feature_is_support_portraitblur" to true)),
        Option("camera_unlock_filter_realme_black_gold", App.Camera, Group.Filters, R.string.camera_unlock_filter_realme_black_gold, mapOf()),
        Option("camera_unlock_filter_realme_cyberpunk", App.Camera, Group.Filters, R.string.camera_unlock_filter_realme_cyberpunk, mapOf()),
        Option("camera_unlock_filter_realme_infrared", App.Camera, Group.Filters, R.string.camera_unlock_filter_realme_infrared, mapOf()),
        Option("camera_unlock_makeup_front", App.Camera, Group.Controls, R.string.camera_unlock_makeup_front, mapOf("com.oplus.feature.custom.makeup.front.support" to true)),
        Option("camera_unlock_makeup_dream", App.Camera, Group.Controls, R.string.camera_unlock_makeup_dream, mapOf("com.oplus.feature.custom.makeup.type.dream.support" to true)),
        Option("camera_unlock_makeup_snow", App.Camera, Group.Controls, R.string.camera_unlock_makeup_snow, mapOf("com.oplus.feature.custom.makeup.type.snow.support" to true)),
        Option("camera_unlock_watermark_makeup", App.Camera, Group.Watermarks, R.string.camera_unlock_watermark_makeup, mapOf("com.oplus.feature.custom.makeup.watermark.support" to true)),
        Option("camera_unlock_watermark_video", App.Camera, Group.Watermarks, R.string.camera_unlock_watermark_video, mapOf("com.oplus.video.watermark.support" to true)),
        Option("camera_unlock_watermark_gallery_edit", App.Camera, Group.Watermarks, R.string.camera_unlock_watermark_gallery_edit, mapOf("com.oplus.watermark.edit.in.gallery.support" to true)),
        Option("gallery_unlock_ai_realme_eliminate", App.Gallery, Group.AI, R.string.gallery_unlock_ai_realme_eliminate, mapOf("feature_is_support_ai_eliminate_rm" to true)),
        Option("gallery_unlock_ai_realme_deblur", App.Gallery, Group.AI, R.string.gallery_unlock_ai_realme_deblur, mapOf("feature_is_support_rm_ai_deblur" to true)),
        Option("gallery_unlock_watermark_oppo_brand", App.Gallery, Group.Watermarks, R.string.gallery_unlock_watermark_oppo_brand, mapOf("feature_is_support_watermark_master" to true, "feature_is_support_photo_editor_frame_watermark" to true, "feature_is_support_ipu_watermark" to true), listOf("personalize_brand_1", "personalize_brand_2", "personalize_brand_5", "personalize_brand_6")),
        Option("gallery_unlock_watermark_oneplus_brand", App.Gallery, Group.Watermarks, R.string.gallery_unlock_watermark_oneplus_brand, mapOf("feature_is_support_watermark_master" to true, "feature_is_support_photo_editor_frame_watermark" to true, "feature_is_support_ipu_watermark" to true), listOf("personalize_brand_1", "personalize_brand_2", "personalize_brand_3", "personalize_brand_6")),
        Option("gallery_unlock_watermark_realme_film", App.Gallery, Group.Watermarks, R.string.gallery_unlock_watermark_realme_film, mapOf("feature_is_support_watermark_master" to true, "feature_is_support_photo_editor_frame_watermark" to true, "feature_is_support_ipu_watermark" to true), listOf("personalize_film_realme_1", "personalize_film_realme_2")),
        Option("gallery_unlock_watermark_realme_inspiration", App.Gallery, Group.Watermarks, R.string.gallery_unlock_watermark_realme_inspiration, mapOf("feature_is_support_watermark_master" to true, "feature_is_support_photo_editor_frame_watermark" to true, "feature_is_support_ipu_watermark" to true), listOf("realme_brand_imprint_inspirationPhoto_1", "realme_brand_imprint_inspirationPhoto_2", "realme_brand_imprint_inspirationPhoto_3")),
        Option("gallery_unlock_watermark_retro", App.Gallery, Group.Watermarks, R.string.gallery_unlock_watermark_retro, mapOf("feature_is_support_watermark_master" to true, "feature_is_support_photo_editor_frame_watermark" to true, "feature_is_support_ipu_watermark" to true), listOf("personalize_retroCamera_1", "personalize_retroCamera_2", "personalize_retroCamera_3", "personalize_retroCamera_4", "personalize_retroCamera_5")),
        Option("gallery_unlock_watermark_signature", App.Gallery, Group.Watermarks, R.string.gallery_unlock_watermark_signature, mapOf("feature_is_support_watermark_master" to true, "feature_is_support_photo_editor_frame_watermark" to true, "feature_is_support_ipu_watermark" to true), listOf("personalize_masterSign_1", "personalize_masterSign_2", "personalize_masterSign_3", "personalize_masterSign_4")),
        Option("gallery_unlock_watermark_hasselblad_xpan", App.Gallery, Group.Watermarks, R.string.gallery_unlock_watermark_hasselblad_xpan, mapOf("feature_is_support_hassel_watermark" to true, "feature_is_support_watermark_master" to true, "feature_is_support_photo_editor_frame_watermark" to true, "feature_is_support_ipu_watermark" to true), listOf("hassel_style_xpan_1")),
        Option("gallery_unlock_watermark_hasselblad_master", App.Gallery, Group.Watermarks, R.string.gallery_unlock_watermark_hasselblad_master, mapOf("feature_is_support_hassel_watermark" to true, "feature_is_support_watermark_master" to true, "feature_is_support_photo_editor_frame_watermark" to true, "feature_is_support_ipu_watermark" to true), listOf("hassel_style_master_mode_1")),
        Option("gallery_unlock_watermark_lumo_master", App.Gallery, Group.Watermarks, R.string.gallery_unlock_watermark_lumo_master, mapOf("feature_is_support_lumo_watermark" to true, "feature_is_support_watermark_master" to true, "feature_is_support_photo_editor_frame_watermark" to true, "feature_is_support_ipu_watermark" to true), listOf("lumo_style_master_mode_1")),
    )
    private val galleryNativeFilters = declaredOptions.filter { it.key.replaceFirst("camera_", "gallery_") in GalleryNativeFilterGroups.keys }
        .map { it.copy(key = it.key.replaceFirst("camera_", "gallery_"), app = App.Gallery,
            title = GalleryNativeFilterGroups.titles.getValue(it.key), overrides = emptyMap(), styles = emptyList()) }
    val options = (declaredOptions + galleryNativeFilters).map { option ->
        when {
            option.app == App.Gallery && option.group == Group.Watermarks -> option.copy(
                overrides = option.overrides + ("feature_is_support_photo_editor_watermark" to true))
            option.key in setOf("camera_unlock_filter_portra", "camera_unlock_filter_tx400") -> option.copy(
                overrides = option.overrides + mapOf("com.oplus.feature.portra.filter.support" to true,
                    "com.oplus.feature.tx400.filter.support" to true))
            else -> option
        }
    }
    val byApp = App.entries.associateWith { app -> options.filter { it.app == app } }
    val sharedStyleOptions = options.filter { it.styles.isNotEmpty() }
    const val CAMERA_MASTER_GATE = "com.oplus.camera.support.ai.master.watermark"
    val keys = options.mapTo(linkedSetOf()) { it.key }
    val gatesByApp = App.entries.associateWith { app -> buildMap<String, Gate> {
        byApp.getValue(app).forEach { option -> option.overrides.forEach { (key, desired) ->
            val old = get(key)
            check(old == null || old.desired == desired) { "Conflicting media gate: $key" }
            put(key, Gate(old?.optionKeys.orEmpty() + option.key, desired))
        } }
    } }

    /** Null means run the original implementation, including when a switch is off. */
    fun overrideValue(app: App, key: String?, enabled: (String) -> Boolean): Boolean? {
        val gate = gatesByApp.getValue(app)[key] ?: return null
        val active = if (gate.optionKeys.size == 1) enabled(gate.optionKeys[0]) else gate.optionKeys.any(enabled)
        if (active) return gate.desired
        // Camera requests these native templates from Gallery's watermark service.
        return if (app == App.Camera && key == CAMERA_MASTER_GATE && sharedStyleOptions.any { enabled(it.key) }) true else null
    }
}
