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
internal fun SystemUiCategoriesPage(onOpen: (FeaturePageMode) -> Unit) {
    FeatureCategoryLinks(
        entries = listOf(
            FeatureCategoryLink(
                FeaturePageMode.SystemUiStatusBar,
                R.string.feature_group_beautify,
                R.string.feature_system_ui_status_bar_entry_summary,
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
internal fun SettingsRegionCategoryEntry(onOpen: (FeaturePageMode) -> Unit) {
    FeatureCategoryLinks(
        entries = listOf(
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
