@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.mi.onextbox.ui.screens

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import com.mi.onextbox.R
import com.mi.onextbox.notifications.NotificationAppState
import com.mi.onextbox.notifications.NotificationAppTarget
import com.mi.onextbox.notifications.NotificationBatchIssue
import com.mi.onextbox.notifications.NotificationMutationResult
import com.mi.onextbox.notifications.NotificationOperationState
import com.mi.onextbox.notifications.NotificationPermissionKind
import com.mi.onextbox.ui.common.ColorOsTopBarButton
import com.mi.onextbox.ui.common.ColorOs17Button
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsLazyGroupItem
import com.mi.onextbox.ui.settings.SettingsPageSurface
import com.mi.onextbox.ui.settings.SettingsSwitchPreference
import io.github.suqi8.coui.kmp.basic.Checkbox as CouiCheckbox
import io.github.suqi8.coui.kmp.basic.Text as CouiText
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class NotificationBatchApp(val target: NotificationAppTarget, val label: String)

internal data class NotificationBatchUiState(
    val apps: List<NotificationBatchApp> = emptyList(),
    val states: List<NotificationAppState> = emptyList(),
    val selected: Set<String> = emptySet(),
    val types: Set<NotificationPermissionKind> = NotificationPermissionKind.entries.toSet(),
    val operation: NotificationOperationState = NotificationOperationState(),
    val loading: Boolean = false,
    val readFailed: Boolean = false,
) {
    val busy: Boolean get() = loading || operation.busy
    val canApply: Boolean get() = !busy && !readFailed && types.isNotEmpty() &&
        states.any { it.issue == null && it.target.packageName in selected }
}

