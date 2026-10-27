// Copyright 2025, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.basic

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastFirst
import androidx.compose.ui.util.lerp
import io.github.suqi8.coui.kmp.basic.TopAppBarState.Companion.Saver
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A [TopAppBar] with COUI style that can collapse and expand based on the
 * scroll position of the content below it.
 *
 * The [TopAppBar] can be configured with a title, a navigation icon, and action icons.
 * The large title will collapse when the content is scrolled up and expand when
 * the content is scrolled down.
 *
 * @param title The title of the [TopAppBar].
 * @param modifier The modifier to be applied to the  [TopAppBar].
 * @param color The background color of the [TopAppBar].
 * @param titleColor The color of the collapsed small title text.
 * @param largeTitle The large title of the [TopAppBar].
 * @param largeTitleColor The color of the expanded large title text.
 * @param subtitle The subtitle displayed below the title bar area.
 * @param subtitleColor The color of the subtitle text.
 * @param dividerColor The color of the hairline divider revealed at the bottom edge while collapsing.
 * @param navigationIcon The [Composable] content that represents the navigation icon.
 * @param actions The [Composable] content that represents the action icons.
 * @param scrollBehavior The [ScrollBehavior] that controls the behavior of the [TopAppBar].
 * @param defaultWindowInsetsPadding Whether to apply default window insets padding to the [TopAppBar].
 * @param showDivider Whether to draw the bottom hairline divider that fades in as the bar collapses.
 * @param hideSubtitleOnCollapse Whether the subtitle fades out while the bar collapses
 *   (COUICollapsableAppBarLayout `subtitleHideEnable`). When false, the subtitle stays fully
 *   opaque and slides into the collapsed bar.
 * @param titlePadding The horizontal padding of the [TopAppBar]'s title & large title.
 * @param navigationIconPadding The start padding of the navigation icon.
 * @param actionIconPadding The end padding of the action icons.
 * @param bottomContent The [Composable] content displayed below the title bar area.
 */
@Composable
fun TopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = COUITheme.colorScheme.surface,
    titleColor: Color = COUITheme.colorScheme.onSurface,
    largeTitle: String = title,
    largeTitleColor: Color = COUITheme.colorScheme.onSurface,
    subtitle: String = "",
    subtitleColor: Color = COUITheme.colorScheme.onSurfaceVariantSummary,
    dividerColor: Color = COUITheme.colorScheme.dividerLine,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
    defaultWindowInsetsPadding: Boolean = true,
    showDivider: Boolean = true,
    hideSubtitleOnCollapse: Boolean = true,
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: Dp = TopAppBarDefaults.NavigationIconPadding,
    actionIconPadding: Dp = TopAppBarDefaults.ActionIconPadding,
    bottomContent: @Composable () -> Unit = {},
) {
    // Wrap the given actions in a Row.
    val actionsRow =
        @Composable {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }

    // Compose a Surface with a TopAppBarLayout content.
    // The surface's background color is animated as specified above.
    // The height of the app bar is determined by subtracting the bar's height offset from the
    // app bar's defined constant height value (i.e. the ContainerHeight token).
    TopAppBarLayout(
        title = title,
        color = color,
        titleColor = titleColor,
        largeTitle = largeTitle,
        largeTitleColor = largeTitleColor,
        subtitle = subtitle,
        subtitleColor = subtitleColor,
        dividerColor = dividerColor,
        navigationIcon = navigationIcon,
        actions = actionsRow,
        titlePadding = titlePadding,
        navigationIconPadding = navigationIconPadding,
        actionIconPadding = actionIconPadding,
        scrollBehavior = scrollBehavior,
        modifier = modifier,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        showDivider = showDivider,
        hideSubtitleOnCollapse = hideSubtitleOnCollapse,
        bottomContent = bottomContent,
    )
}

/**
 * A [SmallTopAppBar] with COUI style.
 *
 * The [SmallTopAppBar] can be configured with a title, a navigation icon, and action icons.
 *
 * @param title The title of the [SmallTopAppBar].
 * @param modifier The modifier to be applied to the  [SmallTopAppBar].
 * @param color The background color of the [SmallTopAppBar].
 * @param titleColor The color of the title text.
 * @param subtitle The subtitle displayed below the title bar area.
 * @param subtitleColor The color of the subtitle text.
 * @param dividerColor The color of the hairline divider revealed at the bottom edge on scroll.
 * @param navigationIcon The [Composable] content that represents the navigation icon.
 * @param actions The [Composable] content that represents the action icons.
 * @param scrollBehavior The [ScrollBehavior] that controls the behavior of the [SmallTopAppBar].
 * @param defaultWindowInsetsPadding Whether to apply default window insets padding to the [SmallTopAppBar].
 * @param showDivider Whether to draw the bottom hairline divider that fades in as content scrolls
 *   beneath the bar (requires [scrollBehavior] to track the content offset).
 * @param titlePadding The horizontal padding of the [SmallTopAppBar]'s title.
 * @param navigationIconPadding The start padding of the navigation icon.
 * @param actionIconPadding The end padding of the action icons.
 * @param bottomContent The [Composable] content displayed below the title bar area.
 */
