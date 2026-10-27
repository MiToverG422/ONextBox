// Copyright 2025, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.basic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.SearchCleanup
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

/**
 * Possible durations of the [Snackbar].
 */
sealed interface SnackbarDuration {
    /** Show the Snackbar for a short period of time. */
    data object Short : SnackbarDuration

    /** Show the Snackbar for a long period of time. */
    data object Long : SnackbarDuration

    /** Show the Snackbar indefinitely until dismissed. */
    data object Indefinite : SnackbarDuration

    /** Show the Snackbar for a custom period of time. */
    data class Custom(val durationMillis: kotlin.Long) : SnackbarDuration {
        init {
            require(durationMillis > 0) { "durationMillis must be greater than 0" }
        }
    }
}

/**
 * Possible results of the [Snackbar].
 */
enum class SnackbarResult {
    /** The Snackbar was dismissed. */
    Dismissed,

    /** The Snackbar's action was performed. */
    ActionPerformed,
}

/**
 * Visuals for a [Snackbar].
 *
 * @param message text to be shown in the Snackbar
 * @param actionLabel optional action label to be shown in the Snackbar
 * @param withDismissAction whether to show a dismiss action in the Snackbar
 * @param duration duration of the Snackbar
 */
@Immutable
data class SnackbarVisuals(
    val message: String,
    val actionLabel: String?,
    val withDismissAction: Boolean,
    val duration: SnackbarDuration,
)

/**
 * Interface representing the data of a [Snackbar].
 */
interface SnackbarData {
    /** Visuals of the Snackbar. */
    val visuals: SnackbarVisuals

    /** Dismiss the Snackbar. */
    suspend fun dismiss()

    /** Perform the action of the Snackbar. */
    suspend fun performAction()
}

private enum class SnackbarSwipeToDismissValue {
    StartToEnd,
    EndToStart,
    Settled,
}

/**
 * State of the [SnackbarHost].
 *
 * It allows to show a [Snackbar] with a message and an optional action.
 */
@Stable
class SnackbarHostState {
    private val entries = mutableStateListOf<SnackbarEntry>()
    internal val currentSnackbars: List<SnackbarEntry> get() = entries
    suspend fun newestSnackbarData(): SnackbarData? = mutex.withLock {
        entries.firstOrNull { it.visible }?.data
    }

    suspend fun oldestSnackbarData(): SnackbarData? = mutex.withLock {
        entries.lastOrNull { it.visible }?.data
    }

    private val mutex = Mutex()
    private var idCounter = 0L

    internal suspend fun removeEntry(entry: SnackbarEntry) {
        mutex.withLock {
            entries.remove(entry)
        }
    }

    /**
     * Shows a [Snackbar] with the provided [message].
     *
     * @param message text to be shown in the Snackbar
     * @param actionLabel optional action label to be shown in the Snackbar
     * @param withDismissAction whether to show a dismiss action in the Snackbar
     * @param duration duration of the Snackbar
     * @return result of the Snackbar
     */
    suspend fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        withDismissAction: Boolean = false,
        duration: SnackbarDuration = SnackbarDuration.Short,
    ): SnackbarResult {
        val result = CompletableDeferred<SnackbarResult>()
        val visuals = SnackbarVisuals(message, actionLabel, withDismissAction, duration)

        mutex.withLock {
            val currentId = ++idCounter
            val data = object : SnackbarData {
                override val visuals = visuals
                private val snackbarMutex = Mutex()
                private var completed = false

                override suspend fun dismiss() {
                    snackbarMutex.withLock {
                        if (completed) return
                        completed = true
                    }
                    if (!result.isCompleted) result.complete(SnackbarResult.Dismissed)
                    clear()
                }

                override suspend fun performAction() {
                    snackbarMutex.withLock {
                        if (completed) return
                        completed = true
                    }
                    if (!result.isCompleted) result.complete(SnackbarResult.ActionPerformed)
                    clear()
                }

                private suspend fun clear() {
                    this@SnackbarHostState.mutex.withLock {
                        val index = entries.indexOfFirst { it.id == currentId }
                        if (index != -1) entries[index] = entries[index].copy(visible = false)
                    }
                }
            }
            val entry = SnackbarEntry(currentId, data)
            entries.add(0, entry)
        }

        return result.await()
    }

    @Immutable
    internal data class SnackbarEntry(
        val id: Long,
        val data: SnackbarData,
        val visible: Boolean = true,
    )
}

