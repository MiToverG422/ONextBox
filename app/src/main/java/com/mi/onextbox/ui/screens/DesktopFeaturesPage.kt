package com.mi.onextbox.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.lsp.FeatureSliderRules
import com.mi.onextbox.ui.common.AssistantScreenOption
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import com.mi.onextbox.ui.settings.SettingsWindowDropdownPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun DesktopFeaturesPage(
    launcherLayoutUnlocked: Boolean,
    onLauncherLayoutUnlockedChange: (Boolean) -> Unit,
    assistantScreenOption: AssistantScreenOption,
    onAssistantScreenOptionChange: (AssistantScreenOption) -> Unit,
    recentTaskRadiusEnabled: Boolean,
    onRecentTaskRadiusEnabledChange: (Boolean) -> Unit,
    recentTaskRadiusDp: Int,
    onRecentTaskRadiusDpChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hideWidgetLabelsEnabled by rememberSaveable {
        mutableStateOf(LspConfig.isLauncherHideWidgetLabelsEnabled(context))
    }
    var launcherSearchBarMode by rememberSaveable {
        mutableStateOf(LspConfig.getLauncherSearchBarMode(context))
    }
    var launcherSearchCompatibility by rememberSaveable {
        mutableStateOf(LspConfig.isLauncherSearchCompatibilityEnabled(context))
    }

    SettingsSection(title = stringResource(R.string.feature_group_minus_one))
    SettingsGroup {
        AssistantScreenRow(
            title = stringResource(R.string.event_page_tool_title),
            summary = stringResource(R.string.event_page_tool_summary),
            selectedOption = assistantScreenOption,
            onOptionChange = onAssistantScreenOptionChange,
            hasDividerAbove = false,
            hasDividerBelow = false,
        )
    }

    SettingsSection(title = stringResource(R.string.feature_group_region))
    SettingsGroup {
        LauncherSearchBarModeRow(
            title = stringResource(R.string.feature_launcher_taskbar_search_box_title),
            summary = stringResource(R.string.feature_launcher_taskbar_search_box_summary),
            selectedMode = launcherSearchBarMode,
            onModeChange = { mode ->
                launcherSearchBarMode = mode
                scope.launch {
                    withContext(Dispatchers.IO) {
                        LspConfig.setLauncherSearchBarMode(context, mode)
                    }
                }
            },
            hasDividerAbove = false,
            hasDividerBelow = true,
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(R.string.feature_launcher_search_compatibility_title),
            summary = stringResource(R.string.feature_launcher_search_compatibility_summary),
            checked = launcherSearchCompatibility,
            enabled = launcherSearchBarMode != LspConfig.LAUNCHER_SEARCH_BAR_MODE_OFF,
            onCheckedChange = { enabled ->
                if (launcherSearchBarMode != LspConfig.LAUNCHER_SEARCH_BAR_MODE_OFF) {
                    launcherSearchCompatibility = enabled
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            LspConfig.setLauncherSearchCompatibilityEnabled(context, enabled)
                        }
                    }
                }
            },
            hasDividerAbove = true,
            hasDividerBelow = false,
        )
    }

    SettingsSection(title = stringResource(R.string.feature_group_layout))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.feature_launcher_layout_unlock_title),
            summary = stringResource(R.string.feature_launcher_layout_unlock_summary),
            checked = launcherLayoutUnlocked,
            onCheckedChange = onLauncherLayoutUnlockedChange,
            hasDividerAbove = false,
            hasDividerBelow = true,
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(R.string.feature_launcher_hide_widget_labels_title),
            summary = stringResource(R.string.feature_launcher_hide_widget_labels_summary),
            checked = hideWidgetLabelsEnabled,
            onCheckedChange = { enabled ->
                hideWidgetLabelsEnabled = enabled
                scope.launch {
                    withContext(Dispatchers.IO) {
                        LspConfig.setLauncherHideWidgetLabelsEnabled(context, enabled)
                    }
                }
            },
            hasDividerAbove = true,
            hasDividerBelow = false,
        )
    }

    SettingsSection(title = stringResource(R.string.launcher_group_pages))
    SettingsGroup { LauncherFeatureRows(LauncherFeatureItems.pages) }

    SettingsSection(title = stringResource(R.string.feature_group_recent_tasks))
    SettingsGroup {
        LauncherFeatureRows(LauncherFeatureItems.recent, hasDividerBelow = true)
        RecentTaskRadiusRow(
            title = stringResource(R.string.feature_recent_task_radius_title),
            checked = recentTaskRadiusEnabled,
            onCheckedChange = onRecentTaskRadiusEnabledChange,
            valueDp = recentTaskRadiusDp,
            onValueDpChange = onRecentTaskRadiusDpChange,
            hasDividerAbove = true,
        )
    }

    SettingsSection(title = stringResource(R.string.launcher_group_badges))
    SettingsGroup { LauncherFeatureRows(LauncherFeatureItems.badges) }

    SettingsSection(title = stringResource(R.string.launcher_group_folder))
    SettingsGroup { LauncherFeatureRows(LauncherFeatureItems.folder) }

    SettingsSection(title = stringResource(R.string.launcher_group_dock))
    SettingsGroup { LauncherFeatureRows(LauncherFeatureItems.dock) }
}

