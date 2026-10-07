package com.mi.onextbox.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspStatus

internal fun homeLspDisplayStatus(
    status: LspStatus,
    reason: String,
    rootStartupPending: Boolean,
): LspStatus = if (
    status == LspStatus.UNKNOWN && reason == "root_shell_unavailable" && rootStartupPending
) LspStatus.CHECKING else status

@StringRes
internal fun lspStatusTextResource(status: LspStatus): Int = when (status) {
    LspStatus.CHECKING -> R.string.lsp_status_checking
    LspStatus.UNKNOWN -> R.string.lsp_status_unknown
    LspStatus.DISABLED -> R.string.lsp_status_module_disabled
    LspStatus.WAITING_CONNECTION -> R.string.lsp_status_waiting_connection
    LspStatus.MISSING_SCOPE -> R.string.lsp_status_missing_scope
    LspStatus.WAITING_RESTART -> R.string.lsp_status_waiting_restart
    LspStatus.API_UNSUPPORTED -> R.string.lsp_status_api_unsupported
    LspStatus.READY -> R.string.lsp_status_ready
}

@Composable
internal fun lspStatusText(
    status: LspStatus,
    frameworkVersionText: String? = null,
    showChecking: Boolean = true,
): String {
    if (status == LspStatus.CHECKING && !showChecking) return "—"
    return formatLspStatusText(
        status = status,
        label = stringResource(lspStatusTextResource(status)),
        frameworkVersionText = frameworkVersionText,
        showChecking = showChecking,
    )
}

internal fun formatLspStatusText(
    status: LspStatus,
    label: String,
    frameworkVersionText: String? = null,
    showChecking: Boolean = true,
): String {
    if (status == LspStatus.CHECKING && !showChecking) return "—"
    val version = frameworkVersionText?.trim()?.takeIf { it.isNotEmpty() }
    return if (status == LspStatus.READY && version != null) version else label
}
