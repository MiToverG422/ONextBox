// Copyright 2026, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * Internal shared layout for the security dialog variants (COUISecurityAlertDialogBuilder,
 * `coui_security_alert_dialog_statement_or_checkbox.xml`): a regular alert dialog extended
 * with a statement paragraph (with an optional inline link), a "don't remind me again"
 * checkbox row and a two-button bar.
 *
 * @param show Whether the dialog is currently shown.
 * @param popupHost A composable that provides the dialog container (e.g., DialogLayout or Dialog).
 * @param onConfirm Invoked when the confirm button is clicked, with the current checkbox state.
 * @param onCancel Invoked when the cancel button is clicked, the user taps outside, or presses
 *   back (COUI reports the back key as the negative selection).
 * @param modifier The modifier to be applied to the dialog content.
 * @param title The title of the dialog.
 * @param summary The summary (message) of the dialog.
 * @param statement The statement paragraph. Hidden when null.
 * @param statementLinkText The substring of [statement] rendered as a tappable link.
 * @param onLinkClick Invoked when the statement link is clicked.
 * @param checkboxText The label of the checkbox row. Hidden when null.
 * @param initialChecked The initial checkbox state, re-applied each time the dialog is shown.
 * @param confirmText The label of the confirm (positive) button.
 * @param cancelText The label of the cancel (negative) button.
 * @param titleColor The color of the title.
 * @param summaryColor The color of the summary.
 * @param backgroundColor The background color of the dialog.
 * @param colors The [SecurityDialogColors] of the statement, link and checkbox texts.
 * @param enableWindowDim Whether to enable window dimming.
 * @param onDismissFinished Invoked when the hide animation completes.
 * @param topInset Optional top inset override forwarded to [DialogContentLayout].
 */
@Composable
internal fun SecurityDialogContentLayout(
    show: Boolean,
    popupHost: @Composable (visible: Boolean, content: @Composable () -> Unit) -> Unit,
    onConfirm: (dontRemind: Boolean) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    summary: String? = null,
    statement: String? = null,
    statementLinkText: String? = null,
    onLinkClick: (() -> Unit)? = null,
    checkboxText: String? = SecurityDialogDefaults.CheckboxText,
    initialChecked: Boolean = false,
    confirmText: String = SecurityDialogDefaults.ConfirmText,
    cancelText: String = SecurityDialogDefaults.CancelText,
    titleColor: Color = Color.Unspecified,
    summaryColor: Color = Color.Unspecified,
    backgroundColor: Color = Color.Unspecified,
    colors: SecurityDialogColors = SecurityDialogDefaults.securityDialogColors(),
    enableWindowDim: Boolean = true,
    onDismissFinished: (() -> Unit)? = null,
    topInset: Dp? = null,
) {
    val checked = remember { mutableStateOf(initialChecked) }
    val currentInitialChecked by rememberUpdatedState(initialChecked)
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentOnCancel by rememberUpdatedState(onCancel)
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)

    LaunchedEffect(show) {
        // COUI re-applies the initial checkbox state on each show.
        if (show) checked.value = currentInitialChecked
    }

    val requestCancel = remember { { currentOnCancel() } }

    DialogContentLayout(
        show = show,
        titleColor = titleColor,
        summaryColor = summaryColor,
        backgroundColor = backgroundColor,
        outsideMargin = DialogDefaults.outsideMargin,
        insideMargin = DialogDefaults.insideMargin,
        popupHost = popupHost,
        modifier = modifier,
        title = title,
        summary = summary,
        enableWindowDim = enableWindowDim,
        onDismissRequest = requestCancel,
        onDismissFinished = onDismissFinished,
        topInset = topInset,
    ) {
        if (statement != null) {
            val linkColor = colors.linkColor
            val statementText = remember(statement, statementLinkText, linkColor) {
                buildAnnotatedString {
                    val link = statementLinkText.takeUnless { it.isNullOrEmpty() }
                    val linkIndex = link?.let { statement.indexOf(it) } ?: -1
                    if (link == null || linkIndex < 0) {
                        append(statement)
                    } else {
                        append(statement.substring(0, linkIndex))
                        // COUI link: accent color at rest, 30% alpha while pressed, no underline.
                        val linkStyles = TextLinkStyles(
                            style = SpanStyle(color = linkColor),
                            pressedStyle = SpanStyle(color = linkColor.copy(alpha = linkColor.alpha * 0.3f)),
                        )
                        withLink(
                            LinkAnnotation.Clickable(tag = "statement-link", styles = linkStyles) {
                                currentOnLinkClick?.invoke()
                            },
                        ) {
                            append(link)
                        }
                        append(statement.substring(linkIndex + link.length))
                    }
                }
            }
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DialogDefaults.insideMargin.width)
                    .padding(top = StatementMarginTop, bottom = StatementMarginBottom),
                text = statementText,
                fontSize = StatementFontSize,
                textAlign = TextAlign.Start,
                lineHeight = StatementLineHeight,
                color = colors.statementColor,
            )
        }
        if (checkboxText != null) {
            val hapticFeedback = LocalHapticFeedback.current
            val currentHapticFeedback by rememberUpdatedState(hapticFeedback)
            Row(
                // COUI aligns the checkbox drawable's inner ring with the text edge.
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = CheckboxRowMarginStart, end = DialogDefaults.insideMargin.width)
                    .toggleable(
                        value = checked.value,
                        interactionSource = null,
                        indication = null,
                        role = Role.Checkbox,
                        onValueChange = {
                            checked.value = it
                            currentHapticFeedback.performHapticFeedback(
                                if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                            )
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    state = ToggleableState(checked.value),
                    onClick = null,
                )
                Text(
                    modifier = Modifier.padding(start = CheckboxTextGap),
                    text = checkboxText,
                    fontSize = CheckboxFontSize,
                    color = colors.checkboxTextColor,
                )
            }
        }
        DialogButtonBar(
            negative = DialogButtonBarAction(text = cancelText, onClick = requestCancel),
            positive = remember(confirmText) {
                DialogButtonBarAction(text = confirmText, onClick = { currentOnConfirm(checked.value) })
            },
            // COUI coui_alert_dialog_customer_layout_padding_bottom, the gap above the bar.
            modifier = Modifier.padding(top = ButtonBarMarginTop),
        )
    }
}

