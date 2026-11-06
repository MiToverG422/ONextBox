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
import com.mi.onextbox.lsp.StatusBarKeepSettings
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun StatusBarKeepSettingsRow(
    hasDividerAbove: Boolean = false,
    hasDividerBelow: Boolean = false,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var enabled by remember(context) { mutableStateOf(StatusBarKeepSettings.isEnabled(context)) }
    var applying by remember { mutableStateOf(false) }
    DisposableEffect(context, owner) {
        fun refresh() { if (!applying) enabled = StatusBarKeepSettings.isEnabled(context) }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        val preferences = LspPreferenceStore.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == StatusBarKeepSettings.ENABLED) refresh()
        }
        owner.lifecycle.addObserver(observer)
        preferences.registerOnSharedPreferenceChangeListener(listener)
        refresh()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    SettingsToggleRow(
        title = stringResource(R.string.status_bar_keep_title),
        summary = stringResource(R.string.status_bar_keep_summary),
        checked = enabled,
        onCheckedChange = { checked ->
            if (!applying) {
                applying = true
                enabled = checked
                val appContext = context.applicationContext
                val pending = FeatureSettingWriteQueue.application.enqueue {
                    StatusBarKeepSettings.setEnabled(appContext, checked)
                    check(StatusBarKeepSettings.isEnabled(appContext) == checked)
                }
                scope.launch {
                    try { pending.await() }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) {
                        Toast.makeText(appContext, R.string.enabled_features_save_failed, Toast.LENGTH_LONG).show()
                    } finally {
                        enabled = StatusBarKeepSettings.isEnabled(appContext)
                        applying = false
                    }
                }
            }
        },
        hasDividerAbove = hasDividerAbove,
        hasDividerBelow = hasDividerBelow,
    )
}
