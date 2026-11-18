package com.mi.onextbox.diagnostics

import com.mi.onextbox.ui.common.AnimatedDataText
import android.content.Context
import android.widget.Toast
import com.mi.onextbox.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.ModernXposedPreferenceSync
import com.mi.onextbox.ui.common.CouiConfirmDialog
import com.mi.onextbox.ui.common.readCachedRootAccessInfo
import com.mi.onextbox.ui.common.RootAccessState
import com.mi.onextbox.ui.settings.*
import com.mi.onextbox.ui.screens.FeatureExpandableVisibility
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val developerToolPages = setOf("bundle", "hookdetails", "sync", "snapshots", "snapshot", "performance", "trace")
internal fun developerToolTitle(context: Context, page: String) = when (page) {
    "bundle" -> context.getString(R.string.diag_tool_bundle)
    "hookdetails" -> context.getString(R.string.diag_tool_hook_details)
    "sync" -> context.getString(R.string.diag_tool_sync)
    "snapshots", "snapshot" -> context.getString(R.string.diag_tool_snapshots)
    "performance" -> context.getString(R.string.diag_tool_performance)
    else -> context.getString(R.string.diag_tool_trace)
}

@Composable private fun DiagnosticText(text: String) {
    SelectionContainer { AnimatedDataText(text, modifier = Modifier.padding(16.dp), fontSize = 13.sp,
        lineHeight = 19.sp, color = COUITheme.colorScheme.onSurface) }
}

