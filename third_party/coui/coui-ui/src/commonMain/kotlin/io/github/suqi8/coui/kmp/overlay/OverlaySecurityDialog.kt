// Copyright 2026, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.overlay

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.layout.SecurityDialogColors
import io.github.suqi8.coui.kmp.layout.SecurityDialogContentLayout
import io.github.suqi8.coui.kmp.layout.SecurityDialogDefaults
import io.github.suqi8.coui.kmp.utils.COUIPopupUtils.Companion.DialogLayout

/**
 * A security statement dialog matching the COUI security alert dialog
 * (COUISecurityAlertDialogBuilder): a regular dialog extended with a statement paragraph
 * (with an optional tappable link), a "don't remind me again" checkbox row and a
 * cancel / confirm button bar.
 *
 * @param show Whether the [OverlaySecurityDialog] is shown.
 * @param onConfirm Invoked when the confirm button is clicked, with the current checkbox state.
 * @param onCancel Invoked when the cancel button is clicked, the user taps outside, or presses
 *   the back button.
 * @param modifier The modifier to be applied to the [OverlaySecurityDialog].
 * @param title The title of the dialog.
 * @param summary The summary (message) of the dialog.
 * @param statement The statement paragraph shown below the summary. Hidden when null.
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
 * @param enableWindowDim Whether to enable window dimming when the dialog is shown.
 * @param onDismissFinished The callback when the [OverlaySecurityDialog] is completely dismissed.
 * @param renderInRootScaffold Whether to render the dialog in the root (outermost) Scaffold.
 */
@Composable
fun OverlaySecurityDialog(
    show: Boolean,
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
    titleColor: Color = DialogDefaults.titleColor(),
    summaryColor: Color = DialogDefaults.summaryColor(),
    backgroundColor: Color = DialogDefaults.backgroundColor(),
    colors: SecurityDialogColors = SecurityDialogDefaults.securityDialogColors(),
    enableWindowDim: Boolean = true,
    onDismissFinished: (() -> Unit)? = null,
    renderInRootScaffold: Boolean = true,
) {
    SecurityDialogContentLayout(
        show = show,
        popupHost = { visible, hostContent ->
            val visibleState = remember { mutableStateOf(false) }
            visibleState.value = visible
            DialogLayout(
                visible = visibleState,
                enableWindowDim = false,
                enterTransition = EnterTransition.None,
                exitTransition = ExitTransition.None,
                enableAutoLargeScreen = false,
                renderInRootScaffold = renderInRootScaffold,
            ) {
                hostContent()
            }
        },
        onConfirm = onConfirm,
        onCancel = onCancel,
        modifier = modifier,
        title = title,
        summary = summary,
        statement = statement,
        statementLinkText = statementLinkText,
        onLinkClick = onLinkClick,
        checkboxText = checkboxText,
        initialChecked = initialChecked,
        confirmText = confirmText,
        cancelText = cancelText,
        titleColor = titleColor,
        summaryColor = summaryColor,
        backgroundColor = backgroundColor,
        colors = colors,
        enableWindowDim = enableWindowDim,
        onDismissFinished = onDismissFinished,
    )
}
