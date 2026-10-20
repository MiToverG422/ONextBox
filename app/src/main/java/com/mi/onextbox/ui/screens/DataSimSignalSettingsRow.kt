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
import com.mi.onextbox.lsp.DataSimSignalSettings
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun DataSimSignalSettingsRow() {
    val context = LocalContext.current
    val application = context.applicationContext
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(DataSimSignalSettings.enabled(context)) }
    var independentOnly by remember { mutableStateOf(DataSimSignalSettings.independentOnly(context)) }
    var savingKey by remember { mutableStateOf<String?>(null) }
    fun refresh() {
        if (savingKey == null) {
            enabled = DataSimSignalSettings.enabled(application)
            independentOnly = DataSimSignalSettings.independentOnly(application)
        }
    }
    DisposableEffect(owner, context) {
        val prefs = com.mi.onextbox.lsp.LspPreferenceStore.prefs(application)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == DataSimSignalSettings.KEY || key == DataSimSignalSettings.INDEPENDENT_ONLY) refresh()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        owner.lifecycle.addObserver(observer)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener); owner.lifecycle.removeObserver(observer) }
    }
    fun save(key: String, next: Boolean) {
        if (savingKey != null) return
        savingKey = key
        if (key == DataSimSignalSettings.KEY) enabled = next else independentOnly = next
        val write = FeatureSettingWriteQueue.application.enqueue {
            if (key == DataSimSignalSettings.KEY) DataSimSignalSettings.set(application, next)
            else DataSimSignalSettings.setIndependentOnly(application, next)
        }
        scope.launch {
            try { write.await() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                Toast.makeText(context, R.string.enabled_features_save_failed, Toast.LENGTH_LONG).show()
            } finally {
                savingKey = null
                refresh()
            }
        }
    }
    FeatureAnimatedRows(hasDividerBelow = true, animateChanges = true) {
        row("data-sim") { above, below ->
            SettingsToggleRow(
                title = stringResource(R.string.feature_only_data_sim_signal_title),
                summary = stringResource(R.string.feature_only_data_sim_signal_summary),
                checked = enabled, interactionEnabled = savingKey != DataSimSignalSettings.KEY,
                hasDividerAbove = above, hasDividerBelow = below,
                onCheckedChange = { save(DataSimSignalSettings.KEY, it) },
            )
        }
        row("independent-only", visible = enabled) { above, below ->
            SettingsToggleRow(
                title = stringResource(R.string.feature_only_data_sim_independent_title), summary = "",
                checked = independentOnly, interactionEnabled = savingKey != DataSimSignalSettings.INDEPENDENT_ONLY,
                hasDividerAbove = above, hasDividerBelow = below,
                onCheckedChange = { save(DataSimSignalSettings.INDEPENDENT_ONLY, it) },
            )
        }
    }
}
