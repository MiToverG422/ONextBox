// Copyright 2025, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.basic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.interfaces.HoldDownObserver
import io.github.suqi8.coui.kmp.interfaces.collectIsHeldDownAsState
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalContentColor
import io.github.suqi8.coui.kmp.utils.PressFeedbackType
import io.github.suqi8.coui.kmp.utils.SinkFeedback
import io.github.suqi8.coui.kmp.utils.TiltFeedback
import io.github.suqi8.coui.kmp.utils.pressable
import kotlin.coroutines.cancellation.CancellationException

/**
 * A [Card] component with COUI style.
 * Card contain content and actions that relate information about a subject.
 *
 * This [Card] does not handle input events
 *
 * @param modifier The modifier to be applied to the [Card].
 * @param cornerRadius The corner radius of the [Card].
 * @param insideMargin The margin inside the [Card].
 * @param colors [CardColors] that will be used to resolve the color(s) used for the [Card].
 * @param content The [Composable] content of the [Card].
 */
@Composable
@NonRestartableComposable
fun Card(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = CardDefaults.CornerRadius,
    insideMargin: PaddingValues = CardDefaults.InsideMargin,
    colors: CardColors = CardDefaults.defaultColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    BasicCard(
        color = colors.color,
        contentColor = colors.contentColor,
        modifier = modifier,
        cornerRadius = cornerRadius,
    ) {
        Column(
            modifier = Modifier.padding(insideMargin),
            content = content,
        )
    }
}

/**
 * A [Card] component with COUI style.
 * Card contain contain content and actions that relate information about a subject.
 *
 * This [Card] handles input events
 *
 * @param modifier The modifier to be applied to the [Card].
 * @param cornerRadius The corner radius of the [Card].
 * @param insideMargin The margin inside the [Card].
 * @param colors [CardColors] that will be used to resolve the color(s) used for the [Card].
 * @param pressFeedbackType The press feedback type of the [Card]. Defaults to
 *   [PressFeedbackType.Tint], which animates the fill towards [CardColors.pressedColor].
 * @param showIndication Whether to show indication of the [Card].
 * @param holdDownState Whether the [Card] is in a hold-down state.
 * @param onClick The callback to be invoked when the [Card] is clicked.
 * @param onLongPress The callback to be invoked when the [Card] is long pressed.
 * @param content The [Composable] content of the [Card].
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = CardDefaults.CornerRadius,
    insideMargin: PaddingValues = CardDefaults.InsideMargin,
    colors: CardColors = CardDefaults.defaultColors(),
    pressFeedbackType: PressFeedbackType = PressFeedbackType.Tint,
    showIndication: Boolean = false,
    holdDownState: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    HoldDownObserver(holdDownState, interactionSource)

    val pressFeedback = remember(pressFeedbackType) {
        when (pressFeedbackType) {
            PressFeedbackType.None, PressFeedbackType.Tint -> null
            PressFeedbackType.Sink -> SinkFeedback()
            PressFeedbackType.Tilt -> TiltFeedback()
        }
    }

    // COUI card press feedback (COUICardListSelectedItemLayout): fill animates towards pressedColor.
    val tintEnabled = pressFeedbackType == PressFeedbackType.Tint
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHeldDown by interactionSource.collectIsHeldDownAsState()
    val tintProgress = remember { Animatable(0f) }
    LaunchedEffect(tintEnabled, isPressed, isHeldDown) {
        if (!tintEnabled) {
            tintProgress.snapTo(0f)
        } else if (isPressed || isHeldDown) {
            tintProgress.animateTo(1f, CardPressFeedbackSpring)
        } else if (tintProgress.value > 0f) {
            if (tintProgress.value < PressedTintMinVisibleProgress) {
                // Keep the press-in animation running until the tint is clearly visible.
                try {
                    tintProgress.animateTo(1f, CardPressFeedbackSpring) {
                        if (value >= PressedTintMinVisibleProgress) throw CardPressTintThresholdReached()
                    }
                } catch (_: CardPressTintThresholdReached) {
                    // Minimum visible progress reached; fall through to the fade-out.
                }
            }
            tintProgress.animateTo(0f, CardPressFeedbackSpring)
        }
    }
    val fillColor = if (tintProgress.value > 0f) {
        lerp(colors.color, colors.pressedColor, tintProgress.value)
    } else {
        colors.color
    }

    val usedInteractionSource = if (pressFeedback != null) interactionSource else null
    val indicationToUse = if (showIndication) LocalIndication.current else null

    val hasOnClick = onClick != null
    val hasLongPress = onLongPress != null
    val isClickable = hasOnClick || hasLongPress
    val clickableModifier = remember(isClickable, hasLongPress, interactionSource, indicationToUse) {
        if (isClickable) {
            Modifier.combinedClickable(
                interactionSource = interactionSource,
                indication = indicationToUse,
                onClick = { currentOnClick?.invoke() },
                onLongClick = if (hasLongPress) {
                    { currentOnLongPress?.invoke() }
                } else {
                    null
                },
            )
        } else {
            Modifier
        }
    }

    BasicCard(
        color = fillColor,
        contentColor = colors.contentColor,
        modifier = modifier.pressable(
            interactionSource = usedInteractionSource,
            indication = pressFeedback,
            delay = null,
        ),
        cornerRadius = cornerRadius,
    ) {
        Column(
            modifier = Modifier
                .then(clickableModifier)
                .padding(insideMargin),
            content = content,
        )
    }
}

/**
 * A [BasicCard] component.
 *
 * @param color The background color of the [BasicCard].
 * @param contentColor The content color of the [BasicCard].
 * @param modifier The modifier to be applied to the [BasicCard].
 * @param cornerRadius The corner radius of the [BasicCard].
 * @param content The [Composable] content of the [BasicCard].
 */
