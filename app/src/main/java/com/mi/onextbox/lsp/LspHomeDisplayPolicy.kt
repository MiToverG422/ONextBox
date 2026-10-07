package com.mi.onextbox.lsp

import com.mi.onextbox.ui.common.LspHomeDisplay

/** Completed results for homepage display, not activation evidence. */
internal fun lspHomeDisplayForCache(
    snapshot: LsposedScopeRequester.StatusSnapshot,
    rootStartupPending: Boolean,
): LspHomeDisplay? {
    if (snapshot.isRefreshing || snapshot.status == LspStatus.CHECKING) return null
    if (snapshot.status == LspStatus.UNKNOWN &&
        snapshot.reason == "root_shell_unavailable" && rootStartupPending
    ) return null
    if (snapshot.status == LspStatus.READY && !snapshot.isReady) return null
    return LspHomeDisplay(
        status = snapshot.status,
        frameworkVersionText = snapshot.frameworkVersionText,
        missingScopes = snapshot.missingScopes.toSet(),
    )
}
