@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.mi.onextbox.ui.screens

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mi.onextbox.R
import com.mi.onextbox.errors.AppErrorKind
import com.mi.onextbox.errors.AppErrorRecord
import com.mi.onextbox.errors.AppErrorRepository
import com.mi.onextbox.errors.AppErrorRules
import com.mi.onextbox.errors.AppErrorTrackingSettings
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.ui.common.ColorOsTopBarButton
import com.mi.onextbox.ui.common.CouiConfirmDialog
import com.mi.onextbox.ui.common.LogExport
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.LogLevelBadge
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsPageSurface
import com.mi.onextbox.ui.settings.SettingsToggleRow
import com.mi.onextbox.ui.settings.SettingsWindowDropdownPreference
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text as CouiText
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AppErrorTrackingRoute(
    modifier: Modifier,
    blurBackdrop: LayerBackdrop?,
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    detailId: Long = 0,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(AppErrorTrackingSettings.isEnabled(context)) }
    var changing by remember { mutableStateOf(false) }
    var records by remember { mutableStateOf(emptyList<AppErrorRecord>()) }
    var readFailed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    var showClear by rememberSaveable { mutableStateOf(false) }
    var pendingExport by rememberSaveable { mutableStateOf<String?>(null) }
    var preparing by remember { mutableStateOf(false) }
    val search = rememberTextFieldState()
    val query = search.text.toString()
    val kind = AppErrorKind.entries.getOrNull(filterIndex - 1)
    val filtered = remember(records, kind, query) { AppErrorRules.filter(records, kind, query) }
    val counts = remember(records) { records.groupingBy { it.packageName }.eachCount() }
    val listState = rememberLazyListState()
    val bottomPadding = errorBottomPadding()
    val labels = listOf(stringResource(R.string.app_errors_all)) + AppErrorKind.entries.map { errorKindLabel(it) }
    fun notify(id: Int) = Toast.makeText(context, resources.getString(id), Toast.LENGTH_SHORT).show()

    DisposableEffect(context) {
        val prefs = LspPreferenceStore.prefs(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AppErrorTrackingSettings.KEY) enabled = AppErrorTrackingSettings.isEnabled(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(owner) {
        if (detailId != 0L) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            AppErrorRepository.observe(context).collect {
                try {
                    records = withContext(Dispatchers.IO) { AppErrorRepository.list(context) }
                    readFailed = false
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { readFailed = true }
                finally { loading = false }
            }
        }
    }
    LaunchedEffect(filterIndex, query) { listState.scrollToItem(0) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val path = pendingExport ?: return@rememberLauncherForActivityResult
        pendingExport = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (uri == null) LogExport.discard(context, path) else LogExport.save(context, path, uri)
                }
                if (uri != null) notify(R.string.app_errors_exported)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { notify(R.string.app_errors_action_failed) }
        }
    }
    fun withReport(id: Long, action: (AppErrorRecord) -> Unit) {
        scope.launch {
            try {
                val record = withContext(Dispatchers.IO) { AppErrorRepository.detail(context, id) }
                if (record == null) notify(R.string.app_errors_missing) else action(record)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { notify(R.string.app_errors_action_failed) }
        }
    }
    val copy: (Long) -> Unit = { id ->
        withReport(id) { record ->
            requireNotNull(context.getSystemService(ClipboardManager::class.java)).setPrimaryClip(
                ClipData.newPlainText(resources.getString(R.string.app_errors_title), record.report()),
            )
            notify(R.string.app_errors_copied)
        }
    }
    val share: (Long) -> Unit = { id ->
        withReport(id) { record ->
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "ONextBox · ${record.packageName}")
                putExtra(Intent.EXTRA_TEXT, record.report())
            }, resources.getString(R.string.app_errors_share)))
        }
    }
    val export: (Long) -> Unit = { id ->
        if (!preparing && pendingExport == null) {
            preparing = true
            scope.launch {
                try {
                    pendingExport = withContext(Dispatchers.IO) {
                        val record = AppErrorRepository.detail(context, id) ?: error("Report deleted")
                        LogExport.prepare(context, record.report())
                    }
                    exportLauncher.launch("ONextBox-error-$id.txt")
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) {
                    pendingExport?.let { path -> withContext(Dispatchers.IO) { LogExport.discard(context, path) } }
                    pendingExport = null
                    notify(R.string.app_errors_action_failed)
                } finally { preparing = false }
            }
        }
    }
    SettingsPageSurface(
        title = stringResource(if (detailId == 0L) R.string.app_errors_title else R.string.app_errors_detail),
        modifier = modifier, showBack = true,
        onBack = onBack,
        blurBackdrop = blurBackdrop, bottomContentPadding = 0.dp, contentScrollable = false, edgeToEdgeBottom = true,
        actions = {
            if (detailId == 0L && records.isNotEmpty()) {
                val label = stringResource(R.string.app_errors_clear)
                run {
                    ColorOsTopBarButton(onClick = { showClear = true }) { Icon(Icons.Rounded.DeleteOutline, label, Modifier.size(22.dp)) }
                }
            }
        },
    ) {
        if (detailId == 0L) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
                item("controls") {
                    SettingsGroup {
                        SettingsToggleRow(
                            title = stringResource(R.string.app_errors_enable), summary = stringResource(R.string.app_errors_enable_summary),
                            checked = enabled, interactionEnabled = !changing, onCheckedChange = { next ->
                                changing = true
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { AppErrorTrackingSettings.setEnabled(context, next) }
                                        enabled = next
                                    } catch (cancel: CancellationException) { throw cancel }
                                    catch (_: Exception) { notify(R.string.app_errors_sync_failed) }
                                    finally { changing = false }
                                }
                            },
                        )
                    }
                    FeatureSearchBar(search, hint = stringResource(R.string.app_errors_search))
                    SettingsGroup {
                        SettingsWindowDropdownPreference(labels, filterIndex, stringResource(R.string.app_errors_kind), onSelectedIndexChange = { filterIndex = it })
                    }
                    ErrorText(
                        stringResource(R.string.app_errors_counts, counts.size, filtered.size, AppErrorRules.MAX_RECORDS),
                        Modifier.padding(horizontal = 32.dp, vertical = 8.dp), secondary = true,
                    )
                }
                if (filtered.isEmpty()) item("empty") {
                    SettingsGroup {
                        SettingsCardRow(title = stringResource(when {
                            readFailed -> R.string.app_errors_read_failed
                            loading -> R.string.app_errors_loading
                            records.isEmpty() -> R.string.app_errors_empty
                            else -> R.string.app_errors_no_matches
                        }), summary = "")
                    }
                }
                items(filtered, key = { it.id }) { record ->
                    AppErrorSummaryCard(record, onOpen = { onOpenDetail(record.id) }, onCopy = { copy(record.id) })
                }
            }
        } else {
            AppErrorDetailPage(detailId, onCopy = { copy(detailId) }, onShare = { share(detailId) }, onExport = { export(detailId) }, exportEnabled = !preparing && pendingExport == null)
        }
    }
    CouiConfirmDialog(
        show = showClear, title = stringResource(R.string.app_errors_clear), summary = stringResource(R.string.app_errors_clear_summary),
        negativeText = stringResource(R.string.config_clear_confirm_cancel), positiveText = stringResource(R.string.app_errors_clear),
        onDismissRequest = { showClear = false }, onPositive = {
            showClear = false
            scope.launch {
                try { withContext(Dispatchers.IO) { AppErrorRepository.clear(context) } }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { notify(R.string.app_errors_action_failed) }
            }
        },
    )
}

