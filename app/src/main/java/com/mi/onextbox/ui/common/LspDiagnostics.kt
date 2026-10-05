package com.mi.onextbox.ui.common

import android.content.Context
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.lsp.LsposedScopeRequester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LSP_HOOK_LOG_TOKEN = "ONextBox-LSP"

suspend fun appendLspDiagnosticsForFeedback(
    context: Context,
    reason: String,
    maxHookLines: Int = 30
) = withContext(Dispatchers.IO) {
    val safeMax = maxHookLines.coerceIn(5, 80)

    // Keep only the latest diagnostics snapshot so refresh replaces previous LSP diagnostics.
    AppLogStore.removeByTagPrefix("LSP")
    AppLogStore.i("LSP", "Collect diagnostics ($reason)")
    val shellUidResult = ShellLogger.exec("LSP", "id -u")
    val shellUid = shellUidResult.out.firstOrNull()?.trim().orEmpty().ifBlank { "unknown" }
    AppLogStore.i("LSP", "Shell uid: $shellUid")
    AppLogStore.i(
        "LSP",
        "Native icon toggle (app): ${LspConfig.isNativeNotifyIconEnabled(context)}"
    )
    AppLogStore.i(
        "LSP",
        "Native icon toggle (xposed readable): ${runCatching { LspConfig.isNativeNotifyIconEnabledXposed() }.getOrNull()}"
    )

    val lsposedPkg = ShellLogger.exec("LSP", "pm list packages org.lsposed.manager")
    val hasLsposedManager = lsposedPkg.out.any { it.contains("org.lsposed.manager") }
    AppLogStore.i("LSP", "LSPosed manager installed: $hasLsposedManager")
    val lsposedSnapshot = runCatching { LsposedScopeRequester.snapshot(context) }.getOrNull()
    val lsposedReady = lsposedSnapshot?.moduleEnabled ?: false

    val systemUiPidResult = ShellLogger.exec("LSP", "pidof com.android.systemui")
    val systemUiPid = systemUiPidResult.out.firstOrNull()?.trim().orEmpty()
    if (systemUiPid.isNotBlank()) {
        AppLogStore.i("LSP", "SystemUI pid: $systemUiPid")
    } else {
        AppLogStore.w("LSP", "SystemUI pid not found")
    }

    val rawLogLines = readRecentLogcatLines(safeMax * 8)
    val hookLines = rawLogLines
        .asReversed()
        .asSequence()
        .filter { it.contains(LSP_HOOK_LOG_TOKEN, ignoreCase = false) }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .take(safeMax)
        .toList()
        .asReversed()

    if (hookLines.isEmpty()) {
        if (lsposedReady) {
            AppLogStore.i("LSP", "No recent hook log lines in current logcat buffer")
        } else {
            AppLogStore.w(
                "LSP",
                "No hook log lines found. Please ensure LSPosed is enabled and restart SystemUI/phone."
            )
        }
        return@withContext
    }

    AppLogStore.i("LSP", "Hook log lines captured: ${hookLines.size}")
    hookLines.forEach { line ->
        AppLogStore.i("LSP-Hook", line.take(320))
    }
}

private fun readRecentLogcatLines(requestedLines: Int): List<String> {
    val safeRequested = requestedLines.coerceIn(120, 2000)
    val candidates = listOf(
        "logcat -d -v time -t $safeRequested",
        "logcat -d -v threadtime -t $safeRequested",
        "logcat -d -v time"
    )

    candidates.forEach { command ->
        val result = ShellLogger.exec("Logcat", command)
        if (result.isSuccess && result.out.isNotEmpty()) {
            return result.out.takeLast(safeRequested)
        }
    }
    return emptyList()
}