@Composable
fun SmallTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = COUITheme.colorScheme.surface,
    titleColor: Color = COUITheme.colorScheme.onSurface,
    subtitle: String = "",
    subtitleColor: Color = COUITheme.colorScheme.onSurfaceVariantSummary,
    dividerColor: Color = COUITheme.colorScheme.dividerLine,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
    defaultWindowInsetsPadding: Boolean = true,
    showDivider: Boolean = true,
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: Dp = TopAppBarDefaults.NavigationIconPadding,
    actionIconPadding: Dp = TopAppBarDefaults.ActionIconPadding,
    bottomContent: @Composable () -> Unit = {},
) {
    SideEffect {
        // Pin the bar: clamp scroll range to 0 so nested content still scrolls even when this bar
        // shares a ScrollBehavior with a collapsible one. Guard against redundant writes.
        scrollBehavior?.state?.let { state ->
            if (state.heightOffsetLimit != 0f) state.heightOffsetLimit = 0f
        }
    }

    // Wrap the given actions in a Row.
    val actionsRow =
        @Composable {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }

    // Compose a Surface with a SmallTopAppBarLayout content.
    // The surface's background color is animated as specified above.
    // The height of the app bar is determined by subtracting the bar's height offset from the
    // app bar's defined constant height value (i.e. the ContainerHeight token).
    SmallTopAppBarLayout(
        title = title,
        color = color,
        titleColor = titleColor,
        subtitle = subtitle,
        subtitleColor = subtitleColor,
        dividerColor = dividerColor,
        navigationIcon = navigationIcon,
        actions = actionsRow,
        titlePadding = titlePadding,
        navigationIconPadding = navigationIconPadding,
        actionIconPadding = actionIconPadding,
        scrollBehavior = scrollBehavior,
        modifier = modifier,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        showDivider = showDivider,
        bottomContent = bottomContent,
    )
}

/**
 * Returns a [ScrollBehavior] that adjusts its properties to affect the colors and
 * height of the top app bar.
 *
 * A top app bar that is set up with this [ScrollBehavior] will immediately collapse
 * when the nested content is pulled up, and will expand back the collapsed area when the
 * content is pulled all the way down.
 *
 * @param state the state object to be used to control or observe the top app bar's scroll
 *   state. See [rememberTopAppBarState] for a state that is remembered across compositions.
 * @param canScroll a callback used to determine whether scroll events are to be handled by this
 *   [ExitUntilCollapsedScrollBehavior]
 * @param snapAnimationSpec an optional [AnimationSpec] that defines how the top app bar snaps
 *   to either fully collapsed or fully extended state when a fling or a drag scrolled it into
 *   an intermediate position.
 * @param flingAnimationSpec an optional [DecayAnimationSpec] that defined how to fling the top
 *   app bar when the user flings the app bar itself, or the content below it
 */
@Suppress("ComposableNaming")
@Composable
fun COUIScrollBehavior(
    state: TopAppBarState = rememberTopAppBarState(),
    canScroll: () -> Boolean = { true },
    snapAnimationSpec: AnimationSpec<Float>? = tween(durationMillis = SnapAnimationDuration, easing = SnapAnimationEasing),
    flingAnimationSpec: DecayAnimationSpec<Float>? = rememberSplineBasedDecay(),
): ScrollBehavior = remember(state, canScroll, snapAnimationSpec, flingAnimationSpec) {
    ExitUntilCollapsedScrollBehavior(
        state = state,
        snapAnimationSpec = snapAnimationSpec,
        flingAnimationSpec = flingAnimationSpec,
        canScroll = canScroll,
    )
}

/**
 * Creates a [TopAppBarState] that is remembered across compositions.
 *
 * @param initialHeightOffsetLimit the initial value for [TopAppBarState.heightOffsetLimit], which
 *   represents the pixel limit that a top app bar is allowed to collapse when the scrollable
 *   content is scrolled
 * @param initialHeightOffset the initial value for [TopAppBarState.heightOffset]. The initial
 *   offset height offset should be between zero and [initialHeightOffsetLimit].
 * @param initialContentOffset the initial value for [TopAppBarState.contentOffset]
 */
@Composable
fun rememberTopAppBarState(
    initialHeightOffsetLimit: Float = -Float.MAX_VALUE,
    initialHeightOffset: Float = 0f,
    initialContentOffset: Float = 0f,
): TopAppBarState = rememberSaveable(saver = Saver) {
    TopAppBarState(initialHeightOffsetLimit, initialHeightOffset, initialContentOffset)
}

/**
 * A state object that can be hoisted to control and observe the top app bar state. The state is
 * read and updated by a [ScrollBehavior] implementation.
 *
 * In most cases, this state will be created via [rememberTopAppBarState].
 *
 * @param initialHeightOffsetLimit the initial value for [TopAppBarState.heightOffsetLimit]
 * @param initialHeightOffset the initial value for [TopAppBarState.heightOffset]
 * @param initialContentOffset the initial value for [TopAppBarState.contentOffset]
 */
