// Copyright 2025, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.basic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.anim.SinOutEasing
import io.github.suqi8.coui.kmp.anim.folmeSpring
import io.github.suqi8.coui.kmp.squircle.addSquircleRect
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.CouiHapticEffect
import io.github.suqi8.coui.kmp.utils.rememberCouiHaptic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

private fun PopupPositionProvider.Align.resolve(layoutDirection: LayoutDirection): PopupPositionProvider.Align {
    if (layoutDirection == LayoutDirection.Ltr) return this
    return when (this) {
        PopupPositionProvider.Align.Start -> PopupPositionProvider.Align.End
        PopupPositionProvider.Align.End -> PopupPositionProvider.Align.Start
        PopupPositionProvider.Align.TopStart -> PopupPositionProvider.Align.TopEnd
        PopupPositionProvider.Align.TopEnd -> PopupPositionProvider.Align.TopStart
        PopupPositionProvider.Align.BottomStart -> PopupPositionProvider.Align.BottomEnd
        PopupPositionProvider.Align.BottomEnd -> PopupPositionProvider.Align.BottomStart
    }
}

private const val MAX_ITEMS_FOR_WIDTH = 8

/** Hairline thickness between adjacent popup rows (COUI coui_popup_list_divider_height). */
private val PopupItemDividerThickness = 0.33.dp

/** Horizontal inset of the row hairline (COUI coui_popup_list_default_divider_margin_horizontal). */
private val PopupItemDividerMargin = 16.dp

/**
 * Start inset of the row hairline when the menu carries leading icons
 * (COUI coui_popup_list_default_divider_margin_start_with_icon); the end inset stays at
 * [PopupItemDividerMargin]. Mirrored under RTL by COUI DefaultAdapter.getDefaultDividerDrawable.
 */
private val PopupItemDividerMarginStartWithIcon = 52.dp

/**
 * Alpha spring of the hairlines adjacent to the pressed row
 * (COUI COUITouchListView.DividerAnimationController: COUISpringForce response 0.25 / bounce 0).
 */
private val PopupItemDividerAlphaSpring = spring<Float>(dampingRatio = 1f, stiffness = 631.65f, visibilityThreshold = 0.002f)

/** Press mask spring of a popup row (COUI COUISpringForce response 0.3 / bounce 0). */
private val PopupItemPressSpring = spring<Float>(dampingRatio = 1f, stiffness = 438.65f)

/**
 * Minimum press progress a row mask reaches before a release may fade it out, so quick taps still
 * flash (COUI COUIMaskEffectDrawable DEFAULT_MIN_PROGRESS_FOR_TOUCH_ENTER_ANIMATION).
 */
private val PopupItemPressMinVisibleProgress = 0.7f

/** couiColorPress in light themes (COUI coui_color_press #1F000000). */
private val PopupItemPressLight = Color(0x1F000000)

/** couiColorPress in dark themes (COUI coui_color_press_dark #33FFFFFF). */
private val PopupItemPressDark = Color(0x33FFFFFF)

/** Travel direction of a drag crossing into a new row (COUI DividerAnimationController MOVE_FLAG_*). */
private const val MOVE_FLAG_OUT = 0
private const val MOVE_FLAG_UP = -1
private const val MOVE_FLAG_DOWN = 1

/**
 * A single popup row registered with a [PopupListGestureHost].
 *
 * Bounds are held as plain fields rather than snapshot state: they are written by
 * [ListPopupColumn]'s measure policy, which knows the exact placement order and offsets, and are
 * only read back during the pointer and draw phases, both of which run after layout on every frame.
 */
@Stable
internal class PopupListItemHandle {
    var top: Float = 0f
    var bottom: Float = 0f
    var enabled: Boolean = true
    var hasIcon: Boolean = false
    var onClick: () -> Unit = {}

    /** Press mask progress (0 → 1), driven exclusively by the owning [PopupListGestureHost]. */
    val pressProgress: Animatable<Float, AnimationVector1D> = Animatable(0f)

    private var pressJob: Job? = null

    fun contains(y: Float): Boolean = y >= top && y < bottom

    /** Runs the press-in mask animation. */
    fun enterPress(scope: CoroutineScope) {
        pressJob?.cancel()
        pressJob = scope.launch { pressProgress.animateTo(1f, PopupItemPressSpring) }
    }

    /**
     * Releases the press mask, first holding the press-in animation until it passes
     * [PopupItemPressMinVisibleProgress] so a fast tap still flashes.
     */
    fun exitPress(scope: CoroutineScope) {
        pressJob?.cancel()
        pressJob = scope.launch {
            if (pressProgress.value > 0f && pressProgress.value < PopupItemPressMinVisibleProgress) {
                try {
                    pressProgress.animateTo(1f, PopupItemPressSpring) {
                        if (value >= PopupItemPressMinVisibleProgress) throw PressMaskThresholdReached()
                    }
                } catch (_: PressMaskThresholdReached) {
                    // Minimum visible progress reached; fall through to the fade-out.
                }
            }
            pressProgress.animateTo(0f, PopupItemPressSpring)
        }
    }
}

/** Thrown to stop a deferred press-in mask once [PopupItemPressMinVisibleProgress] is reached. */
private class PressMaskThresholdReached : CancellationException("Press mask reached the minimum visible progress")

/**
 * Owns the drag-to-move-highlight gesture and the hairline dividers of one popup list, mirroring
 * COUI COUITouchListView. Rows register through [Modifier.popupListItem] and never track their own
 * press state: the host resolves the row under the pointer from the registered bounds, so dragging
 * moves the highlight and a release selects whichever row the finger currently rests on.
 */
@Stable
internal class PopupListGestureHost {
    val items = mutableStateListOf<PopupListItemHandle>()

    /**
     * Rows in placement order, written by [ListPopupColumn]'s measure policy. Registration order is
     * composition order but is not guaranteed, so the measure pass — which walks the measurables in
     * their true order — is the authority.
     */
    private var ordered: List<PopupListItemHandle> = emptyList()

    /**
     * Y offset of each drawn hairline, keyed by the index of the row above it in [ordered]. Only
     * boundaries that actually carry a hairline appear here: a group divider band replaces it
     * (COUI DefaultAdapter.getDividerView item view type 2).
     */
    private val dividerOffsets = mutableMapOf<Int, Float>()

