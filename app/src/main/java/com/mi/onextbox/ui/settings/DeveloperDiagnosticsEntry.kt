package com.mi.onextbox.ui.settings

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.diagnostics.DeveloperDiagnostics
import com.mi.onextbox.diagnostics.DiagnosticActivity
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.ModernXposedPreferenceSync
import com.mi.onextbox.ui.common.CouiConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun DeveloperDiagnosticsEntry() {
    val context = LocalContext.current
    val prefs = remember { DeveloperDiagnostics.prefs(context) }
    var debug by remember { mutableStateOf(prefs.getBoolean("debug", false)) }
    var intercept by remember { mutableStateOf(prefs.getBoolean("intercept", true)) }
    var collect by remember { mutableStateOf(prefs.getBoolean("collect", true)) }
    var crashConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun open(page: String) = context.startActivity(Intent(context, DiagnosticActivity::class.java).putExtra("page", page))
    LaunchedEffect(Unit) { DeveloperDiagnostics.refreshEnvironment(context) }
    SafeModeSettings()
    SettingsSection(title = stringResource(R.string.diagnostics_debug_section))
    SettingsGroup {
        SettingsToggleRow(title = stringResource(R.string.diagnostics_debug), summary = stringResource(R.string.diagnostics_debug_summary),
            checked = debug, onCheckedChange = {
                debug = it; DeveloperDiagnostics.debugEnabled = it
                prefs.edit().putBoolean("debug", it).apply()
                LspPreferenceStore.prefs(context).edit().putBoolean(DeveloperDiagnostics.DEBUG, it).apply()
                ModernXposedPreferenceSync.syncNow(context)
            })
        SettingsDivider()
        SettingsCardRow(stringResource(R.string.diagnostics_cache), "", onClick = { open("cache") }, showArrow = true)
        SettingsDivider()
        SettingsCardRow(stringResource(R.string.diagnostics_hooks), "", onClick = { open("hooks") }, showArrow = true)
        SettingsDivider()
        SettingsCardRow(stringResource(R.string.diagnostics_permissions), "", onClick = { open("permissions") }, showArrow = true)
    }
    SettingsSection(title = "诊断工具")
    SettingsGroup {
        listOf("bundle", "hookdetails", "sync", "snapshots", "performance", "trace").forEachIndexed { index, page ->
            if (index > 0) SettingsDivider()
            SettingsCardRow(com.mi.onextbox.diagnostics.developerToolTitle(page), "",
                onClick = { open(page) }, showArrow = true)
        }
    }
    SettingsSection(title = stringResource(R.string.diagnostics_crash_section))
    SettingsGroup {
        SettingsToggleRow(title = stringResource(R.string.diagnostics_intercept), summary = stringResource(R.string.diagnostics_intercept_summary),
            checked = intercept, onCheckedChange = { intercept = it; prefs.edit().putBoolean("intercept", it).apply() })
        SettingsDivider()
        SettingsToggleRow(title = stringResource(R.string.diagnostics_collect), summary = stringResource(R.string.diagnostics_collect_summary),
            checked = collect, onCheckedChange = { collect = it; prefs.edit().putBoolean("collect", it).apply() })
        SettingsDivider()
        SettingsCardRow(stringResource(R.string.diagnostics_reports), "", onClick = { open("reports") }, showArrow = true)
        SettingsDivider()
        SettingsCardRow(stringResource(R.string.diagnostics_demo), stringResource(R.string.diagnostics_demo_summary), onClick = {
            scope.launch {
                runCatching {
                    val file = withContext(Dispatchers.IO) {
                        DeveloperDiagnostics.save(context, IllegalStateException("ONextBox crash report preview",
                            IllegalArgumentException("Demonstration cause")), Thread.currentThread(), true)
                    }
                    context.startActivity(Intent(context, DiagnosticActivity::class.java).putExtra("page", "report").putExtra("report", file.name))
                }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
            }
        })
        SettingsDivider()
        SettingsCardRow(stringResource(R.string.diagnostics_real_crash), "", onClick = { crashConfirm = true })
    }
    CouiConfirmDialog(show = crashConfirm, title = stringResource(R.string.diagnostics_real_crash),
        summary = stringResource(R.string.diagnostics_real_crash_confirm),
        negativeText = stringResource(android.R.string.cancel), positiveText = stringResource(android.R.string.ok),
        onDismissRequest = { crashConfirm = false }, onPositive = {
            crashConfirm = false
            Handler(Looper.getMainLooper()).post { throw IllegalStateException("ONextBox developer crash test") }
        })
}
