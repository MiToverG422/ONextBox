package com.mi.onextbox.ui.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.AppThemeMode

@Composable
fun SettingsThemeModeDropdown(
    title: String,
    selectedMode: AppThemeMode,
    onModeChange: (AppThemeMode) -> Unit,
    hasDividerAbove: Boolean = false,
    hasDividerBelow: Boolean = false,
) {
    val modes = AppThemeMode.entries
    SettingsWindowDropdownPreference(
        items = modes.map { stringResource(it.labelRes) },
        selectedIndex = selectedMode.ordinal,
        title = title,
        onSelectedIndexChange = { index ->
            modes.getOrNull(index)?.let(onModeChange)
        },
    )
}

@get:StringRes
internal val AppThemeMode.labelRes: Int
    get() = when (this) {
        AppThemeMode.System -> R.string.theme_mode_system
        AppThemeMode.Light -> R.string.theme_mode_light
        AppThemeMode.Dark -> R.string.theme_mode_dark
        AppThemeMode.MonetSystem -> R.string.theme_mode_monet_system
        AppThemeMode.MonetLight -> R.string.theme_mode_monet_light
        AppThemeMode.MonetDark -> R.string.theme_mode_monet_dark
    }