    /** The row currently holding the highlight, or `null` while no row is pressed. */
    private var activeItem: PopupListItemHandle? = null

    /** Divider alpha tracks, indexed by the boundary between [ordered] `i` and `i + 1`. */
    private val dividerAlphas = mutableMapOf<Int, Animatable<Float, AnimationVector1D>>()

    /** True while the list has no scroll range, i.e. COUI's mIsDynamicSelection. */
    var isDynamicSelection: Boolean = true

    /** Suppresses divider fades while the list is settling a scroll (COUI onScrollStateChanged). */
    var isScrolling: Boolean = false

    val hasIcon: Boolean
        get() = items.any { it.hasIcon }

    /**
     * Publishes the placement produced by one measure pass: the rows in order and the exact Y of
     * every hairline. Called from the measure policy, so both are already expressed in the list's
     * own coordinate space and need no cross-node resolution.
     */
    fun onPlaced(rows: List<PopupListItemHandle>, hairlines: Map<Int, Float>) {
        ordered = rows
        dividerOffsets.clear()
        dividerOffsets.putAll(hairlines)
    }

    fun register(handle: PopupListItemHandle) {
        if (!items.contains(handle)) items.add(handle)
    }

    fun unregister(handle: PopupListItemHandle) {
        items.remove(handle)
        ordered = ordered - handle
    }

    /** Rows top to bottom, as placed by the last measure pass. */
    fun sortedItems(): List<PopupListItemHandle> = ordered

    /** The hairlines to paint, as `boundary index` to `Y offset` pairs. */
    fun hairlines(): Map<Int, Float> = dividerOffsets

    fun dividerAlpha(boundary: Int): Float = dividerAlphas[boundary]?.value ?: 1f

    private fun animateDivider(boundary: Int, target: Float, scope: CoroutineScope) {
        if (isScrolling) return
        val track = dividerAlphas.getOrPut(boundary) { Animatable(1f) }
        if (track.value == target && !track.isRunning) return
        scope.launch { track.animateTo(target, PopupItemDividerAlphaSpring) }
    }

    /**
     * Fades the two hairlines around [item] out ([visible] `false`) or back in, suppressing the
     * boundary the finger just crossed so the highlight does not flicker over it
     * (COUI DividerAnimationController.dividerAnim MOVE_FLAG_UP/DOWN).
     */
    private fun animateAdjacentDividers(
        item: PopupListItemHandle?,
        visible: Boolean,
        moveFlag: Int,
        scope: CoroutineScope,
    ) {
        if (item == null || !item.enabled) return
        val ordered = sortedItems()
        val index = ordered.indexOf(item)
        if (index < 0) return
        val target = if (visible) 1f else 0f
        if (index > 0 && !(visible && moveFlag == MOVE_FLAG_UP)) {
            animateDivider(index - 1, target, scope)
        }
        if (index < ordered.lastIndex && !(visible && moveFlag == MOVE_FLAG_DOWN)) {
            animateDivider(index, target, scope)
        }
    }

    /** Enters the row under [y], if any. Returns the row that took the highlight. */
    fun onDown(y: Float, scope: CoroutineScope): PopupListItemHandle? {
        val target = sortedItems().firstOrNull { it.contains(y) }
        activeItem = target
        if (target != null && target.enabled) {
            target.enterPress(scope)
            animateAdjacentDividers(target, visible = false, moveFlag = MOVE_FLAG_OUT, scope = scope)
        }
        return target
    }

    /** Whether a move to [y] would hand the highlight to a different, enabled row. */
    fun willChangeRow(y: Float): Boolean {
        val target = sortedItems().firstOrNull { it.contains(y) } ?: return false
        return target !== activeItem && target.enabled
    }

    /**
     * Moves the highlight to the row under [y]. Only meaningful in dynamic selection mode; the
     * scrollable case releases the highlight through [cancel] once the drag passes the touch slop.
     */
    fun onMoveTo(y: Float, scope: CoroutineScope) {
        val previous = activeItem
        val ordered = sortedItems()
        val target = ordered.firstOrNull { it.contains(y) }
        if (target == null) {
            cancel(scope)
            return
        }
        if (target === previous) return

        val moveFlag = when {
            previous == null -> MOVE_FLAG_OUT
            !target.enabled -> MOVE_FLAG_OUT
            ordered.indexOf(target) > ordered.indexOf(previous) -> MOVE_FLAG_DOWN
            else -> MOVE_FLAG_UP
        }
        previous?.exitPress(scope)
        animateAdjacentDividers(previous, visible = true, moveFlag = moveFlag, scope = scope)

        activeItem = target
        if (target.enabled) {
            target.enterPress(scope)
            animateAdjacentDividers(target, visible = false, moveFlag = MOVE_FLAG_OUT, scope = scope)
        }
    }

    /** Releases the highlight without selecting, e.g. when the finger leaves every row. */
    fun cancel(scope: CoroutineScope) {
        val previous = activeItem ?: return
        activeItem = null
        previous.exitPress(scope)
        animateAdjacentDividers(previous, visible = true, moveFlag = MOVE_FLAG_OUT, scope = scope)
    }

    /** Selects the row the finger currently rests on (COUI resolves the target at UP, not at DOWN). */
    fun onUp(scope: CoroutineScope) {
        val target = activeItem
        cancel(scope)
        if (target != null && target.enabled) target.onClick()
    }
}

internal val LocalPopupListGestureHost = staticCompositionLocalOf<PopupListGestureHost?> { null }

/**
 * Records where an anchor was last touched, so a popup can open at the finger rather than centred on
 * the anchor — COUI `PreciseClickHelper` / `PreciseLongPressHelper`, which stash the DOWN `(x, y)`
 * and pass it to the click callback, after which `PopupMenuLocateHelper.setAnchor` collapses the
 * anchor bounds to that single point.
 *
 * The point is kept in **window** coordinates, because the composable that positions the popup is
 * usually not the node that receives the touch (a preference hosts its popup inside its trailing
 * action slot, while the whole row is what gets tapped), so an anchor-local offset would be measured
 * against the wrong origin.
 *
 * [Offset.Unspecified] means "no point recorded": the popup then falls back to centring on the
 * anchor, which is also COUI's TalkBack path (`PreciseClickHelper` substitutes the view centre when
 * TalkBack is enabled or the recorded point is still the initial `(0, 0)`).
 */
