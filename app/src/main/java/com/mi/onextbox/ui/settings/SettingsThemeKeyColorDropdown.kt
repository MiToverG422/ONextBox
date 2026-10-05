package com.mi.onextbox.ui.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R

private data class ThemeKeyColorOption(
    val argb: Long?,
    @StringRes val labelRes: Int,
)

private val themeKeyColorOptions = listOf(
    ThemeKeyColorOption(null, R.string.theme_key_color_default),
    ThemeKeyColorOption(0xFF3482FF, R.string.theme_key_color_blue),
    ThemeKeyColorOption(0xFF36D167, R.string.theme_key_color_green),
    ThemeKeyColorOption(0xFF7C4DFF, R.string.theme_key_color_purple),
    ThemeKeyColorOption(0xFFFFB21D, R.string.theme_key_color_yellow),
    ThemeKeyColorOption(0xFFFF5722, R.string.theme_key_color_orange),
    ThemeKeyColorOption(0xFFE91E63, R.string.theme_key_color_pink),
    ThemeKeyColorOption(0xFF00BCD4, R.string.theme_key_color_teal),
)

@Composable
fun SettingsThemeKeyColorDropdown(
    title: String,
    selectedKeyColor: Long?,
    onKeyColorChange: (Long?) -> Unit,
    hasDividerAbove: Boolean = false,
    hasDividerBelow: Boolean = false,
) {
    val labels = themeKeyColorOptions.map { stringResource(it.labelRes) }
    val selectedIndex = themeKeyColorOptions
        .indexOfFirst { it.argb == selectedKeyColor }
        .takeIf { it >= 0 }
        ?: 0

    SettingsWindowDropdownPreference(
        items = labels,
        selectedIndex = selectedIndex,
        title = title,
        onSelectedIndexChange = { index ->
            onKeyColorChange(themeKeyColorOptions.getOrNull(index)?.argb)
        },
    )
}

@Composable
fun SettingsThemeTextDropdown(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    hasDividerAbove: Boolean = false,
    hasDividerBelow: Boolean = false,
) {
    SettingsWindowDropdownPreference(
        items = labels,
        selectedIndex = selectedIndex.coerceIn(labels.indices),
        title = title,
        onSelectedIndexChange = onSelectedIndexChange,
    )
}