/**
 * Convert [SnackbarDuration] to milliseconds, taking into account accessibility settings.
 */
internal fun SnackbarDuration.toMillis(
    hasAction: Boolean,
    accessibilityManager: AccessibilityManager?,
): Long {
    val original = when (this) {
        SnackbarDuration.Indefinite -> Long.MAX_VALUE
        SnackbarDuration.Long -> 10000L
        SnackbarDuration.Short -> 4000L
        is SnackbarDuration.Custom -> durationMillis
    }
    if (accessibilityManager == null) {
        return original
    }
    return accessibilityManager.calculateRecommendedTimeoutMillis(
        originalTimeoutMillis = original,
        containsIcons = true,
        containsText = true,
        containsControls = hasAction,
    )
}

/**
 * Host for [Snackbar]s to be shown.
 *
 * @param state state of the [SnackbarHost]
 * @param modifier modifier to be applied to the [SnackbarHost]
 * @param canSwipeToDismiss flag of can be dismissed by swipe of the current [SnackbarHost]
 * @param content content of the [SnackbarHost]
 */
@Composable
fun SnackbarHost(
    state: SnackbarHostState,
    modifier: Modifier = Modifier,
    canSwipeToDismiss: Boolean = true,
    content: @Composable (SnackbarData) -> Unit = { Snackbar(it) },
) {
    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        LazyColumn(
            reverseLayout = true,
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
            // 28dp + the 4dp item padding = 32dp bottom offset (COUI coui_snack_bar_margin_bottom).
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            itemsIndexed(state.currentSnackbars, key = { _, entry -> entry.id }) { index, entry ->
                val visibleState = remember { MutableTransitionState(false) }
                val accessibilityManager = LocalAccessibilityManager.current

                val anchoredDraggableState = remember {
                    AnchoredDraggableState(
                        initialValue = SnackbarSwipeToDismissValue.Settled,
                    )
                }

                visibleState.targetState = entry.visible

                if (!visibleState.targetState && visibleState.isIdle) {
                    LaunchedEffect(entry) { state.removeEntry(entry) }
                }

                LaunchedEffect(entry) {
                    val duration = entry.data.visuals.duration.toMillis(
                        entry.data.visuals.actionLabel != null,
                        accessibilityManager,
                    )
                    delay(duration)

                    if (anchoredDraggableState.currentValue != SnackbarSwipeToDismissValue.Settled) return@LaunchedEffect
                    entry.data.dismiss()
                }

                LaunchedEffect(anchoredDraggableState.currentValue) {
                    if (anchoredDraggableState.currentValue != SnackbarSwipeToDismissValue.Settled) {
                        entry.data.dismiss()
                    }
                }

                AnimatedVisibility(
                    modifier = Modifier
                        .onSizeChanged { size ->
                            val width = size.width.toFloat()

                            val anchors = DraggableAnchors {
                                SnackbarSwipeToDismissValue.Settled at 0f
                                SnackbarSwipeToDismissValue.StartToEnd at width
                                SnackbarSwipeToDismissValue.EndToStart at -width
                            }
                            anchoredDraggableState.updateAnchors(anchors)
                        }
                        .anchoredDraggable(
                            state = anchoredDraggableState,
                            orientation = Orientation.Horizontal,
                            enabled = entry.visible && canSwipeToDismiss,
                            flingBehavior = AnchoredDraggableDefaults.flingBehavior(
                                state = anchoredDraggableState,
                                positionalThreshold = { distance: Float -> distance * 0.5f },
                            ),
                        )
                        .offset {
                            val offset = try {
                                anchoredDraggableState.requireOffset()
                            } catch (_: IllegalStateException) {
                                0f
                            }
                            IntOffset(offset.roundToInt(), 0)
                        }
                        .zIndex((state.currentSnackbars.size - index).toFloat())
                        .then(if (entry.visible) Modifier.animateItem() else Modifier),
                    visibleState = visibleState,
                    // COUISnackBar.setSnackBarProgress: show scales 0.8 -> 1 and fades 0 -> 1,
                    // dismiss scales 1 -> 0.8 and fades 1 -> 0; there is no translation animation.
                    enter = fadeIn(SnackbarShowAnimationSpec) + scaleIn(SnackbarShowAnimationSpec, initialScale = 0.8f),
                    exit = fadeOut(SnackbarDismissAnimationSpec) + scaleOut(SnackbarDismissAnimationSpec, targetScale = 0.8f),
                ) {
                    content(entry.data)
                }
            }
        }
    }
}

