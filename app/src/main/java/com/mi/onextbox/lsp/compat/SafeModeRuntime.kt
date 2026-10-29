package com.mi.onextbox.lsp.compat

import android.os.Process
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Log
import com.mi.onextbox.diagnostics.RuntimeDiagnostics
import com.mi.onextbox.diagnostics.SafeModePolicy
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** The crash observer must remain outside the feature gate, including during safe mode. */
internal object SafeModeRuntime {
    @Volatile private var automaticBlocked = false
    private var file: AtomicFile? = null
    private var state: SafeModePolicy.State? = null
    private val recorded = AtomicBoolean(false)
    private var initialized = false
    private var scopeName = ""

    val blocked: Boolean get() = HookConfigSnapshot.controlBoolean(SafeModePolicy.MANUAL, false) ||
        (automaticBlocked && HookConfigSnapshot.controlBoolean(SafeModePolicy.AUTOMATIC, true))

    fun initialize(process: String, system: Boolean) {
        if (initialized) return
        initialized = true
        val scope = SafeModePolicy.scope(process, system) ?: return
        scopeName = scope
        runCatching {
            val boot = bootIdentity()
            check(boot.isNotBlank()) { "Boot identity unavailable" }
            val directory = if (system) File("/data/system/onextbox-safe-mode") else
                File("/data/user_de/${Process.myUid() / 100000}/$scope/files/onextbox-safe-mode")
            check(directory.isDirectory || directory.mkdirs()) { "Crash state directory unavailable" }
            val storage = AtomicFile(File(directory, "state.json"))
            val previous = runCatching {
                val json = JSONObject(storage.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
                val times = json.optJSONArray("crashes") ?: JSONArray()
                SafeModePolicy.State(json.getString("boot"), List(times.length()) { times.getLong(it) }, json.optBoolean("blocked"))
            }.getOrNull()
            state = SafeModePolicy.forBoot(previous, boot)
            file = storage
            automaticBlocked = state!!.blocked
            save()
            RuntimeDiagnostics.event("safe_mode", scope, if (automaticBlocked) "Automatic protection active; reboot to clear" else "Crash protection ready")
            val handler = Class.forName("com.android.internal.os.RuntimeInit\$KillApplicationHandler")
            val method = handler.getDeclaredMethod("uncaughtException", Thread::class.java, Throwable::class.java)
            method.isAccessible = true
            ModernHotReloadSession.install(method) { chain ->
                runCatching { recordCrash(chain.args.getOrNull(1) as? Throwable) }
                chain.proceed()
            }
        }.onFailure {
            Log.w("ONextBox-SafeMode", "Crash protection unavailable in $scope", it)
            RuntimeDiagnostics.event("safe_mode", scope, "Protection unavailable: ${it.javaClass.simpleName}")
        }
    }

    @Synchronized private fun recordCrash(error: Throwable?) {
        if (blocked || !HookConfigSnapshot.isAvailable ||
            !HookConfigSnapshot.controlBoolean(SafeModePolicy.AUTOMATIC, true) || !recorded.compareAndSet(false, true)) return
        state = SafeModePolicy.crash(state ?: return, SystemClock.elapsedRealtime())
        automaticBlocked = state!!.blocked
        save()
        RuntimeDiagnostics.event("safe_mode", scopeName, "count=${state!!.crashes.size}; blocked=$automaticBlocked; ${error?.javaClass?.name}")
    }

    private fun save() {
        val current = state ?: return
        val storage = file ?: return
        val json = JSONObject().put("boot", current.boot).put("blocked", current.blocked)
            .put("crashes", JSONArray(current.crashes))
        val output = storage.startWrite()
        try {
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            storage.finishWrite(output)
        } catch (error: Throwable) {
            storage.failWrite(output)
            throw error
        }
    }

    private fun bootIdentity(): String = runCatching {
        File("/proc/sys/kernel/random/boot_id").readText().trim()
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: runCatching {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
            .invoke(null, "ro.runtime.firstboot") as String
    }.getOrDefault("")
}