@Stable
class TopAppBarState(
    initialHeightOffsetLimit: Float,
    initialHeightOffset: Float,
    initialContentOffset: Float,
) {

    /**
     * The top app bar's height offset limit in pixels, which represents the limit that a top app
     * bar is allowed to collapse to.
     *
     * Use this limit to coerce the [heightOffset] value when it's updated.
     */
    var heightOffsetLimit = initialHeightOffsetLimit

    /**
     * The top app bar's current height offset in pixels. This height offset is applied to the fixed
     * height of the app bar to control the displayed height when content is being scrolled.
     *
     * Updates to the [heightOffset] value are coerced between zero and [heightOffsetLimit].
     */
    var heightOffset: Float
        get() = _heightOffset.floatValue
        set(newOffset) {
            _heightOffset.floatValue =
                newOffset.coerceIn(minimumValue = heightOffsetLimit, maximumValue = 0f)
        }

    /**
     * The total offset of the content scrolled under the top app bar.
     *
     * The content offset is used to compute the [overlappedFraction], which can later be read by an
     * implementation.
     *
     * This value is updated by a [ScrollBehavior] whenever a nested scroll connection
     * consumes scroll events. A common implementation would update the value to be the sum of all
     * [NestedScrollConnection.onPostScroll] `consumed.y` values.
     */
    var contentOffset by mutableFloatStateOf(initialContentOffset)

    /**
     * A value that represents the collapsed height percentage of the app bar.
     *
     * A `0.0` represents a fully expanded bar, and `1.0` represents a fully collapsed bar (computed
     * as [heightOffset] / [heightOffsetLimit]).
     */
    val collapsedFraction: Float
        get() =
            if (heightOffsetLimit != 0f) {
                heightOffset / heightOffsetLimit
            } else {
                0f
            }

    /**
     * A value that represents the percentage of the app bar area that is overlapping with the
     * content scrolled behind it.
     *
     * A `0.0` indicates that the app bar does not overlap any content, while `1.0` indicates that
     * the entire visible app bar area overlaps the scrolled content.
     */
    val overlappedFraction: Float
        get() =
            if (heightOffsetLimit != 0f) {
                1 -
                    (
                        (heightOffsetLimit - contentOffset).coerceIn(
                            minimumValue = heightOffsetLimit,
                            maximumValue = 0f,
                        ) / heightOffsetLimit
                        )
            } else {
                0f
            }

    companion object {
        /** The default [Saver] implementation for [TopAppBarState]. */
        val Saver: Saver<TopAppBarState, *> =
            listSaver(
                save = { listOf(it.heightOffsetLimit, it.heightOffset, it.contentOffset) },
                restore = {
                    TopAppBarState(
                        initialHeightOffsetLimit = it[0],
                        initialHeightOffset = it[1],
                        initialContentOffset = it[2],
                    )
                },
            )
    }

    private var _heightOffset = mutableFloatStateOf(initialHeightOffset)
}

/** Contains default values used by [TopAppBar] and [SmallTopAppBar]. */
object TopAppBarDefaults {
    /**
     * The default horizontal padding of the title and large title.
     * COUI `coui_appbar_title_expanded_margin_start/end_compat` (16dp).
     */
    val TitlePadding = 16.dp

    /** The default start padding of the navigation icon. */
    val NavigationIconPadding = 16.dp

    /** The default end padding of the action icons. */
    val ActionIconPadding = 16.dp

    /** The default collapsed height of the [TopAppBar]. */
    val CollapsedHeight = 52.dp

    /**
     * The default expanded height of the [TopAppBar] when no subtitle is present.
     * COUI `coui_appbar_title_expanded_height` (107dp); the bar grows beyond it when the
     * measured title content requires more space.
     */
    val ExpandedHeight = 107.dp

    /**
     * The top padding of the expanded large title, measured from the top of the bar.
     * COUI `coui_appbar_title_expanded_margin_top` (54dp).
     */
    val LargeTitleTopPadding = 54.dp

    /**
     * The vertical center height used for [SmallTopAppBar] layout.
     * COUI `coui_appbar_title_toolbar_height` (52dp).
     */
    val SmallTopAppBarCenterHeight = 52.dp

    /**
     * The bottom padding below the large title block when the bar is expanded.
     * COUI `coui_appbar_start_padding_bottom` (12dp, interpolates to 0dp as the bar collapses).
     */
    val LargeTitleBottomPadding = 12.dp

    /** The bottom padding below the subtitle when it overflows the collapsed bar. */
    val SubtitleBottomPadding = 8.dp

    /**
     * The vertical gap between the title and the subtitle.
     * COUI `coui_appbar_subtitle_collapsed_margin_top` (3.5dp).
     */
    val SubtitleMarginTop = 3.5.dp

    /**
     * The horizontal gap between the navigation icon and the collapsed title.
     * COUI `coui_toolbar_gap_between_navigation_and_title` (4dp).
     */
    val NavigationIconGap = 4.dp

    /**
     * The horizontal gap between the title and the action icons.
     * COUI `coui_toolbar_gap_before_menu` (8dp).
     */
    val ActionIconGap = 8.dp
}

@Stable
interface ScrollBehavior {

    /**
     * A [TopAppBarState] that is attached to this behavior and is read and updated when scrolling
     * happens.
     */
    val state: TopAppBarState

    /**
     * Indicates whether the top app bar is pinned.
     *
     * A pinned app bar will stay fixed in place when content is scrolled and will not react to any
     * drag gestures.
     */
    val isPinned: Boolean

    /**
     * An optional [AnimationSpec] that defines how the top app bar snaps to either fully collapsed
     * or fully extended state when a fling or a drag scrolled it into an intermediate position.
     */
    val snapAnimationSpec: AnimationSpec<Float>?

    /**
     * An optional [DecayAnimationSpec] that defined how to fling the top app bar when the user
     * flings the app bar itself, or the content below it.
     */
    val flingAnimationSpec: DecayAnimationSpec<Float>?

    /**
     * A [NestedScrollConnection] that should be attached to a [Modifier.nestedScroll] in order to
     * keep track of the scroll events.
     */
    val nestedScrollConnection: NestedScrollConnection
}

/**
 * A [ScrollBehavior] that adjusts its properties to affect the colors and height of a top
 * app bar.
 *
 * A top app bar that is set up with this [ScrollBehavior] will immediately collapse when
 * the nested content is pulled up, and will expand back the collapsed area when the content is
 * pulled all the way down.
 *
 * @param state a [TopAppBarState]
 * @param snapAnimationSpec an optional [AnimationSpec] that defines how the top app bar snaps to
 *   either fully collapsed or fully extended state when a fling or a drag scrolled it into an
 *   intermediate position
 * @param flingAnimationSpec an optional [DecayAnimationSpec] that defined how to fling the top app
 *   bar when the user flings the app bar itself, or the content below it
 * @param canScroll a callback used to determine whether scroll events are to be handled by this
 *   [ExitUntilCollapsedScrollBehavior]
 */
