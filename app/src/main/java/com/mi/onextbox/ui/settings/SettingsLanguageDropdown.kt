package com.mi.onextbox.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.mi.onextbox.ui.common.AppLocale

@Composable
fun SettingsLanguageDropdown(
    title: String,
    summary: String,
    selectedLanguageTag: String,
    onLanguageChange: (String) -> Unit,
    hasDividerAbove: Boolean,
    hasDividerBelow: Boolean,
) {
    val context = LocalContext.current
    val languageOptions = AppLocale.options(context)
    val languageTags = languageOptions.map { it.tag }
    val languageLabels = languageOptions.map { it.nativeName }
    val selectedIndex = languageTags
        .indexOf(selectedLanguageTag)
        .takeIf { it >= 0 }
        ?: 0

    SettingsWindowDropdownPreference(
        items = languageLabels,
        selectedIndex = selectedIndex,
        title = title,
        summary = summary.takeIf { it.isNotBlank() },
        onSelectedIndexChange = { index ->
            val tag = languageTags.getOrNull(index) ?: AppLocale.LANGUAGE_SYSTEM
            onLanguageChange(tag)
        },
    )
}
