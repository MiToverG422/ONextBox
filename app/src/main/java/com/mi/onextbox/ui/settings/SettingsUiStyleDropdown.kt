package com.mi.onextbox.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.AppUiStyle

@Composable
fun SettingsUiStyleDropdown(
    selectedStyle: AppUiStyle,
    onStyleChange: (AppUiStyle) -> Unit,
) {
    val styles = AppUiStyle.entries
    val labels = listOf(
        stringResource(R.string.ui_style_coloros),
        stringResource(R.string.ui_style_material3_expressive),
    )
    SettingsWindowDropdownPreference(
        items = labels,
        selectedIndex = styles.indexOf(selectedStyle).coerceAtLeast(0),
        title = stringResource(R.string.setting_ui_style),
        onSelectedIndexChange = { index -> styles.getOrNull(index)?.let(onStyleChange) },
    )
}