@Stable
class PreciseClickState {
    /** The last recorded touch point in window coordinates, or [Offset.Unspecified] if none. */
    var touchPoint: Offset by mutableStateOf(Offset.Unspecified)
        internal set

    /**
     * Coordinates of the anchor the point was recorded against, used to convert the touch offset
     * into window space. Held here rather than in a `remember` so [preciseClickAnchor] stays a plain
     * modifier factory instead of a `composed` one.
     */
    internal var anchorCoordinates: LayoutCoordinates? = null

    /** Discards the recorded point, restoring anchor-centred positioning. */
    fun clear() {
        touchPoint = Offset.Unspecified
    }
}

/** Creates and remembers a [PreciseClickState] for a point-anchored popup. */
@Composable
fun rememberPreciseClickState(): PreciseClickState = remember { PreciseClickState() }

/**
 * Records the DOWN position of each touch into [state], mirroring COUI `PreciseClickHelper`'s
 * `OnTouchListener`. Attach to the anchor of a popup that was given the same [PreciseClickState] so
 * the popup opens at the finger.
 *
 * The handler observes in [PointerEventPass.Initial] and consumes nothing, so the anchor's own
 * `clickable` still sees the whole gesture; accessibility clicks produce no pointer event at all and
 * therefore leave the point unrecorded, which is exactly COUI's TalkBack fallback.
 */
fun Modifier.preciseClickAnchor(state: PreciseClickState): Modifier = this
    .onGloballyPositioned { state.anchorCoordinates = it }
    .pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val anchor = state.anchorCoordinates
                state.touchPoint = if (anchor != null && anchor.isAttached) {
                    anchor.localToWindow(down.position)
                } else {
                    Offset.Unspecified
                }
            }
        }
    }

/**
 * Parent data a registered row exposes to [ListPopupColumn]'s measure policy, so the policy can tell
 * selectable rows apart from group divider bands and headers without inspecting the composition.
 */
private class PopupListItemParentData(val handle: PopupListItemHandle) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@PopupListItemParentData
}

private val IntrinsicMeasurable.popupListItemHandle: PopupListItemHandle?
    get() = (parentData as? PopupListItemParentData)?.handle

/**
 * Registers a popup row with the enclosing [PopupListGestureHost] so the list can own its press
 * highlight, and draws the row's press mask (a flat, square-cornered `couiColorPress` rect, as COUI
 * ListItemMaskEffectDrawable disables the round style and the focused state for menu rows).
 *
 * The row keeps its own click semantics: the accessibility action below is registered independently
 * of the pointer tracking, matching COUI's TalkBack path, which bypasses the drag gesture entirely
 * and calls `performItemClick`.
 *
 * @param selected Whether the row is the selected option.
 * @param enabled Whether the row reacts to input.
 * @param role The accessibility [Role] exposed for the row.
 * @param hasIcon Whether the row shows a leading icon; widens the hairline's start inset.
 * @param onClick Invoked when the row is selected, by pointer or by accessibility action.
 */
@Composable
internal fun Modifier.popupListItem(
    selected: Boolean,
    enabled: Boolean,
    role: Role,
    hasIcon: Boolean,
    onClick: () -> Unit,
): Modifier {
    val host = LocalPopupListGestureHost.current ?: return this.selectable(
        selected = selected,
        enabled = enabled,
        role = role,
        onClick = onClick,
    )

    val handle = remember { PopupListItemHandle() }
    val currentOnClick by rememberUpdatedState(onClick)
    handle.enabled = enabled
    handle.hasIcon = hasIcon
    handle.onClick = remember { { currentOnClick() } }

    DisposableEffect(host, handle) {
        host.register(handle)
        onDispose { host.unregister(handle) }
    }

    val parentData = remember(handle) { PopupListItemParentData(handle) }
    val isDark = COUITheme.colorScheme.background.luminance() < 0.5f
    val maskColor = if (isDark) PopupItemPressDark else PopupItemPressLight

    return this
        .then(parentData)
        .drawBehind {
            val progress = handle.pressProgress.value
            if (progress > 0f) {
                drawRect(color = maskColor, alpha = progress.coerceIn(0f, 1f))
            }
        }
        .semantics(mergeDescendants = true) {
            this.role = role
            this.selected = selected
            if (enabled) {
                onClick {
                    currentOnClick()
                    true
                }
            } else {
                disabled()
            }
        }
}

/**
 * A column that automatically aligns the width to the widest item.
 *
 * The column owns the COUI popup gesture: a single pointer handler resolves the row under the
 * finger, so dragging moves the press highlight between rows and a release selects the row the
 * finger currently rests on.
 *
 * It also reserves real layout space for the hairline between every adjacent pair of selectable
 * rows, matching COUI, whose adapter interleaves an actual divider `View` of
 * `coui_popup_list_divider_height` between items (DefaultAdapter.getDefaultDividerView). Because the
 * hairlines are placed by this measure policy, the exact row bounds and hairline offsets are known
 * during layout and are published to the host directly — no cross-node coordinate resolution, which
 * is what previously left the bounds at zero (a child is positioned before its parent).
 *
 * @param content The items
 */
