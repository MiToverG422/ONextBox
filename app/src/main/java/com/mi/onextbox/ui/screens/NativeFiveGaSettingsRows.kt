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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.NativeFiveGaBridge
import com.mi.onextbox.lsp.NativeFiveGaSettings
import com.mi.onextbox.lsp.NativeFiveGaSnapshot
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun NativeFiveGaSettingsRows(hasDividerAbove: Boolean = true) {
    val app = LocalContext.current.applicationContext
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(NativeFiveGaSettings.enabled(app)) }
    var saving by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<NativeFiveGaSnapshot?>(null) }
    DisposableEffect(app) {
        val prefs = LspPreferenceStore.prefs(app)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == NativeFiveGaSettings.ENABLED) {
                scope.launch { if (!saving) enabled = NativeFiveGaSettings.enabled(app) }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        val subscription = NativeFiveGaBridge.subscribe(app) { state -> scope.launch { snapshot = state } }
        onDispose {
            subscription.close()
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    FeatureAnimatedRows(hasDividerAbove = hasDividerAbove) {
    row(NativeFiveGaSettings.ENABLED) { above, below -> SettingsToggleRow(
        title = stringResource(R.string.native_5ga_title),
        summary = stringResource(R.string.native_5ga_summary),
        checked = enabled,
        interactionEnabled = !saving,
        hasDividerAbove = above,
        hasDividerBelow = below,
        onCheckedChange = { next ->
            if (!saving) {
                saving = true
                enabled = next
                val pending = FeatureSettingWriteQueue.application.enqueue { NativeFiveGaSettings.setEnabled(app, next) }
                scope.launch {
                    try { pending.await() }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { Toast.makeText(app, R.string.enabled_features_save_failed, Toast.LENGTH_LONG).show() }
                    finally { saving = false; enabled = NativeFiveGaSettings.enabled(app) }
                }
            }
        },
    ) }
    row("native-5ga-status", visible = enabled) { above, below ->
        val current = snapshot
        val status = if (current == null || current.slots.isEmpty()) {
            stringResource(nativeFiveGaStatusResource(current?.status ?: "waiting_scope"))
        } else {
            current.slots.sortedBy { it.phoneId }.joinToString("\n") { slot ->
                val code = if (!slot.qualified && slot.reason in setOf("qualified", "holding")) "waiting_native" else slot.reason
                resources.getString(R.string.native_5ga_slot_status, slot.phoneId + 1,
                    resources.getString(nativeFiveGaStatusResource(code)))
            }
        }
        SettingsCardRow(title = stringResource(R.string.native_5ga_status_title), summary = status,
            hasDividerAbove = above, hasDividerBelow = below)
    }
    }
}

internal fun nativeFiveGaStatusResource(code: String): Int = when (code) {
    "disabled" -> R.string.native_5ga_status_disabled
    "unsupported", "unsupported_profile", "unsupported_interface" -> R.string.native_5ga_status_unsupported
    "waiting_sim" -> R.string.native_5ga_status_waiting_sim
    "waiting_config" -> R.string.native_5ga_status_waiting_config
    "holding" -> R.string.native_5ga_status_holding
    "qualified" -> R.string.native_5ga_status_qualified
    "restart_required" -> R.string.native_5ga_status_restart_required
    "waiting_scope" -> R.string.native_5ga_status_waiting_scope
    else -> R.string.native_5ga_status_waiting_native
}
