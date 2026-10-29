package com.mi.onextbox.lsp

import android.util.Log
import com.mi.onextbox.lsp.compat.ModernHookRuntime

/** Routes hook diagnostics directly to the modern Xposed framework. */
internal object HookLog {
    private const val LOGCAT_TAG = "ONextBox-LSP"

    fun d(tag: String, message: String, throwable: Throwable? = null) {
        val config = com.mi.onextbox.lsp.compat.HookConfigSnapshot
        if (config.boolean("developer_debug_logging", false) != true &&
            !com.mi.onextbox.diagnostics.DiagnosticRules.temporaryLogging(
                config.long("developer_trace_until", 0L) ?: 0L, System.currentTimeMillis(),
                config.stringSet("developer_trace_tags", emptySet()).orEmpty(), tag)) return
        write("D", tag, message, throwable)
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        write("I", tag, message, throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        write("W", tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        write("E", tag, message, throwable)
    }

    private fun write(level: String, tag: String, message: String, throwable: Throwable?) {
        val scopedTag = tag
            .removePrefix("ONextBox-")
            .removePrefix("ONextBox ")
            .trim()
            .ifBlank { "Hook" }
        val line = "[$scopedTag] $message"
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.event(if (level == "E" || level == "W") "hook_log" else "log", tag, line + (throwable?.let { ": $it" } ?: ""))
        val priority = when (level) {
            "D" -> Log.DEBUG
            "W" -> Log.WARN
            "E" -> Log.ERROR
            else -> Log.INFO
        }
        ModernHookRuntime.log(priority, LOGCAT_TAG, line, throwable)
    }
}
