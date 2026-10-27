package com.mi.onextbox.ui.screens

import com.mi.onextbox.R
import com.mi.onextbox.lsp.SystemAdaptationVersion

internal object FeatureUpdatesCatalog {
    const val EDITION = "2026-10-27-r2"

    enum class Kind { Added, Changed }

    data class Change(val title: Int, val kind: Kind, val note: Int = 0)
    data class Item(val change: Change, val destination: FeatureSearchIndex.Entry)

    val changes = listOf(
        R.string.wallpaper_3d_force_title,
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
        R.string.settings_account_size_title,
        R.string.settings_title_lock_title,
        R.string.clock_period_title,
        R.string.clock_period_shade,
        R.string.clock_compact_spacing,
        R.string.clock_lock_seconds,
        R.string.network_speed_refresh_second,
        R.string.network_speed_hide_per_second,
        R.string.network_speed_hide_units,
        R.string.network_speed_single_line,
        R.string.network_speed_follow_font_weight,
        R.string.network_speed_custom_font_size,
    ).map { Change(it, Kind.Added) } + listOf(
        Change(R.string.launcher_custom_grid_title, Kind.Changed, R.string.feature_updates_layout_note),
        Change(R.string.small_window_white_bar, Kind.Changed, R.string.feature_updates_sidebar_note),
        Change(R.string.keyguard_swipe_pin_title, Kind.Changed, R.string.feature_updates_pin_note),
        Change(R.string.corepatch_title, Kind.Changed, R.string.feature_updates_corepatch_note),
    )

    fun items(version: SystemAdaptationVersion): List<Item> {
        val destinations = featureSearchEntries(version).associateBy { it.title }
        return changes.mapNotNull { change -> destinations[change.title]?.let { Item(change, it) } }
    }

    fun edition(versionName: String): String = "$EDITION:$versionName"
}
