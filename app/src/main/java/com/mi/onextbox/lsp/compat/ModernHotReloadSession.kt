package com.mi.onextbox.lsp.compat

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/** Builds a complete replacement plan before touching the previous generation's method hooks. */
internal object ModernHotReloadSession {
    class Session(
        private var staging: Boolean,
        private val installer: (Executable, String, XposedInterface.Hooker) -> XposedInterface.HookHandle =
            { executable, id, hooker -> installDirect(executable, id, hooker) },
        previousTargets: Collection<Executable> = emptyList(),
    ) {
        private val handles = ConcurrentLinkedQueue<ManagedHandle>()
        private val ids = HotReloadHookIds<Executable>()
        private val failed = AtomicBoolean(false)
        private var precedingTargets = if (staging) previousTargets.toList() else emptyList()

        /** Host methods only: bootstrap dynamic hooks without retaining old callbacks or handles. */
        val previousTargets: List<Executable> get() = precedingTargets.toList()

        var hasCommittedHooks: Boolean = false
            private set
        val healthy: Boolean get() = !failed.get() && handles.any { it.active }

        fun install(executable: Executable, hooker: XposedInterface.Hooker): XposedInterface.HookHandle =
            try {
                val id = ids.next(executable)
                val delegate = if (staging) null else installer(executable, id, hooker)
                if (delegate != null) hasCommittedHooks = true
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.target(executable.toString(), staging)
                ManagedHandle(executable, id, hooker, delegate).also(handles::add)
            } catch (error: Throwable) {
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.event("failure", executable.toString(), error.toString())
                failed.set(true)
                throw error
            }

        fun commit(oldHandles: List<XposedInterface.HookHandle>) {
            check(staging) { "Replacement hooks have already been committed" }
            try {
                check(healthy) { "No complete replacement hook set; restart the scope" }
                val live = handles.filter { it.active }
                val oldKeys = oldHandles.map {
                    HotReloadHookKey(it.executable, checkNotNull(it.id) { "Unmanaged old hook" })
                }
                check(preservesHookCoverage(oldKeys, live.map { it.key })) {
                    val missing = oldKeys.toSet() - live.map { it.key }.toSet()
                    "Replacement hooks do not cover the previous generation; restart the scope. " +
                        "Missing ${missing.size}: ${missing.take(5)}"
                }
                val previous = oldKeys.zip(oldHandles).toMap()
                live.forEach { pending ->
                    // API 102 atomically replaces each hook and keeps in-flight chain snapshots.
                    // No temporarily duplicated interceptors or cross-generation callback objects.
                    pending.delegate = previous[pending.key]?.replaceHook(pending.hooker)
                        ?: installer(pending.executable, pending.id, pending.hooker)
                    hasCommittedHooks = true
                }
                staging = false
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.event("committed", "Hot reload", "targets=${live.size}")
                precedingTargets = emptyList()
            } catch (error: Throwable) {
                markFailed()
                // Replacement is atomic per hook, not per module. Keep successful replacements and
                // untouched old hooks on failure; removing them would leave the target unhooked.
                throw error
            }
        }

        fun markFailed() {
            failed.set(true)
            precedingTargets = emptyList()
        }

        private inner class ManagedHandle(
            private val target: Executable,
            private val hookId: String,
            val hooker: XposedInterface.Hooker,
            var delegate: XposedInterface.HookHandle?,
        ) : XposedInterface.HookHandle {
            var active = true
                private set
            val key get() = HotReloadHookKey(target, hookId)

            override fun getExecutable(): Executable = target
            override fun getId(): String = hookId
            override fun unhook() {
                if (!active) return
                delegate?.unhook()
                active = false
            }
            override fun replaceHook(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
                check(active) { "Hook handle is no longer valid" }
                val replacement = ManagedHandle(target, hookId, hooker, delegate?.replaceHook(hooker))
                active = false
                handles.add(replacement)
                return replacement
            }
        }
    }

    @Volatile
    var current: Session? = null
        private set

    fun begin(staging: Boolean, previousTargets: Collection<Executable> = emptyList()): Session =
        Session(staging, previousTargets = previousTargets).also { current = it }

    fun install(executable: Executable, hooker: XposedInterface.Hooker): XposedInterface.HookHandle =
        current?.install(executable, hooker) ?: installDirect(executable, null, hooker)

    private fun installDirect(
        executable: Executable,
        id: String?,
        hooker: XposedInterface.Hooker,
    ): XposedInterface.HookHandle = ModernHookRuntime.requireModule().hook(executable)
        .setId(id)
        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
        .intercept(hooker)
}
