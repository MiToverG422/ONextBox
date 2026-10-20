package com.mi.onextbox.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.settings.*
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun EnabledFeaturesAction(onClick: () -> Unit) {
    InventoryAction(Icons.Rounded.Checklist, stringResource(R.string.enabled_features_title), onClick)
}

@Composable
private fun InventoryAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        IconButton(onClick = onClick) { Icon(icon, label) }
    } else {
        com.mi.onextbox.ui.common.ColorOsTopBarButton(onClick = onClick) {
            Icon(icon, label, tint = COUITheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
internal fun EnabledFeaturesPage(
    modifier: Modifier,
    bottomPadding: Dp,
    blurBackdrop: LayerBackdrop?,
    transitionScrollState: ScrollState?,
    onBack: () -> Unit,
    onOpen: (FeaturePageMode) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf<List<EnabledFeatureCatalog.Entry>?>(null) }
    var details by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val actions = remember { EnabledFeatureActions() }
    var savingTitle by remember { mutableStateOf<Int?>(null) }
    val saving = savingTitle != null
    var configured by remember { mutableStateOf(emptySet<Int>()) }
    var retained by remember { mutableStateOf(emptySet<Int>()) }
    DisposableEffect(context, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        val prefs = LspPreferenceStore.prefs(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        owner.lifecycle.addObserver(observer)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    LaunchedEffect(revision, saving, configuration) {
        if (saving) return@LaunchedEffect
        failed = false
        try {
            val snapshot = withContext(Dispatchers.IO) {
                val values = EnabledFeatureCatalog.configured(context)
                values to EnabledFeatureCatalog.entries.associate { it.title to EnabledFeatureCatalog.detail(context, it.title) }
            }
            configured = snapshot.first
            retained = retained + EnabledFeatureCatalog.entries
                .filter { EnabledFeatureCatalog.isActive(it.title, configured) }.map { it.title }
            entries = EnabledFeatureCatalog.entries.filter { it.title in retained }
            details = snapshot.second.mapValues { (id, value) ->
                if (id in configured) value else details[id] ?: value
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { failed = true }
    }
    fun change(entry: EnabledFeatureCatalog.Entry, checked: Boolean) {
        if (saving || failed) return
        savingTitle = entry.title
        val write = actions.enqueue(context.applicationContext, entry, checked)
        scope.launch {
            try { write.await() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                Toast.makeText(context, R.string.enabled_features_save_failed, Toast.LENGTH_LONG).show()
            } finally {
                savingTitle = null
                revision++
            }
        }
    }
    val activeCount = EnabledFeatureCatalog.entries.count { EnabledFeatureCatalog.isActive(it.title, configured) }
    val groups = entries.orEmpty().filter { EnabledFeatureCatalog.dependenciesActive(it.title, configured) }
        .sortedBy { it.page.ordinal }
        .groupBy { EnabledFeatureCatalog.group(it.page) }.toSortedMap(compareBy { it.ordinal })
    val groupTitles = groups.keys.associateWith { featurePageTitle(it) }
    val title = stringResource(R.string.enabled_features_title)
    val notice = stringResource(R.string.enabled_features_notice)
    SettingsPageSurface(
        title = title,
        modifier = modifier.fillMaxSize(),
        showBack = true,
        onBack = onBack,
        bottomContentPadding = bottomPadding,
        blurBackdrop = blurBackdrop,
        externalScrollState = transitionScrollState,
        actions = {
            if (entries != null && !failed) {
                InventoryAction(Icons.Rounded.ContentCopy, stringResource(R.string.enabled_features_copy)) {
                    // Keep the action mounted and visually stable during writes.
                    // Wait for the refreshed snapshot before allowing a copy.
                    if (saving) return@InventoryAction
                    val text = buildString {
                        appendLine(title)
                        appendLine(notice)
                        groups.forEach { (group, items) ->
                            val activeItems = items.filter { EnabledFeatureCatalog.isActive(it.title, configured) }
                            if (activeItems.isEmpty()) return@forEach
                            appendLine()
                            appendLine(groupTitles[group])
                            activeItems.forEach {
                                append("• " + resources.getString(it.title))
                                details[it.title]?.takeIf(String::isNotBlank)?.let { value -> append(" · $value") }
                                appendLine()
                            }
                        }
                    }
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText(title, text))
                }
            }
        },
    ) {
        SettingsGroup {
            SettingsCardRow(
                title = when {
                    failed -> stringResource(R.string.enabled_features_error)
                    entries == null -> stringResource(R.string.enabled_features_loading)
                    entries!!.isEmpty() -> stringResource(R.string.enabled_features_empty)
                    else -> stringResource(R.string.enabled_features_count, activeCount)
                },
                summary = "",
                onClick = if (failed) ({ revision++ }) else null,
            )
        }
        groups.forEach { (group, items) ->
            key(group) {
                var expanded by rememberSaveable(group) { mutableStateOf(false) }
                FeatureAnimatedGroup {
                    row("header") { above, below ->
                        SettingsCardRow(
                            title = groupTitles.getValue(group),
                            summary = stringResource(R.string.enabled_features_group_count, items.size),
                            showExpandArrow = true,
                            expandArrowExpanded = expanded,
                            onClick = { expanded = !expanded },
                            hasDividerAbove = above, hasDividerBelow = below,
                        )
                    }
                    items.forEach { entry ->
                        row(entry.title, visible = expanded) { above, below ->
                            if (!actions.supports(entry.title)) {
                                SettingsCardRow(
                                    title = stringResource(entry.title),
                                    summary = featurePageTitle(entry.page),
                                    showArrow = true,
                                    onClick = { if (!saving) onOpen(entry.page) },
                                    hasDividerAbove = above, hasDividerBelow = below,
                                )
                            } else {
                            SettingsToggleRow(
                                title = stringResource(entry.title),
                                summary = listOf(
                                    if (entry.page != group) featurePageTitle(entry.page) else "",
                                    details[entry.title].orEmpty(),
                                )
                                    .filter(String::isNotBlank).joinToString(" · "),
                                checked = entry.title in configured,
                                // A write must not toggle the disabled appearance of
                                // every unrelated switch in the page.
                                switchEnabled = !failed,
                                interactionEnabled = savingTitle != entry.title,
                                onCheckedChange = { change(entry, it) },
                                onRowClick = { if (!saving) onOpen(entry.page) },
                                hasDividerAbove = above, hasDividerBelow = below,
                            )
                            }
                        }
                    }
                }
            }
        }
    }
}