/**
 * A Snackbar is a temporary message that appears at the bottom of the screen.
 *
 * @param data data of the [Snackbar]
 * @param modifier modifier to be applied to the [Snackbar]
 * @param icon optional leading icon, mirroring COUISnackBar's iv_snack_bar_icon slot. Pass it
 *   through [SnackbarHost]'s `content` lambda: `SnackbarHost(state) { Snackbar(it, icon = { ... }) }`.
 * @param cornerRadius corner radius of the [Snackbar] when the message spans multiple lines
 * @param singleLineCornerRadius corner radius of the [Snackbar] when the message fits on a single line
 * @param colors colors of the [Snackbar]
 * @param insideMargin margin inside the [Snackbar]; the end margin is replaced by a 4dp action
 *   margin when the action button is the trailing element
 */
@Composable
fun Snackbar(
    data: SnackbarData,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    cornerRadius: Dp = SnackbarDefaults.CornerRadius,
    singleLineCornerRadius: Dp = SnackbarDefaults.SingleLineCornerRadius,
    colors: SnackbarColors = SnackbarDefaults.snackbarColors(),
    insideMargin: PaddingValues = SnackbarDefaults.InsideMargin,
) {
    val visuals = data.visuals
    val scope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current

    // COUISnackBar.adjustLayout: a snack bar whose content spans a single line keeps the large
    // "default" radius (capsule-like couiRoundCornerXXL), otherwise it falls back to couiRoundCornerL.
    var isSingleLine by remember(visuals.message) { mutableStateOf(true) }
    val shape = RoundedCornerShape(if (isSingleLine) singleLineCornerRadius else cornerRadius)

    Surface(
        modifier = modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            // COUI card margins: grid_guide_column_card_margin_start (16dp) on each side.
            .padding(horizontal = 16.dp, vertical = 4.dp)
            // COUI ShadowUtils level 3 shadow: blur 26.66dp, black at 35/255 alpha.
            .dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = 26.dp,
                    color = Color.Black,
                    alpha = 0.14f,
                    offset = DpOffset(x = 0.dp, y = 4.dp),
                ),
            )
            .pointerInput(Unit) {
                detectTapGestures { /* Consume click */ }
            },
        shape = shape,
        color = colors.containerColor,
        contentColor = colors.contentColor,
    ) {
        val actionIsTrailing = !visuals.actionLabel.isNullOrEmpty() && !visuals.withDismissAction
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                start = insideMargin.calculateStartPadding(layoutDirection),
                top = insideMargin.calculateTopPadding(),
                end = if (actionIsTrailing) SnackbarActionEndMargin else insideMargin.calculateEndPadding(layoutDirection),
                bottom = insideMargin.calculateBottomPadding(),
            ),
        ) {
            if (icon != null) {
                // COUISnackBar iv_snack_bar_icon: a 30dp icon with 16dp top/bottom margins; the row
                // already carries the 10dp child vertical margin, the padding tops it up to 16dp.
                Box(
                    modifier = Modifier
                        .padding(
                            top = (SnackbarIconVerticalMargin - insideMargin.calculateTopPadding()).coerceAtLeast(0.dp),
                            bottom = (SnackbarIconVerticalMargin - insideMargin.calculateBottomPadding()).coerceAtLeast(0.dp),
                            end = SnackbarIconSpacing,
                        )
                        .size(SnackbarDefaults.IconSize),
                    contentAlignment = Alignment.Center,
                ) {
                    icon()
                }
            }

            Text(
                text = visuals.message,
                color = colors.contentColor,
                style = SnackbarDefaults.textStyle(),
                onTextLayout = { isSingleLine = it.lineCount <= 1 },
                modifier = Modifier.weight(1f, fill = false),
            )

            if (!visuals.actionLabel.isNullOrEmpty()) {
                val onAction by rememberUpdatedState(data::performAction)
                Text(
                    text = visuals.actionLabel,
                    color = colors.actionContentColor,
                    style = SnackbarDefaults.textStyle(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = SnackbarActionSpacing)
                        // COUIMaskRippleDrawable custom ripple mask: a capsule around the label.
                        .clip(CircleShape)
                        .clickable { scope.launch { onAction() } }
                        .padding(SnackbarActionPadding),
                )
            }

            if (visuals.withDismissAction) {
                val onDismiss by rememberUpdatedState(data::dismiss)
                Icon(
                    imageVector = COUIIcons.Basic.SearchCleanup,
                    contentDescription = "Dismiss",
                    tint = colors.dismissActionContentColor,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { scope.launch { onDismiss() } },
                )
            }
        }
    }
}

