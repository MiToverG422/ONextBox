package com.mi.onextbox.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.AppThemeMode
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.common.SwitchHaptics
import com.mi.onextbox.ui.common.CouiColorSpec as ThemeColorSpec
import com.mi.onextbox.ui.common.isMonet
import com.mi.onextbox.ui.layout.BottomBarAutoCollapse
import com.mi.onextbox.ui.layout.FloatingBottomBarMode
import com.mi.onextbox.ui.screens.FeatureBackgroundStyleIds
import com.mi.onextbox.ui.screens.rememberFeatureBackgroundSelection
import io.github.suqi8.coui.kmp.theme.ThemePaletteStyle

@Composable
internal fun DisplayThemeSettingsPage(
    rootGranted: Boolean,
    onAppUiStyleChange: (AppUiStyle) -> Unit,
    appThemeMode: AppThemeMode,
    onAppThemeModeChange: (AppThemeMode) -> Unit,
    appThemeKeyColor: Long?,
    onAppThemeKeyColorChange: (Long?) -> Unit,
    appThemePaletteStyle: Int,
    onAppThemePaletteStyleChange: (Int) -> Unit,
    appThemeColorSpec: Int,
    onAppThemeColorSpecChange: (Int) -> Unit,
    floatingBottomBarMode: FloatingBottomBarMode,
    onFloatingBottomBarModeChange: (FloatingBottomBarMode) -> Unit,
    bottomBarAutoCollapse: BottomBarAutoCollapse,
    onBottomBarAutoCollapseChange: (BottomBarAutoCollapse) -> Unit,
    bottomBarHideLabels: Boolean,
    mainPageSwipeEnabled: Boolean,
    subPageSwipeEnabled: Boolean,
    onBottomBarHideLabelsChange: (Boolean) -> Unit,
    onMainPageSwipeEnabledChange: (Boolean) -> Unit,
    onSubPageSwipeEnabledChange: (Boolean) -> Unit,
) {
    val (backgroundStyle, setBackgroundStyle) = rememberFeatureBackgroundSelection()
    DisplayThemePreview(
        rootGranted = rootGranted,
        barMode = floatingBottomBarMode,
        hideBarLabels = bottomBarHideLabels,
    )

    DisplayThemeGroup(R.string.settings_display_theme_colors) {
        SettingsThemeTextDropdown(
            title = stringResource(R.string.setting_ui_style),
            labels = AppUiStyle.entries.map { it.label },
            selectedIndex = LocalAppUiStyle.current.ordinal,
            onSelectedIndexChange = { onAppUiStyleChange(AppUiStyle.entries[it]) },
            hasDividerBelow = true,
        )
        SettingsDivider()
        SettingsThemeModeDropdown(
            title = stringResource(R.string.setting_theme_mode),
            selectedMode = appThemeMode,
            onModeChange = onAppThemeModeChange,
            hasDividerAbove = true,
            hasDividerBelow = true,
        )
        SettingsDivider()
        AnimatedVisibility(
            visible = !appThemeMode.isMonet,
            enter = expandVertically(tween(260, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(160)),
        ) {
            Column {
                SettingsCouiAccentDropdown()
            }
        }
        AnimatedVisibility(
            visible = appThemeMode.isMonet,
            enter = expandVertically(
                animationSpec = tween(260, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(180)),
            exit = shrinkVertically(
                animationSpec = tween(220, easing = FastOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(160)),
        ) {
            Column {
                SettingsThemeKeyColorDropdown(
                    title = stringResource(R.string.setting_theme_key_color),
                    selectedKeyColor = appThemeKeyColor,
                    onKeyColorChange = onAppThemeKeyColorChange,
                    hasDividerAbove = true,
                    hasDividerBelow = true,
                )
                SettingsDivider()
                SettingsThemeTextDropdown(
                    title = stringResource(R.string.setting_theme_palette_style),
                    labels = if (appThemeColorSpec == ThemeColorSpec.Spec2026.ordinal) listOf("CMF") else ThemePaletteStyle.entries.map { it.name },
                    selectedIndex = if (appThemeColorSpec == ThemeColorSpec.Spec2026.ordinal) 0 else appThemePaletteStyle,
                    enabled = appThemeColorSpec != ThemeColorSpec.Spec2026.ordinal,
                    onSelectedIndexChange = onAppThemePaletteStyleChange,
                    hasDividerAbove = true,
                    hasDividerBelow = true,
                )
                SettingsDivider()
                SettingsThemeTextDropdown(
                    title = stringResource(R.string.setting_theme_color_spec),
                    labels = ThemeColorSpec.entries.map { it.label },
                    selectedIndex = appThemeColorSpec,
                    onSelectedIndexChange = onAppThemeColorSpecChange,
                    hasDividerAbove = true,
                    hasDividerBelow = false,
                )
            }
        }
    }
    DisplayThemeGroup(R.string.settings_display_features) {
        SettingsThemeTextDropdown(
            title = stringResource(R.string.setting_feature_background),
            labels = stringArrayResource(R.array.feature_background_styles).toList(),
            selectedIndex = FeatureBackgroundStyleIds.indexOf(backgroundStyle),
            onSelectedIndexChange = {
                setBackgroundStyle(FeatureBackgroundStyleIds[it])
            },
        )
    }
    DisplayThemeGroup(R.string.setting_floating_bottom_bar) {
        SettingsWindowDropdownPreference(
            title = stringResource(R.string.setting_floating_bottom_bar),
            summary = if (floatingBottomBarMode.visible) null else stringResource(R.string.setting_floating_bottom_bar_swipe_hint),
            items = FloatingBottomBarMode.entries.map { bottomBarModeLabel(it) },
            selectedIndex = floatingBottomBarMode.ordinal,
            onSelectedIndexChange = {
                onFloatingBottomBarModeChange(FloatingBottomBarMode.entries[it])
            },
        )
        SettingsDivider()
        if (floatingBottomBarMode.visible) {
            val collapseChoices = BottomBarAutoCollapse.choicesFor(floatingBottomBarMode)
            SettingsWindowDropdownPreference(
                title = stringResource(R.string.setting_bottom_bar_auto_collapse),
                items = collapseChoices.map { stringResource(it.labelRes) },
                selectedIndex = collapseChoices.indexOf(bottomBarAutoCollapse),
                onSelectedIndexChange = {
                    onBottomBarAutoCollapseChange(collapseChoices[it])
                },
            )
            SettingsDivider()
        }
        SettingsToggleRow(
            title = stringResource(R.string.setting_bottom_bar_hide_labels),
            summary = "",
            checked = bottomBarHideLabels,
            enabled = floatingBottomBarMode.visible,
            onCheckedChange = onBottomBarHideLabelsChange,
            hasDividerAbove = true,
            hasDividerBelow = false,
        )
    }
    DisplayThemeGroup(R.string.settings_display_gestures) {
        SettingsToggleRow(
            title = stringResource(R.string.setting_main_page_swipe),
            summary = "",
            checked = floatingBottomBarMode.allowsMainPageSwipe(mainPageSwipeEnabled),
            enabled = floatingBottomBarMode.visible,
            onCheckedChange = onMainPageSwipeEnabledChange,
            hasDividerAbove = false,
            hasDividerBelow = true,
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(R.string.setting_sub_page_swipe),
            summary = "",
            checked = subPageSwipeEnabled,
            onCheckedChange = onSubPageSwipeEnabledChange,
            hasDividerAbove = true,
            hasDividerBelow = true,
        )
        SettingsDivider()
        val context = LocalContext.current
        var switchHaptics by remember(context) { mutableStateOf(SwitchHaptics.enabled(context)) }
        SettingsToggleRow(
            title = stringResource(R.string.setting_switch_haptics),
            summary = stringResource(R.string.setting_switch_haptics_summary),
            checked = switchHaptics,
            onCheckedChange = { enabled ->
                SwitchHaptics.preferences(context).edit().putBoolean(SwitchHaptics.Key, enabled).apply()
                switchHaptics = enabled
            },
            hasDividerAbove = true,
            hasDividerBelow = false,
        )
    }

    AppVisualEffectsSettingsGroup()
}

@Composable
private fun DisplayThemeGroup(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    SettingsSection(title = stringResource(title))
    SettingsGroup(content = content)
}

@Composable
internal fun bottomBarModeLabel(mode: FloatingBottomBarMode): String = when (mode) {
    FloatingBottomBarMode.Coui -> "COUI"
    FloatingBottomBarMode.LiquidGlass -> "LiquidGlass"
    FloatingBottomBarMode.Standard -> stringResource(R.string.setting_standard_bottom_bar)
    FloatingBottomBarMode.Off -> stringResource(R.string.setting_visual_effect_off)
}

@get:StringRes
internal val BottomBarAutoCollapse.labelRes: Int
    get() = when (this) {
        BottomBarAutoCollapse.Left -> R.string.setting_bottom_bar_collapse_left
        BottomBarAutoCollapse.Right -> R.string.setting_bottom_bar_collapse_right
        BottomBarAutoCollapse.Bottom -> R.string.setting_bottom_bar_collapse_bottom
        BottomBarAutoCollapse.Off -> R.string.setting_visual_effect_off
    }
