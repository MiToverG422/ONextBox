package com.mi.onextbox.ui.common

import android.content.Context
import com.mi.onextbox.lsp.LsposedScopeRequester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun appendLspDiagnosticsForFeedback(
    context: Context,
    reason: String,
) = withContext(Dispatchers.IO) {
    // Keep the latest framework status only.
    AppLogStore.removeByTagPrefix("LSP")
    AppLogStore.i("LSP", "Status check (${reason.oneLine()})")
    val snapshot = runCatching { LsposedScopeRequester.refreshSnapshot(context) }.getOrNull()
    if (snapshot == null) {
        AppLogStore.w("LSP", "module=UNKNOWN status=UNKNOWN source=unavailable reason=status_query_failed")
        return@withContext
    }
    AppLogStore.i(
        "LSP",
        "module=${snapshot.moduleState} status=${snapshot.status} " +
            "connected=${snapshot.serviceConnected} ready=${snapshot.isReady} " +
            "source=${snapshot.source} reason=${snapshot.reason.oneLine()}",
    )
    snapshot.frameworkVersionText?.takeIf { it.isNotBlank() }?.let { framework ->
        AppLogStore.i("LSP", "Framework: ${framework.oneLine()}")
    }
}

private fun String.oneLine(): String = replace('\n', ' ').replace('\r', ' ').trim().take(160)
