package com.mi.onextbox.lsp

import android.util.Log
import com.mi.onextbox.lsp.compat.ModernHookRuntime

/** Routes hook diagnostics directly to the modern Xposed framework. */
internal object HookLog {
    private const val LOGCAT_TAG = "ONextBox-LSP"

    fun d(tag: String, message: String, throwable: Throwable? = null) {
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
        val priority = when (level) {
            "D" -> Log.DEBUG
            "W" -> Log.WARN
            "E" -> Log.ERROR
            else -> Log.INFO
        }
        ModernHookRuntime.log(priority, LOGCAT_TAG, line, throwable)
    }
}
