package com.mi.onextbox.ui.screens

import android.content.Context
import com.mi.onextbox.R
import com.mi.onextbox.lsp.C16StackedSignalSettings
import com.mi.onextbox.lsp.NetworkIdentitySettings
import com.mi.onextbox.lsp.QsTileCornerSettings
import com.mi.onextbox.lsp.QsSliderPercentageSettings
import com.mi.onextbox.lsp.SideVolumePercentageSettings
import com.mi.onextbox.lsp.VolumeSafetyWarningSettings
import com.mi.onextbox.lsp.ScreenTranslationSettings

/** Incremental migration: one definition drives search, inventory and quick switches. */
internal object SharedFeatureDefinitions {
    data class Toggle(
        val title: Int,
        val summary: Int,
        val page: FeaturePageMode,
        val read: suspend (Context) -> Boolean,
        val write: suspend (Context, Boolean) -> Unit,
    ) {
        fun catalogEntry() = EnabledFeatureCatalog.Entry(title, page, read = read)
        fun searchEntry() = FeatureSearchIndex.Entry(page, title, summary)
        fun action() = title to write
    }

    val networkIdentity = Toggle(R.string.network_identity_title, R.string.network_identity_summary,
        FeaturePageMode.MobileNetwork, { NetworkIdentitySettings.state(it).enabled },
        { context, enabled -> NetworkIdentitySettings.setEnabled(context, enabled) })

    val cameraDefaultGallery = Toggle(R.string.camera_default_gallery_title, R.string.camera_default_gallery_summary,
        FeaturePageMode.Camera, { com.mi.onextbox.lsp.CameraGallerySettings.enabled(it) },
        { context, enabled -> com.mi.onextbox.lsp.CameraGallerySettings.set(context, enabled) })