object SecurityDialogDefaults {
    /** The default checkbox label. COUI `coui_security_alertdialog_checkbox_msg`. */
    val CheckboxText = "Don't remind me again"

    /** The default confirm (positive) button label. */
    val ConfirmText = "OK"

    /** The default cancel (negative) button label. */
    val CancelText = "Cancel"

    /**
     * The default [SecurityDialogColors]. Statement and checkbox use couiColorSecondNeutral
     * (secondary label) and the inline link uses couiColorLink (accent).
     */
    @Composable
    fun securityDialogColors(
        statementColor: Color = COUITheme.colorScheme.onBackgroundVariant,
        linkColor: Color = COUITheme.colorScheme.primary,
        checkboxTextColor: Color = COUITheme.colorScheme.onBackgroundVariant,
    ): SecurityDialogColors = remember(statementColor, linkColor, checkboxTextColor) {
        SecurityDialogColors(
            statementColor = statementColor,
            linkColor = linkColor,
            checkboxTextColor = checkboxTextColor,
        )
    }
}

@Immutable
data class SecurityDialogColors(
    val statementColor: Color,
    val linkColor: Color,
    val checkboxTextColor: Color,
)

/** COUI coui_security_alert_dialog_statement_text_size. */
private val StatementFontSize = 12.sp

/** COUI coui_security_alert_dialog_checkbox_text_size. */
private val CheckboxFontSize = 12.sp

/** COUI statement lineSpacingMultiplier 1.2, resolved against sans-serif font metrics. */
private val StatementLineHeight = 1.41f.em

/** COUI statement android:layout_marginTop. */
private val StatementMarginTop = 10.dp

/** COUI statement android:layout_marginBottom. */
private val StatementMarginBottom = 6.dp

/** COUI coui_alert_dialog_checkbox_margin_left. */
private val CheckboxRowMarginStart = 20.dp

/** COUI coui_checkbox_margin_between_text_drawable. */
private val CheckboxTextGap = 8.dp

/** COUI coui_alert_dialog_customer_layout_padding_bottom, the gap above the button bar. */
private val ButtonBarMarginTop = 8.dp
