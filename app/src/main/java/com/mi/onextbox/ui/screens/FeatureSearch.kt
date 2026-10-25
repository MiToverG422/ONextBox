package com.mi.onextbox.ui.screens

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.ColorOsSearchSurface
import com.mi.onextbox.ui.common.rememberSystemAdaptationVersion
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsListGroup
import com.mi.onextbox.ui.settings.SettingsSection
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun FeatureSearchBar(
    state: TextFieldState,
    hint: String = stringResource(R.string.feature_search_hint),
) {
    val foreground = COUITheme.colorScheme.onSurface
    val hintColor = COUITheme.colorScheme.onSurface.copy(alpha = .55f)
    val background = COUITheme.colorScheme.surfaceContainerHigh
    val accent = COUITheme.colorScheme.primary
    val keyboard = LocalSoftwareKeyboardController.current
    val inputFocus = remember { FocusRequester() }
    val capsuleInteraction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    val empty = state.text.isEmpty()
    val inputStyle = TextStyle(
        color = foreground,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

    // Search capsule content.
    val searchContent: @Composable BoxScope.() -> Unit = {
        Row(
            modifier = Modifier.fillMaxSize().then(Modifier.clickable(
                interactionSource = capsuleInteraction,
                indication = null,
            ) {
                // Avoid restarting the input session on repeated taps.
                if (!focused) inputFocus.requestFocus()
                else if (!imeVisible) keyboard?.show()
            }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier.size(36.dp), contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = hintColor, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(4.dp))
            BasicTextField(
                state = state,
                modifier = Modifier.weight(1f).focusRequester(inputFocus).onFocusChanged {
                    focused = it.isFocused
                },
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = inputStyle,
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                onKeyboardAction = { keyboard?.hide() },
                decorator = { field ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        // Share the placeholder and editor baseline.
                        if (empty) Text(hint, style = inputStyle.copy(color = hintColor), maxLines = 1)
                        field()
                    }
                },
            )
            if (!empty) {
                IconButton(onClick = { state.clearText() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.feature_search_clear), tint = hintColor, modifier = Modifier.size(20.dp))
                }
            } else Spacer(Modifier.width(16.dp))
        }
    }
    val surfaceModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        .height(40.dp)
    ColorOsSearchSurface(surfaceModifier, background, focused, editorEmpty = empty, content = searchContent)
}

@Composable
internal fun FeatureSearchResults(query: String, onOpen: (FeaturePageMode) -> Unit) {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val version = rememberSystemAdaptationVersion()
    val results = remember(query, configuration, resources, version) {
        featureSearchEntries(version).map { entry ->
            entry to featureSearchScore(
                query, resources.getString(entry.title),
                if (entry.description == 0) "" else resources.getString(entry.description),
            )
        }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
    }
    SettingsSection(stringResource(R.string.feature_search_results, results.size))
    if (results.isEmpty()) {
        SettingsGroup {
            SettingsCardRow(
                title = stringResource(R.string.feature_search_empty),
                summary = stringResource(R.string.feature_search_empty_summary),
            )
        }
    } else {
        SettingsListGroup(results, key = { it.title }) { index, entry ->
            run {SettingsCardRow(
                    title = stringResource(entry.title),
                    summary = featurePageTitle(entry.page),
                    showArrow = true,
                    onClick = {
                        focus.clearFocus()
                        keyboard?.hide()
                        onOpen(entry.page)
                    },
                    hasDividerAbove = index > 0,
                    hasDividerBelow = index < results.lastIndex,
                )}
        }
    }
}