private class ExitUntilCollapsedScrollBehavior(
    override val state: TopAppBarState,
    override val snapAnimationSpec: AnimationSpec<Float>?,
    override val flingAnimationSpec: DecayAnimationSpec<Float>?,
    val canScroll: () -> Boolean = { true },
) : ScrollBehavior {
    override val isPinned: Boolean = false
    override var nestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Don't intercept if scrolling down.
                if (!canScroll() || available.y > 0) return Offset.Zero
                val prevHeightOffset = state.heightOffset
                state.heightOffset += available.y
                return if (prevHeightOffset != state.heightOffset) {
                    // We're in the middle of top app bar collapse or expand.
                    // Consume only the scroll on the Y axis.
                    available.copy(x = 0f)
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!canScroll()) return Offset.Zero
                state.contentOffset += consumed.y

                if (available.y < 0f || consumed.y < 0f) {
                    // When scrolling up, just update the state's height offset.
                    val oldHeightOffset = state.heightOffset
                    state.heightOffset += consumed.y
                    return Offset(0f, state.heightOffset - oldHeightOffset)
                }

                if (available.y > 0f) {
                    // Adjust the height offset in case the consumed delta Y is less than what was
                    // recorded as available delta Y in the pre-scroll.
                    val oldHeightOffset = state.heightOffset
                    state.heightOffset += available.y
                    return Offset(0f, state.heightOffset - oldHeightOffset)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y > 0) {
                    // Reset the total content offset to zero when scrolling all the way down. This
                    // will eliminate some float precision inaccuracies.
                    state.contentOffset = 0f
                }
                val superConsumed = super.onPostFling(consumed, available)
                return superConsumed +
                    settleAppBar(state, available.y, flingAnimationSpec, snapAnimationSpec)
            }
        }
}

/**
 * Settles the app bar to a stable state (fully expanded or collapsed) by animating
 * its height offset.
 *
 * This function is invoked after a drag or fling gesture, using the provided velocity
 * to drive a decay animation, followed by a snap animation if the bar is left in an
 * intermediate state.
 *
 * @param state The [TopAppBarState] that holds the current and target height offsets.
 * @param velocity The velocity from the fling gesture to be consumed.
 * @param flingAnimationSpec The [DecayAnimationSpec] for the fling animation.
 * @param snapAnimationSpec The [AnimationSpec] for the final snap to a stable state.
 * @return The [Velocity] that was actually consumed by the fling decay animation. This
 * ensures accurate reporting within the nested scroll system, allowing any unconsumed
 * velocity to be propagated to parent consumers.
 */
private suspend fun settleAppBar(
    state: TopAppBarState,
    velocity: Float,
    flingAnimationSpec: DecayAnimationSpec<Float>?,
    snapAnimationSpec: AnimationSpec<Float>?,
): Velocity {
    // Check if the app bar is completely collapsed/expanded. If so, no need to settle the app bar,
    // and just return Zero Velocity.
    // Note that we don't check for 0f due to float precision with the collapsedFraction
    // calculation.
    if (state.collapsedFraction < 0.01f || state.collapsedFraction == 1f) {
        return Velocity.Zero
    }
    var remainingVelocity = velocity
    // In case there is an initial velocity that was left after a previous user fling, animate to
    // continue the motion to expand or collapse the app bar.
    if (flingAnimationSpec != null && abs(velocity) > 1f) {
        var lastValue = 0f
        AnimationState(initialValue = 0f, initialVelocity = velocity).animateDecay(
            flingAnimationSpec,
        ) {
            val delta = value - lastValue
            val initialHeightOffset = state.heightOffset
            state.heightOffset = initialHeightOffset + delta
            val consumed = abs(initialHeightOffset - state.heightOffset)
            lastValue = value
            remainingVelocity = this.velocity
            // avoid rounding errors and stop if anything is unconsumed
            if (abs(delta - consumed) > 0.5f) this.cancelAnimation()
        }
    }
    // Snap if animation specs were provided.
    if (snapAnimationSpec != null) {
        if (state.heightOffset < 0 && state.heightOffset > state.heightOffsetLimit) {
            AnimationState(initialValue = state.heightOffset).animateTo(
                if (state.collapsedFraction < 0.5f) {
                    0f
                } else {
                    state.heightOffsetLimit
                },
                animationSpec = snapAnimationSpec,
            ) {
                state.heightOffset = value
            }
        }
    }
    return Velocity(0f, velocity - remainingVelocity)
}

/**
 * The base [Layout] for [TopAppBar]. This function lays out a [TopAppBar] navigation icon
 * (leading icon), a title (header), and action icons (trailing icons). Note that the navigation and
 * the actions are optional.
 *
 * @param title the [TopAppBar] title (header).
 * @param color the background color of the [TopAppBar].
 * @param titleColor the color of the collapsed small title text.
 * @param largeTitleColor the color of the expanded large title text.
 * @param subtitle the subtitle text displayed below the title bar area.
 * @param subtitleColor the color of the subtitle text.
 * @param dividerColor the color of the bottom hairline divider.
 * @param navigationIcon a navigation icon [Composable].
 * @param actions actions [Composable].
 * @param titlePadding the horizontal padding of the [TopAppBar]'s title & large title.
 * @param navigationIconPadding the start padding of the navigation icon.
 * @param actionIconPadding the end padding of the action icons.
 * @param scrollBehavior the [ScrollBehavior] that drives the collapse/expand state. The layout
 *   reads its `heightOffset` directly inside the measure pass (so scroll only re-measures, never
 *   recomposes this subtree) and writes the matching `heightOffsetLimit` from the measured
 *   expanded height.
 * @param modifier the [Modifier] to be applied to this layout.
 * @param largeTitle the large title of the [TopAppBar], if not specified, it will be the same as title.
 * @param defaultWindowInsetsPadding whether to apply default window insets padding to the [TopAppBar].
 * @param showDivider whether to draw the bottom hairline divider while collapsing.
 * @param hideSubtitleOnCollapse whether the subtitle fades out while the bar collapses.
 * @param bottomContent the composable content displayed below the title bar area.
 */
