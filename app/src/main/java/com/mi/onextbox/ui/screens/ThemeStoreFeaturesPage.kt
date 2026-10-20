package com.mi.onextbox.ui.screens

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
import com.mi.onextbox.lsp.ThemeStoreSettings
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ThemeStoreFeaturesPage() {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var enabled by remember(context) { mutableStateOf(ThemeStoreSettings.enabled(context)) }
    var adsEnabled by remember(context) { mutableStateOf(ThemeStoreSettings.adsEnabled(context)) }
    var savingKey by remember { mutableStateOf<String?>(null) }
    fun refresh() {
        enabled = ThemeStoreSettings.enabled(context)
        adsEnabled = ThemeStoreSettings.adsEnabled(context)
    }
    DisposableEffect(context, owner) {
        val prefs = LspPreferenceStore.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (savingKey == null && (key == null || key in setOf(ThemeStoreSettings.ENABLED, ThemeStoreSettings.ADS))) refresh()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && savingKey == null) refresh()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        owner.lifecycle.addObserver(observer)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            owner.lifecycle.removeObserver(observer)
        }
    }
    fun save(key: String, next: Boolean) {
        if (savingKey != null) return
        if (key == ThemeStoreSettings.ADS) adsEnabled = next else enabled = next
        savingKey = key
        val write = FeatureSettingWriteQueue.application.enqueue {
            if (key == ThemeStoreSettings.ADS) ThemeStoreSettings.setAds(context, next) else ThemeStoreSettings.set(context, next)
        }
        scope.launch {
            try { write.await() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                Toast.makeText(context, R.string.enabled_features_save_failed, Toast.LENGTH_LONG).show()
            } finally {
                refresh()
                savingKey = null
            }
        }
    }
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.theme_store_vip_title),
            summary = stringResource(R.string.theme_store_vip_summary),
            checked = enabled,
            interactionEnabled = savingKey != ThemeStoreSettings.ENABLED,
            hasDividerBelow = true,
            onCheckedChange = { save(ThemeStoreSettings.ENABLED, it) },
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(R.string.theme_store_ads_title),
            summary = stringResource(R.string.theme_store_ads_summary),
            checked = adsEnabled,
            interactionEnabled = savingKey != ThemeStoreSettings.ADS,
            hasDividerAbove = true,
            onCheckedChange = { save(ThemeStoreSettings.ADS, it) },
        )
    }
}
