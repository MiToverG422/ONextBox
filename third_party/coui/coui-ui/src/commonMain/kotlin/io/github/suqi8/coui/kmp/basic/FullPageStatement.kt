// Copyright 2026, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.basic

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * A full page statement component with COUI style, mirroring ColorOS's COUIFullPageStatement
 * (coui_full_page_statement.xml): a centered title, a scrollable statement body with vertical
 * fading edges, and a bottom area holding a filled primary button plus an optional borderless
 * exit text button. Typically used as the first-launch privacy / user statement page.
 *
 * The statement body takes all the space left between the title and the button area, so the
 * buttons stay pinned to the bottom of the page.
 *
 * @param title The title of the [FullPageStatement], centered at the top
 *   (COUI couiTextHeadlineXS: 16sp, medium).
 * @param content The scrollable statement text (COUI couiTextBodyM: 14sp, secondary color).
 * @param primaryButtonText The label of the filled primary button (COUI btn_confirm).
 * @param onPrimaryButtonClick The callback when the primary button is clicked.
 * @param modifier The modifier to be applied to the [FullPageStatement].
 * @param secondaryButtonText The label of the borderless exit text button below the primary
 *   button (COUI txt_exit). When null, the exit button is not shown.
 * @param onSecondaryButtonClick The callback when the exit text button is clicked.
 * @param primaryButtonWidth The width of the primary button. When null, the COUI adaptive
 *   width is used (174dp, 220dp from 300dp wide windows, 280dp from 600dp wide windows —
 *   coui_full_page_statement_button_width buckets).
 * @param colors The [FullPageStatementColors] of the [FullPageStatement].
 * @param primaryButtonColors The [ButtonColors] of the filled primary button. COUI uses the
 *   colorful default style (theme primary fill).
 * @param contentPadding The padding around the statement text inside the scrollable area
 *   (COUI coui_full_page_statement_padding_horizontal/top/bottom).
 * @param scrollState The [ScrollState] of the statement text area.
 */
