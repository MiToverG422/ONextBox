package com.mi.onextbox.ui.screens

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.HideScreenshotFloatingSettings
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun HideScreenshotFloatingSettingsRow(hasDividerAbove: Boolean, hasDividerBelow: Boolean) {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(HideScreenshotFloatingSettings.enabled(app)) }
    var saving by remember { mutableStateOf(false) }
    DisposableEffect(app) {
        val prefs = LspPreferenceStore.prefs(app)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (!saving && (key == null || key == HideScreenshotFloatingSettings.KEY)) enabled = HideScreenshotFloatingSettings.enabled(app)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    SettingsToggleRow(title = stringResource(R.string.hide_screenshot_floating_title),
        summary = stringResource(R.string.hide_screenshot_floating_summary),
        checked = enabled, interactionEnabled = !saving, hasDividerAbove = hasDividerAbove, hasDividerBelow = hasDividerBelow,
        onCheckedChange = { next ->
            if (!saving) {
                saving = true
                enabled = next
                val pending = FeatureSettingWriteQueue.application.enqueue { HideScreenshotFloatingSettings.set(app, next) }
                scope.launch {
                    try { pending.await() }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { Toast.makeText(app, R.string.enabled_features_save_failed, Toast.LENGTH_LONG).show() }
                    finally { saving = false; enabled = HideScreenshotFloatingSettings.enabled(app) }
                }
            }
        })
}