@Composable
private fun TopAppBarLayout(
    title: String,
    color: Color,
    titleColor: Color,
    largeTitleColor: Color,
    subtitle: String,
    subtitleColor: Color,
    dividerColor: Color,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    titlePadding: Dp,
    navigationIconPadding: Dp,
    actionIconPadding: Dp,
    scrollBehavior: ScrollBehavior?,
    modifier: Modifier = Modifier,
    largeTitle: String = title,
    defaultWindowInsetsPadding: Boolean = true,
    showDivider: Boolean = true,
    hideSubtitleOnCollapse: Boolean = true,
    bottomContent: @Composable () -> Unit = {},
) {
    // Producer lambdas — reads stay in layout/draw phases so scroll never recomposes this subtree.
    val scrolledOffset = remember(scrollBehavior) {
        { scrollBehavior?.state?.heightOffset ?: 0f }
    }
    // Divider progress: COUIDividerAppBarLayout fades the divider in over the whole
    // collapse range (alpha 0 -> 1, horizontal margin 24dp -> 0dp).
    val dividerFraction = remember(scrollBehavior) {
        { (scrollBehavior?.state?.collapsedFraction ?: 0f).coerceIn(0f, 1f) }
    }
    val updateHeightOffsetLimit = remember(scrollBehavior) {
        { height: Int ->
            scrollBehavior?.state?.let { state ->
                val limit = -height.toFloat()
                if (state.heightOffsetLimit != limit) state.heightOffsetLimit = limit
            }
            Unit
        }
    }

    // CollapsingTextHelper lerps the text size from expanded 32dp to collapsed 18dp; replicated
    // as a scale factor around the title's top-start corner.
    val largeTitleFontSize = COUITheme.textStyles.title1.fontSize
    val collapsedTitleScale = remember(largeTitleFontSize) {
        if (largeTitleFontSize.value > 0f) {
            CollapsedTitleTextSize.value / largeTitleFontSize.value
        } else {
            1f
        }
    }

    val animatedTitleColor by animateColorAsState(
        targetValue = titleColor,
        animationSpec = tween(durationMillis = 50),
    )
    val animatedLargeTitleColor by animateColorAsState(
        targetValue = largeTitleColor,
        animationSpec = tween(durationMillis = 50),
    )
    val animatedSubtitleColor by animateColorAsState(
        targetValue = subtitleColor,
        animationSpec = tween(durationMillis = 50),
    )

    Layout(
        {
            Box(
                Modifier
                    .layoutId("navigationIcon")
                    .padding(start = navigationIconPadding),
            ) {
                navigationIcon()
            }
            // Collapsed title: only shown once the bar is fully collapsed; until then the
            // large title morphs into it.
            Box(Modifier.layoutId("title")) {
                Text(
                    text = title,
                    color = animatedTitleColor,
                    fontSize = CollapsedTitleTextSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            }
            Box(
                Modifier
                    .layoutId("actionIcons")
                    .padding(end = actionIconPadding),
            ) {
                actions()
            }
            // Expanded title: position and scale are driven by the collapse fraction in the
            // placement pass (CollapsingTextHelper SCALE mode).
            Box(Modifier.layoutId("largeTitle")) {
                Text(
                    text = largeTitle,
                    color = animatedLargeTitleColor,
                    fontSize = largeTitleFontSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            }
            if (subtitle.isNotEmpty()) {
                // Subtitle: alpha and collapsed position are driven in the placement pass
                // (COUICollapsableAppBarLayout.adjustSubtitleIfNeed).
                Box(Modifier.layoutId("subtitle")) {
                    Text(
                        text = subtitle,
                        color = animatedSubtitleColor,
                        style = COUITheme.textStyles.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                }
            }
            Box(Modifier.layoutId("bottomContent")) {
                bottomContent()
            }
        },
        modifier = modifier
            .background(color)
            .then(
                if (defaultWindowInsetsPadding) {
                    Modifier
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                } else {
                    Modifier
                },
            )
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures { /* Consume click */ }
            }
            .then(
                if (showDivider) {
                    Modifier.drawWithContent {
                        drawContent()
                        // COUIDividerAppBarLayout: hairline at the app bar's bottom edge,
                        // alpha 0 -> 1 and horizontal margin 24dp -> 0dp over the collapse.
                        val fraction = dividerFraction()
                        if (fraction > 0f) {
                            val inset = lerp(DividerExpandedMarginHorizontal.toPx(), 0f, fraction)
                            val stroke = DividerThickness.toPx()
                            val y = size.height - stroke / 2f
                            drawLine(
                                color = dividerColor,
                                start = Offset(inset, y),
                                end = Offset(size.width - inset, y),
                                strokeWidth = stroke,
                                alpha = fraction,
                            )
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) { measurables, constraints ->
        val navigationIconPlaceable =
            measurables
                .fastFirst { it.layoutId == "navigationIcon" }
                .measure(constraints.copy(minWidth = 0, minHeight = 0))

        val actionIconsPlaceable =
            measurables
                .fastFirst { it.layoutId == "actionIcons" }
                .measure(constraints.copy(minWidth = 0, minHeight = 0))

        val titlePaddingPx = titlePadding.roundToPx()
        val navigationIconPaddingPx = navigationIconPadding.roundToPx()
        val actionIconPaddingPx = actionIconPadding.roundToPx()

        // Collapsed title bounds: start-aligned after the navigation icon; falls back to the
        // title margin on sides without an icon.
        val hasNavigationIcon = navigationIconPlaceable.width > navigationIconPaddingPx
        val hasActions = actionIconsPlaceable.width > actionIconPaddingPx
        val collapsedTitleX = if (hasNavigationIcon) {
            navigationIconPlaceable.width + TopAppBarDefaults.NavigationIconGap.roundToPx()
        } else {
            titlePaddingPx
        }
        val collapsedTitleEnd = if (hasActions) {
            constraints.maxWidth - actionIconsPlaceable.width - TopAppBarDefaults.ActionIconGap.roundToPx()
        } else {
            constraints.maxWidth - titlePaddingPx
        }
        val collapsedTitleMaxWidth = (collapsedTitleEnd - collapsedTitleX).coerceAtLeast(0)

        val titlePlaceable =
            measurables
                .fastFirst { it.layoutId == "title" }
                .measure(constraints.copy(minWidth = 0, maxWidth = collapsedTitleMaxWidth, minHeight = 0))

        val largeTitleMaxWidth = (constraints.maxWidth - 2 * titlePaddingPx).coerceAtLeast(0)
        val largeTitlePlaceable =
            measurables
                .fastFirst { it.layoutId == "largeTitle" }
                .measure(constraints.copy(minWidth = 0, maxWidth = largeTitleMaxWidth, minHeight = 0))

        val subtitlePlaceable =
            measurables
                .firstOrNull { it.layoutId == "subtitle" }
                ?.measure(constraints.copy(minWidth = 0, maxWidth = largeTitleMaxWidth, minHeight = 0))

        val bottomContentPlaceable =
            measurables
                .fastFirst { it.layoutId == "bottomContent" }
                .measure(constraints.copy(minWidth = 0, minHeight = 0))

        val collapsedHeight = TopAppBarDefaults.CollapsedHeight.roundToPx()
        val largeTitleTop = TopAppBarDefaults.LargeTitleTopPadding.roundToPx()
        val subtitleMarginTopPx = TopAppBarDefaults.SubtitleMarginTop.roundToPx()
        val largeTitleBottomPaddingPx = TopAppBarDefaults.LargeTitleBottomPadding.roundToPx()
        val subtitleHeight = subtitlePlaceable?.height ?: 0

        // Expanded bar height (COUICollapsableAppBarLayout.onMeasure): the fixed 107dp grows by
        // the subtitle height, with the 12dp expanded bottom padding folded in.
        val expandedContentHeight = largeTitleTop + largeTitlePlaceable.height +
            (subtitlePlaceable?.let { subtitleMarginTopPx + it.height } ?: 0)
        val expandedBarHeight = maxOf(
            TopAppBarDefaults.ExpandedHeight.roundToPx() + subtitleHeight,
            expandedContentHeight + largeTitleBottomPaddingPx,
        )
        val expansion = (expandedBarHeight - collapsedHeight).coerceAtLeast(0)
        updateHeightOffsetLimit(expansion)

        val offset = scrolledOffset()
        val offsetPx = if (offset.isNaN()) 0 else offset.roundToInt()
        val collapseFraction = if (expansion > 0) {
            (abs(offsetPx) / expansion.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        // The bar height follows the raw scroll offset; only the title morph below is
        // eased (COUIEaseInterpolator on the CollapsingTextHelper position/text size).
        val barHeight = (collapsedHeight + expansion + offsetPx).coerceAtLeast(collapsedHeight)
        val easedFraction = CollapsingTitleEasing.transform(collapseFraction)

        val verticalCenter = collapsedHeight / 2
        // Collapsed title: vertically centered in the toolbar area; shifted up above a subtitle
        // that stays visible at full collapse (COUICollapsingToolbarLayout.translateTitleIfNeed).
        val subtitleVisibleWhenCollapsed = subtitlePlaceable != null && !hideSubtitleOnCollapse
        val collapsedTitleY = if (subtitleVisibleWhenCollapsed) {
            (collapsedHeight - subtitleHeight - subtitleMarginTopPx - titlePlaceable.height).coerceAtLeast(0)
        } else {
            verticalCenter - titlePlaceable.height / 2
        }

        // Large title morph (CollapsingTextHelper.calculateOffsets): eased lerp between the
        // expanded and collapsed slots, shifted by the raw scroll offset.
        val largeTitleX = lerp(titlePaddingPx.toFloat(), collapsedTitleX.toFloat(), easedFraction)
        val largeTitleY =
            lerp(largeTitleTop.toFloat(), (collapsedTitleY + expansion).toFloat(), easedFraction) + offsetPx
        val largeTitleScale = lerp(1f, collapsedTitleScale, easedFraction)
        val collapsed = collapseFraction >= CollapsedTitleSwapFraction
        val pivotX = if (layoutDirection == LayoutDirection.Rtl) 1f else 0f

        val contentTop = barHeight
        val layoutHeight = contentTop + bottomContentPlaceable.height

        layout(constraints.maxWidth, layoutHeight) {
            // Navigation icon
            navigationIconPlaceable.placeRelative(
                x = 0,
                y = verticalCenter - navigationIconPlaceable.height / 2,
            )

            // Action icons
            actionIconsPlaceable.placeRelative(
                x = constraints.maxWidth - actionIconsPlaceable.width,
                y = verticalCenter - actionIconsPlaceable.height / 2,
            )

            // Subtitle (COUICollapsableAppBarLayout.adjustSubtitleIfNeed): rides the scroll 1:1,
            // lifted by the interpolating bottom padding; fades out only when hideSubtitleOnCollapse.
            subtitlePlaceable?.placeRelativeWithLayer(
                x = titlePaddingPx,
                y = expandedBarHeight - subtitleHeight -
                    (largeTitleBottomPaddingPx * (1f - collapseFraction)).roundToInt() + offsetPx,
            ) {
                alpha = if (hideSubtitleOnCollapse) 1f - collapseFraction else 1f
            }

            // Large title: translates and scales into the collapsed title; swapped for the
            // dedicated collapsed title only at full collapse.
            largeTitlePlaceable.placeRelativeWithLayer(
                x = largeTitleX.roundToInt(),
                y = largeTitleY.roundToInt(),
            ) {
                transformOrigin = TransformOrigin(pivotX, 0f)
                scaleX = largeTitleScale
                scaleY = largeTitleScale
                alpha = if (collapsed) 0f else 1f
            }

            // Collapsed title (start-aligned, visible only at full collapse).
            titlePlaceable.placeRelativeWithLayer(
                x = collapsedTitleX,
                y = collapsedTitleY,
            ) {
                alpha = if (collapsed) 1f else 0f
            }

            // Bottom content (pinned, below the bar)
            bottomContentPlaceable.placeRelative(x = 0, y = contentTop)
        }
    }
}

/**
 * The base [Layout] for [SmallTopAppBar]. This function lays out a [SmallTopAppBar] navigation icon
 * (leading icon), a title (header), and action icons (trailing icons). Note that the navigation and
 * the actions are optional.
 *
 * @param title the [SmallTopAppBar] title (header).
 * @param color the background color of the [SmallTopAppBar].
 * @param titleColor the color of the title text.
 * @param subtitle the subtitle text displayed below the title bar area.
 * @param subtitleColor the color of the subtitle text.
 * @param dividerColor the color of the bottom hairline divider.
 * @param navigationIcon a navigation icon [Composable].
 * @param actions actions [Composable].
 * @param titlePadding the horizontal padding of the [SmallTopAppBar]'s title.
 * @param navigationIconPadding the start padding of the navigation icon.
 * @param actionIconPadding the end padding of the action icons.
 * @param scrollBehavior the [ScrollBehavior] whose content offset drives the divider reveal.
 * @param modifier the [Modifier] to be applied to this layout.
 * @param defaultWindowInsetsPadding whether to apply default window insets padding to the [SmallTopAppBar].
 * @param showDivider whether to draw the bottom hairline divider on scroll.
 * @param bottomContent the composable content displayed below the title bar area.
 */
@Composable
private fun SmallTopAppBarLayout(
    title: String,
    color: Color,
    titleColor: Color,
    subtitle: String,
    subtitleColor: Color,
    dividerColor: Color,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    titlePadding: Dp,
    navigationIconPadding: Dp,
    actionIconPadding: Dp,
    scrollBehavior: ScrollBehavior?,
    modifier: Modifier = Modifier,
    defaultWindowInsetsPadding: Boolean = true,
    showDivider: Boolean = true,
    bottomContent: @Composable () -> Unit = {},
) {
    val animatedTitleColor by animateColorAsState(
        targetValue = titleColor,
        animationSpec = tween(durationMillis = 50),
    )
    val animatedSubtitleColor by animateColorAsState(
        targetValue = subtitleColor,
        animationSpec = tween(durationMillis = 50),
    )

    // Distance the content has scrolled beneath the pinned bar, in pixels. Drives the
    // divider like COUI's second-page toolbar behaviors (alpha over the first 10dp,
    // margin 24dp -> 0dp over the next 25dp after a 10dp threshold).
    val scrolledDistance = remember(scrollBehavior) {
        { -(scrollBehavior?.state?.contentOffset ?: 0f) }
    }

    Layout(
        {
            Box(
                Modifier
                    .layoutId("navigationIcon")
                    .padding(start = navigationIconPadding),
            ) {
                navigationIcon()
            }
            // Title: TextAppearance.COUI.Toolbar.SecondTitle (18dp, medium, viewStart alignment).
            Box(Modifier.layoutId("title")) {
                Text(
                    text = title,
                    color = animatedTitleColor,
                    maxLines = 1,
                    fontSize = CollapsedTitleTextSize,
                    fontWeight = FontWeight.Medium,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            }
            Box(
                Modifier
                    .layoutId("actionIcons")
                    .padding(end = actionIconPadding),
            ) {
                actions()
            }
            if (subtitle.isNotEmpty()) {
                // Subtitle: coui_toolbar_subtitle_text_size, single line.
                Box(Modifier.layoutId("subtitle")) {
                    Text(
                        text = subtitle,
                        color = animatedSubtitleColor,
                        style = COUITheme.textStyles.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                }
            }
            Box(Modifier.layoutId("bottomContent")) {
                bottomContent()
            }
        },
        modifier = modifier
            .background(color)
            .then(
                if (defaultWindowInsetsPadding) {
                    Modifier
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                } else {
                    Modifier
                },
            )
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures { /* Consume click */ }
            }
            .then(
                if (showDivider) {
                    Modifier.drawWithContent {
                        drawContent()
                        // COUI second-page toolbar divider: fades in over the first 10dp of
                        // content scroll, then shrinks its horizontal margin 24dp -> 0dp
                        // over the following 25dp.
                        val distance = scrolledDistance()
                        val alphaFraction = (distance / DividerAlphaChangeOffset.toPx()).coerceIn(0f, 1f)
                        if (alphaFraction > 0f) {
                            val marginFraction =
                                ((distance - DividerWidthStartCountOffset.toPx()) / DividerWidthChangeOffset.toPx())
                                    .coerceIn(0f, 1f)
                            val inset = lerp(DividerExpandedMarginHorizontal.toPx(), 0f, marginFraction)
                            val stroke = DividerThickness.toPx()
                            val y = size.height - stroke / 2f
                            drawLine(
                                color = dividerColor,
                                start = Offset(inset, y),
                                end = Offset(size.width - inset, y),
                                strokeWidth = stroke,
                                alpha = alphaFraction,
                            )
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) { measurables, constraints ->
        val navigationIconPlaceable =
            measurables
                .fastFirst { it.layoutId == "navigationIcon" }
                .measure(constraints.copy(minWidth = 0, minHeight = 0))

        val actionIconsPlaceable =
            measurables
                .fastFirst { it.layoutId == "actionIcons" }
                .measure(constraints.copy(minWidth = 0, minHeight = 0))

        val titlePaddingPx = titlePadding.roundToPx()
        val navigationIconPaddingPx = navigationIconPadding.roundToPx()
        val actionIconPaddingPx = actionIconPadding.roundToPx()

        // Title bounds: start-aligned after the navigation icon (COUIToolbar viewStart layout);
        // falls back to the title margin on sides without an icon.
        val hasNavigationIcon = navigationIconPlaceable.width > navigationIconPaddingPx
        val hasActions = actionIconsPlaceable.width > actionIconPaddingPx
        val titleX = if (hasNavigationIcon) {
            navigationIconPlaceable.width + TopAppBarDefaults.NavigationIconGap.roundToPx()
        } else {
            titlePaddingPx
        }
        val titleEnd = if (hasActions) {
            constraints.maxWidth - actionIconsPlaceable.width - TopAppBarDefaults.ActionIconGap.roundToPx()
        } else {
            constraints.maxWidth - titlePaddingPx
        }
        val titleMaxWidth = (titleEnd - titleX).coerceAtLeast(0)

        val titlePlaceable =
            measurables
                .fastFirst { it.layoutId == "title" }
                .measure(constraints.copy(minWidth = 0, maxWidth = titleMaxWidth, minHeight = 0))

        val subtitlePlaceable =
            measurables
                .firstOrNull { it.layoutId == "subtitle" }
                ?.measure(constraints.copy(minWidth = 0, maxWidth = titleMaxWidth, minHeight = 0))

        val bottomContentPlaceable =
            measurables
                .fastFirst { it.layoutId == "bottomContent" }
                .measure(constraints.copy(minWidth = 0, minHeight = 0))

        val collapsedHeight = TopAppBarDefaults.CollapsedHeight.roundToPx()
        val verticalCenter = TopAppBarDefaults.SmallTopAppBarCenterHeight.roundToPx() / 2
        val subtitleMarginTopPx = TopAppBarDefaults.SubtitleMarginTop.roundToPx()

        // Title stack: vertically centered in the 52dp toolbar area; the subtitle sits
        // 3.5dp below the title, matching the collapsed COUI app bar stack.
        val stackHeight = titlePlaceable.height +
            (subtitlePlaceable?.let { subtitleMarginTopPx + it.height } ?: 0)
        val titleY = if (subtitlePlaceable != null) {
            ((TopAppBarDefaults.SmallTopAppBarCenterHeight.roundToPx() - stackHeight) / 2).coerceAtLeast(0)
        } else {
            verticalCenter - titlePlaceable.height / 2
        }
        val subtitleY = titleY + titlePlaceable.height + subtitleMarginTopPx
        val subtitleBottomPadding = if (subtitlePlaceable != null) TopAppBarDefaults.SubtitleBottomPadding.roundToPx() else 0
        val contentTop = maxOf(collapsedHeight, stackHeight + subtitleBottomPadding)
        val layoutHeight = contentTop + bottomContentPlaceable.height

        layout(constraints.maxWidth, layoutHeight) {
            // Navigation icon
            navigationIconPlaceable.placeRelative(
                x = 0,
                y = verticalCenter - navigationIconPlaceable.height / 2,
            )

            // Title (start-aligned)
            titlePlaceable.placeRelative(
                x = titleX,
                y = titleY,
            )

            // Action icons
            actionIconsPlaceable.placeRelative(
                x = constraints.maxWidth - actionIconsPlaceable.width,
                y = verticalCenter - actionIconsPlaceable.height / 2,
            )

            // Subtitle (start-aligned, 3.5dp below the title)
            subtitlePlaceable?.placeRelative(
                x = titleX,
                y = subtitleY,
            )

            // Bottom content (below the toolbar area)
            bottomContentPlaceable.placeRelative(x = 0, y = contentTop)
        }
    }
}

/** COUI `coui_appbar_title_collapsed_text_size` (18dp, sans-serif-medium). */
private val CollapsedTitleTextSize = 18.sp

/**
 * COUIEaseInterpolator: cubic-bezier(0.33, 0, 0.67, 1), applied to the collapsing title's
 * position and scale.
 */
private val CollapsingTitleEasing = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)

/**
 * The duration of the snap animation that settles the bar after a drag or fling
 * (AppBarLayout.BaseBehavior.animateOffsetTo for the app bar's snap distance).
 */
private val SnapAnimationDuration = 180

/** The DecelerateInterpolator that AppBarLayout.BaseBehavior installs on its offset animator. */
private val SnapAnimationEasing = Easing { fraction -> 1f - (1f - fraction) * (1f - fraction) }

/**
 * The collapsed fraction at which the morphing large title is swapped for the dedicated
 * collapsed title (CollapsingTextHelper isClose(fraction, 1f)).
 */
private val CollapsedTitleSwapFraction = 0.9999f

/** COUI: toolbar_divider_height = 0.33dp. */
private val DividerThickness = 0.33.dp

/** COUI: coui_appbar_divider_expanded_margin_horizontal = 24dp (collapsed = 0dp). */
private val DividerExpandedMarginHorizontal = 24.dp

/** COUI: preference_line_alpha_range_change_offset = 10dp (pinned bar divider alpha ramp). */
private val DividerAlphaChangeOffset = 10.dp

/** COUI: preference_divider_width_start_count_offset = 10dp. */
private val DividerWidthStartCountOffset = 10.dp

/** COUI: preference_divider_width_change_offset = 25dp. */
private val DividerWidthChangeOffset = 25.dp