@Composable
internal fun AppErrorSummaryCard(record: AppErrorRecord, onOpen: () -> Unit, onCopy: () -> Unit) {
    ErrorCard {
        Column(
            Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onClickLabel = stringResource(R.string.app_errors_detail), onLongClick = onCopy, onLongClickLabel = stringResource(R.string.app_errors_copy)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppErrorAppIcon(record.packageName)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    ErrorText(record.appName.ifBlank { record.packageName }, maxLines = 1, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    ErrorText(errorTime(record.time), secondary = true, maxLines = 1)
                }
                val arrow = Icons.AutoMirrored.Rounded.KeyboardArrowRight
                Icon(arrow, null, Modifier.size(20.dp), tint = COUITheme.colorScheme.onSurfaceVariantActions)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val accent = COUITheme.colorScheme.primary
                LogLevelBadge(errorKindLabel(record.kind), accent)
                ErrorText(record.packageName, Modifier.weight(1f), secondary = true, maxLines = 1)
            }
            if (record.processName != record.packageName) ErrorText(record.processName, secondary = true, maxLines = 1)
            ErrorText(record.summary, maxLines = 3)
        }
    }
}

@Composable
private fun AppErrorDetailPage(id: Long, onCopy: () -> Unit, onShare: () -> Unit, onExport: () -> Unit, exportEnabled: Boolean) {
    val context = LocalContext.current
    var record by remember(id) { mutableStateOf<AppErrorRecord?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    var failed by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) {
        try { record = withContext(Dispatchers.IO) { AppErrorRepository.detail(context, id) } }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    val value = record
    if (value == null) {
        LazyColumn(contentPadding = PaddingValues(bottom = errorBottomPadding()), modifier = Modifier.fillMaxSize()) {
            item {
                SettingsGroup { SettingsCardRow(title = stringResource(when {
                    loading -> R.string.app_errors_loading
                    failed -> R.string.app_errors_read_failed
                    else -> R.string.app_errors_missing
                }), summary = "") }
            }
        }
    } else AppErrorDetailContent(value, onCopy, onShare, onExport, exportEnabled)
}

@Composable
internal fun AppErrorDetailContent(value: AppErrorRecord, onCopy: () -> Unit, onShare: () -> Unit, onExport: () -> Unit, exportEnabled: Boolean) {
    val meta = value.metadata
    val eventRows = buildList {
        add(stringResource(R.string.app_errors_kind) to errorKindLabel(value.kind))
        add(stringResource(R.string.app_errors_time) to errorTime(value.time))
        add(stringResource(R.string.app_errors_reason) to value.summary)
        if (meta.exceptionClass.isNotBlank()) add(stringResource(R.string.app_errors_exception) to meta.exceptionClass)
        if (meta.exceptionMessage.isNotBlank()) add(stringResource(R.string.app_errors_message) to meta.exceptionMessage)
        if (meta.location().isNotBlank()) add(stringResource(R.string.app_errors_location) to meta.location())
        if (meta.activity.isNotBlank()) add(stringResource(R.string.app_errors_activity) to meta.activity)
        if (meta.parentActivity.isNotBlank()) add(stringResource(R.string.app_errors_parent_activity) to meta.parentActivity)
        if (meta.parentProcess.isNotBlank()) add(stringResource(R.string.app_errors_parent_process) to meta.parentProcess)
        val traceStatus = when (meta.traceStatus) {
            "captured" -> R.string.app_errors_trace_captured
            "truncated" -> R.string.app_errors_trace_truncated
            "not_provided" -> R.string.app_errors_trace_missing
            "unavailable" -> R.string.app_errors_trace_unavailable
            else -> null
        }
        if (traceStatus != null) add(stringResource(R.string.app_errors_trace_status) to stringResource(traceStatus))
    }
    val appRows = buildList {
        add(stringResource(R.string.app_errors_package) to value.packageName)
        add(stringResource(R.string.app_errors_process) to value.processName)
        if (value.version.isNotBlank()) add(stringResource(R.string.app_errors_version) to value.version)
        add("UID / PID" to "${value.uid} / ${value.pid}")
        add(stringResource(R.string.app_errors_user) to (value.uid / 100_000).toString())
        if (meta.targetSdk > 0) add("Target SDK" to meta.targetSdk.toString())
        if (meta.minSdk > 0) add("Min SDK" to meta.minSdk.toString())
        meta.systemApp?.let { add(stringResource(R.string.app_errors_app_type) to stringResource(if (it) R.string.app_errors_system_app else R.string.app_errors_user_app)) }
    }
    val environmentRows = buildList {
        if (meta.device.isNotBlank()) add(stringResource(R.string.app_errors_device) to meta.device)
        if (meta.androidVersion.isNotBlank() || meta.sdk > 0) add("Android" to listOfNotNull(meta.androidVersion.takeIf { it.isNotBlank() }, meta.sdk.takeIf { it > 0 }?.let { "API $it" }).joinToString(" · "))
        if (meta.abi.isNotBlank()) add("ABI" to meta.abi)
        if (meta.fingerprint.isNotBlank()) add(stringResource(R.string.app_errors_fingerprint) to meta.fingerprint)
    }
    LazyColumn(contentPadding = PaddingValues(bottom = errorBottomPadding()), modifier = Modifier.fillMaxSize().testTag("appErrorDetails")) {
        item("appHeader") {
            ErrorCard {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppErrorAppIcon(value.packageName)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ErrorText(value.appName.ifBlank { value.packageName }, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        if (value.appName.isNotBlank() && value.appName != value.packageName) ErrorText(value.packageName, secondary = true)
                    }
                }
            }
        }
        item("reportActions") {
            SettingsGroup {
                SettingsCardRow(stringResource(R.string.app_errors_copy), "", onClick = onCopy, hasDividerBelow = true)
                SettingsDivider()
                SettingsCardRow(stringResource(R.string.app_errors_share), "", onClick = onShare, hasDividerAbove = true, hasDividerBelow = true)
                SettingsDivider()
                SettingsCardRow(stringResource(R.string.app_errors_export), "", onClick = onExport.takeIf { exportEnabled }, hasDividerAbove = true)
            }
        }
        item("eventInfo") {
            SettingsSection(stringResource(R.string.app_errors_event_info))
            AppErrorInfoGroup(eventRows)
        }
        item("appInfo") {
            SettingsSection(stringResource(R.string.app_errors_app_info))
            AppErrorInfoGroup(appRows)
        }
        if (environmentRows.isNotEmpty()) item("environment") {
            SettingsSection(stringResource(R.string.app_errors_environment))
            AppErrorInfoGroup(environmentRows)
        }
        item("stack") {
            SettingsSection(stringResource(R.string.app_errors_stack))
            if (value.truncated) ErrorText(stringResource(R.string.app_errors_truncated), Modifier.padding(horizontal = 32.dp, vertical = 8.dp), secondary = true)
            ErrorCard {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    ErrorText(value.detail.ifBlank { stringResource(R.string.app_errors_no_stack) }, Modifier.fillMaxWidth().padding(16.dp), monospace = true, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
internal fun AppErrorInfoGroup(rows: List<Pair<String, String>>) {
    SettingsGroup {
        rows.forEachIndexed { index, (label, value) ->
            androidx.compose.foundation.text.selection.SelectionContainer {
                SettingsCardRow(label, value, hasDividerAbove = index > 0, hasDividerBelow = index < rows.lastIndex)
            }
            if (index < rows.lastIndex) SettingsDivider()
        }
    }
}

@Composable
private fun AppErrorAppIcon(packageName: String) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, context, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    val current = bitmap
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        if (current != null) Image(current, null, Modifier.fillMaxSize())
        else Icon(Icons.Rounded.BugReport, null, Modifier.size(28.dp), tint = COUITheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorCard(content: @Composable () -> Unit) {
    SettingsGroup(bottomPadding = 10.dp) {
        content()
    }
}

@Composable
private fun ErrorText(
    text: String, modifier: Modifier = Modifier, secondary: Boolean = false,
    maxLines: Int = Int.MAX_VALUE, monospace: Boolean = false,
    fontSize: TextUnit = if (secondary) 12.sp else 14.sp, fontWeight: FontWeight = FontWeight.Normal,
) {
    val color = run {
        if (secondary) COUITheme.colorScheme.onSurfaceVariantSummary else COUITheme.colorScheme.onSurface
    }
    val family = if (monospace) FontFamily.Monospace else FontFamily.Default
    CouiText(text, modifier, color = color, fontSize = fontSize, fontWeight = fontWeight, lineHeight = fontSize * 1.5f, maxLines = maxLines, overflow = TextOverflow.Ellipsis, fontFamily = family)
}

@Composable
private fun errorKindLabel(kind: AppErrorKind): String = stringResource(when (kind) {
    AppErrorKind.CRASH -> R.string.app_errors_crash
    AppErrorKind.NATIVE -> R.string.app_errors_native
    AppErrorKind.ANR -> R.string.app_errors_anr
})

private fun errorTime(time: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    .format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))

@Composable
private fun errorBottomPadding() = WindowInsets.navigationBars.union(WindowInsets.mandatorySystemGestures)
    .union(WindowInsets.displayCutout).asPaddingValues().calculateBottomPadding() + 24.dp
