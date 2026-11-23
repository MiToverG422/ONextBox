package com.mi.onextbox.ui.screens

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import android.os.SystemClock
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import com.mi.onextbox.ui.common.AppUiTokens
import com.mi.onextbox.ui.common.ColorOsScrollEntranceHost
import com.mi.onextbox.ui.common.ColorOsSegmentBackdropView
import com.mi.onextbox.ui.common.ColorOsSegmentEdge
import com.mi.onextbox.ui.common.ColorOsSpotlightRenderer
import com.mi.onextbox.ui.common.LocalSegmentBackdropEnabled
import com.mi.onextbox.ui.common.LocalAppVisualEffects
import com.mi.onextbox.ui.common.colorOsCapsulePull
import com.mi.onextbox.ui.common.colorOsScrollEntrance
import com.mi.onextbox.ui.common.rememberColorOsScrollEntrance
import com.mi.onextbox.ui.settings.SettingsDivider
import io.github.suqi8.coui.kmp.basic.Text as CouiText
import io.github.suqi8.coui.kmp.basic.couiCardSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.abs

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
@Composable
internal fun PushMonitorContent(
    apps: List<PushApp>,
    notice: String?,
    now: Long,
    refreshId: String = "",
    bottomInsets: WindowInsets = WindowInsets.navigationBars
        .union(WindowInsets.mandatorySystemGestures).union(WindowInsets.displayCutout),
) {

    val search = rememberTextFieldState()
    val query = search.text.toString()
    var filter by rememberSaveable { mutableStateOf(PushFilter.All) }
    val listState = rememberLazyListState()
    val cardEntrance = rememberColorOsScrollEntrance(listState)
    val shown = remember(apps, query, filter) { PushMonitorRules.shown(apps, query, filter) }
    val bottomPadding = bottomInsets.asPaddingValues().calculateBottomPadding() + 24.dp
    BackHandler(enabled = query.isNotBlank()) { search.clearText() }

    Column(Modifier.fillMaxSize()) {
        PushMonitorFilterBar(
            selected = filter,
            onSelect = { option ->
                filter = option
                listState.requestScrollToItem(0)
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
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
        }
        ColorOsScrollEntranceHost(state = cardEntrance, modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 0.dp, bottom = bottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp),
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
                        style = run {
                            COUITheme.textStyles.footnote1.copy(fontSize = 12.sp, lineHeight = 18.sp)
                        },
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
    }
}

@Composable
private fun PushMonitorFilterBar(
    selected: PushFilter,
    onSelect: (PushFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = COUITheme.colorScheme.surface.luminance() < .5f
    val indicatorColor = if (dark) Color(0x33FFFFFF) else Color(0xB2FFFFFF)
    val labelColor = if (dark) Color(0xE6FFFFFF) else Color(0xE6000000)
    val baseTrackHeight = 40.dp
    val touchHeight = 48.dp
    val trackInset = (touchHeight - baseTrackHeight) / 2
    val segmentInset = 4.dp
    val host = LocalView.current
    val density = LocalDensity.current
    val trackInsetPx = with(density) { trackInset.roundToPx() }
    val effects = LocalAppVisualEffects.current
    val blurEnabled = effects.backgroundBlur && LocalSegmentBackdropEnabled.current
    val lightFrame = remember { mutableIntStateOf(0) }
    val spotlight = remember(host) {
        ColorOsSpotlightRenderer(host, ColorOsSpotlightRenderer.Style.Segment, dark) {
            lightFrame.intValue++
        }
    }
    SideEffect { spotlight.updateAppearance(darkTheme = dark) }
    DisposableEffect(spotlight) { onDispose(spotlight::cancel) }
    val selectionEdge = remember(density.density) { ColorOsSegmentEdge(density.density) }
    val filters = PushFilter.entries
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val position = remember { Animatable(selected.ordinal.toFloat(), visibilityThreshold = .002f) }
    val widthScale = remember { Animatable(1f, visibilityThreshold = .002f) }
    val heightScale = remember { Animatable(1f, visibilityThreshold = .002f) }
    val pullOffset = remember { Animatable(0f, visibilityThreshold = .25f) }
    val pullSpring = remember { spring<Float>(dampingRatio = .92f, stiffness = 145f) }
    val interactions = remember { filters.map { MutableInteractionSource() } }
    var dragging by remember { mutableStateOf(false) }
    val selectedPressed by interactions[selected.ordinal].collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        if (selectedPressed || dragging) .93f else 1f,
        spring(dampingRatio = .88f, stiffness = 450f), label = "pushFilterPress",
    )
    val selectFilter: (PushFilter) -> Unit = { option ->
        if (option != selected) {
            scope.launch {
                position.animateTo(option.ordinal.toFloat(), spring(dampingRatio = .88f, stiffness = 420f))
            }
            scope.launch {
                widthScale.animateTo(1f, spring(dampingRatio = .74f, stiffness = 255f),
                    initialVelocity = (widthScale.velocity + 3.2f).coerceAtMost(4.5f))
            }
            scope.launch {
                heightScale.animateTo(1f, spring(dampingRatio = .85f, stiffness = 340f),
                    initialVelocity = (heightScale.velocity - 2.6f).coerceAtLeast(-4f))
            }
        }
        onSelect(option)
    }
    val currentFilter by rememberUpdatedState(selected)
    val currentSelectFilter by rememberUpdatedState(selectFilter)
    fun releasePull() {
        if (pullOffset.value != 0f) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) { pullOffset.animateTo(0f, pullSpring) }
        }
    }
    val dragGesture = Modifier.pointerInput(rtl) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val inset = segmentInset.toPx()
            val segmentWidth = (size.width - 2 * inset).coerceAtLeast(1f) / filters.size
            val switchInset = 5.dp.toPx().coerceAtMost(segmentWidth / 4f)
            fun indexAt(x: Float): Int {
                val visualIndex = ((x - inset) / segmentWidth).toInt()
                    .coerceIn(0, filters.lastIndex)
                return if (rtl) filters.lastIndex - visualIndex else visualIndex
            }
            var index = indexAt(down.position.x)
            if (filters[index] != currentFilter) return@awaitEachGesture
            val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                ?: return@awaitEachGesture
            var anchor = down.position.x
            var reanchorAfterSwitch = false
            fun moveIndicator(x: Float) {
                val target = indexAt(x)
                val visualTarget = if (rtl) filters.lastIndex - target else target
                val targetStart = inset + visualTarget * segmentWidth
                if (target != index && x in (targetStart + switchInset)..(targetStart + segmentWidth - switchInset)) {
                    index = target
                    reanchorAfterSwitch = true
                    releasePull()
                    currentSelectFilter(filters[index])
                } else if (!position.isRunning) {
                    if (reanchorAfterSwitch) {
                        anchor = x
                        reanchorAfterSwitch = false
                    }
                    val allowance = if (index == 0 || index == filters.lastIndex) inset else switchInset
                    val limit = allowance + (1f - pressScale.value).coerceAtLeast(0f) * segmentWidth / 2f
                    val travel = (x - anchor) * .12f
                    val offset = travel * limit / (limit + abs(travel))
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { pullOffset.snapTo(offset) }
                }
            }
            dragging = true
            try {
                moveIndicator(start.position.x)
                horizontalDrag(start.id) { change ->
                    moveIndicator(change.position.x)
                    change.consume()
                }
            } finally {
                dragging = false
                releasePull()
            }
        }
    }
    Box(
        modifier = modifier.fillMaxWidth().then(dragGesture).colorOsCapsulePull(trackInset, spotlight)
            .onSizeChanged { size ->
                spotlight.onSizeChanged(size.width, (size.height - trackInsetPx * 2).coerceAtLeast(0))
            },
    ) {
        key(effects.outline) {
            AndroidView(
                factory = { ColorOsSegmentBackdropView(it, effects.outline) },
                modifier = Modifier.matchParentSize().padding(vertical = trackInset),
                update = { it.updateAppearance(dark, blurEnabled) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .drawWithCache {
                    val edge = segmentInset.toPx()
                    val trackTop = trackInsetPx.toFloat()
                    val indicatorTop = trackTop + edge
                    val trackHeight = (size.height - trackTop * 2).coerceAtLeast(0f)
                    val indicatorHeight = (size.height - indicatorTop * 2).coerceAtLeast(0f)
                    val segmentWidth = (size.width - edge * 2).coerceAtLeast(0f) / filters.size
                    val trackClip = Path().apply {
                        addRoundRect(RoundRect(0f, trackTop, size.width, trackTop + trackHeight,
                            CornerRadius(trackHeight / 2f)))
                    }
                    onDrawBehind {
                        val selection = position.value.coerceIn(0f, filters.lastIndex.toFloat())
                        val index = if (rtl) filters.lastIndex - selection else selection
                        val center = (edge + (index + .5f) * segmentWidth + pullOffset.value).coerceIn(0f, size.width)
                        val width = (segmentWidth * widthScale.value * (.5f + .5f * pressScale.value))
                            .coerceAtMost(2f * minOf(center, size.width - center).coerceAtLeast(0f))
                        val height = indicatorHeight * heightScale.value * pressScale.value
                        val left = center - width / 2f
                        val top = indicatorTop + (indicatorHeight - height) / 2f
                        drawRoundRect(indicatorColor, Offset(left, top), Size(width, height), CornerRadius(height / 2f))
                        if (effects.outline) drawIntoCanvas { canvas ->
                            val native = canvas.nativeCanvas
                            val saved = native.save()
                            native.translate(left, top)
                            selectionEdge.draw(native, width, height, dark, selected = true)
                            native.restoreToCount(saved)
                        }
                        lightFrame.intValue
                        if (effects.backgroundBlur && spotlight.isLit) {
                            clipPath(trackClip) {
                                drawIntoCanvas { canvas ->
                                    val native = canvas.nativeCanvas
                                    val saved = native.save()
                                    native.translate(0f, trackTop)
                                    spotlight.draw(native)
                                    native.restoreToCount(saved)
                                }
                            }
                        }
                    }
                }
                .padding(horizontal = segmentInset)
                .height(IntrinsicSize.Min)
                .selectableGroup(),
        ) {
            for (option in filters) {
                val isSelected = selected == option
                val title = stringResource(when (option) {
                    PushFilter.All -> R.string.push_monitor_all
                    PushFilter.Registered -> R.string.push_monitor_registered
                    PushFilter.Unregistered -> R.string.push_monitor_unregistered
                })
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = touchHeight)
                        .selectable(
                            selected = isSelected,
                            interactionSource = interactions[option.ordinal],
                            indication = null,
                            role = Role.Tab,
                            onClick = { selectFilter(option) },
                        )
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CouiText(
                        text = title,
                        style = COUITheme.textStyles.body1,
                        color = labelColor,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Joined application status card. */
@Composable
private fun PushMonitorRow(
    app: PushApp,
    index: Int,
    count: Int,
    now: Long,
    refreshId: String,
    modifier: Modifier,
) {

    run {
        val top by animateDpAsState(
            if (index == 0) AppUiTokens.PreferenceCardCornerRadius else 0.dp,
            spring(dampingRatio = 0.9f, stiffness = 800f), label = "pushCardTop",
        )
        val bottom by animateDpAsState(
            if (index == count - 1) AppUiTokens.PreferenceCardCornerRadius else 0.dp,
            spring(dampingRatio = 0.9f, stiffness = 800f), label = "pushCardBottom",
        )
        Column(
            modifier.fillMaxWidth().animateContentSize().colorOsScrollEntrance(key = app.packageName)
                .couiCardSurface(COUITheme.colorScheme.surfaceContainer, top, bottom, preferenceGroup = true)
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

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PushMonitorText(
                app.label, Modifier.weight(1f),
                style = COUITheme.textStyles.headline1,
                color = COUITheme.colorScheme.onSurface,
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
                    style = COUITheme.textStyles.body2,
                    color = if (registration == PushRegistration.Registered) {
                        COUITheme.colorScheme.primary
                    } else {
                        COUITheme.colorScheme.onSurfaceVariantSummary
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
    style: TextStyle = COUITheme.textStyles.body2,
    color: Color = COUITheme.colorScheme.onSurfaceVariantSummary,
    textAlign: TextAlign? = null,
) {
    run {
        CouiText(text, modifier, style = style, color = color, textAlign = textAlign)
    }
}
