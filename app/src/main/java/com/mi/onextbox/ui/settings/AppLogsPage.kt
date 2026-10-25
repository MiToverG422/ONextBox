@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.mi.onextbox.ui.settings

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.onextbox.BuildConfig
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.AppLogLevel
import com.mi.onextbox.ui.common.AppLogStore
import com.mi.onextbox.ui.common.ColorOsTopBarButton
import com.mi.onextbox.ui.common.CouiConfirmDialog
import com.mi.onextbox.ui.common.LogExport
import com.mi.onextbox.ui.common.LogLevelFilter
import com.mi.onextbox.ui.common.LogRecord
import com.mi.onextbox.ui.common.LogSource
import com.mi.onextbox.ui.common.ModuleLogFailure
import com.mi.onextbox.ui.common.ModuleLogReadResult
import com.mi.onextbox.ui.common.ModuleLogReader
import com.mi.onextbox.ui.common.filterLogRecords
import com.mi.onextbox.ui.common.toLogRecord
import com.mi.onextbox.ui.screens.FeatureSearchBar
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AppLogsRoute(
    modifier: Modifier,
    blurBackdrop: LayerBackdrop?,
    bottomContentPadding: Dp,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val appEntries by AppLogStore.entries.collectAsState()
    var sourceIndex by rememberSaveable { mutableIntStateOf(0) }
    var levelIndex by rememberSaveable { mutableIntStateOf(0) }
    val source = LogSource.entries[sourceIndex.coerceIn(LogSource.entries.indices)]
    val filter = LogLevelFilter.entries[levelIndex.coerceIn(LogLevelFilter.entries.indices)]
    val search = rememberTextFieldState()
    val query = search.text.toString()
    var moduleLogs by remember { mutableStateOf(ModuleLogReadResult()) }
    var loading by remember { mutableStateOf(false) }
    var refreshGeneration by remember { mutableIntStateOf(0) }
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    var pendingExportPath by rememberSaveable { mutableStateOf<String?>(null) }
    var preparingExport by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val sourceLabels = listOf(stringResource(R.string.logs_source_app), stringResource(R.string.logs_source_module))
    val levelLabels = listOf(
        stringResource(R.string.logs_level_all), stringResource(R.string.logs_level_debug),
        stringResource(R.string.logs_level_info), stringResource(R.string.logs_level_warn),
        stringResource(R.string.logs_level_error),
    )
    val records = remember(source, appEntries, moduleLogs) {
        if (source == LogSource.APP) appEntries.map { it.toLogRecord() } else moduleLogs.records
    }
    val visibleRecords = remember(records, filter, query) { filterLogRecords(records, filter, query) }

    fun notify(message: Int) = Toast.makeText(context, resources.getString(message), Toast.LENGTH_SHORT).show()

    LaunchedEffect(source, refreshGeneration) {
        loading = true
        try {
            if (source == LogSource.MODULE) moduleLogs = withContext(Dispatchers.IO) { ModuleLogReader.read() }
            else withContext(Dispatchers.IO) { AppLogStore.initialize(context) }
        } finally {
            loading = false
        }
    }
    LaunchedEffect(source, filter, query) {
        expandedKey = null
        listState.scrollToItem(0)
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val path = pendingExportPath ?: return@rememberLauncherForActivityResult
        pendingExportPath = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (uri == null) LogExport.discard(context, path) else LogExport.save(context, path, uri)
                }
                if (uri != null) notify(R.string.logs_export_success)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                notify(R.string.logs_export_failed)
            }
        }
    }

    val export: () -> Unit = {
        if (visibleRecords.isNotEmpty() && !preparingExport && pendingExportPath == null) {
            preparingExport = true
            val text = "ONextBox ${BuildConfig.VERSION_NAME} · ${sourceLabels[source.ordinal]}\n\n" +
                visibleRecords.asReversed().joinToString("\n\n") { it.raw }
            scope.launch {
                try {
                    pendingExportPath = withContext(Dispatchers.IO) { LogExport.prepare(context, text) }
                    val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                    exportLauncher.launch("ONextBox-${source.name.lowercase()}-$time.txt")
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    pendingExportPath?.let { path -> withContext(Dispatchers.IO) { LogExport.discard(context, path) } }
                    pendingExportPath = null
                    notify(R.string.logs_export_failed)
                } finally {
                    preparingExport = false
                }
            }
        }
    }

    SettingsPageSurface(
        title = stringResource(R.string.about_logs_title),
        modifier = modifier,
        showBack = true,
        onBack = onBack,
        blurBackdrop = blurBackdrop,
        bottomContentPadding = bottomContentPadding,
        contentScrollable = false,
        edgeToEdgeBottom = true,
        actions = {
            LogsTopBarActions(
                loading = loading,
                exportEnabled = visibleRecords.isNotEmpty() && !preparingExport && pendingExportPath == null && !loading,
                clearEnabled = appEntries.isNotEmpty(),
                showClear = source == LogSource.APP,
                onRefresh = {
                    refreshGeneration++
                    scope.launch { listState.animateScrollToItem(0) }
                },
                onExport = export,
                onClear = { showClearConfirm = true },
            )
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomContentPadding + 20.dp),
        ) {
            item(key = "search") { FeatureSearchBar(state = search, hint = stringResource(R.string.logs_search_hint)) }
            item(key = "filters") {
                SettingsGroup(bottomPadding = 8.dp) {
                    run {SettingsWindowDropdownPreference(
                            title = stringResource(R.string.logs_source),
                            items = sourceLabels, selectedIndex = source.ordinal,
                            onSelectedIndexChange = { sourceIndex = it },
                        )}
                    SettingsDivider()
                    run {SettingsWindowDropdownPreference(
                            title = stringResource(R.string.logs_level),
                            items = levelLabels, selectedIndex = filter.ordinal,
                            onSelectedIndexChange = { levelIndex = it },
                        )}
                }
            }
            item(key = "count") { SettingsSection(stringResource(R.string.logs_count, visibleRecords.size, records.size)) }
            if (source == LogSource.MODULE && !loading && moduleLogs.failure == null) {
                item(key = "hook_evidence") { HookEvidenceSummary(moduleLogs.records) }
            }
            val failure = if (source == LogSource.MODULE) moduleLogs.failure else null
            if (loading || failure != null || visibleRecords.isEmpty()) {
                item(key = "status") {
                    SettingsGroup {
                        SettingsCardRow(
                            summary = "",
                            title = stringResource(when {
                                loading -> R.string.logs_loading
                                failure == ModuleLogFailure.ROOT_REQUIRED -> R.string.logs_root_required
                                failure == ModuleLogFailure.UNAVAILABLE -> R.string.logs_unavailable
                                failure == ModuleLogFailure.READ_FAILED -> R.string.logs_read_failed
                                records.isEmpty() -> R.string.logs_empty
                                else -> R.string.logs_no_matches
                            }),
                        )
                    }
                }
            } else {
                items(visibleRecords, key = { it.key }) { record ->
                    LogRecordCard(
                        modifier = logRecordListAnimation(),
                        record = record,
                        levelLabel = record.level?.let { levelLabels[it.ordinal + 1] },
                        expanded = expandedKey == record.key,
                        onClick = { expandedKey = if (expandedKey == record.key) null else record.key },
                        onCopy = {
                            runCatching {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                    ?: error("Clipboard unavailable")
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(resources.getString(R.string.about_logs_title), record.raw),
                                )
                            }.fold(
                                onSuccess = { notify(R.string.logs_copied) },
                                onFailure = { notify(R.string.logs_copy_failed) },
                            )
                        },
                    )
                }
            }
        }
    }

    CouiConfirmDialog(
        show = showClearConfirm,
        title = stringResource(R.string.logs_clear_title),
        summary = stringResource(R.string.logs_clear_summary),
        negativeText = stringResource(R.string.config_clear_confirm_cancel),
        positiveText = stringResource(R.string.logs_clear),
        onDismissRequest = { showClearConfirm = false },
        onPositive = {
            showClearConfirm = false
            AppLogStore.clear()
            notify(R.string.logs_clear_success)
        },
    )
}

