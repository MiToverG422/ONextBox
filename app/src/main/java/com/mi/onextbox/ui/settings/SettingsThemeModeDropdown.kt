package com.mi.onextbox.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.AppThemeMode
import com.mi.onextbox.ui.common.isMonet

internal fun AppThemeMode.material3ExpressiveBrightness(): AppThemeMode = when (this) {
    AppThemeMode.System, AppThemeMode.MonetSystem -> AppThemeMode.System
    AppThemeMode.Light, AppThemeMode.MonetLight -> AppThemeMode.Light
    AppThemeMode.Dark, AppThemeMode.MonetDark -> AppThemeMode.Dark
}

internal fun AppThemeMode.withMaterial3ExpressiveBrightness(brightness: AppThemeMode): AppThemeMode =
    when (brightness.material3ExpressiveBrightness()) {
        AppThemeMode.System -> if (isMonet) AppThemeMode.MonetSystem else AppThemeMode.System
        AppThemeMode.Light -> if (isMonet) AppThemeMode.MonetLight else AppThemeMode.Light
        AppThemeMode.Dark -> if (isMonet) AppThemeMode.MonetDark else AppThemeMode.Dark
        else -> error("Unsupported brightness mode")
    }

internal fun AppThemeMode.withMaterial3ExpressiveDynamicColor(enabled: Boolean): AppThemeMode =
    when (material3ExpressiveBrightness()) {
        AppThemeMode.System -> if (enabled) AppThemeMode.MonetSystem else AppThemeMode.System
        AppThemeMode.Light -> if (enabled) AppThemeMode.MonetLight else AppThemeMode.Light
        AppThemeMode.Dark -> if (enabled) AppThemeMode.MonetDark else AppThemeMode.Dark
        else -> error("Unsupported brightness mode")
    }

@Composable
fun SettingsThemeModeDropdown(
    title: String,
    selectedMode: AppThemeMode,
    onModeChange: (AppThemeMode) -> Unit,
    hasDividerAbove: Boolean = false,
    hasDividerBelow: Boolean = false,
) {
    val modes = listOf(
        AppThemeMode.System,
        AppThemeMode.Light,
        AppThemeMode.Dark,
        AppThemeMode.MonetSystem,
        AppThemeMode.MonetLight,
        AppThemeMode.MonetDark,
    )
    val labels = listOf(
        stringResource(R.string.theme_mode_system),
        stringResource(R.string.theme_mode_light),
        stringResource(R.string.theme_mode_dark),
        stringResource(R.string.theme_mode_monet_system),
        stringResource(R.string.theme_mode_monet_light),
        stringResource(R.string.theme_mode_monet_dark),
    )
    val selectedIndex = modes.indexOf(selectedMode).takeIf { it >= 0 } ?: 0

    SettingsWindowDropdownPreference(
        items = labels,
        selectedIndex = selectedIndex,
        title = title,
        onSelectedIndexChange = { index ->
            modes.getOrNull(index)?.let(onModeChange)
        },
    )
}

@Composable
internal fun Material3ExpressiveThemeModeDropdown(
    title: String,
    selectedMode: AppThemeMode,
    onModeChange: (AppThemeMode) -> Unit,
) {
    val labels = listOf(
        stringResource(R.string.theme_mode_system),
        stringResource(R.string.theme_mode_light),
        stringResource(R.string.theme_mode_dark),
    )
    val brightnessModes = listOf(AppThemeMode.System, AppThemeMode.Light, AppThemeMode.Dark)
    val selectedIndex = brightnessModes.indexOf(selectedMode.material3ExpressiveBrightness()).coerceAtLeast(0)

    SettingsWindowDropdownPreference(
        items = labels,
        selectedIndex = selectedIndex,
        title = title,
        onSelectedIndexChange = { index ->
            brightnessModes.getOrNull(index)?.let { brightness ->
                onModeChange(selectedMode.withMaterial3ExpressiveBrightness(brightness))
            }
        },
    )
}
