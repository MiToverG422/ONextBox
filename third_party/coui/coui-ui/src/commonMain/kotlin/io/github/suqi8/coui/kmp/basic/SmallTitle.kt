// Copyright 2025, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.basic

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * A [SmallTitle] with COUI style.
 *
 * Typography follows the COUI category title (couiTextAppearanceSmallButton: 12sp sans-serif-medium).
 *
 * @param text The text to be displayed in the [SmallTitle].
 * @param modifier The modifier to be applied to the [SmallTitle].
 * @param textColor The color of the [SmallTitle].
 * @param insideMargin The margin inside the [SmallTitle].
 */
@Composable
@NonRestartableComposable
fun SmallTitle(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = COUITheme.colorScheme.onBackgroundVariant,
    insideMargin: PaddingValues = SmallTitleDefaults.InsideMargin,
) {
    Text(
        modifier = modifier
            .padding(insideMargin)
            .heightIn(min = SmallTitleDefaults.MinHeight)
            .wrapContentHeight(),
        text = text,
        style = COUITheme.textStyles.subtitle.copy(
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        ),
        color = textColor,
    )
}

/** Contains default values used by [SmallTitle]. */
object SmallTitleDefaults {
    /**
     * The default inside margin of the [SmallTitle] (COUI category title metrics:
     * 16dp page margin + 16dp card content inset horizontally, 8dp vertically).
     */
    val InsideMargin = PaddingValues(32.dp, 8.dp)

    /**
     * The default minimum text height of the [SmallTitle] (COUI coui_preference_category_text_height).
     */
    val MinHeight = 16.dp
}