@Composable
internal fun AssistantScreenRow(
    title: String,
    summary: String,
    selectedOption: AssistantScreenOption,
    onOptionChange: (AssistantScreenOption) -> Unit,
    hasDividerAbove: Boolean,
    hasDividerBelow: Boolean,
) {
    val options = listOf(
        AssistantScreenOption.Shelf to stringResource(R.string.event_shelf_option),
        AssistantScreenOption.Disabled to stringResource(R.string.event_disable_option),
        AssistantScreenOption.Default to stringResource(R.string.event_default_option),
    )
    val selectedLabel = options.firstOrNull { it.first == selectedOption }?.second ?: options.last().second
    OptionDropdownRow(
        title = title,
        summary = summary,
        selectedLabel = selectedLabel,
        options = options,
        selectedValue = selectedOption,
        onValueChange = onOptionChange,
        hasDividerAbove = hasDividerAbove,
        hasDividerBelow = hasDividerBelow,
    )
}

@Composable
internal fun LauncherSearchBarModeRow(
    title: String,
    summary: String,
    selectedMode: Int,
    onModeChange: (Int) -> Unit,
    hasDividerAbove: Boolean,
    hasDividerBelow: Boolean,
) {
    val options = listOf(
        LspConfig.LAUNCHER_SEARCH_BAR_MODE_CHINA to
            stringResource(R.string.feature_launcher_search_bar_mode_china),
        LspConfig.LAUNCHER_SEARCH_BAR_MODE_INTERNATIONAL to
            stringResource(R.string.feature_launcher_search_bar_mode_international),
        LspConfig.LAUNCHER_SEARCH_BAR_MODE_OFF to
            stringResource(R.string.feature_launcher_search_bar_mode_off),
    )
    val selectedLabel = options.firstOrNull { it.first == selectedMode }?.second
        ?: options.last().second
    OptionDropdownRow(
        title = title,
        summary = summary,
        selectedLabel = selectedLabel,
        options = options,
        selectedValue = selectedMode,
        onValueChange = onModeChange,
        hasDividerAbove = hasDividerAbove,
        hasDividerBelow = hasDividerBelow,
    )
}

@Composable
internal fun <T> OptionDropdownRow(
    title: String,
    summary: String,
    selectedLabel: String,
    options: List<Pair<T, String>>,
    selectedValue: T,
    onValueChange: (T) -> Unit,
    hasDividerAbove: Boolean,
    hasDividerBelow: Boolean,
) {
    val selectedIndex = options
        .indexOfFirst { it.first == selectedValue }
        .takeIf { it >= 0 }
        ?: 0
    FeatureSegmentPosition(
        index = if (hasDividerAbove) 1 else 0,
        count = 1 + (if (hasDividerAbove) 1 else 0) + (if (hasDividerBelow) 1 else 0),
    ) {
    SettingsWindowDropdownPreference(
        title = title,
        summary = summary.takeIf { it.isNotBlank() },
        items = options.map { it.second },
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { index ->
            options.getOrNull(index)?.first?.let(onValueChange)
        },
    )
    }
}

@Composable
internal fun RecentTaskRadiusRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    valueDp: Int,
    onValueDpChange: (Int) -> Unit,
    hasDividerAbove: Boolean,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val defaultRadiusDp = remember(context, configuration) { systemRecentTaskRadiusDp(context) }
    val radiusPreference = FeatureSliderRules.normalizeRadius(valueDp)
    val displayedRadius = if (radiusPreference == FeatureSliderRules.SYSTEM_DEFAULT) {
        defaultRadiusDp ?: FeatureSliderRules.RECENT_RADIUS_PREVIEW_DP
    } else valueDp
    SettingsToggleRow(
        title = title,
        summary = "",
        checked = checked,
        onCheckedChange = onCheckedChange,
        hasDividerAbove = hasDividerAbove,
        hasDividerBelow = true,
    )
    SettingsDivider()
    FeatureSliderRow(
        heading = stringResource(R.string.feature_recent_task_radius_label),
        currentValue = if (radiusPreference == FeatureSliderRules.SYSTEM_DEFAULT) {
            stringResource(R.string.feature_slider_current_default)
        } else stringResource(R.string.feature_slider_current_dp, valueDp),
        value = displayedRadius.toFloat(),
        valueRange = 0f..260f,
        steps = 259,
        enabled = checked,
        onValueChange = { onValueDpChange(FeatureSliderRules.radiusFromSlider(it.roundToInt(), defaultRadiusDp)) },
        onReset = { onValueDpChange(FeatureSliderRules.SYSTEM_DEFAULT) },
        hasDividerAbove = true,
        hasDividerBelow = false,
    )
}

@SuppressLint("DiscouragedApi")
private fun systemRecentTaskRadiusDp(context: Context): Int? = runCatching {
    val resources = context.packageManager.getResourcesForApplication("com.android.launcher")
    val density = resources.displayMetrics.density
    if (density <= 0f) return@runCatching null
    FeatureSliderRules.recentRadiusResourceNames.firstNotNullOfOrNull { name ->
        val id = resources.getIdentifier(name, "dimen", "com.android.launcher")
        if (id == 0) null
        else (resources.getDimension(id) / density).takeIf { it.isFinite() && it >= 0f }?.roundToInt()
    }
}.getOrNull()