@Composable
fun ListPopupColumn(
    content: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()
    val host = remember { PopupListGestureHost() }
    val coroutineScope = rememberCoroutineScope()
    val performHaptic = rememberCouiHaptic()
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val layoutDirection = LocalLayoutDirection.current
    val dividerColor = COUITheme.colorScheme.dividerLine

    host.isDynamicSelection = !scrollState.canScrollForward && !scrollState.canScrollBackward
    host.isScrolling = scrollState.isScrollInProgress

    val measurePolicy = remember(host) {
        object : MeasurePolicy {
            /** Width clamp shared by the measure and intrinsic passes (COUI min/max window width). */
            private fun Density.resolveIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int {
                val widthCount = min(MAX_ITEMS_FOR_WIDTH, measurables.size)
                var maxIntrinsic = 0
                for (i in 0 until widthCount) {
                    val w = measurables[i].maxIntrinsicWidth(height)
                    if (w > maxIntrinsic) maxIntrinsic = w
                }
                return maxIntrinsic
            }

            override fun MeasureScope.measure(
                measurables: List<Measurable>,
                constraints: Constraints,
            ): MeasureResult {
                val minPx = ListPopupDefaults.MinWidth.roundToPx()
                val maxPx = ListPopupDefaults.MaxWidth.roundToPx()
                val maxIntrinsic = resolveIntrinsicWidth(measurables, constraints.maxHeight)
                val parentMin = constraints.minWidth
                val parentMax = constraints.maxWidth
                val upper = maxOf(maxPx, parentMin).coerceAtMost(parentMax)
                val lower = maxOf(minPx, parentMin).coerceAtMost(upper)
                val listWidth = maxIntrinsic.coerceIn(lower, upper)

                // At least 1px: coui_popup_list_divider_height is 0.33dp, which floors to zero on
                // low densities, and a hairline that rounds away would be invisible.
                val hairline = PopupItemDividerThickness.toPx().toInt().coerceAtLeast(1)
                val childConstraints = constraints.copy(minWidth = listWidth, maxWidth = listWidth, minHeight = 0)

                // COUI COUIPopupListWindow.measurePopupWindow stops accumulating once the next row
                // would exceed getMaxMainMenuHeight(), and backs off the divider it already added.
                val maxHeight = constraints.maxHeight

                val placeables = ArrayList<Placeable>(measurables.size)
                val offsets = ArrayList<Int>(measurables.size)
                val rows = ArrayList<PopupListItemHandle>(measurables.size)
                val hairlines = mutableMapOf<Int, Float>()

                var listHeight = 0
                var pendingHairline = 0
                var previousWasRow = false

                for (i in measurables.indices) {
                    val measurable = measurables[i]
                    val handle = measurable.popupListItemHandle
                    val isRow = handle != null
                    // A hairline only separates two adjacent selectable rows; a group divider band
                    // or a header owns the gap itself (COUI item view types 2 and 5).
                    val gap = if (isRow && previousWasRow) hairline else 0

                    val placeable = measurable.measure(childConstraints)
                    if (listHeight + gap + placeable.height > maxHeight && placeables.isNotEmpty()) {
                        listHeight -= pendingHairline
                        break
                    }

                    if (gap > 0) hairlines[rows.lastIndex] = (listHeight).toFloat()
                    listHeight += gap
                    offsets.add(listHeight)
                    placeables.add(placeable)

                    if (handle != null) {
                        handle.top = listHeight.toFloat()
                        handle.bottom = (listHeight + placeable.height).toFloat()
                        rows.add(handle)
                    }
                    listHeight += placeable.height
                    pendingHairline = gap
                    previousWasRow = isRow
                }

                host.onPlaced(rows, hairlines)

                return layout(listWidth, listHeight) {
                    for (i in placeables.indices) {
                        placeables[i].placeRelative(0, offsets[i])
                    }
                }
            }

            override fun IntrinsicMeasureScope.minIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>,
                width: Int,
            ): Int {
                val minPx = ListPopupDefaults.MinWidth.roundToPx()
                val maxPx = ListPopupDefaults.MaxWidth.roundToPx()
                val listWidth = resolveIntrinsicWidth(measurables, Int.MAX_VALUE).coerceIn(minPx, maxPx)
                val hairline = PopupItemDividerThickness.toPx().toInt().coerceAtLeast(1)

                var height = 0
                var previousWasRow = false
                for (measurable in measurables) {
                    val isRow = measurable.popupListItemHandle != null
                    if (isRow && previousWasRow) height += hairline
                    height += measurable.minIntrinsicHeight(listWidth)
                    previousWasRow = isRow
                }
                return height
            }
        }
    }

    Layout(
        content = {
            CompositionLocalProvider(LocalPopupListGestureHost provides host) {
                content()
            }
        },
        modifier = Modifier
            .focusGroup()
            .height(IntrinsicSize.Min)
            .verticalScroll(state = scrollState)
            .popupListGestures(host, coroutineScope, touchSlop, performHaptic)
            .drawWithContent {
                drawContent()
                drawPopupItemDividers(host, dividerColor, layoutDirection)
            },
        measurePolicy = measurePolicy,
    )
}

/**
 * The COUI popup drag-to-select gesture (COUI COUITouchListView.dispatchTouchEvent). A single
 * handler on the list owns DOWN/MOVE/UP: in dynamic selection mode (a list with no scroll range)
 * dragging moves the highlight across rows with a haptic per crossing; in the scrollable case the
 * highlight is released once the drag passes the touch slop so scrolling wins.
 */
private fun Modifier.popupListGestures(
    host: PopupListGestureHost,
    scope: CoroutineScope,
    touchSlop: Float,
    performHaptic: (CouiHapticEffect) -> Unit,
): Modifier = pointerInput(host) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val downY = down.position.y
            var pressed = host.onDown(downY, scope) != null
            var released = false

            while (pressed && !released) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change: PointerInputChange = event.changes.firstOrNull { it.id == down.id } ?: break

                if (event.changes.size > 1) {
                    // A second pointer cancels the selection, as COUI does on ACTION_POINTER_DOWN.
                    host.cancel(scope)
                    pressed = false
                    break
                }

                if (change.changedToUpIgnoreConsumed()) {
                    host.onUp(scope)
                    released = true
                    break
                }

                val y = change.position.y
                if (host.isDynamicSelection) {
                    val crossing = host.willChangeRow(y)
                    host.onMoveTo(y, scope)
                    if (crossing) performHaptic(CouiHapticEffect.Switch)
                } else if (abs(y - downY) > touchSlop) {
                    host.cancel(scope)
                    pressed = false
                }
            }
        }
    }
}

/**
 * Draws the hairline in each gap the measure policy reserved between two adjacent rows. The line is
 * inset horizontally — COUI's divider `View` is `match_parent` but its background is an
 * `InsetDrawable` (DefaultAdapter.getDefaultDividerDrawable), so only the drawn line is inset.
 */
