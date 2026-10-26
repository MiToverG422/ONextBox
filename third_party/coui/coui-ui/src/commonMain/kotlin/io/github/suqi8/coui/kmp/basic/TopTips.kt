// Copyright 2026, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.basic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * A [TopTips] component with COUI style.
 *
 * A tip banner shown at the top of a page, mirroring ColorOS's COUIDefaultTopTips: a rounded
 * card (couiColorContainer4 fill, couiRoundCornerM corners) holding an optional 24dp start icon,
 * a 14sp message and one trailing element. Like the original view the trailing element is
 * exclusive: providing [actionText] shows an accent colored text button (COUI text-button type),
 * otherwise providing [onClose] shows a round close button (COUI image-button type). When the
 * message would collide with the action label, the label drops to its own row below the text
 * (COUIDefaultTopTipsView.isNeedMultiText).
 *
 * Toggling [visible] plays the COUICustomTopTips show/dismiss animation (a 250ms vertical scale);
 * the banner leaves the composition once the dismiss animation finishes.
 *
 * @param text The message of the [TopTips].
 * @param modifier The modifier to be applied to the [TopTips].
 * @param visible Whether the [TopTips] is visible. Changing this value animates the banner in/out.
 * @param startIcon The optional leading icon of the [TopTips], laid out in a
 *   [TopTipsDefaults.IconSize] box.
 * @param actionText The optional label of the trailing action button.
 * @param onAction The callback when the action button is clicked.
 * @param onClose The callback when the close button is clicked. Only shown when [actionText] is
 *   absent, matching COUIDefaultTopTipsView's exclusive button types.
 * @param cornerRadius The corner radius of the [TopTips].
 * @param colors The [TopTipsColors] of the [TopTips].
 */
