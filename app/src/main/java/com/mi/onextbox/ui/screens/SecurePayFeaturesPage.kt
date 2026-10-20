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
import com.mi.onextbox.lsp.SecurePaySettings
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SecurePayFeaturesPage() {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var enabled by remember(context) { mutableStateOf(SecurePaySettings.enabled(context)) }
    var saving by remember { mutableStateOf(false) }
    DisposableEffect(context, owner) {
        val prefs = LspPreferenceStore.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (!saving && (key == null || key == SecurePaySettings.HIDE_RISK_CLOUD))
                enabled = SecurePaySettings.enabled(context)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (!saving && event == Lifecycle.Event.ON_RESUME) enabled = SecurePaySettings.enabled(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        owner.lifecycle.addObserver(observer)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            owner.lifecycle.removeObserver(observer)
        }
    }
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.secure_pay_hide_risk_cloud_title),
            summary = stringResource(R.string.secure_pay_hide_risk_cloud_summary),
            checked = enabled,
            interactionEnabled = !saving,
            onCheckedChange = { next ->
                if (!saving) {
                    saving = true
                    enabled = next
                    val write = FeatureSettingWriteQueue.application.enqueue { SecurePaySettings.setEnabled(context, next) }
                    scope.launch {
                        try {
                            write.await()
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            Toast.makeText(context, R.string.enabled_features_save_failed, Toast.LENGTH_LONG).show()
                        } finally {
                            enabled = SecurePaySettings.enabled(context)
                            saving = false
                        }
                    }
                }
            },
        )
    }
}
