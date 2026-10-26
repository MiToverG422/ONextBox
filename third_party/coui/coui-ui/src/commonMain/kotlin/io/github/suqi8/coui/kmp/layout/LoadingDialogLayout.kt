// Copyright 2026, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * Internal shared layout for the loading dialog variants (COUIRotatingDialogBuilder,
 * `coui_progress_dialog_rotating.xml`): a small centered card holding a rotating spinner
 * and an optional message below it.
 *
 * @param show Whether the dialog is currently shown.
 * @param popupHost A composable that provides the dialog container (e.g., DialogLayout or Dialog).
 * @param modifier The modifier to be applied to the dialog content.
 * @param text The optional message shown below the spinner.
 * @param textColor The color of the message text.
 * @param spinnerColor The color of the spinner.
 * @param backgroundColor The background color of the dialog card.
 * @param enableWindowDim Whether to enable window dimming.
 * @param onDismissRequest Invoked when the user taps outside or presses back. When null,
 *   the dialog cannot be dismissed by the user (matching a non-cancelable COUI dialog).
 * @param onDismissFinished Invoked when the hide animation completes.
 * @param topInset Optional top inset override forwarded to [DialogContentLayout].
 */
@Composable
internal fun LoadingDialogContentLayout(
    show: Boolean,
    popupHost: @Composable (visible: Boolean, content: @Composable () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    textColor: Color = Color.Unspecified,
    spinnerColor: Color = Color.Unspecified,
    backgroundColor: Color = Color.Unspecified,
    enableWindowDim: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    topInset: Dp? = null,
) {
    DialogContentLayout(
        show = show,
        titleColor = Color.Unspecified,
        summaryColor = Color.Unspecified,
        backgroundColor = backgroundColor,
        outsideMargin = DialogDefaults.outsideMargin,
        // The rotating layout manages its own margins; the card is not padded by the panel.
        insideMargin = DpSize(0.dp, 0.dp),
        popupHost = popupHost,
        modifier = modifier,
        title = null,
        summary = null,
        enableWindowDim = enableWindowDim,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        // COUI rotating dialogs are always centered with the center-dialog transitions.
        forceCentered = true,
        cornerRadius = LoadingDialogDefaults.CornerRadius,
        topInset = topInset,
    ) {
        Column(
            modifier = Modifier
                .width(LoadingDialogDefaults.CardWidth)
                .heightIn(min = LoadingDialogDefaults.CardMinHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (text != null) {
                InfiniteProgressIndicator(
                    modifier = Modifier.padding(top = SpinnerMarginTop, bottom = SpinnerMarginBottom),
                    color = spinnerColor,
                    size = LoadingDialogDefaults.SpinnerSize,
                )
                Text(
                    modifier = Modifier
                        .padding(horizontal = TextMarginHorizontal)
                        .padding(bottom = TextMarginBottom),
                    text = text,
                    fontSize = COUITheme.textStyles.body2.fontSize,
                    textAlign = TextAlign.Center,
                    color = textColor,
                )
            } else {
                // Without a message the spinner simply centers inside the card.
                InfiniteProgressIndicator(
                    color = spinnerColor,
                    size = LoadingDialogDefaults.SpinnerSize,
                )
            }
        }
    }
}

object LoadingDialogDefaults {
    /**
     * The width of the loading card. COUI `coui_spinner_layout_width` /
     * `coui_loading_alert_dialog_max_width` (152dp).
     */
    val CardWidth = 152.dp

    /** The minimum height of the loading card. COUI `coui_spinner_layout_min_height` (120dp). */
    val CardMinHeight = 120.dp

    /**
     * The corner radius of the loading card. COUI `coui_round_corner_m_radius`, used
     * instead of the XXL radius of regular alert dialogs.
     */
    val CornerRadius = 9.dp

    /**
     * The size of the rotating spinner. COUI `coui_spinner_loading_anim_width/height` (26dp).
     */
    val SpinnerSize = 26.dp

    /**
     * The default color of the message below the spinner. COUI progress_tips uses
     * couiColorLabelPrimary.
     */
    @Composable
    fun textColor() = COUITheme.colorScheme.onBackground

    /**
     * The default color of the spinner. The COUI rotating animation strokes the primary
     * label color equivalent in both themes.
     */
    @Composable
    fun spinnerColor() = COUITheme.colorScheme.onBackground
}

/** COUI coui_spinner_margin_top. */
private val SpinnerMarginTop = 26.dp

/** COUI coui_spinner_margin_bottom. */
private val SpinnerMarginBottom = 16.dp

/** COUI coui_spinner_text_margin_bottom. */
private val TextMarginBottom = 26.dp

/** COUI coui_spinner_text_margin_horizontal. */
private val TextMarginHorizontal = 16.dp