    private fun translation(key: String, title: Int, summary: Int) = Toggle(title, summary,
        FeaturePageMode.Translation, { ScreenTranslationSettings.enabled(it, key) },
        { context, enabled -> ScreenTranslationSettings.set(context, key, enabled) })
    val translationFloating = translation(ScreenTranslationSettings.ALLOW_FLOATING,
        R.string.translation_allow_floating_title, R.string.translation_allow_floating_summary)
    val translationBlacklist = translation(ScreenTranslationSettings.BYPASS_APP_BLACKLIST,
        R.string.translation_bypass_app_blacklist_title, R.string.translation_bypass_app_blacklist_summary)
    val translationPages = translation(ScreenTranslationSettings.ALLOW_RESTRICTED_PAGES,
        R.string.translation_allow_restricted_pages_title, R.string.translation_allow_restricted_pages_summary)
    val translationLandscape = translation(ScreenTranslationSettings.ALLOW_LANDSCAPE,
        R.string.translation_allow_landscape_title, R.string.translation_allow_landscape_summary)
    val translationBrowser = translation(ScreenTranslationSettings.UNLOCK_BROWSER,
        R.string.translation_unlock_browser_title, R.string.translation_unlock_browser_summary)
    val translationRows = listOf(
        ScreenTranslationSettings.ALLOW_FLOATING to translationFloating,
        ScreenTranslationSettings.BYPASS_APP_BLACKLIST to translationBlacklist,
        ScreenTranslationSettings.ALLOW_RESTRICTED_PAGES to translationPages,
        ScreenTranslationSettings.ALLOW_LANDSCAPE to translationLandscape,
        ScreenTranslationSettings.UNLOCK_BROWSER to translationBrowser,
    )
    private fun installer(key: String, title: Int, summary: Int, page: FeaturePageMode) = Toggle(title, summary,
        page, { com.mi.onextbox.lsp.InstallerCleanupSettings.enabled(it, key) },
        { context, enabled -> com.mi.onextbox.lsp.InstallerCleanupSettings.set(context, key, enabled) })
    val installerNative = installer(com.mi.onextbox.lsp.InstallerCleanupSettings.NATIVE_UI,
        R.string.installer_native_ui_title, R.string.installer_native_ui_summary, FeaturePageMode.PackageInstaller)
    val installerSkipScan = installer(com.mi.onextbox.lsp.InstallerCleanupSettings.APP_SKIP_SCAN,
        R.string.installer_skip_detection_title, R.string.installer_skip_detection_summary, FeaturePageMode.AppInstaller)
    val installerRemoveAds = installer(com.mi.onextbox.lsp.InstallerCleanupSettings.APP_REMOVE_ADS,
        R.string.installer_remove_ads_title, R.string.installer_remove_ads_summary, FeaturePageMode.AppInstaller)
    val installerHideCollectionNotice = installer(com.mi.onextbox.lsp.InstallerCleanupSettings.APP_HIDE_COLLECTION_NOTICE,
        R.string.installer_hide_collection_notice_title, R.string.installer_hide_collection_notice_summary, FeaturePageMode.AppInstaller)
    val installerBlockNetwork = installer(com.mi.onextbox.lsp.InstallerCleanupSettings.APP_BLOCK_NETWORK,
        R.string.installer_block_network_title, R.string.installer_block_network_summary, FeaturePageMode.AppInstaller)
    val appInstallerRows = listOf(
        com.mi.onextbox.lsp.InstallerCleanupSettings.APP_SKIP_SCAN to installerSkipScan,
        com.mi.onextbox.lsp.InstallerCleanupSettings.APP_REMOVE_ADS to installerRemoveAds,
        com.mi.onextbox.lsp.InstallerCleanupSettings.APP_HIDE_COLLECTION_NOTICE to installerHideCollectionNotice,
        com.mi.onextbox.lsp.InstallerCleanupSettings.APP_BLOCK_NETWORK to installerBlockNetwork,
    )
    val packageInstallerRows = listOf(
        com.mi.onextbox.lsp.InstallerCleanupSettings.NATIVE_UI to installerNative,
    )
    val installerSubPages = listOf(
        FeaturePageMode.Installer to R.string.installer_title,
        FeaturePageMode.CorePatch to R.string.corepatch_title,
    )
    val qsCorner = Toggle(R.string.qs_tile_corner_title, R.string.qs_tile_corner_summary,
        FeaturePageMode.SystemUiControlCenter, { QsTileCornerSettings.state(it).enabled },
        { context, enabled -> QsTileCornerSettings.setEnabled(context, enabled) })
    val qsPercentage = Toggle(R.string.qs_slider_percentage_title, R.string.qs_slider_percentage_summary,
        FeaturePageMode.SystemUiControlCenter, { QsSliderPercentageSettings.state(it).enabled },
        { context, enabled -> QsSliderPercentageSettings.setEnabled(context, enabled) })
    val sideVolumePercentage = Toggle(R.string.side_volume_percentage_title, R.string.side_volume_percentage_summary,
        FeaturePageMode.SystemUiVolume, { SideVolumePercentageSettings.state(it).enabled },
        { context, enabled -> SideVolumePercentageSettings.setEnabled(context, enabled) })
    val sideVolumeKeepWide = Toggle(R.string.side_volume_keep_wide_title, 0,
        FeaturePageMode.SystemUiVolume, { com.mi.onextbox.lsp.SideVolumeAppearanceSettings.state(it).keepWide },
        { context, enabled -> com.mi.onextbox.lsp.SideVolumeAppearanceSettings.setKeepWide(context, enabled) })
    val stackedSignal = Toggle(R.string.feature_c16_stacked_signal_title, R.string.feature_c16_stacked_signal_summary,
        FeaturePageMode.SystemUiNative, { C16StackedSignalSettings.enabled(it) },
        { context, enabled -> C16StackedSignalSettings.set(context, enabled) })
    val volumeSafetyWarning = Toggle(
        R.string.volume_block_hearing_safety_warning_title,
        R.string.volume_block_hearing_safety_warning_summary,
        FeaturePageMode.SystemUiVolume,
        { VolumeSafetyWarningSettings.enabled(it) },
        { context, enabled -> VolumeSafetyWarningSettings.set(context, enabled) },
    )
    val powerIndicator = Toggle(R.string.power_indicator_title, R.string.power_indicator_summary,
        FeaturePageMode.SystemUiStatusBar, { com.mi.onextbox.lsp.PowerIndicatorSettings.state(it).enabled },
        { context, enabled -> com.mi.onextbox.lsp.PowerIndicatorSettings.setEnabled(context, enabled) })
    val phoneManagerRiskCloud = Toggle(R.string.phone_manager_risk_cloud_title, R.string.phone_manager_risk_cloud_summary,
        FeaturePageMode.PhoneManager, { com.mi.onextbox.lsp.PhoneManagerRiskCloudSettings.enabled(it) },
        { context, enabled -> com.mi.onextbox.lsp.PhoneManagerRiskCloudSettings.set(context, enabled) })
    val rotationSuggestion = Toggle(R.string.rotation_suggestion_hide_title, R.string.rotation_suggestion_hide_summary,
        FeaturePageMode.SystemUiNavigationBar, { com.mi.onextbox.lsp.RotationSuggestionSettings.enabled(it) },
        { context, enabled -> com.mi.onextbox.lsp.RotationSuggestionSettings.set(context, enabled) })
    val launcherLabelSpacing = Toggle(R.string.launcher_icon_label_spacing_title, R.string.launcher_icon_label_spacing_summary,
        FeaturePageMode.DesktopIcons, { com.mi.onextbox.lsp.LauncherGestureSettings.iconLabelSpacingEnabled(it) },
        { context, enabled -> com.mi.onextbox.lsp.LauncherGestureSettings.save(context, com.mi.onextbox.lsp.LauncherGestureSettings.SPACING_ENABLED, enabled) })
    val hideRecentApps = Toggle(R.string.hide_recent_apps_title, R.string.hide_recent_apps_summary,
        FeaturePageMode.DesktopHiddenApps, { com.mi.onextbox.lsp.HideRecentAppsSettings.enabled(it) },
        { context, enabled -> com.mi.onextbox.lsp.HideRecentAppsSettings.setEnabled(context, enabled) })
    val hideNotificationClear = Toggle(R.string.hide_notification_clear_title, R.string.hide_notification_clear_summary,
        FeaturePageMode.SystemUiNotificationCenter, { com.mi.onextbox.lsp.HideNotificationClearSettings.enabled(it) },
        { context, enabled -> com.mi.onextbox.lsp.HideNotificationClearSettings.set(context, enabled) })
    val hideScreenshotFloating = Toggle(R.string.hide_screenshot_floating_title, R.string.hide_screenshot_floating_summary,
        FeaturePageMode.Screenshot, { com.mi.onextbox.lsp.HideScreenshotFloatingSettings.enabled(it) },
        { context, enabled -> com.mi.onextbox.lsp.HideScreenshotFloatingSettings.set(context, enabled) })
    val galleryAiRetouch = Toggle(R.string.gallery_ai_retouch_title, R.string.gallery_ai_retouch_summary,
        FeaturePageMode.Gallery, { com.mi.onextbox.lsp.GalleryAiRetouchSettings.enabled(it) },
        { context, enabled -> com.mi.onextbox.lsp.GalleryAiRetouchSettings.set(context, enabled) })
    val nativeLiveUpdate = Toggle(R.string.native_live_update_title, R.string.native_live_update_summary,
        FeaturePageMode.SystemUiNative, { com.mi.onextbox.lsp.NativeLiveUpdateSettings.enabled(it) },
        { context, enabled -> com.mi.onextbox.lsp.NativeLiveUpdateSettings.set(context, enabled) })
    val all = listOf(
        networkIdentity,
        qsCorner,
        qsPercentage,
        sideVolumePercentage,
        sideVolumeKeepWide,
        stackedSignal,
        volumeSafetyWarning,
        powerIndicator,
        phoneManagerRiskCloud,
        rotationSuggestion,
        launcherLabelSpacing,
        hideRecentApps,
        hideNotificationClear,
        hideScreenshotFloating,
        galleryAiRetouch,
        nativeLiveUpdate,
        translationFloating,
        translationBlacklist,
        translationPages,
        translationLandscape,
        translationBrowser,
        installerNative,
        installerSkipScan,
        installerRemoveAds,
        installerHideCollectionNotice,
        installerBlockNetwork,
    )
}
