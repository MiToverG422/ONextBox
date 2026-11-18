package com.mi.onextbox.lsp

/** Only dedicated initializers can provide evidence for an individual feature. */
internal object FeatureRuntimeCatalog {
    private val keysByHook = mapOf(
        "AospSystemUiHooker.hook" to AospUiSettings.chargingKeys.toList(),
        "KeyboardMaterialHooker.hook" to listOf("keyboard_material_unlock"),
        "KeyboardBottomLiftHooker.hook" to listOf("keyboard_bottom_lift_fix"),
        "KeyboardDismissButtonHooker.hook" to listOf("keyboard_hide_dismiss"),
        "KeyboardFlagEmojiHooker.hook" to listOf("keyboard_flag_emoji"),
        "AospVolumeHooker.hook" to listOf("systemui_aosp_volume"),
        "ColorOs16SystemUI.native-volume" to listOf("systemui_aosp_volume"),
        "PowerIndicatorHooker.hook" to listOf("statusbar_power_enabled"),
        "ColorOs16SystemUI.power-indicator" to listOf("statusbar_power_enabled"),
        "QsTileCornerHooker.hook" to listOf("systemui_qs_tile_corner_enabled"),
        "ColorOs16SystemUI.qs-corners" to listOf("systemui_qs_tile_corner_enabled"),
        "QsSliderPercentageHooker.hook" to listOf("systemui_qs_slider_percentage_enabled"),
        "ColorOs16SystemUI.qs-percentage" to listOf("systemui_qs_slider_percentage_enabled"),
        "SideVolumePercentageHooker.hook" to listOf("systemui_side_volume_percentage_enabled"),
        "ColorOs16SystemUI.volume-percentage" to listOf("systemui_side_volume_percentage_enabled"),
        "VolumeSafetyWarningHooker.hook" to listOf("systemui_block_hearing_safety_warning"),
        "ColorOs16SystemUI.volume-safety" to listOf("systemui_block_hearing_safety_warning"),
        "HideNotificationClearHooker.hook" to listOf("systemui_hide_notification_clear"),
        "RetainLockscreenNotificationsHooker.hook" to listOf("systemui_retain_lockscreen_notifications"),
        "CameraGalleryHooker.hook" to listOf("camera_follow_default_gallery"),
        "MediaUnlockHooker.camera" to MediaUnlockCatalog.byApp.getValue(MediaUnlockCatalog.App.Camera).map { it.key },
        "MediaUnlockHooker.gallery" to MediaUnlockCatalog.byApp.getValue(MediaUnlockCatalog.App.Gallery).map { it.key },
        "CameraFilterCatalogHooker.hook" to CameraFilterCatalogRules.idsByKey.keys.toList(),
        "CameraFilterSdkHooker.hook" to CameraFilterSdkHooker.options.map { it.key },
        "CameraXpanFilterHooker.hook" to listOf("camera_unlock_xpan", "camera_unlock_xpan_legacy"),
        "CameraMediaGuardHooker.hook" to listOf("camera_unlock_vibe", "camera_unlock_gr", MediaWatermarkSettings.ENABLED),
        "CameraAiCompositionHooker.hook" to listOf(CameraAiCompositionHooker.INSPIRATION, CameraAiCompositionHooker.SCENE),
        "CameraPaletteHooker.camera" to listOf(CameraPaletteSupport.OPTION),
        "CameraPaletteHooker.gallery" to listOf("gallery_unlock_palette", "gallery_unlock_palette_ipu"),
        "GalleryNativeFilterGroups.hook" to GalleryNativeFilterGroups.keys.toList(),
        "CameraFilterRecommendationHooker.hook" to CameraFilterRecommendationRules.keys,
        "GalleryWatermarkCatalogHooker.hook" to GalleryWatermarkCatalogRules.keys,
        "GalleryWatermarkDeviceHooker.hook" to GalleryWatermarkDeviceHooker.keys.toList(),
        "MediaWatermarkHooker.hook" to listOf(MediaWatermarkSettings.ENABLED),
        "NativeLiveUpdateHooker.hook" to listOf("systemui_native_live_update"),
        "C16StackedSignalHooker.hook" to listOf("systemui_c16_stacked_signal"),
        "HideRecentAppsHooker.hook" to listOf("launcher_hide_recent_apps_enabled"),
        "LauncherIconLabelSpacingHooker.hook" to listOf("launcher_icon_label_spacing_enabled"),
        "GalleryAiRetouchHooker.hook" to listOf("gallery_ai_retouch_unlock"),
        "PhoneManagerRiskCloudHooker.hook" to listOf("phonemanager_hide_risk_app_cloud"),
    )

    fun keysFor(hook: String): List<String> = keysByHook[hook].orEmpty()
}
