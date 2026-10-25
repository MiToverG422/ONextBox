package com.mi.onextbox.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
fun CouiConfirmDialog(
    show: Boolean,
    title: String?,
    summary: String?,
    negativeText: String,
    positiveText: String,
    onDismissRequest: () -> Unit,
    onNegative: () -> Unit = onDismissRequest,
    onPositive: () -> Unit,
    onDismissFinished: (() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    ColorOs17ConfirmDialog(
        show = show,
        title = title,
        summary = summary,
        negativeText = negativeText,
        positiveText = positiveText,
        onDismissRequest = onDismissRequest,
        onNegative = onNegative,
        onPositive = onPositive,
        onDismissFinished = onDismissFinished,
        content = content,
    )
}
