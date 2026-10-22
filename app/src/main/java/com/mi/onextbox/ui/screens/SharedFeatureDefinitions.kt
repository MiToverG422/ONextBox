package com.mi.onextbox.ui.screens

import android.content.Context
import com.mi.onextbox.R
import com.mi.onextbox.lsp.C16StackedSignalSettings
import com.mi.onextbox.lsp.NetworkIdentitySettings
import com.mi.onextbox.lsp.QsTileCornerSettings
import com.mi.onextbox.lsp.QsSliderPercentageSettings
import com.mi.onextbox.lsp.VolumeSafetyWarningSettings

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
    val qsCorner = Toggle(R.string.qs_tile_corner_title, R.string.qs_tile_corner_summary,
        FeaturePageMode.SystemUiControlCenter, { QsTileCornerSettings.state(it).enabled },
        { context, enabled -> QsTileCornerSettings.setEnabled(context, enabled) })
    val qsPercentage = Toggle(R.string.qs_slider_percentage_title, R.string.qs_slider_percentage_summary,
        FeaturePageMode.SystemUiControlCenter, { QsSliderPercentageSettings.state(it).enabled },
        { context, enabled -> QsSliderPercentageSettings.setEnabled(context, enabled) })
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
    )
}