/**
 * Colors for [Snackbar].
 *
 * @param containerColor container color of the Snackbar
 * @param contentColor content color of the Snackbar
 * @param actionContentColor action content color of the Snackbar
 * @param dismissActionContentColor dismiss action content color of the Snackbar
 */
@Immutable
data class SnackbarColors(
    val containerColor: Color,
    val contentColor: Color,
    val actionContentColor: Color,
    val dismissActionContentColor: Color,
)

/**
 * Defaults for [Snackbar].
 */
object SnackbarDefaults {
    /** The default corner radius when the message spans multiple lines (COUI coui_snack_bar_radius / couiRoundCornerL). */
    val CornerRadius = 16.dp

    /** The default corner radius when the message fits on a single line (COUI coui_snack_bar_radius_single_line / couiRoundCornerXXL). */
    val SingleLineCornerRadius = 24.dp

    /**
     * The default inside margin
     * (COUI coui_snack_bar_child_margin_horizontal_no_icon_start/end, coui_snack_bar_child_margin_vertical).
     */
    val InsideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp)

    /** The size of the leading icon (COUI coui_snack_bar_icon_width/height). */
    val IconSize = 30.dp

    /** The default text style of the message and action label (COUI couiTextAppearanceButton: 14sp, sans-serif-medium). */
    @Composable
    fun textStyle(): TextStyle = COUITheme.textStyles.body2.copy(fontWeight = FontWeight.Medium)

    /**
     * The default [SnackbarColors]. Container and content follow COUI couiColorSurfaceTop /
     * couiColorPrimaryNeutral; the action label uses the accent color (couiColorPrimaryTextOnPopup)
     * and the dismiss icon uses couiColorLabelTertiary.
     */
    @Composable
    fun snackbarColors(
        containerColor: Color = COUITheme.colorScheme.surfaceContainerHighest,
        contentColor: Color = COUITheme.colorScheme.onSurfaceContainer,
        actionContentColor: Color = COUITheme.colorScheme.primary,
        dismissActionContentColor: Color = COUITheme.colorScheme.onSurfaceVariantActions,
    ): SnackbarColors = remember(containerColor, contentColor, actionContentColor, dismissActionContentColor) {
        SnackbarColors(
            containerColor = containerColor,
            contentColor = contentColor,
            actionContentColor = actionContentColor,
            dismissActionContentColor = dismissActionContentColor,
        )
    }
}

/** The end margin when the action button is the trailing element (COUI coui_snack_bar_action_margin_horizontal_end). */
private val SnackbarActionEndMargin = 4.dp

/** The vertical clearance of the leading icon (COUI coui_snack_bar_icon_margin_top_horizontal). */
private val SnackbarIconVerticalMargin = 16.dp

/** The gap between the leading icon and the message (COUI coui_snack_bar_child_margin_horizontal_start). */
private val SnackbarIconSpacing = 16.dp

/** The minimum gap between the message and the action press area (COUI coui_snack_bar_child_margin_horizontal_end). */
private val SnackbarActionSpacing = 16.dp

/** The padding of the action press area (COUI text_ripple_bg_padding_horizontal/vertical). */
private val SnackbarActionPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)

/** COUISnackBar.animateSpring show spring: COUISpringForce(response = 0.3f, bounce = 0f). */
private val SnackbarShowAnimationSpec = spring<Float>(dampingRatio = 1f, stiffness = 438.65f)

/** COUISnackBar.animateSpring dismiss spring: COUISpringForce(response = 0.25f, bounce = 0f). */
private val SnackbarDismissAnimationSpec = spring<Float>(dampingRatio = 1f, stiffness = 631.65f)
