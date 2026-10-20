package com.mi.onextbox.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import com.mi.onextbox.R
import com.mi.onextbox.lsp.FluidCloudMediaRules
import com.mi.onextbox.lsp.FluidCloudMediaSettings
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsLazyGroupItem
import com.mi.onextbox.ui.settings.SettingsPageSurface
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsSwitchPreference
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun FluidCloudMediaBlacklistPage(modifier: Modifier, backdrop: LayerBackdrop?, onBack: () -> Unit) =
    PackageSelectionPage(modifier, backdrop, onBack, R.string.fluid_media_apps_title,
        FluidCloudMediaSettings.PACKAGES, FluidCloudMediaSettings::packages, FluidCloudMediaSettings::setPackages)

@Composable
internal fun PackageSelectionPage(
    modifier: Modifier, backdrop: LayerBackdrop?, onBack: () -> Unit, title: Int,
    packagesKey: String, readPackages: (android.content.Context) -> Set<String>,
    savePackages: (android.content.Context, Set<String>) -> Unit,
    header: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember(context) { mutableStateOf(readPackages(context)) }
    var apps by remember { mutableStateOf(emptyList<MediaBlacklistApp>()) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var movingPackage by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val search = rememberTextFieldState()
    val query = search.text.toString().trim()
    val locale = LocalConfiguration.current.locales[0]
    val listed = remember(apps, selected, query, locale) {
        mediaBlacklistApps(apps, selected, query, locale)
    }
    val manualPackage = query.takeIf {
        FluidCloudMediaRules.isPackageName(it) && it !in selected && apps.none { app -> app.packageName == it }
    }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp

    fun updateSelected(updated: Set<String>) {
        if (updated == selected) return
        listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        selected = updated
    }

    fun change(packageName: String, checked: Boolean) {
        if (saving) return
        val updated = if (checked) selected + packageName else selected - packageName
        if (updated == selected) return
        movingPackage = packageName
        saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { savePackages(context, updated) }
                updateSelected(readPackages(context))
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                Toast.makeText(context, R.string.fluid_media_save_failed, Toast.LENGTH_LONG).show()
            } finally { saving = false }
        }
    }

    DisposableEffect(context) {
        val prefs = LspPreferenceStore.prefs(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == packagesKey) {
                scope.launch { updateSelected(readPackages(context)) }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(context, locale) {
        loading = true
        loadFailed = false
        try {
            apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                manageableApplications(pm).asSequence()
                    .map { info -> MediaBlacklistApp(info.packageName,
                        runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName)) }
                    .toList()
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { loadFailed = true }
        finally { loading = false }
    }

    SettingsPageSurface(title = stringResource(title), modifier = modifier,
        showBack = true, onBack = onBack, blurBackdrop = backdrop, contentScrollable = false) {
        LazyColumn(modifier = Modifier.fillMaxSize().imePadding(), state = listState,
            contentPadding = PaddingValues(bottom = bottom)) {
            item("header") { header() }
            item("search") { FeatureSearchBar(search, hint = stringResource(R.string.fluid_media_search_hint)) }
            item("count") { SettingsSection(title = stringResource(R.string.fluid_media_selected_count, selected.size)) }
            if (loading || loadFailed || (listed.isEmpty() && manualPackage == null)) {
                item("status") {
                    SettingsGroup {
                        SettingsCardRow(title = stringResource(when {
                            loading -> R.string.fluid_media_loading
                            loadFailed -> R.string.fluid_media_load_failed
                            else -> R.string.fluid_media_no_results
                        }), summary = "")
                    }
                }
            }
            manualPackage?.let { name ->
                item("manual:$name") {
                    SettingsGroup {
                        SettingsSwitchPreference(title = stringResource(R.string.fluid_media_add_package),
                            summary = name, checked = false, interactionEnabled = !saving,
                            onCheckedChange = { change(name, it) })
                    }
                }
            }
            itemsIndexed(listed, key = { _, app -> app.packageName }) { index, app ->
                SettingsLazyGroupItem(index = index, count = listed.size,
                    modifier = mediaBlacklistItemAnimation(raised = app.packageName == movingPackage)) {
                    SettingsSwitchPreference(title = app.label, summary = app.packageName,
                        checked = app.packageName in selected, interactionEnabled = !saving,
                        startAction = { MediaBlacklistAppIcon(app.packageName) },
                        onCheckedChange = { change(app.packageName, it) })
                }
            }
        }
    }
}

internal fun LazyItemScope.mediaBlacklistItemAnimation(raised: Boolean): Modifier = Modifier
    .animateItem(
        fadeInSpec = tween(180),
        placementSpec = tween(280, easing = FastOutSlowInEasing),
        fadeOutSpec = tween(140),
    )
    .zIndex(if (raised) 1f else 0f)

@Composable
private fun MediaBlacklistAppIcon(packageName: String) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, context, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    val image = icon
    if (image != null) Image(image, contentDescription = null, modifier = Modifier.size(36.dp))
    else Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(36.dp),
        tint = if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) MaterialTheme.colorScheme.onSurfaceVariant
        else COUITheme.colorScheme.onSurface)
}