@Composable
internal fun NotificationBatchLayout(
    ui: NotificationBatchUiState,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null,
    onBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onAction: (Int) -> Unit = {},
    onSelectionChange: (Set<String>) -> Unit = {},
    onTypesChange: (Set<NotificationPermissionKind>) -> Unit = {},
) {
    val search = rememberTextFieldState()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var actionsHeightPx by remember { mutableIntStateOf(0) }
    val query = search.text.toString().trim()
    var typesExpanded by rememberSaveable { mutableStateOf(false) }
    var reportExpanded by rememberSaveable { mutableStateOf(false) }
    val states = remember(ui.states) { ui.states.associateBy { it.target.packageName } }
    val eligible = remember(ui.states) { ui.states.filter { it.issue == null }.map { it.target.packageName }.toSet() }
    val visible = remember(ui.apps, query) {
        ui.apps.filter { query.isBlank() || it.label.contains(query, true) || it.target.packageName.contains(query, true) }
    }
    val visibleEligible = remember(visible, eligible) { visible.map { it.target.packageName }.filter { it in eligible }.toSet() }
    val chosenTypes = NotificationPermissionKind.entries.filter { it in ui.types }.map { stringResource(notificationPermissionLabel(it)) }
    val typeSummary = chosenTypes.joinToString(" · ").ifEmpty { stringResource(R.string.notification_batch_no_permissions) }
    val failures = remember(ui.operation.report) { ui.operation.report?.results.orEmpty().filter { it.issue != null } }
    LaunchedEffect(ui.operation.revision) { reportExpanded = false }

    SettingsPageSurface(
        title = stringResource(R.string.notification_batch_title), modifier = modifier,
        showBack = true, onBack = onBack, blurBackdrop = backdrop, contentScrollable = false, edgeToEdgeBottom = true,
        actions = {
            if (ui.operation.undo.isNotEmpty()) {
                BatchToolbarButton(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.notification_batch_undo), !ui.busy) { onAction(3) }
            }
            BatchToolbarButton(Icons.Rounded.Refresh, stringResource(R.string.notification_batch_refresh), !ui.busy, onRefresh)
        },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 680.dp).fillMaxSize().testTag("batchList"),
                state = listState, contentPadding = PaddingValues(bottom = with(density) { actionsHeightPx.toDp() } + 8.dp),
            ) {
                item("types") {
                    NotificationBatchExpandableCard(
                        title = stringResource(R.string.notification_batch_types), summary = typeSummary,
                        expanded = typesExpanded, childCount = 3, tag = "batchPermissions",
                        onExpandChange = { typesExpanded = it },
                    ) {
                        NotificationPermissionKind.entries.forEachIndexed { index, kind ->
                            NotificationBatchSegmentDivider(index)
                            run {SettingsSwitchPreference(
                                    checked = kind in ui.types, enabled = !ui.busy,
                                    title = stringResource(notificationPermissionLabel(kind)),
                                    onCheckedChange = { enabled -> onTypesChange(if (enabled) ui.types + kind else ui.types - kind) },
                                )}
                        }
                    }
                }
                item("result") {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = ui.operation.busy || ui.operation.failed || ui.operation.report != null,
                        enter = fadeIn(tween(160)) + expandVertically(spring(dampingRatio = 1f, stiffness = 438.65f)),
                        exit = fadeOut(tween(100)) + shrinkVertically(spring(dampingRatio = 1f, stiffness = 438.65f)),
                        modifier = Modifier.clipToBounds(),
                    ) {
                        NotificationBatchExpandableCard(
                            title = stringResource(when {
                                ui.operation.busy -> R.string.notification_batch_applying
                                ui.operation.failed -> R.string.notification_batch_action_failed
                                else -> R.string.notification_batch_result
                            }),
                            summary = ui.operation.report?.let { stringResource(R.string.notification_batch_result_counts, it.changes.size, it.unchanged, it.failed) }.orEmpty(),
                            expanded = reportExpanded && failures.isNotEmpty(), expandable = failures.isNotEmpty(),
                            childCount = failures.size, tag = "batchResults",
                            onExpandChange = { reportExpanded = it },
                        ) {
                            NotificationBatchFailures(failures, ui.apps)
                        }
                    }
                }
                item("search") {
                    Box(Modifier.testTag("batchSearch")) { FeatureSearchBar(search, hint = stringResource(R.string.notification_batch_search)) }
                }
                if (ui.readFailed) item("readFailure") {
                    SettingsGroup {
                        SettingsCardRow(stringResource(R.string.notification_batch_read_failed), summary = "",
                            onClick = if (!ui.busy) onRefresh else null)
                    }
                }
                item("selection") {
                    SettingsLazyGroupItem(index = 0, count = visible.size + 1, modifier = notificationBatchListAnimation()) {
                        NotificationBatchSelectionRow(
                            selectedCount = ui.selected.size, visibleCount = visible.size, visibleEligible = visibleEligible,
                            selected = ui.selected, enabled = !ui.busy, loading = ui.loading,
                            onSelectVisible = {
                                onSelectionChange(if (visibleEligible.all { it in ui.selected }) ui.selected - visibleEligible
                                    else ui.selected + visibleEligible)
                            },
                            onClear = { onSelectionChange(emptySet()) },
                        )
                    }
                }
                itemsIndexed(visible, key = { _, app -> "app:${app.target.packageName}" }) { index, app ->
                    SettingsLazyGroupItem(index + 1, visible.size + 1, modifier = notificationBatchListAnimation()) {
                        NotificationBatchAppRow(app, states[app.target.packageName], app.target.packageName in ui.selected,
                            !ui.busy && app.target.packageName in eligible,
                            Modifier.testTag("batchApp:${app.target.packageName}")) { checked ->
                            onSelectionChange(if (checked) ui.selected + app.target.packageName else ui.selected - app.target.packageName)
                        }
                    }
                }
                if (!ui.readFailed && visible.isEmpty()) item("status") {
                    SettingsGroup {
                        SettingsCardRow(stringResource(when {
                            ui.loading -> R.string.notification_batch_loading
                            else -> R.string.notification_batch_empty
                        }), summary = "")
                    }
                }
            }
            NotificationBatchActions(ui.canApply, ui.operation.busy, onEnable = { onAction(1) }, onDisable = { onAction(2) },
                modifier = Modifier.align(Alignment.BottomCenter).zIndex(1f).onSizeChanged { actionsHeightPx = it.height })
        }
    }
}

@Composable
internal fun NotificationBatchExpandableCard(
    title: String, summary: String, expanded: Boolean, childCount: Int,
    tag: String, onExpandChange: (Boolean) -> Unit, expandable: Boolean = true,
    content: @Composable () -> Unit,
) {
    val visibility = remember { MutableTransitionState(expanded) }
    visibility.targetState = expanded
    val transition = rememberTransition(visibility, label = "batchCardExpansion")
    val sizeSpec = spring<IntSize>(dampingRatio = 1f, stiffness = 438.65f)
    Box(Modifier.clipToBounds().testTag(tag)) {
        SettingsGroup {
            NotificationBatchHeaderSegment(transition, childCount) {
                SettingsCardRow(title, summary, showExpandArrow = expandable, expandArrowExpanded = expanded,
                    onClick = if (expandable) ({ onExpandChange(!expanded) }) else null)
            }
            transition.AnimatedVisibility(
                visible = { it },
                enter = expandVertically(sizeSpec, expandFrom = Alignment.Top) + fadeIn(tween(150, delayMillis = 30)),
                exit = shrinkVertically(sizeSpec, shrinkTowards = Alignment.Top) + fadeOut(tween(90)),
                modifier = Modifier.fillMaxWidth().clipToBounds(),
            ) { Column(Modifier.fillMaxWidth()) { content() } }
        }
    }
}