@Composable
fun TopTips(
    text: String,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    startIcon: (@Composable () -> Unit)? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    cornerRadius: Dp = TopTipsDefaults.CornerRadius,
    colors: TopTipsColors = TopTipsDefaults.topTipsColors(),
) {
    // COUICustomTopTips.showWithAnim/dismissWithAnim: ObjectAnimator scaleY 0..1 over 250ms with
    // the default accelerate-decelerate interpolator, pivoting at the view center.
    val visibility = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        val target = if (visible) 1f else 0f
        if (visibility.value != target) {
            visibility.animateTo(target, tween(ShowDismissDurationMillis, easing = ShowDismissEasing))
        }
    }
    if (!visible && visibility.value == 0f) return

    val hasAction = !actionText.isNullOrEmpty()
    val showClose = !hasAction && onClose != null
    val textStyle = TopTipsDefaults.textStyle()
    val actionStyle = TopTipsDefaults.actionTextStyle()

    Box(
        modifier = modifier
            .graphicsLayer { scaleY = visibility.value }
            .squircleSurface(color = colors.containerColor, cornerRadius = cornerRadius),
    ) {
        BoxWithConstraints {
            // COUIDefaultTopTipsView.isNeedMultiText: the layout switches to the multi-line
            // variant when the single-line message plus the button clearance would reach into
            // the action label.
            val density = LocalDensity.current
            val textMeasurer = rememberTextMeasurer()
            val multiLine = hasAction && constraints.hasBoundedWidth && with(density) {
                val startInset = TopTipsDefaults.ContentPadding +
                    if (startIcon != null) TopTipsDefaults.IconSize + TopTipsDefaults.IconSpacing else 0.dp
                val actionWidth = textMeasurer
                    .measure(AnnotatedString(actionText!!), style = actionStyle, maxLines = 1)
                    .size.width.toDp() + ActionPaddingHorizontal * 2
                val titleWidth = textMeasurer
                    .measure(AnnotatedString(text), style = textStyle, maxLines = 1)
                    .size.width.toDp()
                val actionStart = this@BoxWithConstraints.maxWidth - ActionEndMargin - actionWidth
                startInset + titleWidth + ButtonClearance >= actionStart
            }

            if (multiLine) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = TopTipsDefaults.ContentPadding, bottom = MultiActionBottomPadding),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        if (startIcon != null) {
                            Box(
                                modifier = Modifier
                                    .padding(top = MultiIconTopPadding)
                                    .size(TopTipsDefaults.IconSize),
                                contentAlignment = Alignment.Center,
                            ) {
                                startIcon()
                            }
                        }
                        Text(
                            text = text,
                            color = colors.contentColor,
                            style = textStyle,
                            modifier = Modifier
                                .weight(1f)
                                .padding(
                                    start = if (startIcon != null) TopTipsDefaults.IconSpacing else 0.dp,
                                    top = TopTipsDefaults.VerticalPadding,
                                    end = MultiTitleEndMargin,
                                ),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = ActionRowTopSpacing, end = ActionEndMargin),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TopTipsActionLabel(
                            text = actionText!!,
                            color = colors.actionColor,
                            style = actionStyle,
                            onClick = onAction,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = TopTipsDefaults.ContentPadding,
                            top = TopTipsDefaults.VerticalPadding,
                            end = if (hasAction) ActionEndMargin else TopTipsDefaults.ContentPadding,
                            bottom = TopTipsDefaults.VerticalPadding,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (startIcon != null) {
                        Box(
                            modifier = Modifier.size(TopTipsDefaults.IconSize),
                            contentAlignment = Alignment.Center,
                        ) {
                            startIcon()
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(
                                start = if (startIcon != null) TopTipsDefaults.IconSpacing else 0.dp,
                                end = if (hasAction || showClose) TitleTrailingSpacing else 0.dp,
                            )
                            .defaultMinSize(minHeight = TopTipsDefaults.TextMinHeight),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = text,
                            color = colors.contentColor,
                            style = textStyle,
                        )
                    }
                    if (hasAction) {
                        TopTipsActionLabel(
                            text = actionText!!,
                            color = colors.actionColor,
                            style = actionStyle,
                            onClick = onAction,
                        )
                    } else if (showClose) {
                        TopTipsCloseButton(
                            backgroundColor = colors.closeButtonColor,
                            iconColor = colors.closeIconColor,
                            onClick = onClose!!,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The trailing action label: an accent colored 14sp medium text with the capsule press ripple
 * COUITextViewCompatUtil.setPressRippleDrawable applies (12dp/4dp ripple padding).
 */
@Composable
private fun TopTipsActionLabel(
    text: String,
    color: Color,
    style: TextStyle,
    onClick: (() -> Unit)?,
) {
    Text(
        text = text,
        color = color,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(ActionPadding),
    )
}

/**
 * The trailing close button, redrawn from coui_ic_toptips_close: a 24dp icon holding an 18dp
 * couiColorControls circle with a white rounded cross.
 */
@Composable
private fun TopTipsCloseButton(
    backgroundColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(TopTipsDefaults.IconSize)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Close" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(TopTipsDefaults.IconSize)) {
            drawCircle(color = backgroundColor, radius = CloseCircleRadius.toPx(), center = center)
            val arm = CloseCrossArm.toPx()
            val stroke = CloseCrossStrokeWidth.toPx()
            drawLine(
                color = iconColor,
                start = Offset(center.x - arm, center.y - arm),
                end = Offset(center.x + arm, center.y + arm),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = iconColor,
                start = Offset(center.x - arm, center.y + arm),
                end = Offset(center.x + arm, center.y - arm),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Contains default values used by [TopTips]. */
object TopTipsDefaults {
    /** The corner radius of the banner (COUI couiRoundCornerM: coui_round_corner_m 12dp). */
    val CornerRadius = 12.dp

    /** The start/end padding of the banner content (COUI coui_toptips_view_btn_margin). */
    val ContentPadding = 12.dp

    /** The top/bottom padding of the message (COUI coui_toptips_view_title_top_margin). */
    val VerticalPadding = 12.dp

    /** The size of the start icon and the close button (COUI coui_toptips_view_icon_btn_size). */
    val IconSize = 24.dp

    /** The spacing between the start icon and the message (COUI coui_toptips_view_title_start_margin). */
    val IconSpacing = 8.dp

    /** The minimum height of the message (COUI coui_toptips_view_title_min_height). */
    val TextMinHeight = 20.dp

    /** The default text style of the message (COUI coui_toptips_view_default_text_size: 14sp). */
    @Composable
    fun textStyle(): TextStyle = COUITheme.textStyles.body2

    /** The default text style of the action button (14sp, sans-serif-medium in coui_default_toptips.xml). */
    @Composable
    fun actionTextStyle(): TextStyle = COUITheme.textStyles.body2.copy(fontWeight = FontWeight.Medium)

    /**
     * The default [TopTipsColors], mapping COUIDefaultTopTips: container = couiColorContainer4
     * (#0A000000 light / #14FFFFFF dark), message = couiColorSecondNeutral, action label =
     * couiColorPrimaryText and the close button circle = couiColorControls (#29000000 light /
     * #40FFFFFF dark) with a white cross.
     */
    @Composable
    fun topTipsColors(
        containerColor: Color = if (COUITheme.colorScheme.background.luminance() < 0.5f) ContainerColorDark else ContainerColorLight,
        contentColor: Color = COUITheme.colorScheme.onSurfaceSecondary,
        actionColor: Color = COUITheme.colorScheme.primary,
        closeButtonColor: Color = if (COUITheme.colorScheme.background.luminance() < 0.5f) CloseButtonColorDark else CloseButtonColorLight,
        closeIconColor: Color = Color.White,
    ): TopTipsColors = remember(containerColor, contentColor, actionColor, closeButtonColor, closeIconColor) {
        TopTipsColors(
            containerColor = containerColor,
            contentColor = contentColor,
            actionColor = actionColor,
            closeButtonColor = closeButtonColor,
            closeIconColor = closeIconColor,
        )
    }
}

/**
 * Colors used by a [TopTips].
 *
 * @param containerColor The background color of the banner.
 * @param contentColor The color of the message.
 * @param actionColor The color of the action label.
 * @param closeButtonColor The fill color of the close button circle.
 * @param closeIconColor The color of the cross inside the close button.
 */
@Immutable
data class TopTipsColors(
    val containerColor: Color,
    val contentColor: Color,
    val actionColor: Color,
    val closeButtonColor: Color,
    val closeIconColor: Color,
)

/** couiColorContainer4 in light themes (coui_color_container4 #0A000000). */
private val ContainerColorLight = Color(0x0A000000)

/** couiColorContainer4 in dark themes (coui_color_container4_dark #14FFFFFF). */
private val ContainerColorDark = Color(0x14FFFFFF)

/** couiColorControls in light themes (coui_color_controls #29000000). */
private val CloseButtonColorLight = Color(0x29000000)

/** couiColorControls in dark themes (coui_color_controls_dark #40FFFFFF). */
private val CloseButtonColorDark = Color(0x40FFFFFF)

/** The spacing between the message and the trailing element (COUI coui_toptips_view_btn_margin_right). */
private val TitleTrailingSpacing = 8.dp

/** The end margin of the message in the multi-line layout (COUI coui_toptips_view_title_end_margin). */
private val MultiTitleEndMargin = 18.dp

/** The spacing between the message and the action row in the multi-line layout (COUI coui_toptips_view_btn_top_margin). */
private val ActionRowTopSpacing = 8.dp

/** The bottom padding of the action row in the multi-line layout (COUI coui_toptips_view_multi_btn_text_bottom_margin). */
private val MultiActionBottomPadding = 8.dp

/** The minimum top padding of the start icon in the multi-line layout (COUI coui_toptips_view_multi_title_top_margin). */
private val MultiIconTopPadding = 10.dp

/** The end margin of the action button (layout_marginEnd of the action view in coui_default_toptips.xml). */
private val ActionEndMargin = 6.dp

/** The horizontal padding of the action press area (COUI text_ripple_bg_padding_horizontal). */
private val ActionPaddingHorizontal = 12.dp

/** The padding of the action press area (COUI text_ripple_bg_padding_horizontal/vertical). */
private val ActionPadding = PaddingValues(horizontal = ActionPaddingHorizontal, vertical = 4.dp)

/** The clearance kept between the message and the action label before wrapping (COUI coui_toptips_view_btn_margin). */
private val ButtonClearance = 12.dp

/** The radius of the close button circle (coui_ic_toptips_close: r=9 in a 24dp viewport). */
private val CloseCircleRadius = 9.dp

/**
 * Half the reach of each cross arm endpoint in coui_ic_toptips_close: the rounded stroke spans
 * 8.464..15.536, so with the 1.6dp stroke caps the line endpoints sit 2.736dp from the center.
 */
private val CloseCrossArm = 2.736.dp

/** The stroke width of the cross in coui_ic_toptips_close. */
private val CloseCrossStrokeWidth = 1.6.dp

/** COUICustomTopTips show/dismiss animation duration (ObjectAnimator setDuration(250)). */
private val ShowDismissDurationMillis = 250

/**
 * The show/dismiss easing: Android's default AccelerateDecelerateInterpolator, closely matched
 * by the symmetric ease-in-out cubic bezier.
 */
private val ShowDismissEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