private fun DrawScope.drawPopupItemDividers(
    host: PopupListGestureHost,
    color: Color,
    layoutDirection: LayoutDirection,
) {
    val hairlines = host.hairlines()
    if (hairlines.isEmpty()) return

    val thickness = PopupItemDividerThickness.toPx().coerceAtLeast(1f)
    val margin = PopupItemDividerMargin.toPx()
    val startMargin = if (host.hasIcon) PopupItemDividerMarginStartWithIcon.toPx() else margin
    val left = if (layoutDirection == LayoutDirection.Rtl) margin else startMargin
    val right = if (layoutDirection == LayoutDirection.Rtl) startMargin else margin
    val width = (size.width - left - right).coerceAtLeast(0f)
    if (width <= 0f) return

    for ((boundary, y) in hairlines) {
        val alpha = host.dividerAlpha(boundary)
        if (alpha <= 0f) continue
        drawRect(
            color = color,
            alpha = alpha.coerceIn(0f, 1f),
            topLeft = Offset(left, y),
            size = Size(width = width, height = thickness),
        )
    }
}

@Stable
interface PopupPositionProvider {
    /**
     * Calculate the position (offset) of Popup
     *
     * @param anchorBounds Bounds of the anchored (parent) component
     * @param windowBounds Bounds of the safe area of window (excluding the [WindowInsets.Companion.statusBars],
     *   [WindowInsets.Companion.navigationBars] and [WindowInsets.Companion.captionBar])
     * @param layoutDirection [LayoutDirection]
     * @param popupContentSize Actual size of the popup content
     * @param popupMargin (Extra) Margins for the popup content. See [PopupPositionProvider.getMargins]
     * @param alignment Alignment of the popup (relative to the window). See [PopupPositionProvider.Align]
     */
    fun calculatePosition(
        anchorBounds: IntRect,
        windowBounds: IntRect,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
        popupMargin: IntRect,
        alignment: Align,
    ): IntOffset

    /**
     * (Extra) Margins for the popup content.
     */
    fun getMargins(): PaddingValues

    /**
     * Position relative to the window, not relative to the anchor!
     */
    enum class Align {
        Start,
        End,
        TopStart,
        TopEnd,
        BottomStart,
        BottomEnd,
    }
}

object ListPopupDefaults {
    /**
     * Scale enter spring (COUI main-menu enter COUISpringForce response 0.35 / bounce 0.2).
     */
    val FractionAnimationSpec = spring(dampingRatio = 0.8f, stiffness = 322.27f, visibilityThreshold = 0.0001f)

    /**
     * Scale exit spring (COUI main-menu exit scale COUISpringForce response 0.3 / bounce 0). COUI's
     * exit is asymmetric: the scale settles on its own spring while [AlphaExitAnimationSpec] drives
     * the faster fade and the unmount timing.
     */
    val FractionExitAnimationSpec = spring<Float>(dampingRatio = 1f, stiffness = 438.65f, visibilityThreshold = 0.0001f)

    /**
     * Content alpha enter spring (COUI main-menu enter alpha COUISpringForce
     * response 0.35 / bounce 0.2, mapped by [folmeSpring]).
     */
    val AlphaEnterAnimationSpec = folmeSpring<Float>(damping = 0.8f, response = 0.35f)

    /**
     * Content alpha exit spring; also the master timing for unmounting the popup
     * (COUI main-menu exit alpha COUISpringForce response 0.25 / bounce 0). The Compose
     * default visibility threshold avoids COUI's long invisible tap-blocking tail.
     */
    val AlphaExitAnimationSpec = folmeSpring<Float>(damping = 1f, response = 0.25f)

    /**
     * Extra clearance reserved below the status bar before the popup may be placed
     * (COUI coui_popup_list_window_margin_top_or_bottom_barrier).
     */
    val TopBarrier = 16.dp

    /**
     * Extra clearance reserved above the navigation bar before the popup may be placed
     * (COUI coui_popup_list_window_margin_bottom_barrier_with_navigation).
     */
    val BottomBarrier = 32.dp

    /** Default animation spec driving the background dim while entering. */
    val DimEnterAnimationSpec = tween<Float>(durationMillis = 300, easing = SinOutEasing)

    /** Default animation spec driving the background dim while exiting. */
    val DimExitAnimationSpec = tween<Float>(durationMillis = 150, easing = SinOutEasing)

    /** Re-enter spring used when a cancelled back gesture restores the popup; matches [FractionAnimationSpec]. */
    val ResetAnimationSpec = spring(dampingRatio = 0.8f, stiffness = 322.27f, visibilityThreshold = 0.0001f)

    /**
     * Default minimum width of the popup (COUI coui_popup_list_window_min_width).
     * Also matches the lower clamp inside [ListPopupColumn]'s width measurement.
     */
    val MinWidth = 178.dp

    /**
     * Default maximum width of the popup window (COUI coui_popup_list_window_max_width).
     * Also matches the upper clamp inside [ListPopupColumn]'s width measurement.
     */
    val MaxWidth = 232.dp

    /**
     * Default minimum height the popup will occupy when measured. Used as the floor for the
     * effective `maxHeight` and `minHeight` constraints during placement.
     */
    val MinPopupHeight = 50.dp

    /**
     * The horizontal margin a dropdown keeps from the window edges, so the menu never sits flush to
     * a screen edge. Stands in for COUI's responsive-grid barrier, whose margin lives in the vendor
     * `responsiveui` component; measured at 16dp on ColorOS 16.
     */
    val HorizontalBarrier = 16.dp

