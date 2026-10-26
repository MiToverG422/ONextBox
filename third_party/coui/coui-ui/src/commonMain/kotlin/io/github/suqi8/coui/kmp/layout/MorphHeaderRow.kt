// Copyright 2026, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.layout

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlendModeColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.util.lerp
import io.github.suqi8.coui.kmp.basic.DropdownColors
import io.github.suqi8.coui.kmp.basic.DropdownDefaults
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.ArrowRight
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * Header row at the top of a cascading secondary popup. Visually mirrors its primary trigger
 * (chevron at the trailing edge); during expansion its outer height and top/bottom padding
 * interpolate from anchor measurements to intrinsic values. Tapping collapses the secondary.
 */
@Composable
internal fun MorphHeaderRow(
    triggerItem: DropdownItem,
    arrowRotation: () -> Float,
    expandFraction: () -> Float,
    anchorHeightPx: Int,
    anchorPaddingTopPx: Int,
    dropdownColors: DropdownColors,
    onClick: () -> Unit,
) {
    // COUI: title/summary keep the regular palette; only the chevron is theme-tinted while expanded.
    val backgroundColor = dropdownColors.containerColor
    val backgroundColorState = rememberUpdatedState(backgroundColor)
    val titleColor = dropdownColors.contentColor
    val summaryColor = dropdownColors.summaryColor
    val chevronColor = dropdownColors.selectedIndicatorColor
    val chevronColorFilter = remember(chevronColor) {
        BlendModeColorFilter(chevronColor, BlendMode.SrcIn)
    }
    val currentOnClick by rememberUpdatedState(onClick)

    Layout(
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DropdownDefaults.InsideHorizontalPadding),
            ) {
                Row(
                    modifier = Modifier.widthIn(max = DropdownDefaults.MaxItemTextWidth),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    triggerItem.icon?.let {
                        it(
                            Modifier
                                .sizeIn(
                                    minWidth = DropdownDefaults.IconMinSize,
                                    minHeight = DropdownDefaults.IconMinSize,
                                )
                                .padding(end = DropdownDefaults.IconEndPadding),
                        )
                    }
                    Column {
                        Text(
                            text = triggerItem.text,
                            fontSize = COUITheme.textStyles.body1.fontSize,
                            // COUI applies a medium typeface to the expanded submenu header row.
                            fontWeight = FontWeight.Medium,
                            color = titleColor,
                        )
                        triggerItem.summary?.let {
                            Text(
                                text = it,
                                fontSize = COUITheme.textStyles.body2.fontSize,
                                color = summaryColor,
                            )
                        }
                    }
                }
                Image(
                    modifier = Modifier
                        .padding(start = DropdownDefaults.CheckIconStartPadding)
                        .size(
                            width = DropdownDefaults.ChevronSize.width,
                            height = DropdownDefaults.ChevronSize.height,
                        )
                        .graphicsLayer { rotationZ = arrowRotation() },
                    imageVector = COUIIcons.Basic.ArrowRight,
                    colorFilter = chevronColorFilter,
                    contentDescription = null,
                )
            }
        },
        // selectable + background on the outer Layout so the click target spans the full
        // interpolated row height (paddings included), not just the inner Row's content.
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(backgroundColorState.value) }
            .selectable(
                selected = true,
                enabled = true,
                role = Role.Button,
                onClick = { currentOnClick() },
            ),
    ) { measurables, constraints ->
        val intrinsicTop = DropdownDefaults.FirstLastVerticalPadding.roundToPx()
        val intrinsicBottom = DropdownDefaults.MiddleVerticalPadding.roundToPx()
        val frac = expandFraction()
        val padTop = lerp(anchorPaddingTopPx.toFloat(), intrinsicTop.toFloat(), frac).toInt()

        // COUI popup-row min content height, so the clone's height and centering
        // match the primary row it mirrors.
        val minContentHeight = (
            DropdownDefaults.PopupItemMinHeight.roundToPx() -
                2 * DropdownDefaults.MiddleVerticalPadding.roundToPx()
            ).coerceIn(0, constraints.maxHeight)
        val placeable = measurables.first().measure(constraints.copy(minHeight = minContentHeight))
        val intrinsicTotal = placeable.height + intrinsicTop + intrinsicBottom
        val target = if (anchorHeightPx <= 0) {
            intrinsicTotal
        } else {
            lerp(anchorHeightPx.toFloat(), intrinsicTotal.toFloat(), frac)
                .toInt()
                .coerceAtLeast(0)
        }
        val width = placeable.width.coerceAtMost(constraints.maxWidth)

        layout(width, target) {
            placeable.place(0, padTop)
        }
    }
}
