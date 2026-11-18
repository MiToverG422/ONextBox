package com.mi.onextbox.ui.settings

import com.mi.onextbox.R
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.foundation.layout.Column
import com.mi.onextbox.ui.screens.FeatureExpandableVisibility
import com.mi.onextbox.diagnostics.DiagnosticRepository
import com.mi.onextbox.diagnostics.SafeModePolicy
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.ModernXposedPreferenceSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun SafeModeSettings() {
    val context = LocalContext.current
    val resources = LocalResources.current
    val preferences = remember { LspPreferenceStore.prefs(context) }
    var manual by remember { mutableStateOf(preferences.getBoolean(SafeModePolicy.MANUAL, false)) }
    var automatic by remember { mutableStateOf(preferences.getBoolean(SafeModePolicy.AUTOMATIC, true)) }
    var busy by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var records by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    fun refresh() = scope.launch {
        if (reading) return@launch
        reading = true
        try {
            records = withContext(Dispatchers.IO) {
                DiagnosticRepository.events(context).filter { it.kind == "safe_mode" }.associate { it.name to it.detail }
            }
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (error: Exception) { Toast.makeText(context, error.message, Toast.LENGTH_LONG).show() }
        finally { reading = false }
    }
    fun change(key: String, value: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { ModernXposedPreferenceSync.setBooleanAndSync(context, key, value) } }
                .onSuccess {
                    manual = preferences.getBoolean(SafeModePolicy.MANUAL, false)
                    automatic = preferences.getBoolean(SafeModePolicy.AUTOMATIC, true)
                    Toast.makeText(context, resources.getString(R.string.safe_saved), Toast.LENGTH_LONG).show()
                }.onFailure { Toast.makeText(context, resources.getString(R.string.safe_save_failed, it.cause?.message ?: it.message), Toast.LENGTH_LONG).show() }
            busy = false
        }
    }
    LaunchedEffect(context, automatic) { if (automatic) refresh() }
    SettingsSection(title = resources.getString(R.string.feature_updates_safe_mode))
    SettingsGroup {
        SettingsToggleRow(title = resources.getString(R.string.feature_updates_safe_mode), summary = resources.getString(R.string.safe_mode_summary),
            checked = manual, onCheckedChange = { change(SafeModePolicy.MANUAL, it) })
        SettingsDivider()
        SettingsToggleRow(title = resources.getString(R.string.safe_auto_title), summary = resources.getString(R.string.safe_auto_summary),
            checked = automatic, onCheckedChange = { change(SafeModePolicy.AUTOMATIC, it) })
        FeatureExpandableVisibility(visible = automatic) {
            Column {
                SettingsDivider()
                SettingsDataRow(resources.getString(R.string.safe_records), resources.getString(R.string.safe_records_summary), refreshing = reading, onClick = { refresh() })
                listOf("com.android.systemui" to resources.getString(R.string.section_lsp), "com.android.phone" to resources.getString(R.string.safe_phone_scope), "com.android.launcher" to resources.getString(R.string.section_system_desktop), "system" to resources.getString(R.string.lsp_scope_system_title)).forEach { (name, title) ->
                    SettingsDivider()
                    val record = records[name] ?: if (name == "com.android.launcher") records.entries.firstOrNull {
                        SafeModePolicy.scope(it.key, false)?.contains("launcher") == true
                    }?.value else null
                    val summary = when {
                        record == null -> resources.getString(R.string.safe_record_missing)
                        record.startsWith("Protection unavailable") -> resources.getString(R.string.safe_record_failed)
                        "blocked=true" in record || record.startsWith("Automatic protection active") -> resources.getString(R.string.safe_record_blocked)
                        record.startsWith("count=") -> resources.getString(R.string.safe_record_counting)
                        else -> resources.getString(R.string.safe_record_ready)
                    }
                    SettingsDataRow(title, summary, onClick = { refresh() })
                }
            }
        }
    }
}
