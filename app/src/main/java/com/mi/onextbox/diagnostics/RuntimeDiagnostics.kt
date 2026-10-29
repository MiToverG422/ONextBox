package com.mi.onextbox.diagnostics

import android.app.Application
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.mi.onextbox.BuildConfig
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

internal object RuntimeDiagnostics {
    const val TAG = "ONextBox-Diag"
    const val UNTIL = "developer_trace_until"
    const val TAGS = "developer_trace_tags"
    private val generation = System.currentTimeMillis()
    private val emitted = AtomicInteger()
    val localEvents = java.util.concurrent.ConcurrentLinkedDeque<String>()
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

    fun event(kind: String, name: String, detail: String = "", ms: Long = 0) {
        runCatching {
            if (emitted.incrementAndGet() > 4000 && kind !in setOf("config", "module", "startup", "failure", "committed")) return
            val line = JSONObject().put("time", System.currentTimeMillis()).put("generation", generation)
                .put("process", Application.getProcessName()).put("pid", Process.myPid())
                .put("version", BuildConfig.VERSION_CODE).put("kind", kind).put("name", name)
                .put("detail", detail.take(1800)).put("ms", ms).toString()
            localEvents.addLast(line)
            while (localEvents.size > 500) localEvents.pollFirst()
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
            work.set(parent)
        }
    }
    fun target(target: String, staged: Boolean) {
        work.get()?.let { if (staged) it.staged++ else it.installed++ }
        event(if (staged) "staged" else "installed", work.get()?.name ?: "Dynamic Hook", target)
    }
    fun config(values: Map<String, *>) = event("config", "preferences", DiagnosticRules.fingerprint(values))
}
