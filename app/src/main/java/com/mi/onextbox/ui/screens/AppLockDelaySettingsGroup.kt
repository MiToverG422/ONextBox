package com.mi.onextbox.ui.screens

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.AppLockDelaySettings
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AppLockDelaySettingsGroup() {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var checked by remember(app) { mutableStateOf(AppLockDelaySettings.enabled(app)) }
    var saving by remember { mutableStateOf(false) }
    DisposableEffect(app) {
        val prefs = LspPreferenceStore.prefs(app)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (!saving && (key == null || key == AppLockDelaySettings.ENABLED)) checked = AppLockDelaySettings.enabled(app)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    SettingsSection(stringResource(R.string.app_lock_group))
    SettingsGroup {
        SettingsToggleRow(title = stringResource(R.string.app_lock_delay_title), summary = stringResource(R.string.app_lock_delay_summary),
            checked = checked, interactionEnabled = !saving,
            onCheckedChange = { enabled ->
                if (!saving) {
                    checked = enabled; saving = true
                    val pending = FeatureSettingWriteQueue.application.enqueue { AppLockDelaySettings.set(app, enabled) }
                    scope.launch {
                        try { pending.await() }
                        catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { Toast.makeText(app, R.string.app_errors_sync_failed, Toast.LENGTH_LONG).show() }
                        finally { checked = AppLockDelaySettings.enabled(app); saving = false }
                    }
                }
            })
    }
}