    /**
     * Creates a [PopupPositionProvider] that anchors the popup directly below (or above when there
     * is no room) the anchor, used by dropdown-style list popups.
     *
     * When the anchor bounds arrive collapsed to a single point — which is what
     * [Modifier.preciseClickAnchor] produces from the recorded touch point — the popup is centred on
     * that point and the vertical gap to the anchor is dropped, matching COUI
     * `PopupMenuLocateHelper.setAnchor`: it collapses `mAnchorBounds` to `(left + x, top + y)` and
     * pairs it with the context rule's EMPTY_OUTSETS instead of DEFAULT_ANCHOR_OUTSETS.
     *
     * @param verticalMargin The extra vertical margin between the popup and the anchor.
     * @param horizontalMargin The extra horizontal margin applied to the popup.
     */
    fun dropdownPositionProvider(
        verticalMargin: Dp = 8.dp,
        horizontalMargin: Dp = HorizontalBarrier,
    ): PopupPositionProvider = object : PopupPositionProvider {
        private val margins = PaddingValues(horizontal = horizontalMargin, vertical = verticalMargin)

        override fun calculatePosition(
            anchorBounds: IntRect,
            windowBounds: IntRect,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize,
            popupMargin: IntRect,
            alignment: PopupPositionProvider.Align,
        ): IntOffset {
            // COUI PopupMenuLocateHelper centres the menu on the anchor and then clamps it into
            // the available bounds; the requested Align only matters for context menus. The
            // available width is inset on BOTH sides, so the menu never sits flush to a screen edge.
            val availableLeft = windowBounds.left + popupMargin.left
            val availableRight = windowBounds.right - popupMargin.right
            val available = availableRight - availableLeft

            // A zero-area anchor is a recorded touch point; COUI then uses EMPTY_OUTSETS, so the
            // 8dp gap collapses and the below/above test runs against the point itself.
            val isPointAnchored = anchorBounds.width == 0 && anchorBounds.height == 0
            val verticalGap = if (isPointAnchored) 0 else popupMargin.bottom
            val topGap = if (isPointAnchored) 0 else popupMargin.top

            val centeredX = anchorBounds.left + anchorBounds.width / 2 - popupContentSize.width / 2
            val offsetX = if (popupContentSize.width > available) {
                availableLeft + (available - popupContentSize.width) / 2
            } else {
                centeredX
            }
            val offsetY = if (windowBounds.bottom - anchorBounds.bottom > popupContentSize.height) {
                // Show below
                anchorBounds.bottom + verticalGap
            } else if (anchorBounds.top - windowBounds.top > popupContentSize.height) {
                // Show above
                anchorBounds.top - popupContentSize.height - topGap
            } else {
                // Middle
                anchorBounds.top + anchorBounds.height / 2 - popupContentSize.height / 2
            }
            return IntOffset(
                x = offsetX.coerceIn(
                    availableLeft,
                    (availableRight - popupContentSize.width).coerceAtLeast(availableLeft),
                ),
                y = offsetY.coerceIn(
                    (windowBounds.top + popupMargin.top).coerceAtMost(windowBounds.bottom - popupContentSize.height - popupMargin.bottom),
                    windowBounds.bottom - popupContentSize.height - popupMargin.bottom,
                ),
            )
        }

        override fun getMargins(): PaddingValues = margins
    }

    /** Default dropdown [PopupPositionProvider] created by [dropdownPositionProvider]. */
    val DropdownPositionProvider: PopupPositionProvider = dropdownPositionProvider()

    /** A [PopupPositionProvider] that anchors the popup to a corner of the anchor for context menus. */
    val ContextMenuPositionProvider = object : PopupPositionProvider {
        override fun calculatePosition(
            anchorBounds: IntRect,
            windowBounds: IntRect,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize,
            popupMargin: IntRect,
            alignment: PopupPositionProvider.Align,
        ): IntOffset {
            val offsetX: Int
            val offsetY: Int
            when (alignment.resolve(layoutDirection)) {
                PopupPositionProvider.Align.TopStart -> {
                    offsetX = anchorBounds.left + popupMargin.left
                    offsetY = anchorBounds.bottom + popupMargin.top
                }

                PopupPositionProvider.Align.TopEnd -> {
                    offsetX = anchorBounds.right - popupContentSize.width - popupMargin.right
                    offsetY = anchorBounds.bottom + popupMargin.top
                }

                PopupPositionProvider.Align.BottomStart -> {
                    offsetX = anchorBounds.left + popupMargin.left
                    offsetY = anchorBounds.top - popupContentSize.height - popupMargin.bottom
                }

                PopupPositionProvider.Align.BottomEnd -> {
                    offsetX = anchorBounds.right - popupContentSize.width - popupMargin.right
                    offsetY = anchorBounds.top - popupContentSize.height - popupMargin.bottom
                }

                else -> {
                    // Fallback
                    offsetX = if (alignment.resolve(layoutDirection) == PopupPositionProvider.Align.End) {
                        anchorBounds.right - popupContentSize.width - popupMargin.right
                    } else {
                        anchorBounds.left + popupMargin.left
                    }
                    offsetY = if (windowBounds.bottom - anchorBounds.bottom > popupContentSize.height) {
                        // Show below
                        anchorBounds.bottom + popupMargin.bottom
                    } else if (anchorBounds.top - windowBounds.top > popupContentSize.height) {
                        // Show above
                        anchorBounds.top - popupContentSize.height - popupMargin.top
                    } else {
                        // Middle
                        anchorBounds.top + anchorBounds.height / 2 - popupContentSize.height / 2
                    }
                }
            }
            return IntOffset(
                x = offsetX.coerceIn(
                    windowBounds.left,
                    (windowBounds.right - popupContentSize.width - popupMargin.right).coerceAtLeast(windowBounds.left),
                ),
                y = offsetY.coerceIn(
                    (windowBounds.top + popupMargin.top).coerceAtMost(windowBounds.bottom - popupContentSize.height - popupMargin.bottom),
                    windowBounds.bottom - popupContentSize.height - popupMargin.bottom,
                ),
            )
        }

        override fun getMargins(): PaddingValues = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
    }
}

/** Corner radius of the popup container (COUI couiRoundCornerM, coui_round_corner_m 12dp). */
internal val PopupCornerRadius = 12.dp

/** COUI popup surface in light themes (coui_popup_window_background -> ?couiColorSurfaceTop, coui_color_surface_top #FFFFFF). */
private val PopupSurfaceLight = Color.White

/** COUI popup surface in dark themes (coui_color_surface_top #333333, values-night). */
private val PopupSurfaceDark = Color(0xFF333333)

/**
 * Resolves the COUI popup container color for the current theme: the popup floats above
 * arbitrary content, so COUI uses the opaque couiColorSurfaceTop pair (#FFFFFF / #333333)
 * instead of the translucent dark card color (coui_popup_window_background).
 */
@Composable
internal fun popupSurfaceColor(): Color = if (COUITheme.colorScheme.background.luminance() < 0.5f) PopupSurfaceDark else PopupSurfaceLight

/**
 * Drop shadow of the popup container, calibrated to COUI SHADOW_LV4: 26.66dp blur at 13.7% black
 * (coui_shadow_color_lv4), offset down 3.33dp (coui_shadow_elevation_four).
 * Shared with the cascading popup surfaces so main and cascading menus cast identical shadows.
 */
