package com.mi.onextbox.ui.settings

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.SystemAdaptationSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun DeveloperHyperOsSupportGroup() {
    val app = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var allowed by remember(app) { mutableStateOf(SystemAdaptationSettings.hyperOsAllowed(app)) }
    var saving by remember { mutableStateOf(false) }
    DisposableEffect(app, owner) {
        val prefs = LspPreferenceStore.prefs(app)
        fun refresh() { if (!saving) allowed = SystemAdaptationSettings.hyperOsAllowed(app) }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == SystemAdaptationSettings.ALLOW_HYPER_OS) refresh()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        owner.lifecycle.addObserver(observer)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            owner.lifecycle.removeObserver(observer)
        }
    }
    SettingsSection(title = stringResource(R.string.settings_section_system_adaptation))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.developer_allow_hyperos3_title),
            summary = "",
            checked = allowed,
            interactionEnabled = !saving,
            onCheckedChange = { next ->
                if (!saving) {
                    saving = true
                    allowed = next
                    val pending = SystemAdaptationWriteQueue.writes.enqueue {
                        SystemAdaptationSettings.setHyperOsAllowed(app, next)
                    }
                    scope.launch {
                        try {
                            pending.await()
                        }
                        catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) {
                            Toast.makeText(app, R.string.setting_system_adaptation_save_failed, Toast.LENGTH_LONG).show()
                        } finally {
                            allowed = SystemAdaptationSettings.hyperOsAllowed(app)
                            saving = false
                        }
                    }
                }
            },
        )
    }
}