@Composable
internal fun LogRecordCard(
    record: LogRecord,
    levelLabel: String?,
    expanded: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onCopy: () -> Unit,
) {
    val surface = COUITheme.colorScheme.surface
    val dark = surface.luminance() < .5f
    val secondaryColor = COUITheme.colorScheme.onSurfaceVariantSummary
    val severityColor = when (record.level) {
        AppLogLevel.ERROR -> if (dark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A)
        AppLogLevel.WARN -> if (dark) Color(0xFFFFCC80) else Color(0xFF875000)
        AppLogLevel.INFO -> COUITheme.colorScheme.primary
        else -> secondaryColor
    }
    var canExpand by remember(record.message) { mutableStateOf(false) }
    val expansionDescription = stringResource(if (expanded) R.string.logs_expanded else R.string.logs_collapsed)
    val sizeSpec = spring<IntSize>(dampingRatio = 1f, stiffness = 438.65f)
    val arrowSpec = spring<Float>(dampingRatio = 1f, stiffness = 438.65f)
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, arrowSpec, label = "logExpandArrow")
    val enterFade = tween<Float>(150, delayMillis = 30)
    val exitFade = tween<Float>(90)
    val content: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().combinedClickable(
                onClickLabel = if (canExpand) stringResource(R.string.logs_expand) else null,
                onLongClickLabel = stringResource(R.string.logs_copy),
                onClick = { if (canExpand) onClick() }, onLongClick = onCopy,
            ).semantics { if (canExpand) stateDescription = expansionDescription }.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (levelLabel != null) {
                        LogLevelBadge(levelLabel, severityColor)
                    }
                    LogText(
                        record.origin.ifEmpty { record.metadata }, Modifier.weight(1f), maxLines = 1,
                        fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    )
                    if (canExpand) {
                        val arrowModifier = Modifier.size(20.dp).graphicsLayer { rotationZ = arrowRotation }
                        Icon(Icons.Rounded.ExpandMore, null, arrowModifier, tint = secondaryColor)
                    }
                }
                if (record.timestamp.isNotEmpty()) {
                    LogText(record.timestamp, secondary = true, fontSize = 11.sp, maxLines = 1)
                }
            }
            // Keep the outgoing full text alive during collapse, so the card never shrinks
            // through an empty area after maxLines has already truncated its contents.
            AnimatedContent(
                targetState = expanded,
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopStart,
                transitionSpec = {
                    (fadeIn(enterFade) togetherWith fadeOut(exitFade)).using(
                        SizeTransform(clip = true) { _, _ -> sizeSpec },
                    )
                },
                label = "logMessageExpansion",
            ) { showFullMessage ->
                LogText(
                    record.message, modifier = Modifier.fillMaxWidth(), monospace = true,
                    maxLines = if (showFullMessage) Int.MAX_VALUE else LogPreviewLines,
                    onTextLayout = { canExpand = it.hasVisualOverflow || it.lineCount > LogPreviewLines },
                )
            }
        }
    }
    Box(modifier.clipToBounds()) {
        SettingsGroup(bottomPadding = 10.dp) {
            content()
        }
    }
}

