package com.mi.onextbox.ui.screens

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.SettingsHomeCleanupSettings
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.Material3ExpressiveSegmentPosition
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SettingsHomeCleanupGroup() {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var account by remember(context) { mutableStateOf(SettingsHomeCleanupSettings.enabled(context, SettingsHomeCleanupSettings.ACCOUNT)) }
    var update by remember(context) { mutableStateOf(SettingsHomeCleanupSettings.enabled(context, SettingsHomeCleanupSettings.UPDATE)) }
    var saving by remember { mutableStateOf(false) }
    fun refresh() {
        if (saving) return
        account = SettingsHomeCleanupSettings.enabled(context, SettingsHomeCleanupSettings.ACCOUNT)
        update = SettingsHomeCleanupSettings.enabled(context, SettingsHomeCleanupSettings.UPDATE)
    }
    DisposableEffect(context, owner) {
        val prefs = LspPreferenceStore.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in SettingsHomeCleanupSettings.keys) refresh()
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        owner.lifecycle.addObserver(observer)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            owner.lifecycle.removeObserver(observer)
        }
    }
    fun save(key: String, enabled: Boolean) {
        if (saving) return
        saving = true
        val pending = FeatureSettingWriteQueue.application.enqueue { SettingsHomeCleanupSettings.set(context, key, enabled) }
        scope.launch {
            try { pending.await() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { Toast.makeText(context, R.string.app_errors_sync_failed, Toast.LENGTH_LONG).show() }
            finally { saving = false; refresh() }
        }
    }
    SettingsSection(title = stringResource(R.string.settings_home_cleanup_group))
    SettingsGroup {
        Material3ExpressiveSegmentPosition(index = 0, count = 2) {
            SettingsToggleRow(
                title = stringResource(R.string.settings_home_hide_account_title),
                summary = stringResource(R.string.settings_home_hide_account_summary),
                checked = account, interactionEnabled = !saving,
                onCheckedChange = { save(SettingsHomeCleanupSettings.ACCOUNT, it) }, hasDividerBelow = true,
            )
        }
        SettingsDivider()
        Material3ExpressiveSegmentPosition(index = 1, count = 2) {
            SettingsToggleRow(
                title = stringResource(R.string.settings_home_hide_update_title),
                summary = stringResource(R.string.settings_home_hide_update_summary),
                checked = update, interactionEnabled = !saving,
                onCheckedChange = { save(SettingsHomeCleanupSettings.UPDATE, it) }, hasDividerAbove = true,
            )
        }
    }
}