@Composable
fun FullPageStatement(
    title: String,
    content: String,
    primaryButtonText: String,
    onPrimaryButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryButtonText: String? = null,
    onSecondaryButtonClick: (() -> Unit)? = null,
    primaryButtonWidth: Dp? = null,
    colors: FullPageStatementColors = FullPageStatementDefaults.fullPageStatementColors(),
    primaryButtonColors: ButtonColors = ButtonDefaults.buttonColorsPrimary(),
    contentPadding: PaddingValues = FullPageStatementDefaults.ContentPadding,
    scrollState: ScrollState = rememberScrollState(),
) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Text(
            text = title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = FullPageStatementDefaults.TitleMarginTop,
                    bottom = FullPageStatementDefaults.TitleMarginBottom,
                )
                .padding(horizontal = FullPageStatementDefaults.TitleMarginHorizontal),
            fontSize = COUITheme.textStyles.headline2.fontSize,
            fontWeight = FontWeight.Medium,
            color = colors.titleColor,
            textAlign = TextAlign.Center,
        )
        // Statement body with vertical fading edges, like the COUIMaxHeightScrollView host.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fadePx = FullPageStatementDefaults.ScrollFadeLength
                        .toPx()
                        .coerceAtMost(size.height / 2f)
                    if (fadePx <= 0f || scrollState.maxValue <= 0) return@drawWithContent
                    // Android fading edges ramp in over the first fade length of scroll.
                    val topLength = fadePx * (scrollState.value / fadePx).coerceIn(0f, 1f)
                    if (topLength > 0f) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 0f,
                                endY = topLength,
                            ),
                            size = Size(size.width, topLength),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    val remaining = (scrollState.maxValue - scrollState.value).toFloat()
                    val bottomLength = fadePx * (remaining / fadePx).coerceIn(0f, 1f)
                    if (bottomLength > 0f) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = size.height - bottomLength,
                                endY = size.height,
                            ),
                            topLeft = Offset(0f, size.height - bottomLength),
                            size = Size(size.width, bottomLength),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                }
                .verticalScroll(scrollState),
        ) {
            Text(
                text = content,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                fontSize = COUITheme.textStyles.body2.fontSize,
                color = colors.contentColor,
            )
        }
        // Bottom button area (coui_full_page_statement.xml button_layout_normal).
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
        ) {
            val resolvedButtonWidth = primaryButtonWidth
                ?: FullPageStatementDefaults.primaryButtonWidth(maxWidth)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = onPrimaryButtonClick,
                    modifier = Modifier
                        .padding(
                            top = FullPageStatementDefaults.PrimaryButtonMarginTop,
                            bottom = FullPageStatementDefaults.PrimaryButtonMarginBottom,
                        )
                        .width(resolvedButtonWidth),
                    colors = primaryButtonColors,
                ) {
                    // COUIFullPageStatement caps the confirm label at two lines.
                    Text(
                        text = primaryButtonText,
                        style = COUITheme.textStyles.button,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (secondaryButtonText != null) {
                    // COUI txt_exit is a bare accent-colored TextView with no fill and no press overlay.
                    val interactionSource = remember { MutableInteractionSource() }
                    Text(
                        text = secondaryButtonText,
                        modifier = Modifier
                            .padding(bottom = FullPageStatementDefaults.SecondaryButtonMarginBottom)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Button,
                                onClick = { onSecondaryButtonClick?.invoke() },
                            ),
                        fontSize = COUITheme.textStyles.headline2.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = colors.secondaryButtonColor,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Contains default values used by [FullPageStatement]. */
object FullPageStatementDefaults {

    /** Top margin of the title (COUI coui_full_page_statement_text_button_padding). */
    val TitleMarginTop = 12.dp

    /** Bottom margin of the title (COUI coui_full_page_statement_content_margin). */
    val TitleMarginBottom = 12.dp

    /**
     * Horizontal margin of the title
     * (COUI coui_full_page_statement_text_button_padding_horizontal).
     */
    val TitleMarginHorizontal = 24.dp

    /**
     * The default padding around the statement text
     * (COUI coui_full_page_statement_padding_horizontal 24dp / _top 12dp / _bottom 14dp).
     */
    val ContentPadding = PaddingValues(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 14.dp)

    /** Length of the vertical fading edges (COUI coui_full_page_statement_scroll_fade_length). */
    val ScrollFadeLength = 46.dp

    /** Top margin of the primary button (COUI coui_full_page_statement_button_margin_top). */
    val PrimaryButtonMarginTop = 20.dp

    /** Bottom margin of the primary button (COUI coui_full_page_statement_button_margin). */
    val PrimaryButtonMarginBottom = 16.dp

    /**
     * Bottom margin of the exit text button
     * (COUI coui_full_page_statement_exit_button_margin_bottom).
     */
    val SecondaryButtonMarginBottom = 24.dp

    /** Primary button width in windows narrower than [MediumWidthThreshold] (base bucket). */
    val PrimaryButtonWidthCompact = 174.dp

    /** Primary button width in windows at least [MediumWidthThreshold] wide (values-w300dp). */
    val PrimaryButtonWidthMedium = 220.dp

    /** Primary button width in windows at least [ExpandedWidthThreshold] wide (values-w600dp). */
    val PrimaryButtonWidthExpanded = 280.dp

    /** Available width from which [PrimaryButtonWidthMedium] applies. */
    val MediumWidthThreshold = 300.dp

    /** Available width from which [PrimaryButtonWidthExpanded] applies. */
    val ExpandedWidthThreshold = 600.dp

    /**
     * The COUI adaptive primary button width for the given available width, following the
     * coui_full_page_statement_button_width resource buckets (base / w300dp / w600dp).
     */
    fun primaryButtonWidth(availableWidth: Dp): Dp = when {
        availableWidth >= ExpandedWidthThreshold -> PrimaryButtonWidthExpanded
        availableWidth >= MediumWidthThreshold -> PrimaryButtonWidthMedium
        else -> PrimaryButtonWidthCompact
    }

    /**
     * The default [FullPageStatementColors]: title on couiColorPrimaryNeutral, statement body
     * on couiColorSecondNeutral and exit text button on couiColorPrimaryTextOnPopup.
     */
    @Composable
    fun fullPageStatementColors(
        titleColor: Color = COUITheme.colorScheme.onBackground,
        contentColor: Color = COUITheme.colorScheme.onSurfaceSecondary,
        secondaryButtonColor: Color = COUITheme.colorScheme.primary,
    ): FullPageStatementColors = remember(titleColor, contentColor, secondaryButtonColor) {
        FullPageStatementColors(
            titleColor = titleColor,
            contentColor = contentColor,
            secondaryButtonColor = secondaryButtonColor,
        )
    }
}

/**
 * Colors used by a [FullPageStatement].
 *
 * @param titleColor The color of the title.
 * @param contentColor The color of the statement text.
 * @param secondaryButtonColor The color of the exit text button.
 */
@Immutable
data class FullPageStatementColors(
    val titleColor: Color,
    val contentColor: Color,
    val secondaryButtonColor: Color,
)
