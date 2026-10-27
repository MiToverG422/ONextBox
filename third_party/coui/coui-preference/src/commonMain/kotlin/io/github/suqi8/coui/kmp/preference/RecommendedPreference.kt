// Copyright 2026, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.preference

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * A standalone rounded card that recommends related settings, mirroring COUIRecommendedPreference.
 *
 * The card holds a small secondary header followed by one tappable text row per
 * [RecommendedItem]. Nothing is rendered when [items] is empty. Callers place the card like any
 * other card block, e.g. with 16dp horizontal outer margins.
 *
 * @param title The header text of the card, e.g. "You might be looking for:".
 * @param items The recommended entries of the card. Build the list inside a `remember` block to
 *   keep it stable across recompositions.
 * @param modifier The modifier to be applied to the [RecommendedPreference].
 * @param cornerRadius The corner radius of the card.
 * @param colors The [RecommendedPreferenceColors] of the [RecommendedPreference].
 * @param insideMargin The horizontal margin between the card edge and the texts.
 */
@Composable
fun RecommendedPreference(
    title: String,
    items: List<RecommendedItem>,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = RecommendedPreferenceDefaults.CornerRadius,
    colors: RecommendedPreferenceColors = RecommendedPreferenceDefaults.recommendedPreferenceColors(),
    insideMargin: PaddingValues = RecommendedPreferenceDefaults.InsideMargin,
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            // squircleSurface also masks the children, clipping the row press overlays to the card corners.
            .squircleSurface(color = colors.containerColor, cornerRadius = cornerRadius),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(insideMargin)
                .padding(
                    top = RecommendedPreferenceDefaults.HeaderTopPadding,
                    bottom = RecommendedPreferenceDefaults.HeaderBottomSpacing,
                )
                .heightIn(min = RecommendedPreferenceDefaults.HeaderMinHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.titleColor,
            )
        }
        items.forEachIndexed { index, item ->
            val onClick = item.onClick
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onClick != null) {
                            // COUI marks the recommended rows as buttons for accessibility.
                            Modifier.clickable(role = Role.Button) { onClick() }
                        } else {
                            Modifier
                        },
                    )
                    .padding(insideMargin)
                    .padding(
                        top = RecommendedPreferenceDefaults.ItemVerticalPadding,
                        bottom = if (index == items.lastIndex) {
                            RecommendedPreferenceDefaults.ItemVerticalPadding +
                                RecommendedPreferenceDefaults.BottomPadding
                        } else {
                            RecommendedPreferenceDefaults.ItemVerticalPadding
                        },
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = item.text,
                    fontSize = COUITheme.textStyles.body2.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = colors.itemColor,
                )
            }
        }
    }
}

/**
 * An entry of a [RecommendedPreference], mirroring COUIRecommendedPreference.RecommendedEntity.
 *
 * @param text The text of the row.
 * @param onClick The callback when the row is clicked. A `null` value renders an inert row.
 */
@Stable
data class RecommendedItem(
    val text: String,
    val onClick: (() -> Unit)? = null,
)

object RecommendedPreferenceDefaults {

    /** The default corner radius of the card (COUI couiRoundCornerM, 12dp). */
    val CornerRadius = 12.dp

    /**
     * The default horizontal margin between the card edge and the texts
     * (COUI recommended_recyclerView_padding_start minus the 16dp card margin).
     */
    val InsideMargin = PaddingValues(horizontal = 16.dp)

    /** The padding above the header text (COUI recommended_recyclerView_padding_top). */
    val HeaderTopPadding = 16.dp

    /**
     * The spacing between the header text and the first row
     * (COUI recommended_preference_list_item_head_bottom_margin).
     */
    val HeaderBottomSpacing = 8.dp

    /** The minimum height of the header text (COUI recommended_preference_list_item_height_head). */
    val HeaderMinHeight = 20.dp

    /**
     * The vertical padding of each row text
     * (COUI recommended_preference_list_item_common_margin_vertical).
     */
    val ItemVerticalPadding = 10.dp

    /** The extra padding below the last row (COUI recommended_recyclerView_padding_bottom). */
    val BottomPadding = 8.dp

    /**
     * The default [RecommendedPreferenceColors]. [containerColor] follows COUI couiColorContainer4
     * (#0A000000 light / #14FFFFFF dark), inferred from the background luminance.
     */
    @Composable
    fun recommendedPreferenceColors(
        containerColor: Color = if (COUITheme.colorScheme.background.luminance() < 0.5f) {
            Color(0x14FFFFFF)
        } else {
            Color(0x0A000000)
        },
        titleColor: Color = COUITheme.colorScheme.onSurfaceSecondary,
        itemColor: Color = COUITheme.colorScheme.onSurface,
    ): RecommendedPreferenceColors = remember(containerColor, titleColor, itemColor) {
        RecommendedPreferenceColors(
            containerColor = containerColor,
            titleColor = titleColor,
            itemColor = itemColor,
        )
    }
}

/**
 * Colors used by a [RecommendedPreference].
 *
 * @param containerColor The background color of the card.
 * @param titleColor The color of the header text.
 * @param itemColor The color of the row texts.
 */
@Immutable
data class RecommendedPreferenceColors(
    val containerColor: Color,
    val titleColor: Color,
    val itemColor: Color,
)