@Composable
private fun BasicCard(
    color: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = CardDefaults.CornerRadius,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
    ) {
        Box(
            modifier = modifier
                .semantics(mergeDescendants = false) {
                    isTraversalGroup = true
                }
                .squircleSurface(color = color, cornerRadius = cornerRadius),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

/**
 * The position of a row inside a card group, mirroring COUI's `COUICardListHelper` constants
 * (`NONE = 0`, `HEAD = 1`, `MIDDLE = 2`, `TAIL = 3`, `FULL = 4`).
 *
 * COUI's `COUICardListSelectedItemLayout.setPadding` uses the position to decide which of the card's
 * outer edges get an extra [CardListPosition.HeadOrTailPadding] of vertical padding, and grows the
 * row's minimum height by the same amount. Only the edges that are visually rounded receive it, so a
 * standalone row (both edges rounded) gains the padding twice while a middle row gains none.
 *
 * @see cardListPositionOf
 */
enum class CardListPosition {
    /** Not part of a card group; no rounded edges and no extra padding. */
    None,

    /** First row of a multi-row group; the top edge is rounded. */
    Head,

    /** A row with a sibling on both sides; neither edge is rounded. */
    Middle,

    /** Last row of a multi-row group; the bottom edge is rounded. */
    Tail,

    /** The only row of the group; both edges are rounded. */
    Full,
    ;

    /** The extra top padding contributed by this position. */
    internal val extraPaddingTop: Dp
        get() = if (this == Head || this == Full) HeadOrTailPadding else 0.dp

    /** The extra bottom padding contributed by this position. */
    internal val extraPaddingBottom: Dp
        get() = if (this == Tail || this == Full) HeadOrTailPadding else 0.dp

    companion object {
        /**
         * The padding COUI adds to each rounded outer edge of a card group
         * (COUI `coui_list_card_head_or_tail_padding`).
         */
        val HeadOrTailPadding = 2.dp
    }
}

/**
 * Resolves the [CardListPosition] of the row at [index] within a card group of [count] rows,
 * mirroring COUI's `COUICardListHelper.getPositionInGroup(int, int)`.
 *
 * @param index The zero-based index of the row within the group.
 * @param count The total number of rows in the group.
 */
@Stable
fun cardListPositionOf(index: Int, count: Int): CardListPosition = when {
    count <= 0 || index !in 0 until count -> CardListPosition.None
    count == 1 -> CardListPosition.Full
    index == 0 -> CardListPosition.Head
    index == count - 1 -> CardListPosition.Tail
    else -> CardListPosition.Middle
}

object CardDefaults {

    /**
     * The default corner radius of the [Card] (COUI coui_round_corner_m).
     */
    val CornerRadius = 12.dp

    /**
     * The default margin inside the [Card].
     */
    val InsideMargin = PaddingValues(0.dp)

    /**
     * The default colors width of the [Card].
     */
    @Composable
    fun defaultColors(
        color: Color = COUITheme.colorScheme.surfaceContainer,
        contentColor: Color = COUITheme.colorScheme.onSurfaceContainer,
        pressedColor: Color = COUITheme.colorScheme.surfaceContainerHigh,
    ): CardColors = remember(color, contentColor, pressedColor) {
        CardColors(
            color = color,
            contentColor = contentColor,
            pressedColor = pressedColor,
        )
    }
}

/**
 * Colors used by a [Card].
 *
 * @param color The background color of the card.
 * @param contentColor The content color of the card.
 * @param pressedColor The fill color the card animates towards while pressed (couiColorCardPressed).
 */
@Immutable
data class CardColors(
    val color: Color,
    val contentColor: Color,
    val pressedColor: Color,
)

/** The COUI card press feedback spring (COUISpringForce response 0.3f / bounce 0f). */
private val CardPressFeedbackSpring = spring<Float>(dampingRatio = 1f, stiffness = 438.65f)

/** The minimum press tint progress before a release may fade out (COUIMaskEffectDrawable). */
private val PressedTintMinVisibleProgress = 0.7f

/** Thrown to stop the deferred press-in tint animation once [PressedTintMinVisibleProgress] is reached. */
private class CardPressTintThresholdReached : CancellationException("Press tint reached the minimum visible progress")
