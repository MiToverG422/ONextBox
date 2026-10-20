package com.mi.onextbox.ui.settings

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
import com.mi.onextbox.lsp.SystemAdaptationSettings
import com.mi.onextbox.lsp.SystemAdaptationVersion
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SettingsSystemAdaptationGroup() {
    val app = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var selected by remember(app) { mutableStateOf(SystemAdaptationSettings.selectedVersion(app)) }
    var allowHyperOs by remember(app) { mutableStateOf(SystemAdaptationSettings.hyperOsAllowed(app)) }
    var saving by remember { mutableStateOf(false) }

    DisposableEffect(app, owner) {
        val prefs = LspPreferenceStore.prefs(app)
        fun refresh() {
            allowHyperOs = SystemAdaptationSettings.hyperOsAllowed(app)
            if (!saving) selected = SystemAdaptationSettings.selectedVersion(app)
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == SystemAdaptationSettings.KEY || key == SystemAdaptationSettings.ALLOW_HYPER_OS) refresh()
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

    fun save(version: SystemAdaptationVersion) {
        if (saving || !version.selectable || version == selected) return
        saving = true
        selected = version
        val pending = SystemAdaptationWriteQueue.writes.enqueue {
            SystemAdaptationSettings.setVersion(app, version)
        }
        scope.launch {
            try {
                pending.await()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                Toast.makeText(app, R.string.setting_system_adaptation_save_failed, Toast.LENGTH_LONG).show()
            } finally {
                selected = SystemAdaptationSettings.selectedVersion(app)
                saving = false
            }
        }
    }

    SettingsSection(title = stringResource(R.string.settings_section_system_adaptation))
    SettingsGroup {
        SettingsWindowDropdownPreference(
            title = stringResource(R.string.setting_system_adaptation_version),
            interactionEnabled = !saving,
            entry = DropdownEntry(
                items = SystemAdaptationVersion.visibleVersions(allowHyperOs).map { version ->
                    DropdownItem(
                        text = version.label,
                        selected = version == selected,
                        enabled = version.selectable,
                        onClick = { save(version) },
                    )
                },
            ),
        )
    }
}
