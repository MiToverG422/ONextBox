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
import com.mi.onextbox.lsp.PhoneManagerSettings
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun PhoneManagerFeaturesPage() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var enabled by remember(appContext) { mutableStateOf(PhoneManagerSettings.isEnabled(appContext)) }
    var applying by remember { mutableStateOf(false) }

    DisposableEffect(appContext, owner) {
        val prefs = LspPreferenceStore.prefs(appContext)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (!applying && (key == null || key == PhoneManagerSettings.ENABLED)) {
                enabled = PhoneManagerSettings.isEnabled(appContext)
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !applying) {
                enabled = PhoneManagerSettings.isEnabled(appContext)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        if (!applying) enabled = PhoneManagerSettings.isEnabled(appContext)
        owner.lifecycle.addObserver(observer)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            owner.lifecycle.removeObserver(observer)
        }
    }

    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.phone_manager_skip_countdown_title),
            summary = stringResource(R.string.phone_manager_skip_countdown_summary),
            checked = enabled,
            hasDividerBelow = true,
            interactionEnabled = !applying,
            onCheckedChange = { checked ->
                if (!applying) {
                    applying = true
                    enabled = checked
                    val pending = FeatureSettingWriteQueue.application.enqueue {
                        PhoneManagerSettings.setEnabled(appContext, checked)
                        check(PhoneManagerSettings.isEnabled(appContext) == checked)
                    }
                    scope.launch {
                        try {
                            pending.await()
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            Toast.makeText(appContext, R.string.phone_manager_save_failed, Toast.LENGTH_LONG).show()
                        } finally {
                            enabled = PhoneManagerSettings.isEnabled(appContext)
                            applying = false
                        }
                    }
                }
            },
        )
        com.mi.onextbox.ui.settings.SettingsDivider()
        PhoneManagerRiskCloudSettingsRow()
    }
}