@Composable internal fun DeveloperToolsContent(page: String, detail: String?, navigate: (String) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var events by remember { mutableStateOf<List<DiagnosticEvent>>(emptyList()) }
    var featureRows by remember { mutableStateOf<List<FeatureDiagnosticRow>>(emptyList()) }
    var files by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var process by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var minutes by remember { mutableIntStateOf(5) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var tags by remember { mutableStateOf(setOf("*")) }
    var showTags by remember { mutableStateOf(false) }
    var until by remember { mutableLongStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun runWork(refresh: Boolean = true, block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try { withContext(Dispatchers.IO) { block() }; if (refresh) revision++ }
            catch (error: Exception) { Toast.makeText(context, error.cause?.message ?: error.message, Toast.LENGTH_LONG).show() }
            finally { busy = false }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runWork {
            checkNotNull(context.contentResolver.openOutputStream(uri)).use { DiagnosticRepository.bundle(context, it) }
            message = resources.getString(R.string.diag_bundle_exported)
        }
    }
    LaunchedEffect(context, page, detail, revision) {
        busy = true
        try {
            withContext(Dispatchers.IO) {
                if (page in setOf("hookdetails", "performance", "sync", "trace")) events = DiagnosticRepository.events(context)
                if (page == "hookdetails") featureRows = featureDiagnosticRows(context, events)
                when (page) {
                    "snapshots" -> files = DiagnosticRepository.list(context)
                    "snapshot" -> message = DiagnosticRepository.diff(context, detail.orEmpty())
                    "sync" -> {
                        val (local, remote) = ModernXposedPreferenceSync.diagnosticReadback(context)
                        val hash = DiagnosticRules.fingerprint(local)
                        val remoteHash = remote?.let(DiagnosticRules::fingerprint)
                        val ready = remote?.get("__onextbox_api102_snapshot_ready") == true
                        message = listOf(
                            resources.getString(R.string.diag_sync_local, hash),
                            resources.getString(R.string.diag_sync_remote, remoteHash ?: resources.getString(R.string.diag_unavailable)),
                            resources.getString(R.string.diag_sync_status, resources.getString(when {
                                !ready -> R.string.diag_sync_not_ready
                                hash == remoteHash -> R.string.diag_sync_match
                                else -> R.string.diag_sync_mismatch
                            })),
                            resources.getString(R.string.diag_sync_last_confirmed, ModernXposedPreferenceSync.lastDiagnosticWrite
                                .takeIf { it > 0 }?.let(::diagnosticTime) ?: resources.getString(R.string.diag_no_session_record)),
                            resources.getString(R.string.diag_last_error, ModernXposedPreferenceSync.lastDiagnosticError
                                ?: resources.getString(R.string.diag_no_error)),
                            "",
                            resources.getString(R.string.diag_receipts_header),
                            DiagnosticRepository.currentEvents(events).filter { it.kind == "config" }.groupBy { it.process }
                                .values.joinToString("\n\n") { rows -> rows.last().let {
                                    "${it.process} · PID ${it.pid}\n${diagnosticTime(it.time)}\n${resources.getString(if (it.detail == hash) R.string.diag_receipt_match else R.string.diag_receipt_mismatch)} · ${it.detail}"
                                } }.ifEmpty { resources.getString(R.string.diag_no_receipts) },
                        ).joinToString("\n")
                    }
                    "trace" -> {
                        val prefs = LspPreferenceStore.prefs(context)
                        until = prefs.getLong(RuntimeDiagnostics.UNTIL, 0)
                    }
                }
            }
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (error: Exception) { message = error.cause?.message ?: error.toString() }
        finally { busy = false }
    }
    LaunchedEffect(page) {
        if (page == "trace") tags = LspPreferenceStore.prefs(context)
            .getStringSet(RuntimeDiagnostics.TAGS, setOf("*")).orEmpty().toSet()
    }
    LaunchedEffect(page) { if (page == "trace") while (true) { now = System.currentTimeMillis(); delay(1000) } }
    if (page != "bundle") SettingsGroup {
        SettingsDataRow(resources.getString(R.string.diagnostics_refresh), "", refreshing = busy, onClick = { revision++ })
    }
    when (page) {
        "bundle" -> {
            SettingsGroup { DiagnosticText(resources.getString(R.string.diag_bundle_notice)) }
            SettingsGroup { SettingsDataRow(if (busy) resources.getString(R.string.diag_exporting) else resources.getString(R.string.diag_export_zip), message,
                refreshing = busy, onClick = { if (!busy) export.launch("ONextBox-diagnostics-${System.currentTimeMillis()}.zip") }) }
        }
        "sync" -> {
            SettingsGroup { DiagnosticText(message) }
            SettingsGroup { SettingsCardRow(resources.getString(R.string.diag_resync), resources.getString(R.string.diag_resync_summary), onClick = {
                if (!busy) { ModernXposedPreferenceSync.syncNow(context); revision++ }
            }) }
        }
        "snapshots" -> {
            SettingsGroup { SettingsCardRow(resources.getString(R.string.diag_save_snapshot), resources.getString(R.string.diag_save_snapshot_summary), onClick = {
                runWork { DiagnosticRepository.saveSnapshot(context) }
            }) }
            if (files.isEmpty()) SettingsGroup { DiagnosticText(resources.getString(R.string.diag_no_snapshots)) }
            files.forEach { file -> SettingsGroup {
                SettingsCardRow(diagnosticTime(file.nameWithoutExtension.toLongOrNull() ?: file.lastModified()),
                    resources.getString(R.string.diag_snapshot_actions), showArrow = true, onClick = { navigate("snapshot:${file.name}") })
            } }
        }
        "snapshot" -> {
            SettingsSection(resources.getString(R.string.diag_diff_title))
            SettingsGroup { DiagnosticText(message) }
            SettingsGroup {
                SettingsCardRow(resources.getString(R.string.diag_restore_this_snapshot), resources.getString(R.string.diag_restore_summary), onClick = { if (!busy) confirm = "restore" })
                SettingsDivider()
                SettingsCardRow(resources.getString(R.string.diag_compare_previous), "", onClick = { runWork(refresh = false) {
                    val list = DiagnosticRepository.list(context)
                    val index = list.indexOfFirst { it.name == detail }
                    val other = list.getOrNull(index + 1) ?: error(resources.getString(R.string.diag_no_previous))
                    val a = DiagnosticRepository.load(context, detail.orEmpty())
                    val b = DiagnosticRepository.load(context, other.name)
                    message = resources.getString(R.string.diag_comparing, diagnosticTime(other.nameWithoutExtension.toLong())) + "\n\n" +
                        (a.keys + b.keys).sorted().filter { a[it] != b[it] }.joinToString("\n\n") { resources.getString(R.string.diag_diff_snapshot_values, it, a[it] ?: resources.getString(R.string.diag_default), b[it] ?: resources.getString(R.string.diag_default)) }.ifEmpty { resources.getString(R.string.diag_no_differences) }
                } })
                SettingsDivider()
                SettingsCardRow(resources.getString(R.string.diag_delete_this_snapshot), "", onClick = { if (!busy) confirm = "delete" })
            }
        }
        "trace" -> {
            SettingsGroup {
                SettingsDataRow(resources.getString(R.string.diag_trace_status), if (until > now) resources.getString(R.string.diag_trace_remaining, (until - now) / 1000) else resources.getString(R.string.diag_trace_stopped))
                SettingsDivider()
                SettingsCardRow(resources.getString(R.string.diag_trace_duration), resources.getString(R.string.diag_minutes, minutes), onClick = { minutes = when (minutes) { 5 -> 10; 10 -> 15; else -> 5 } })
                SettingsDivider()
                SettingsCardRow(resources.getString(R.string.diag_trace_start), resources.getString(R.string.diag_trace_notice), onClick = { runWork {
                    check(tags.isNotEmpty()) { resources.getString(R.string.diag_select_tag_required) }
                    ModernXposedPreferenceSync.replaceDiagnosticValues(context, mapOf(RuntimeDiagnostics.UNTIL to System.currentTimeMillis() + minutes * 60_000L,
                        RuntimeDiagnostics.TAGS to tags), featuresOnly = false)
                } })
                SettingsDivider()
                SettingsCardRow(resources.getString(R.string.diag_trace_stop), "", onClick = { runWork {
                    ModernXposedPreferenceSync.replaceDiagnosticValues(context, mapOf(RuntimeDiagnostics.UNTIL to 0L), featuresOnly = false)
                } })
            }
            SettingsSection(resources.getString(R.string.diag_log_tags))
            val choices = (listOf("*", "App") + diagnosticLogTags + events.filter { it.kind == "log" || it.kind == "hook_log" }.map { it.name }).distinct()
            SettingsGroup { SettingsCardRow(resources.getString(R.string.diag_select_tags), if ("*" in tags) resources.getString(R.string.diag_all_tags) else resources.getString(R.string.diag_selected_tags, tags.size),
                showExpandArrow = true, expandArrowExpanded = showTags, onClick = { showTags = !showTags }) }
            FeatureExpandableVisibility(visible = showTags) {
                SettingsGroup { choices.forEachIndexed { i, tag ->
                    if (i > 0) SettingsDivider()
                    SettingsToggleRow(title = if (tag == "*") resources.getString(R.string.diag_all_tags) else tag, summary = "", checked = tag in tags,
                        onCheckedChange = { enabled -> tags = if (enabled) (if (tag == "*") emptySet() else tags - "*") + tag else tags - tag })
                } }
            }
        }
        else -> {
            if (page == "hookdetails" && featureRows.isNotEmpty()) {
                SettingsSection(stringResource(R.string.feature_state_section))
                SettingsGroup { DiagnosticText(stringResource(R.string.feature_state_notice)) }
                SettingsGroup {
                    featureRows.forEachIndexed { index, row ->
                        if (index > 0) SettingsDivider()
                        val label = when (row.state) {
                            FeatureLoadState.Disabled -> R.string.feature_state_disabled
                            FeatureLoadState.Unsupported -> R.string.feature_state_unsupported
                            FeatureLoadState.Saved -> R.string.feature_state_saved
                            FeatureLoadState.WaitingReload -> R.string.feature_state_waiting_reload
                            FeatureLoadState.Loaded -> R.string.feature_state_loaded
                            FeatureLoadState.Unmatched -> R.string.feature_state_unmatched
                            FeatureLoadState.Failed -> R.string.feature_state_failed
                        }
                        SettingsCardRow(stringResource(row.title), stringResource(label) +
                            (row.time?.let { "\n${diagnosticTime(it)} · ${row.process.orEmpty()}" } ?: ""))
                    }
                }
            }
            SettingsGroup { DiagnosticText(resources.getString(R.string.diag_events_notice) +
                if (readCachedRootAccessInfo(context)?.state != RootAccessState.Granted) "\n" + resources.getString(R.string.diag_events_no_root) else "\n" + resources.getString(R.string.diag_events_current_generation)) }
            val current = DiagnosticRepository.currentEvents(events)
            val processes = current.map { it.process }.distinct()
            SettingsGroup { SettingsCardRow(resources.getString(R.string.diag_process), process ?: resources.getString(R.string.logs_level_all), onClick = {
                val options = listOf<String?>(null) + processes
                process = options[(options.indexOf(process) + 1) % options.size]
            }) }
            val rows = current.filter { process == null || it.process == process }.filter {
                if (page == "performance") it.kind in setOf("startup", "dex", "initialization", "config_sync", "catalog")
                else it.kind in setOf("initialization", "installed", "staged", "failure", "callback_failure", "hook_log", "committed")
            }
            val groups = rows.groupBy { it.process + "\n" + it.name }.entries
                .sortedByDescending { if (page == "performance") it.value.maxOf { r -> r.ms } else it.value.maxOf { r -> r.time } }.take(100)
            if (groups.isEmpty()) SettingsGroup { DiagnosticText(message.ifBlank { resources.getString(R.string.diag_no_events) }) }
            groups.forEach { (key, records) -> SettingsGroup {
                val recent = records.last()
                SettingsCardRow(recent.name, recent.process + "\n" + if (page == "performance") resources.getString(R.string.diag_timing, recent.ms, records.maxOf { it.ms }) else
                    "${diagnosticTime(recent.time)} · ${when (recent.kind) { "installed" -> resources.getString(R.string.diag_event_installed); "staged" -> resources.getString(R.string.diag_event_staged); "failure", "callback_failure" -> resources.getString(R.string.diag_event_failed); "initialization" -> resources.getString(R.string.diag_event_initialized); "committed" -> resources.getString(R.string.diag_event_committed); else -> resources.getString(R.string.diag_event_log) }}",
                    showExpandArrow = true, expandArrowExpanded = expanded == key, onClick = { expanded = if (expanded == key) null else key })
                FeatureExpandableVisibility(visible = expanded == key) {
                    androidx.compose.foundation.layout.Column {
                        SettingsDivider()
                        DiagnosticText(records.takeLast(10).joinToString("\n\n") {
                            "${diagnosticTime(it.time)} · ${it.kind} · ${it.ms} ms\n${it.detail}"
                        })
                    }
                }
            } }
        }
    }
    CouiConfirmDialog(show = confirm != null, title = if (confirm == "restore") resources.getString(R.string.diag_restore_title) else resources.getString(R.string.diag_delete_title),
        summary = if (confirm == "restore") resources.getString(R.string.diag_restore_confirm) else resources.getString(R.string.diag_delete_confirm),
        negativeText = resources.getString(android.R.string.cancel), positiveText = resources.getString(android.R.string.ok), onDismissRequest = { confirm = null }, onPositive = {
            val action = confirm; confirm = null
            runWork {
                if (action == "restore") {
                    val values = DiagnosticRepository.load(context, detail.orEmpty())
                    DiagnosticRepository.saveSnapshot(context)
                    ModernXposedPreferenceSync.replaceDiagnosticValues(context, values, featuresOnly = true)
                } else {
                    check(DiagnosticRepository.list(context).firstOrNull { it.name == detail }?.delete() == true)
                    navigate("")
                }
            }
        })
}

private fun diagnosticTime(time: Long): String = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(time))
