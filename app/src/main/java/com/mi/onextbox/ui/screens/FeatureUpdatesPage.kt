package com.mi.onextbox.ui.screens

import android.content.SharedPreferences
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mi.onextbox.BuildConfig
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.ColorOsTopBarButton
import com.mi.onextbox.ui.common.rememberSystemAdaptationVersion
import com.mi.onextbox.ui.settings.AppUpdatePreferences
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsListGroup
import com.mi.onextbox.ui.settings.SettingsPageSurface
import com.mi.onextbox.ui.settings.SettingsSection
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Update
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun FeatureUpdatesAction(onClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { AppUpdatePreferences.preferences(context) }
    val edition = FeatureUpdatesCatalog.edition(BuildConfig.VERSION_NAME)
    var unread by remember(prefs, edition) { mutableStateOf(!AppUpdatePreferences.hasSeenFeatureUpdates(context, edition)) }
    DisposableEffect(prefs, edition) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            unread = !AppUpdatePreferences.hasSeenFeatureUpdates(context, edition)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    FeatureUpdatesButton(unread, onClick)
}

@Composable
internal fun FeatureUpdatesButton(unread: Boolean, onClick: () -> Unit) {
    ColorOsTopBarButton(onClick = onClick, modifier = Modifier.testTag("featureUpdatesAction")) {
        Icon(COUIIcons.Update, stringResource(R.string.feature_updates_title),
            tint = COUITheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
        if (unread) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 7.dp)
                .size(5.dp).background(COUITheme.colorScheme.primary, CircleShape)
                .testTag("featureUpdatesUnread"))
        }
    }
}

@Composable
internal fun FeatureUpdatesPage(
    modifier: Modifier,
    bottomPadding: Dp,
    blurBackdrop: LayerBackdrop?,
    transitionScrollState: ScrollState?,
    onBack: () -> Unit,
    onOpen: (FeaturePageMode) -> Unit,
) {
    val context = LocalContext.current
    val version = rememberSystemAdaptationVersion()
    val items = remember(version) { FeatureUpdatesCatalog.items(version) }
    val spotlight = LocalFeatureSpotlight.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        AppUpdatePreferences.markFeatureUpdatesSeen(context, FeatureUpdatesCatalog.edition(BuildConfig.VERSION_NAME))
    }
    SettingsPageSurface(
        title = stringResource(R.string.feature_updates_title),
        modifier = modifier.fillMaxSize(),
        showBack = true,
        onBack = onBack,
        bottomContentPadding = bottomPadding,
        blurBackdrop = blurBackdrop,
        externalScrollState = transitionScrollState,
    ) {
        FeatureUpdatesContent(items) { item ->
            focus.clearFocus()
            keyboard?.hide()
            spotlight?.show(item.destination)
            onOpen(item.destination.page)
        }
    }
}

@Composable
internal fun ColumnScope.FeatureUpdatesContent(
    items: List<FeatureUpdatesCatalog.Item>,
    onOpen: (FeatureUpdatesCatalog.Item) -> Unit,
) {
    val search = rememberTextFieldState()
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val query = search.text.toString()
    val filtered = remember(items, query, configuration, resources) {
        if (query.isBlank()) items else items.filter {
            featureSearchScore(query, resources.getString(it.change.title),
                if (it.destination.description == 0) "" else resources.getString(it.destination.description)) > 0
        }
    }
    SettingsGroup {
        SettingsCardRow(
            title = stringResource(R.string.feature_updates_version, BuildConfig.VERSION_NAME),
            summary = stringResource(R.string.feature_updates_counts,
                items.count { it.change.kind == FeatureUpdatesCatalog.Kind.Added },
                items.count { it.change.kind == FeatureUpdatesCatalog.Kind.Changed }),
        )
    }
    FeatureSearchBar(search)
    if (filtered.isEmpty()) {
        SettingsGroup {
            SettingsCardRow(stringResource(R.string.feature_updates_empty), "")
        }
    }
    FeatureUpdatesCatalog.Kind.entries.forEach { kind ->
        val group = filtered.filter { it.change.kind == kind }
        if (group.isNotEmpty()) {
            SettingsSection(stringResource(if (kind == FeatureUpdatesCatalog.Kind.Added)
                R.string.feature_updates_added else R.string.feature_updates_changed, group.size))
            SettingsListGroup(group, key = { it.change.title }) { index, item ->
                val location = featurePageTitle(item.destination.page)
                SettingsCardRow(
                    title = stringResource(item.change.title),
                    summary = if (item.change.note == 0) location
                        else "$location · ${stringResource(item.change.note)}",
                    showArrow = true,
                    onClick = { onOpen(item) },
                    hasDividerAbove = index > 0,
                    hasDividerBelow = index < group.lastIndex,
                )
            }
        }
    }
}
