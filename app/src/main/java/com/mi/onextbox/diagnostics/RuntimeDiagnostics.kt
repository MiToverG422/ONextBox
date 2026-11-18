package com.mi.onextbox.diagnostics

import android.app.Application
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.mi.onextbox.BuildConfig
import org.json.JSONObject

internal object RuntimeDiagnostics {
    const val TAG = "ONextBox-Diag"
    const val UNTIL = "developer_trace_until"
    const val TAGS = "developer_trace_tags"
    private val generation = System.currentTimeMillis()
    val localEvents = DiagnosticBuffer<String>(500)
    private val informationalEvents = DiagnosticRateLimiter()
    private val hookMessages = DiagnosticRateLimiter()
    private val detailedKinds = setOf("log", "installed", "staged")
    private val throttledKinds = setOf("initialization", "dex")
    private val started = System.nanoTime() / 1_000_000
    private val firstDrawRecorded = java.util.concurrent.atomic.AtomicBoolean()
    fun recordFirstDraw(view: android.view.View) {
        val listener = object : android.view.ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                if (!firstDrawRecorded.compareAndSet(false, true)) return
                event("startup", "Application to first draw", "First draw began", System.nanoTime() / 1_000_000 - started)
                view.post { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(this) }
            }
        }
        view.viewTreeObserver.addOnDrawListener(listener)
    }
    private data class Work(val name: String, var installed: Int = 0, var staged: Int = 0)
    private val work = ThreadLocal<Work?>()

    fun detailedLogging(tag: String): Boolean = runCatching {
        if (Application.getProcessName() == BuildConfig.APPLICATION_ID) {
            DeveloperDiagnostics.logEnabled(tag)
        } else {
            val config = com.mi.onextbox.lsp.compat.HookConfigSnapshot
            config.controlBoolean(DeveloperDiagnostics.DEBUG, false) || DiagnosticRules.temporaryLogging(
                config.long(UNTIL, 0L) ?: 0L, System.currentTimeMillis(),
                config.stringSet(TAGS, emptySet()).orEmpty(), tag,
            )
        }
    }.getOrDefault(false)

    fun allowInformationalLog(tag: String, message: String): Boolean =
        detailedLogging(tag) || hookMessages.allow("$tag\u0000$message", SystemClock.elapsedRealtime())

    fun event(kind: String, name: String, detail: String = "", ms: Long = 0) {
        runCatching {
            if (kind in detailedKinds && !detailedLogging(name)) return
            if (kind in throttledKinds && !informationalEvents.allow(
                    "$kind\u0000$name\u0000$detail", SystemClock.elapsedRealtime())) return
            val line = JSONObject().put("time", System.currentTimeMillis()).put("generation", generation)
                .put("process", Application.getProcessName()).put("pid", Process.myPid())
                .put("version", BuildConfig.VERSION_CODE).put("kind", kind).put("name", name)
                .put("detail", detail.take(1800)).put("ms", ms).toString()
            localEvents.add(line)
            Log.i(TAG, line)
        }
    }
    fun <T> measure(name: String, action: () -> T): T {
        val parent = work.get()
        val current = Work(name)
        work.set(current)
        val start = System.nanoTime() / 1_000_000
        var failure: Throwable? = null
        return try { action() } catch (error: Throwable) { failure = error; throw error }
        finally {
            event(if (failure == null) "initialization" else "failure", name,
                "installed=${current.installed}; staged=${current.staged}" + (failure?.let { "; $it" } ?: ""),
                System.nanoTime() / 1_000_000 - start)
            com.mi.onextbox.lsp.FeatureRuntimeCatalog.keysFor(name).forEach { key ->
                event("feature_init", key,
                    "enabled=${com.mi.onextbox.lsp.compat.HookConfigSnapshot.boolean(key, false) == true};" +
                        "installed=${current.installed};staged=${current.staged};failed=${failure != null}")
            }
            work.set(parent)
        }
    }
    fun target(target: String, staged: Boolean) {
        work.get()?.let { if (staged) it.staged++ else it.installed++ }
        event(if (staged) "staged" else "installed", work.get()?.name ?: "Dynamic Hook", target)
    }
    fun config(values: Map<String, *>) = event("config", "preferences", DiagnosticRules.fingerprint(values))
}
