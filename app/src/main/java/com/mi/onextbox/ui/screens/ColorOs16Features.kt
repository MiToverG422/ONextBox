package com.mi.onextbox.ui.screens

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ColorOs16FeatureCategories(mode: FeaturePageMode, onOpen: (FeaturePageMode) -> Unit) {
    FeatureAnimatedGroup {
        ColorOs16FeaturePolicy.categories[mode].orEmpty().forEach { page ->
            row(page) { above, below ->
                SettingsCardRow(title = featurePageTitle(page), summary = "", showArrow = true,
                    onClick = { onOpen(page) }, hasDividerAbove = above, hasDividerBelow = below)
            }
        }
    }
}

@Composable
internal fun ColorOs16Features(mode: FeaturePageMode, onOpen: (FeaturePageMode) -> Unit) {
    if (mode in ColorOs16FeaturePolicy.categories) {
        ColorOs16FeatureCategories(mode, onOpen)
        return
    }
    when (mode) {
        FeaturePageMode.SystemUiIconOrder -> { StatusBarIconOrderPage(); return }
        FeaturePageMode.SystemUiCornerSettings -> { QsTileCornerSettingsPage(); return }
        FeaturePageMode.SystemUiPercentageSettings -> { QsSliderPercentageSettingsPage(); return }
        FeaturePageMode.SystemUiSideVolumePercentageSettings -> { QsSliderPercentageSettingsPage(sideVolume = true); return }
        FeaturePageMode.SystemUiNavigationBar -> { NavigationBarFeaturesPage(); return }
        FeaturePageMode.CorePatch -> { CorePatchPage(); return }
        FeaturePageMode.Installer -> { InstallerPage(); return }
        // Every current option on these two pages is present in the C16 registry.
        // Keep the native selector/confirmation/tool actions as well as their switches.
        FeaturePageMode.MobileNetwork -> if (colorOs16PageHasAll(mode, colorOs16MobileNetworkTitles)) {
            MobileNetworkFeaturesPage(); return
        }
        FeaturePageMode.SecurityPermission -> if (colorOs16PageHasAll(mode, colorOs16SecurityPermissionTitles)) {
            SecurityPermissionFeaturesPage(); return
        }
        FeaturePageMode.SettingsRealme -> {
            SettingsRealmePage(availableTitles = ColorOs16FeatureRegistry.titlesForPage(mode)); return
        }
        else -> Unit
    }
    val advancedTitles = ColorOs16AdvancedFeatureGroups(mode, onOpen)
    ColorOs16CatalogRows(mode, advancedTitles)
}

/** The same catalog and dependency rules drive C16 switches and the enabled-feature inventory. */
internal fun colorOs16CatalogEntries(page: FeaturePageMode, advancedTitles: Set<Int>): List<EnabledFeatureCatalog.Entry> =
    enabledFeatureEntries(com.mi.onextbox.lsp.SystemAdaptationVersion.ColorOs16)
        .filter { it.page == page && it.title !in advancedTitles }

@Composable
private fun ColorOs16CatalogRows(mode: FeaturePageMode, advancedTitles: Set<Int>) {
    val app = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val actions = remember { EnabledFeatureActions() }
    val entries = remember(mode, advancedTitles) {
        colorOs16CatalogEntries(mode, advancedTitles).filter { actions.supports(it.title) }
    }
    var revision by remember { mutableIntStateOf(0) }
    var configured by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var ready by remember { mutableStateOf(false) }
    var savingTitle by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(app, owner) {
        val preferences = LspPreferenceStore.prefs(app)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
            owner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(app, revision) {
        configured = withContext(Dispatchers.IO) { EnabledFeatureCatalog.configured(app) }
        ready = true
    }
    val visibleEntries = if (ready) entries.filter { EnabledFeatureCatalog.dependenciesActive(it.title, configured) } else emptyList()
    if (visibleEntries.isEmpty()) return
    FeatureAnimatedGroup {
        visibleEntries.forEach { entry ->
            row(entry.title) { above, below ->
                val description = FeatureSearchIndex.entries.firstOrNull { it.title == entry.title && it.page == mode }?.description ?: 0
                SettingsToggleRow(title = stringResource(entry.title),
                    summary = if (description == 0 || entry.title == R.string.feature_assistant_native_combined_title) "" else stringResource(description),
                    checked = EnabledFeatureCatalog.isActive(entry.title, configured),
                    interactionEnabled = savingTitle == null,
                    onCheckedChange = { next ->
                        if (savingTitle == null) {
                            savingTitle = entry.title
                            val pending = actions.enqueue(app, entry, next)
                            scope.launch {
                                try { pending.await() }
                                catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { Toast.makeText(app, R.string.enabled_features_save_failed, Toast.LENGTH_LONG).show() }
                                finally { savingTitle = null; revision++ }
                            }
                        }
                    }, hasDividerAbove = above, hasDividerBelow = below)
            }
        }
    }
}

internal data class ColorOs16Setting<T>(val value: T, val saving: Boolean, val change: (T) -> Unit)

/** Advanced controls retain their real value and use the application write queue. */
@Composable
internal fun <T> rememberColorOs16Setting(key: String, read: (Context) -> T, write: (Context, T) -> Unit): ColorOs16Setting<T> {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val currentRead by rememberUpdatedState(read)
    var value by remember(app, key) { mutableStateOf(read(app)) }
    var desired by remember(app, key) { mutableStateOf(value) }
    var saving by remember(app, key) { mutableStateOf(false) }
    DisposableEffect(app, key) {
        val preferences = LspPreferenceStore.prefs(app)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> if (!saving) value = currentRead(app) }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return ColorOs16Setting(value, saving) { next ->
        // Sliders can publish a newer request while a previous mirror sync is running.
        // Keep that latest value on screen and coalesce intermediate requests.
        value = next
        desired = next
        if (!saving) {
            saving = true
            scope.launch {
                try {
                    do {
                        val requested = desired
                        val pending = FeatureSettingWriteQueue.application.enqueue { write(app, requested) }
                        pending.await()
                    } while (desired != requested)
                }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { Toast.makeText(app, R.string.enabled_features_save_failed, Toast.LENGTH_LONG).show() }
                finally {
                    value = currentRead(app)
                    desired = value
                    saving = false
                }
            }
        }
    }
}
