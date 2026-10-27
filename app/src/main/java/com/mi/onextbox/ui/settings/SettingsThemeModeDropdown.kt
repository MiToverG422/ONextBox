package com.mi.onextbox.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.AppThemeMode
import com.mi.onextbox.ui.common.isMonet

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