internal val PopupShadow: Shadow = Shadow(
    radius = 26.66.dp,
    color = Color.Black.copy(alpha = 0.137f),
    spread = 0.dp,
    offset = DpOffset(0.dp, 3.33.dp),
)

internal fun safeTransformOrigin(x: Float, y: Float): TransformOrigin {
    val safeX = if (x.isNaN() || x < 0f) 0f else x
    val safeY = if (y.isNaN() || y < 0f) 0f else y
    return TransformOrigin(safeX, safeY)
}

/**
 * Describes how the popup is placed relative to its anchor, used to drive the directional reveal
 * and transform origin.
 *
 * @property showBelow Whether the popup is shown below the anchor.
 * @property showAbove Whether the popup is shown above the anchor.
 * @property isRightAligned Whether the popup is aligned to the right edge of the anchor.
 */
@Immutable
data class PopupLayoutPosition(
    val showBelow: Boolean,
    val showAbove: Boolean,
    val isRightAligned: Boolean,
)

/**
 * The resolved layout information for a list popup, produced by [rememberListPopupLayoutInfo].
 *
 * @property windowBounds Bounds of the safe area of the window the popup is placed within.
 * @property popupMargin The (extra) margins applied around the popup content.
 * @property effectiveTransformOrigin The transform origin in window coordinates used to scale the popup from its anchor corner.
 * @property localTransformOrigin The transform origin local to the popup content used by its [graphicsLayer] scaling.
 * @property popupLayoutPosition The resolved [PopupLayoutPosition] describing how the popup is placed relative to its anchor.
 */
@Immutable
data class ListPopupLayoutInfo(
    val windowBounds: IntRect,
    val popupMargin: IntRect,
    val effectiveTransformOrigin: TransformOrigin,
    val localTransformOrigin: TransformOrigin,
    val popupLayoutPosition: PopupLayoutPosition,
)

/**
 * Computes and remembers the [ListPopupLayoutInfo] for a list popup from its anchor and content size.
 *
 * @param alignment The [PopupPositionProvider.Align] of the popup relative to the window.
 * @param popupPositionProvider The [PopupPositionProvider] that computes the popup offset and margins.
 * @param parentBounds The bounds of the anchor (parent) component in window coordinates.
 * @param popupContentSize The measured size of the popup content; [IntSize.Zero] before it is measured.
 */
@Composable
fun rememberListPopupLayoutInfo(
    alignment: PopupPositionProvider.Align,
    popupPositionProvider: PopupPositionProvider,
    parentBounds: IntRect,
    popupContentSize: IntSize,
): ListPopupLayoutInfo {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val layoutDirection = LocalLayoutDirection.current
    val displayCutout = WindowInsets.displayCutout
    val statusBars = WindowInsets.statusBars
    val navigationBars = WindowInsets.navigationBars
    val captionBar = WindowInsets.captionBar

    val margins = popupPositionProvider.getMargins()
    val popupMargin = remember(layoutDirection, density, margins) {
        with(density) {
            IntRect(
                left = margins.calculateLeftPadding(layoutDirection).roundToPx(),
                top = margins.calculateTopPadding().roundToPx(),
                right = margins.calculateRightPadding(layoutDirection).roundToPx(),
                bottom = margins.calculateBottomPadding().roundToPx(),
            )
        }
    }

    val containerSize = windowInfo.containerSize

    val windowBounds = remember(
        layoutDirection,
        density,
        displayCutout,
        statusBars,
        navigationBars,
        captionBar,
        containerSize,
    ) {
        with(density) {
            // COUI reserves an extra barrier inside the raw insets before the menu may be placed.
            IntRect(
                left = displayCutout.getLeft(this, layoutDirection),
                top = statusBars.getTop(this) + ListPopupDefaults.TopBarrier.roundToPx(),
                right = containerSize.width - displayCutout.getRight(this, layoutDirection),
                bottom = containerSize.height - navigationBars.getBottom(this) - captionBar.getBottom(this) -
                    ListPopupDefaults.BottomBarrier.roundToPx(),
            )
        }
    }

    val predictedTransformOrigin = remember(alignment, popupMargin, parentBounds, layoutDirection, containerSize) {
        val xInWindow = when (alignment.resolve(layoutDirection)) {
            PopupPositionProvider.Align.End,
            PopupPositionProvider.Align.TopEnd,
            PopupPositionProvider.Align.BottomEnd,
            -> parentBounds.right - popupMargin.right

            else -> parentBounds.left + popupMargin.left
        }
        val yInWindow = when (alignment.resolve(layoutDirection)) {
            PopupPositionProvider.Align.BottomEnd, PopupPositionProvider.Align.BottomStart ->
                parentBounds.top - popupMargin.bottom

            else ->
                parentBounds.bottom + popupMargin.bottom
        }
        safeTransformOrigin(
            xInWindow / containerSize.width.toFloat(),
            yInWindow / containerSize.height.toFloat(),
        )
    }

    val calculatedOffset = remember(
        popupContentSize,
        windowBounds,
        parentBounds,
        alignment,
        layoutDirection,
        popupMargin,
        popupPositionProvider,
    ) {
        if (popupContentSize == IntSize.Zero) {
            IntOffset.Zero
        } else {
            popupPositionProvider.calculatePosition(
                parentBounds,
                windowBounds,
                layoutDirection,
                popupContentSize,
                popupMargin,
                alignment,
            )
        }
    }

    val popupLayoutPosition = remember(
        popupContentSize,
        windowBounds,
        parentBounds,
        alignment,
        calculatedOffset,
        layoutDirection,
    ) {
        if (popupContentSize == IntSize.Zero) {
            val isRightAligned = when (alignment.resolve(layoutDirection)) {
                PopupPositionProvider.Align.End,
                PopupPositionProvider.Align.TopEnd,
                PopupPositionProvider.Align.BottomEnd,
                -> true

                else -> false
            }
            PopupLayoutPosition(showBelow = true, showAbove = false, isRightAligned = isRightAligned)
        } else {
            val popupCenterY = calculatedOffset.y + popupContentSize.height / 2
            val anchorCenterY = parentBounds.top + parentBounds.height / 2
            val showBelow = popupCenterY > anchorCenterY
            val showAbove = popupCenterY < anchorCenterY

            val distLeft = abs(calculatedOffset.x - parentBounds.left)
            val distRight = abs((calculatedOffset.x + popupContentSize.width) - parentBounds.right)
            val isRightAligned = distRight < distLeft

            PopupLayoutPosition(showBelow = showBelow, showAbove = showAbove, isRightAligned = isRightAligned)
        }
    }

    val effectiveTransformOrigin = remember(
        popupContentSize,
        calculatedOffset,
        popupLayoutPosition,
        containerSize,
        predictedTransformOrigin,
    ) {
        if (popupContentSize == IntSize.Zero) {
            predictedTransformOrigin
        } else {
            val (showBelow, showAbove, isRightAligned) = popupLayoutPosition
            val cornerX = if (isRightAligned) {
                (calculatedOffset.x + popupContentSize.width).toFloat()
            } else {
                calculatedOffset.x.toFloat()
            }

            val showMiddle = !showBelow && !showAbove
            val topLeftY = calculatedOffset.y
            val cornerY = when {
                showMiddle -> (topLeftY + popupContentSize.height / 2f)
                showBelow -> topLeftY.toFloat()
                showAbove -> (topLeftY + popupContentSize.height).toFloat()
                else -> topLeftY.toFloat()
            }

            safeTransformOrigin(
                cornerX / containerSize.width.toFloat(),
                cornerY / containerSize.height.toFloat(),
            )
        }
    }

    val localTransformOrigin = remember(popupLayoutPosition, popupContentSize, calculatedOffset, parentBounds) {
        val (showBelow, _, isRightAligned) = popupLayoutPosition

        // COUI PopupMenuDomain.getMainMenuEnterPivotX: the scale pivot X is the anchor's
        // horizontal center clamped into the menu bounds — not the menu corner. Fall back to
        // the near corner until the popup has been measured.
        val pivotFractionX = if (popupContentSize == IntSize.Zero) {
            if (isRightAligned) 1f else 0f
        } else {
            ((parentBounds.left + parentBounds.width / 2f - calculatedOffset.x) / popupContentSize.width)
                .coerceIn(0f, 1f)
        }

        TransformOrigin(
            pivotFractionX = pivotFractionX,
            // COUI PopupMenuDomain.getMainMenuEnterPivotY is binary: the menu top edge when the
            // menu sits below the anchor, the bottom edge otherwise.
            pivotFractionY = if (showBelow) 0f else 1f,
        )
    }

    return ListPopupLayoutInfo(
        windowBounds = windowBounds,
        popupMargin = popupMargin,
        effectiveTransformOrigin = effectiveTransformOrigin,
        localTransformOrigin = localTransformOrigin,
        popupLayoutPosition = popupLayoutPosition,
    )
}

