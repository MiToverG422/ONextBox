package com.mi.onextbox.ui.screens

import com.mi.onextbox.R
import com.mi.onextbox.lsp.SystemAdaptationVersion

internal object FeatureUpdatesCatalog {
    const val EDITION = "2026-10-26"

    enum class Kind { Added, Changed }

    data class Change(val title: Int, val kind: Kind, val note: Int = 0)
    data class Item(val change: Change, val destination: FeatureSearchIndex.Entry)

    val changes = listOf(
        R.string.power_indicator_title,
        R.string.battery_show_health_status_title,
        R.string.battery_show_full_charge_capacity_title,
        R.string.battery_show_rated_capacity_title,
        R.string.battery_show_typical_capacity_title,
        R.string.battery_show_calculated_health_title,
        R.string.battery_block_whitelist_restore_title,
        R.string.camera_default_gallery_title,
        R.string.translation_allow_floating_title,
        R.string.translation_bypass_app_blacklist_title,
        R.string.translation_allow_restricted_pages_title,
        R.string.translation_allow_landscape_title,
        R.string.translation_unlock_browser_title,
        R.string.installer_native_ui_title,
        R.string.installer_skip_detection_title,
        R.string.installer_remove_ads_title,
        R.string.installer_hide_collection_notice_title,
        R.string.installer_block_network_title,
        R.string.launcher_icon_label_spacing_title,
        R.string.launcher_colorful_notification_dot,
        R.string.launcher_notification_dot_shadow,
        SharedFeatureDefinitions.hideRecentApps.title,
        SharedFeatureDefinitions.hideNotificationClear.title,
        SharedFeatureDefinitions.rotationSuggestion.title,
        SharedFeatureDefinitions.hideScreenshotFloating.title,
        R.string.phone_manager_risk_cloud_title,
        R.string.keyguard_block_password_highlight_title,
        R.string.keyguard_swipe_pin_title,
        R.string.game_assistant_music_title,
        R.string.corepatch_title,
        R.string.settings_home_hide_account_title,
        R.string.settings_account_size_title,
        R.string.settings_title_lock_title,
        R.string.settings_home_hide_update_title,
        R.string.settings_realme_title,
        R.string.app_lock_delay_title,
        R.string.native_live_update_title,
        R.string.feature_settings_disable_adaptive_refresh_rate_title,
        R.string.display_enhancement_sr_thermal_title,
        R.string.display_enhancement_color_thermal_title,
        R.string.gallery_ai_retouch_title,
        SharedFeatureDefinitions.volumeSafetyWarning.title,
        R.string.qs_slider_percentage_title,
        R.string.side_volume_percentage_title,
        R.string.side_volume_keep_wide_title,
        R.string.notification_removal_extreme_refresh_rate,
    ).map { Change(it, Kind.Added) } + listOf(
        Change(R.string.launcher_custom_grid_title, Kind.Changed, R.string.feature_updates_layout_note),
        Change(R.string.qs_tile_corner_title, Kind.Changed, R.string.feature_updates_corners_note),
        Change(R.string.feature_settings_c15_about_layout_title, Kind.Changed, R.string.feature_updates_about_note),
        Change(R.string.fluid_browser_title, Kind.Changed, R.string.feature_updates_browser_note),
        Change(R.string.small_window_white_bar, Kind.Changed),
        Change(R.string.network_speed_refresh_second, Kind.Added),
        Change(R.string.network_speed_hide_per_second, Kind.Added),
        Change(R.string.network_speed_hide_units, Kind.Added),
        Change(R.string.network_speed_single_line, Kind.Added),
        Change(R.string.network_speed_follow_font_weight, Kind.Added),
        Change(R.string.network_speed_custom_font_size, Kind.Added),
        Change(R.string.ota_page_title, Kind.Changed, R.string.feature_updates_ota_note),
    )

    fun items(version: SystemAdaptationVersion): List<Item> {
        val destinations = featureSearchEntries(version).associateBy { it.title }
        return changes.mapNotNull { change -> destinations[change.title]?.let { Item(change, it) } }
    }

    fun edition(versionName: String): String = "$EDITION:$versionName"
}
