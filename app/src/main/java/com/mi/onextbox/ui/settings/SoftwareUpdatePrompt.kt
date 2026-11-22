package com.mi.onextbox.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.CouiConfirmDialog

@Composable
internal fun SoftwareUpdatePrompt(
    versionName: String?,
    onDismiss: () -> Unit,
    onOpenSoftwareUpdate: () -> Unit,
) {
    CouiConfirmDialog(
        show = versionName != null,
        title = stringResource(R.string.update_notification_title),
        summary = versionName?.let { stringResource(R.string.update_prompt_body, it) },
        negativeText = stringResource(android.R.string.cancel),
        positiveText = stringResource(R.string.update_prompt_open),
        onDismissRequest = onDismiss,
        onPositive = onOpenSoftwareUpdate,
    )
}
