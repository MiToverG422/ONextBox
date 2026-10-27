package com.mi.onextbox.ui.screens

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.CorePatchSettings
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.ui.common.rememberSystemAdaptationVersion
import com.mi.onextbox.ui.settings.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun CorePatchPage() {
    val app = LocalContext.current.applicationContext
    val adaptation = rememberSystemAdaptationVersion()
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    DisposableEffect(app) {
        val prefs = LspPreferenceStore.prefs(app)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun save(option: CorePatchSettings.Option, enabled: Boolean) {
        if (saving) return
        saving = true
        val write = FeatureSettingWriteQueue.application.enqueue { CorePatchSettings.set(app, option, enabled) }
        scope.launch {
            try { write.await() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { Toast.makeText(app, R.string.enabled_features_save_failed, Toast.LENGTH_LONG).show() }
            finally { saving = false; revision++ }
        }
    }
    SettingsSection(title = "CorePatch N")
    FeatureAnimatedGroup {
        CorePatchSettings.availableOptions(adaptation).forEach { option ->
            row(option.key) { above, below ->
                val enabled = remember(revision, option) { CorePatchSettings.enabled(app, option) }
                SettingsToggleRow(title = stringResource(option.title), summary = stringResource(option.summary),
                    checked = enabled, interactionEnabled = !saving, hasDividerAbove = above, hasDividerBelow = below,
                    onCheckedChange = { next -> save(option, next) })
            }
        }
    }
}
