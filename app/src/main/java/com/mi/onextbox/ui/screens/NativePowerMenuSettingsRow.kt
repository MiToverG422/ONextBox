package com.mi.onextbox.ui.screens

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun NativePowerMenuSettingsRow() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(LspConfig.isSystemUiNativePowerMenuEnabled(context)) }
    var saving by remember { mutableStateOf(false) }
    fun refresh() {
        enabled = LspConfig.isSystemUiNativePowerMenuEnabled(context)
    }
    fun persist(write: () -> Unit) {
        if (saving) return
        saving = true
        val pending = FeatureSettingWriteQueue.application.enqueue(write)
        scope.launch {
            try { pending.await() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { Toast.makeText(context, R.string.enabled_features_save_failed, Toast.LENGTH_LONG).show() }
            finally { saving = false; refresh() }
        }
    }
    DisposableEffect(context, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME && !saving) refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    SettingsToggleRow(
        title = stringResource(R.string.feature_native_power_menu_title),
        summary = stringResource(R.string.feature_native_power_menu_summary),
        checked = enabled, interactionEnabled = !saving,
        onCheckedChange = { checked -> enabled = checked; persist { LspConfig.setSystemUiNativePowerMenuEnabled(context, checked) } },
        hasDividerAbove = true, hasDividerBelow = true,
    )
}