@Composable
internal fun NotificationBatchHeaderSegment(
    transition: Transition<Boolean>, childCount: Int, content: @Composable () -> Unit,
) {
    content()
}

@Composable
private fun NotificationBatchSegmentDivider(index: Int) {
    SettingsDivider()
}

@Composable
private fun NotificationBatchFailures(failures: List<NotificationMutationResult>, apps: List<NotificationBatchApp>) {
    val labels = remember(apps) { apps.associate { it.target to it.label } }
    val row: @Composable (Int, NotificationMutationResult) -> Unit = { index, result ->
        NotificationBatchSegmentDivider(index)
        run {SettingsCardRow(
                title = labels[result.mutation.target] ?: result.mutation.target.packageName,
                summary = "${stringResource(notificationPermissionLabel(result.mutation.kind))} · ${batchIssueText(requireNotNull(result.issue))}",
            )}
    }
    if (failures.size <= 4) failures.forEachIndexed { index, result -> row(index, result) }
    else LazyColumn(Modifier.fillMaxWidth().height(320.dp).clipToBounds()) {
        itemsIndexed(failures, key = { _, result -> "${result.mutation.target.packageName}:${result.mutation.kind}" }) { index, result -> row(index, result) }
    }
}

@Composable
private fun NotificationBatchSelectionRow(
    selectedCount: Int, visibleCount: Int, visibleEligible: Set<String>, selected: Set<String>,
    enabled: Boolean, loading: Boolean, onSelectVisible: () -> Unit, onClear: () -> Unit,
) {
    val count = visibleEligible.count { it in selected }
    val all = visibleEligible.isNotEmpty() && count == visibleEligible.size
    val checkbox = when { all -> ToggleableState.On; count > 0 -> ToggleableState.Indeterminate; else -> ToggleableState.Off }
    BatchSegmentSurface {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp).testTag("batchSelection"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BatchText(stringResource(R.string.notification_batch_selected, selectedCount, visibleCount), Modifier.weight(1f), secondary = true)
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = batchPrimary(), strokeWidth = 2.dp)
            Box(Modifier.size(48.dp).clickable(enabled = enabled && visibleEligible.isNotEmpty(), role = Role.Checkbox,
                onClickLabel = stringResource(R.string.notification_batch_select_visible), onClick = onSelectVisible)
                .semantics {
                    toggleableState = checkbox
                    stateDescription = "${count}/${visibleEligible.size}"
                }.testTag("batchSelectVisible"), contentAlignment = Alignment.Center) {
                BatchCheckbox(checkbox, enabled && visibleEligible.isNotEmpty())
            }
            IconButton(onClick = onClear, enabled = enabled && selected.isNotEmpty()) {
                Icon(Icons.Rounded.Close, stringResource(R.string.notification_batch_clear_selection), tint = batchForeground(secondary = true))
            }
        }
    }
}

@Composable
internal fun NotificationBatchAppRow(
    app: NotificationBatchApp, state: NotificationAppState?, selected: Boolean, enabled: Boolean,
    modifier: Modifier = Modifier, onSelected: (Boolean) -> Unit,
) {
    val selectionColor by animateColorAsState(if (selected) batchPrimary().copy(alpha = .08f) else Color.Transparent,
        tween(160), label = "batchAppSelection")
    BatchSegmentSurface {
        Column(modifier.fillMaxWidth().background(selectionColor).toggleable(selected, enabled = enabled,
            role = Role.Checkbox, onValueChange = onSelected).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NotificationBatchAppIcon(app.target.packageName)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    BatchText(app.label, fontSize = 16, weight = FontWeight.Medium, maxLines = 2)
                    BatchText(app.target.packageName, secondary = true, fontSize = 12, maxLines = 1)
                }
                BatchCheckbox(ToggleableState(selected), enabled)
            }
            if (state?.issue != null) BatchText(batchIssueText(state.issue), secondary = true, fontSize = 12)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NotificationPermissionKind.entries.forEach { kind -> NotificationPermissionBadge(kind, state?.values?.get(kind)) }
            }
        }
    }
}

@Composable
private fun BatchSegmentSurface(content: @Composable () -> Unit) {
    content()
}

@Composable
private fun BatchCheckbox(state: ToggleableState, enabled: Boolean) {
    val modifier = Modifier.clearAndSetSemantics { }
    CouiCheckbox(state = state, onClick = null, modifier = modifier.alpha(if (enabled) 1f else .38f))
}

