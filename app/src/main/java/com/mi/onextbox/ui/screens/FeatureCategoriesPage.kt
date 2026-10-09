package com.mi.onextbox.ui.screens

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup

private data class FeatureCategoryLink(
    val page: FeaturePageMode,
    @param:StringRes val title: Int,
    @param:StringRes val summary: Int,
)

@Composable
internal fun DesktopCategoriesPage(onOpen: (FeaturePageMode) -> Unit) {
    FeatureCategoryLinks(
        entries = listOf(
            FeatureCategoryLink(FeaturePageMode.DesktopLayout, R.string.desktop_category_layout, R.string.desktop_layout_entry_summary),
            FeatureCategoryLink(FeaturePageMode.DesktopIcons, R.string.desktop_category_icons, R.string.desktop_icons_entry_summary),
            FeatureCategoryLink(FeaturePageMode.DesktopRecent, R.string.feature_group_recent_tasks, R.string.desktop_recent_entry_summary),
            FeatureCategoryLink(FeaturePageMode.DesktopPages, R.string.desktop_category_pages, R.string.desktop_pages_entry_summary),
        ),
        onOpen = onOpen,
    )
}

@Composable
internal fun SystemUiCategoriesPage(onOpen: (FeaturePageMode) -> Unit) {
    FeatureCategoryLinks(
        entries = listOf(
            FeatureCategoryLink(
                FeaturePageMode.SystemUiSmallWindow,
                R.string.small_window_title,
                R.string.small_window_entry_summary,
            ),
            FeatureCategoryLink(
                FeaturePageMode.SystemUiLockScreen,
                R.string.keyguard_page_title,
                R.string.keyguard_page_entry_summary,
            ),
            FeatureCategoryLink(
                FeaturePageMode.SystemUiNavigationBar,
                R.string.feature_group_navigation_bar,
                R.string.feature_system_ui_navigation_bar_entry_summary,
            ),
            FeatureCategoryLink(
                FeaturePageMode.SystemUiStatusBar,
                R.string.feature_group_beautify,
                R.string.feature_system_ui_status_bar_entry_summary,
            ),
            FeatureCategoryLink(
                FeaturePageMode.SystemUiFluidCloud,
                R.string.feature_fluid_cloud_title,
                R.string.feature_fluid_cloud_entry_summary,
            ),
            FeatureCategoryLink(
                FeaturePageMode.SystemUiNotificationCenter,
                R.string.feature_group_notification_center,
                R.string.feature_system_ui_notification_center_entry_summary,
            ),
            FeatureCategoryLink(
                FeaturePageMode.SystemUiControlCenter,
                R.string.feature_group_control_center,
                R.string.feature_system_ui_control_center_entry_summary,
            ),
            FeatureCategoryLink(
                FeaturePageMode.SystemUiDynamicColor,
                R.string.feature_group_dynamic_color,
                R.string.feature_system_ui_dynamic_color_entry_summary,
            ),
            FeatureCategoryLink(
                FeaturePageMode.SystemUiNative,
                R.string.feature_group_native,
                R.string.feature_system_ui_native_entry_summary,
            ),
        ),
        onOpen = onOpen,
    )
}

@Composable
internal fun SettingsCategoriesPage(onOpen: (FeaturePageMode) -> Unit) {
    FeatureCategoryLinks(
        entries = listOf(
            FeatureCategoryLink(
                FeaturePageMode.SettingsAppearance,
                R.string.settings_category_interface_display,
                R.string.settings_appearance_entry_summary,
            ),
            FeatureCategoryLink(
                FeaturePageMode.SettingsApps,
                R.string.feature_group_app_management,
                R.string.settings_apps_entry_summary,
            ),
            FeatureCategoryLink(
                FeaturePageMode.SettingsPermissions,
                R.string.settings_category_permissions,
                R.string.settings_permissions_entry_summary,
            ),
            FeatureCategoryLink(
                FeaturePageMode.SettingsAboutDevice,
                R.string.feature_group_about_device,
                R.string.settings_about_device_entry_summary,
            ),
            FeatureCategoryLink(
                FeaturePageMode.SettingsRegion,
                R.string.feature_group_settings_hidden_features,
                R.string.feature_settings_region_entry_summary,
            ),
        ),
        onOpen = onOpen,
    )
}

@Composable
private fun FeatureCategoryLinks(
    entries: List<FeatureCategoryLink>,
    onOpen: (FeaturePageMode) -> Unit,
) {
    SettingsGroup {
        entries.forEachIndexed { index, entry ->
            if (index > 0) SettingsDivider()
            SettingsCardRow(
                title = stringResource(entry.title),
                summary = stringResource(entry.summary),
                onClick = { onOpen(entry.page) },
                showArrow = true,
                hasDividerAbove = index > 0,
                hasDividerBelow = index < entries.lastIndex,
            )
        }
    }
}