/**
 * The scaling and fading container that hosts a list popup's content.
 *
 * @param popupContentSize The last reported size of the content, compared against the latest
 *   measurement to avoid redundant [onPopupContentSizeChange] callbacks.
 * @param onPopupContentSizeChange Called when the measured content size changes.
 * @param fractionProgress Provides the current scale fraction (0 → 1) of the popup.
 * @param alphaProgress Provides the current alpha (0 → 1) of the popup content.
 * @param popupLayoutPosition The [PopupLayoutPosition] describing the popup's spawn direction.
 * @param localTransformOrigin The transform origin local to the content used while scaling.
 * @param modifier The modifier to be applied to the popup container.
 * @param content The content of the popup.
 */
@Composable
fun ListPopupContent(
    popupContentSize: IntSize,
    onPopupContentSizeChange: (IntSize) -> Unit,
    fractionProgress: () -> Float,
    alphaProgress: () -> Float,
    popupLayoutPosition: PopupLayoutPosition,
    localTransformOrigin: TransformOrigin,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val backgroundColor = popupSurfaceColor()
    val shape = remember { RoundedCornerShape(PopupCornerRadius) }

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val size = coordinates.size
                if (popupContentSize != size) onPopupContentSizeChange(size)
            }
            .graphicsLayer {
                // COUI's main menu scales a true 0 -> 1; only the small-screen sub-menu clip-reveals.
                val scale = fractionProgress()
                scaleX = scale
                scaleY = scale
                alpha = alphaProgress()
                transformOrigin = localTransformOrigin
            }
            .dropShadow(shape = shape, shadow = PopupShadow)
            .squircleSurface(color = backgroundColor, cornerRadius = PopupCornerRadius),
    ) {
        content()
    }
}

/**
 * Directional clip-reveal used during popup enter/exit. The visible band grows along the popup's
 * spawn direction encoded by [popupLayoutPosition] as [fractionProgress] moves 0 → 1: from the top
 * when shown below the anchor, from the bottom when shown above, and outwards from the center
 * otherwise. The band itself is shaped as a squircle (via [addSquircleRect]) so the four corners
 * stay aligned with the surrounding [squircleSurface] / [squircleClip] during reveal — when
 * [squircleEnabled] is `false`, [addSquircleRect] falls back to a plain rounded rectangle to
 * match the squircle modifiers' fallback rendering.
 */
internal fun Modifier.popupClipReveal(
    fractionProgress: () -> Float,
    popupLayoutPosition: PopupLayoutPosition,
    cornerRadius: Dp,
    squircleEnabled: Boolean,
): Modifier = drawWithCache {
    val path = Path()
    val showBelow = popupLayoutPosition.showBelow
    val showAbove = popupLayoutPosition.showAbove
    onDrawWithContent {
        // Clamp — source spring overshoots; an oversized reveal path would cut downstream content.
        val progress = fractionProgress().coerceIn(0f, 1f)
        if (progress <= 0f) return@onDrawWithContent

        val height = size.height
        val visibleHeight = height * progress
        if (visibleHeight <= 0f) return@onDrawWithContent

        val clipStart = when {
            showBelow -> 0f
            showAbove -> height * (1f - progress)
            else -> height * (0.5f - 0.5f * progress)
        }

        path.rewind()
        path.addSquircleRect(
            width = size.width,
            height = visibleHeight,
            cornerRadius = cornerRadius.toPx(),
            squircleEnabled = squircleEnabled,
        )
        if (clipStart == 0f) {
            clipPath(path) {
                this@onDrawWithContent.drawContent()
            }
        } else {
            translate(top = clipStart) {
                clipPath(path) {
                    translate(top = -clipStart) {
                        this@onDrawWithContent.drawContent()
                    }
                }
            }
        }
    }
}