@Composable
private fun NotificationPermissionBadge(kind: NotificationPermissionKind, value: Int?) {
    val active = value == 1 || (kind == NotificationPermissionKind.LockScreen && value == 0)
    val color by animateColorAsState(if (active) batchPrimary() else batchForeground(secondary = true), tween(160), label = "batchPermissionBadge")
    val status = when {
        value == null -> R.string.notification_batch_unknown
        kind == NotificationPermissionKind.LockScreen && value == 0 -> R.string.notification_batch_private
        value == -1000 -> R.string.notification_batch_default
        value == 1 -> R.string.notification_batch_on
        else -> R.string.notification_batch_off
    }
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = .10f))
        .heightIn(min = 24.dp).padding(horizontal = 7.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        BatchText("${stringResource(notificationPermissionLabel(kind))} ${stringResource(status)}", color = color, fontSize = 11,
            maxLines = 2, centered = true)
    }
}

@Composable
internal fun NotificationBatchActions(
    enabled: Boolean, applying: Boolean, onEnable: () -> Unit, onDisable: () -> Unit, modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().testTag("batchActions").windowInsetsPadding(WindowInsets.navigationBars
        .union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
        contentAlignment = Alignment.TopCenter) {
        Row(Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BatchButton(stringResource(if (applying) R.string.notification_batch_applying else R.string.notification_batch_enable), enabled,
                Modifier.weight(1f), onEnable, tag = "batchEnable")
            BatchButton(stringResource(R.string.notification_batch_disable), enabled, Modifier.weight(1f), onDisable, secondary = true, tag = "batchDisable")
        }
    }
}

@Composable
private fun BatchButton(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit, secondary: Boolean = false, tag: String) {
    run {
        Box(modifier.background(COUITheme.colorScheme.surface.copy(alpha = 1f), CircleShape)) {
            ColorOs17Button(label, primary = !secondary, onClick = onClick, modifier = Modifier.fillMaxWidth(), minimumHeight = 48.dp,
                enabled = enabled, buttonModifier = Modifier.testTag(tag))
        }
    }
}

@Composable
private fun BatchToolbarButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    ColorOsTopBarButton(onClick = { if (enabled) onClick() }, modifier = Modifier.alpha(if (enabled) 1f else .38f).semantics { if (!enabled) disabled() }) {
        Icon(icon, label, Modifier.size(22.dp), tint = batchForeground())
    }
}

@Composable
private fun BatchText(
    text: String, modifier: Modifier = Modifier, secondary: Boolean = false, fontSize: Int = 14,
    maxLines: Int = Int.MAX_VALUE, weight: FontWeight = FontWeight.Normal, color: Color = batchForeground(secondary), centered: Boolean = false,
) {
    val base = COUITheme.textStyles.body2
    val style: TextStyle = base.copy(fontSize = fontSize.sp, lineHeight = (fontSize + 5).sp, fontWeight = weight,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both))
    CouiText(text, modifier, color = color, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun batchForeground(secondary: Boolean = false): Color = if (secondary) COUITheme.colorScheme.onSurfaceVariantSummary else COUITheme.colorScheme.onSurface

@Composable
private fun batchPrimary(): Color = COUITheme.colorScheme.primary

internal fun notificationPermissionLabel(kind: NotificationPermissionKind): Int = when (kind) {
    NotificationPermissionKind.Notifications -> R.string.notification_batch_notifications
    NotificationPermissionKind.Banner -> R.string.notification_batch_banner
    NotificationPermissionKind.LockScreen -> R.string.notification_batch_lock_screen
}

@Composable
private fun batchIssueText(issue: NotificationBatchIssue): String = stringResource(when (issue) {
    NotificationBatchIssue.Protected -> R.string.notification_batch_protected
    NotificationBatchIssue.IdentityChanged -> R.string.notification_batch_identity_changed
    NotificationBatchIssue.StateChanged -> R.string.notification_batch_state_changed
    else -> R.string.notification_batch_unavailable
})

internal fun LazyItemScope.notificationBatchListAnimation(): Modifier = Modifier.animateItem(
    fadeInSpec = tween(180), placementSpec = null, fadeOutSpec = null,
)

@Composable
private fun NotificationBatchAppIcon(packageName: String) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) { runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    }
    val bitmap = icon
    if (bitmap == null) Icon(Icons.Rounded.Apps, null, Modifier.size(36.dp), tint = batchForeground(secondary = true))
    else Image(bitmap, null, Modifier.size(36.dp))
}