private const val LogPreviewLines = 4

internal fun LazyItemScope.logRecordListAnimation(): Modifier = Modifier.animateItem(
    fadeInSpec = tween(150),
    fadeOutSpec = tween(120),
    // The body already changes row height every frame. A second placement spring
    // makes following cards lag behind their actual layout position and overlap.
    placementSpec = null,
)

@Composable
internal fun LogLevelBadge(label: String, color: Color, modifier: Modifier = Modifier) {
    val style = (COUITheme.textStyles.body2).copy(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
    Box(
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = .12f))
            .heightIn(min = 24.dp).padding(horizontal = 7.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, style = style, maxLines = 1)
    }
}

@Composable
private fun LogText(
    text: String,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    monospace: Boolean = false,
    color: Color? = null,
    fontSize: TextUnit = if (secondary) 12.sp else 13.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val foreground = color ?: run {
        if (secondary) COUITheme.colorScheme.onSurfaceVariantSummary else COUITheme.colorScheme.onSurface
    }
    val fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
    run {
        Text(text, modifier, color = foreground, fontSize = fontSize, fontFamily = fontFamily,
            fontWeight = fontWeight, lineHeight = 19.sp, maxLines = maxLines,
            overflow = TextOverflow.Ellipsis, onTextLayout = onTextLayout)
    }
}

@Composable
private fun RowScope.LogsTopBarActions(
    loading: Boolean,
    exportEnabled: Boolean,
    clearEnabled: Boolean,
    showClear: Boolean,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    LogsToolbarButton(Icons.Rounded.Refresh, stringResource(R.string.logs_refresh), !loading, onRefresh)
    Box {
        LogsToolbarButton(Icons.Rounded.MoreVert, stringResource(R.string.logs_menu), true) { menuExpanded = true }
        val menuItems = buildList {
            add(LogMenuItem(stringResource(R.string.logs_export), exportEnabled, onExport))
            if (showClear) add(LogMenuItem(stringResource(R.string.logs_clear), clearEnabled, onClear))
        }
        run {
            ColorOsDropdownPopup(
                show = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                selectionMenu = false,
                entry = DropdownEntry(items = menuItems.map { DropdownItem(text = it.title, enabled = it.enabled) }),
                onItemClick = { index -> menuExpanded = false; menuItems[index].onClick() },
            )
        }
    }
}

private data class LogMenuItem(val title: String, val enabled: Boolean, val onClick: () -> Unit)

@Composable
private fun LogsToolbarButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    run {
        ColorOsTopBarButton(onClick = { if (enabled) onClick() }, modifier = Modifier
            .alpha(if (enabled) 1f else .38f).semantics { if (!enabled) disabled() }) {
            Icon(icon, label, tint = COUITheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
        }
    }
}
