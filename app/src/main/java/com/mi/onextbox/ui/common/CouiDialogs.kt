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
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        if (show) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = onDismissRequest,
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.90f),
                properties = DialogProperties(usePlatformDefaultWidth = false),
                title = title?.let { { androidx.compose.material3.Text(it) } },
                text = if (summary != null || content != null) {
                    {
                        Column {
                            summary?.let { androidx.compose.material3.Text(it) }
                            content?.invoke()
                        }
                    }
                } else null,
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = onPositive) {
                        androidx.compose.material3.Text(positiveText)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = onNegative) {
                        androidx.compose.material3.Text(negativeText)
                    }
                },
            )
        }
        return
    }
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
