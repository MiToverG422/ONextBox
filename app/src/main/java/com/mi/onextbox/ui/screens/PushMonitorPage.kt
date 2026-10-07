package com.mi.onextbox.ui.screens

import android.os.SystemClock
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mi.onextbox.R
import com.mi.onextbox.push.MCS_PACKAGE
import com.mi.onextbox.push.PushApp
import com.mi.onextbox.push.PushFilter
import com.mi.onextbox.push.PushMonitorRepository
import com.mi.onextbox.push.PushMonitorRules
import com.mi.onextbox.push.PushMonitorView
import com.mi.onextbox.push.PushRegistration
import com.mi.onextbox.push.PushSnapshot
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.AppUiTokens
import com.mi.onextbox.ui.common.ColorOsScrollEntranceHost
import com.mi.onextbox.ui.common.colorOsScrollEntrance
import com.mi.onextbox.ui.common.rememberColorOsScrollEntrance
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.settings.LocalMaterial3ExpressiveSegmentShapes
import com.mi.onextbox.ui.settings.Material3ExpressiveAnimatedSegmentPosition
import com.mi.onextbox.ui.settings.SettingsDivider
import io.github.suqi8.coui.kmp.basic.Chip
import io.github.suqi8.coui.kmp.basic.Text as CouiText
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
internal fun PushMonitorPage(refresh: Int) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var view by remember { mutableStateOf(PushMonitorView(PushSnapshot(), emptyList(), true)) }
    var loading by remember { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf(false) }
    var readFailed by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(refresh, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val id = UUID.randomUUID().toString()
            val started = SystemClock.elapsedRealtime()
            loading = true
            unavailable = false
            readFailed = false
            try {
                withContext(Dispatchers.IO) { PushMonitorRepository.requestRefresh(context, id) }
                while (true) {
                    try {
                        view = withContext(Dispatchers.IO) { PushMonitorRepository.read(context) }
                        now = System.currentTimeMillis()
                        readFailed = false
                        if (view.snapshot.requestId == id) {
                            loading = false
                            unavailable = false
                        } else if (SystemClock.elapsedRealtime() - started > 8000) {
                            loading = false
                            unavailable = true
                        }
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { readFailed = true; loading = false }
                    delay(if (loading) 1000 else 15_000)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { readFailed = true }
            finally { loading = false }
        }
    }
    val notice = when {
        readFailed -> stringResource(R.string.push_monitor_read_failed)
        !view.mcsInstalled -> stringResource(R.string.push_monitor_no_mcs)
        loading -> stringResource(R.string.push_monitor_loading)
        unavailable || view.snapshot.time == 0L -> stringResource(R.string.push_monitor_unavailable)
        !view.snapshot.complete -> stringResource(R.string.push_monitor_incomplete)
        else -> null
    }
    PushMonitorContent(view.apps, notice, now, view.snapshot.requestId)
}

/** Search, filters and joined push-status rows. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PushMonitorContent(
    apps: List<PushApp>,
    notice: String?,
    now: Long,
    refreshId: String = "",
    bottomInsets: WindowInsets = WindowInsets.navigationBars
        .union(WindowInsets.mandatorySystemGestures).union(WindowInsets.displayCutout),
) {
    val material = LocalAppUiStyle.current == AppUiStyle.Material3Expressive
    val search = rememberTextFieldState()
    val query = search.text.toString()
    var filter by rememberSaveable { mutableStateOf(PushFilter.All) }
    val listState = rememberLazyListState()
    val cardEntrance = rememberColorOsScrollEntrance(listState)
    val shown = remember(apps, query, filter) { PushMonitorRules.shown(apps, query, filter) }
    val bottomPadding = bottomInsets.asPaddingValues().calculateBottomPadding() + 24.dp
    BackHandler(enabled = query.isNotBlank()) { search.clearText() }

    Column(Modifier.fillMaxSize()) {
        FeatureSearchBar(search, hint = stringResource(R.string.push_monitor_search))
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
            AnimatedContent(
                targetState = notice,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "pushMonitorNotice",
            ) { message ->
                if (message != null) PushMonitorText(
                    message, Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    textAlign = TextAlign.Center,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                PushFilter.entries.forEach { option ->
                    val title = stringResource(when (option) {
                        PushFilter.All -> R.string.push_monitor_all
                        PushFilter.Registered -> R.string.push_monitor_registered
                        PushFilter.Unregistered -> R.string.push_monitor_unregistered
                    })
                    PushMonitorFilterChip(
                        selected = filter == option,
                        onClick = {
                            filter = option
                            listState.requestScrollToItem(0)
                        },
                        title = title, modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        ColorOsScrollEntranceHost(state = cardEntrance, modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = if (material) 2.dp else 0.dp, bottom = bottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(if (material) 2.dp else 0.dp),
            ) {
                if (shown.isEmpty()) item(key = "empty") {
                    PushMonitorText(
                        stringResource(R.string.push_monitor_empty),
                        Modifier.animateItem(fadeInSpec = tween(200), fadeOutSpec = tween(160)).padding(16.dp),
                    )
                }
                itemsIndexed(shown, key = { _, app -> app.packageName }) { index, app ->
                    PushMonitorRow(
                        app, index, shown.size, now, refreshId,
                        Modifier.animateItem(
                            fadeInSpec = tween(220), placementSpec = tween(280), fadeOutSpec = tween(180),
                        ),
                    )
                }
                item(key = "privacy") {
                    PushMonitorText(
                        stringResource(R.string.push_monitor_privacy),
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                        style = if (material) MaterialTheme.typography.bodySmall else {
                            COUITheme.textStyles.footnote1.copy(fontSize = 12.sp, lineHeight = 18.sp)
                        },
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
    }
}

/** Themed filter with the same selection transition. */
@Composable
private fun PushMonitorFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    modifier: Modifier,
) {
    if (LocalAppUiStyle.current == AppUiStyle.ColorOs) {
        Chip(selected = selected, onClick = onClick, label = title, modifier = modifier)
        return
    }
    val defaults = FilterChipDefaults.filterChipColors()
    val container by animateColorAsState(
        if (selected) defaults.selectedContainerColor else defaults.containerColor,
        spring(dampingRatio = 1f, stiffness = 438.65f), label = "pushFilterContainer",
    )
    val label by animateColorAsState(
        if (selected) defaults.selectedLabelColor else defaults.labelColor,
        spring(dampingRatio = 1f, stiffness = 438.65f), label = "pushFilterLabel",
    )
    val outline by animateColorAsState(
        if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
        spring(dampingRatio = 1f, stiffness = 438.65f), label = "pushFilterOutline",
    )
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        colors = defaults.copy(
            containerColor = container, selectedContainerColor = container,
            labelColor = label, selectedLabelColor = label,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected,
            borderColor = outline, selectedBorderColor = outline,
            borderWidth = 1.dp, selectedBorderWidth = 1.dp,
        ),
        label = { Text(title, Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
    )
}

/** Joined application status card. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PushMonitorRow(
    app: PushApp,
    index: Int,
    count: Int,
    now: Long,
    refreshId: String,
    modifier: Modifier,
) {
    val material = LocalAppUiStyle.current == AppUiStyle.Material3Expressive
    if (material) {
        Material3ExpressiveAnimatedSegmentPosition(index, count, durationMillis = 280) {
            SegmentedListItem(
                onClick = {},
                modifier = modifier.fillMaxWidth().animateContentSize(),
                shapes = LocalMaterial3ExpressiveSegmentShapes.current ?: ListItemDefaults.segmentedShapes(index, count),
                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
            ) {
                PushMonitorRowContent(app, now, refreshId, Modifier.padding(vertical = 4.dp))
            }
        }
    } else {
        val top by animateDpAsState(
            if (index == 0) AppUiTokens.CardCornerRadius else 0.dp,
            spring(dampingRatio = 0.9f, stiffness = 800f), label = "pushCardTop",
        )
        val bottom by animateDpAsState(
            if (index == count - 1) AppUiTokens.CardCornerRadius else 0.dp,
            spring(dampingRatio = 0.9f, stiffness = 800f), label = "pushCardBottom",
        )
        Column(
            modifier.fillMaxWidth().animateContentSize().colorOsScrollEntrance(key = app.packageName)
                .squircleSurface(COUITheme.colorScheme.surfaceContainer, top, top, bottom, bottom)
                .semantics(mergeDescendants = true) {},
        ) {
            PushMonitorRowContent(
                app, now, refreshId,
                Modifier.padding(
                    start = 16.dp, end = 16.dp,
                    top = if (index == 0) 12.dp else 10.dp,
                    bottom = if (index == count - 1) 12.dp else 10.dp,
                ),
            )
            if (index < count - 1) SettingsDivider()
        }
    }
}

@Composable
private fun PushMonitorRowContent(app: PushApp, now: Long, refreshId: String, modifier: Modifier) {
    val material = LocalAppUiStyle.current == AppUiStyle.Material3Expressive
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PushMonitorText(
                app.label, Modifier.weight(1f),
                style = if (material) MaterialTheme.typography.titleMedium else COUITheme.textStyles.headline1,
                color = if (material) MaterialTheme.colorScheme.onSurface else COUITheme.colorScheme.onSurface,
            )
            AnimatedContent(
                targetState = app.registration to refreshId,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 4 }) togetherWith fadeOut(tween(160))
                },
                label = "pushRegistration",
            ) { (registration, _) ->
                PushMonitorText(
                    stringResource(when (registration) {
                        PushRegistration.Registered -> R.string.push_monitor_registered
                        PushRegistration.Unregistered -> R.string.push_monitor_unregistered
                        PushRegistration.Unknown -> R.string.push_monitor_unknown
                        PushRegistration.SystemService -> R.string.push_monitor_system_service
                    }),
                    style = if (material) MaterialTheme.typography.labelMedium else COUITheme.textStyles.body2,
                    color = if (registration == PushRegistration.Registered) {
                        if (material) MaterialTheme.colorScheme.primary else COUITheme.colorScheme.primary
                    } else {
                        if (material) MaterialTheme.colorScheme.onSurfaceVariant else COUITheme.colorScheme.onSurfaceVariantSummary
                    },
                )
            }
        }
        PushMonitorText(app.packageName)
        if (app.packageName != MCS_PACKAGE) {
            val lastPush = if (app.lastPush > 0) {
                DateUtils.getRelativeTimeSpanString(app.lastPush, now, DateUtils.MINUTE_IN_MILLIS).toString()
            } else stringResource(R.string.push_monitor_no_record)
            val recent = stringResource(R.string.push_monitor_last_push, lastPush)
            AnimatedContent(
                targetState = recent,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "pushRecentTime",
            ) { PushMonitorText(it) }
        }
    }
}

@Composable
private fun PushMonitorText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        MaterialTheme.typography.bodySmall
    } else COUITheme.textStyles.body2,
    color: Color = if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else COUITheme.colorScheme.onSurfaceVariantSummary,
    textAlign: TextAlign? = null,
) {
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        Text(text, modifier, style = style, color = color, textAlign = textAlign)
    } else {
        CouiText(text, modifier, style = style, color = color, textAlign = textAlign)
    }
}
