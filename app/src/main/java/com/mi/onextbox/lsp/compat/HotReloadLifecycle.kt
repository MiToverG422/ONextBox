package com.mi.onextbox.lsp.compat

import android.os.Handler
import android.os.Looper
import java.lang.reflect.Executable
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Owns process-local resources separately from method hooks and never exports module callbacks. */
internal object HotReloadLifecycle : HotReloadResourceCoordinator() {
    override fun <T> withHook(fallback: () -> T, action: () -> T): T =
        super.withHook(fallback) {
            if (AdaptationHookGate.isBoundProfileActive()) action() else fallback()
        }

    fun <T> withInfrastructureHook(fallback: () -> T, action: () -> T): T =
        super.withHook(fallback, action)
}

/** The dispatcher is injectable so retirement/rollback contracts can be exercised on the JVM. */
internal open class HotReloadResourceCoordinator(
    private val mainDispatch: ((() -> Unit) -> Unit)? = null,
) {
    private data class Participant(
        val capture: () -> Any?,
        val detach: () -> Unit,
        val restore: (Any?) -> Unit,
        val canReload: () -> Boolean,
        val resumeAfterRejection: () -> Unit,
    )
    private data class Replay(val capture: () -> Any?, val replay: (Any?) -> Unit)

    private val lock = Any()
    private val participants = linkedMapOf<String, Participant>()
    private val replays = linkedMapOf<String, Replay>()
    private val afterCommitActions = arrayListOf<() -> Unit>()
    private val replayed = hashSetOf<String>()
    private val gate = HotReloadCallbackGate()
    private val main by lazy { Handler(Looper.getMainLooper()) }

    @Volatile private var staging = false
    @Volatile private var imported: Map<String, Any?> = emptyMap()
    @Volatile var isRetiring: Boolean = false
        private set

    fun begin(savedState: Map<String, Any?>? = null) {
        staging = savedState != null
        imported = savedState ?: emptyMap()
        if (staging) check(gate.pause()) { "New generation unexpectedly has active callbacks" }
    }

    /**
     * [resumeAfterRejection] re-arms still-owned work after a rejected reload. It runs on main with
     * the callback gate open and [isRetiring] false, after any required restore succeeded. It must
     * be idempotent across repeated rejections and must not acquire resources belonging to a new
     * generation. A failure quarantines this generation instead of silently losing its work.
     */
    fun register(
        key: String,
        capture: () -> Any? = { null },
        detach: () -> Unit = {},
        restore: (Any?) -> Unit = {},
        canReload: () -> Boolean = { true },
        resumeAfterRejection: () -> Unit = {},
    ) {
        synchronized(lock) {
            check(!isRetiring) { "Cannot acquire resources after retirement" }
            participants[key] = Participant(capture, detach, restore, canReload, resumeAfterRejection)
        }
    }

    fun registerReplay(key: String, capture: () -> Any?, replay: (Any?) -> Unit) {
        synchronized(lock) {
            check(!isRetiring) { "Cannot register dynamic hooks after retirement" }
            replays[key] = Replay(capture, replay)
        }
    }

    fun restoredState(key: String): Any? = imported[key]

    fun afterCommit(action: () -> Unit) {
        synchronized(lock) {
            if (isRetiring) return
            if (staging) {
                afterCommitActions += action
                return
            }
        }
        if (mainDispatch != null) mainDispatch { if (!isRetiring) action() }
        else if (Looper.myLooper() == Looper.getMainLooper()) action()
        else check(main.post { if (!isRetiring) action() }) { "Main thread is unavailable" }
    }

    open fun <T> withHook(fallback: () -> T, action: () -> T): T = gate.invoke(fallback, action)

    /** All checks precede detach; a busy authentication/download flow vetoes the whole update. */
    fun prepare(): Map<String, Any?> {
        check(!isRetiring) { "This generation has already retired; restart the scope" }
        var rollbackFailed = false
        try {
            check(gate.pause()) { "Hook callbacks are busy; restart the scope" }
            isRetiring = true
            return onMain {
                val owned = synchronized(lock) { participants.toList() }
                val dynamic = synchronized(lock) { replays.toList() }
                owned.forEach { (key, participant) ->
                    check(participant.canReload()) { "Resource is busy: $key" }
                }
                val state = linkedMapOf<String, Any?>()
                owned.forEach { (key, participant) -> state[key] = participant.capture() }
                dynamic.forEach { (key, replay) ->
                    check(key !in state) { "Duplicate resource key: $key" }
                    state[key] = replay.capture()
                }
                validateState(state)
                val detached = arrayListOf<Pair<String, Participant>>()
                try {
                    owned.asReversed().forEach { entry ->
                        // Also restore a participant which threw halfway through its detach.
                        detached += entry
                        entry.second.detach()
                    }
                } catch (error: Throwable) {
                    isRetiring = false
                    detached.asReversed().forEach { (key, participant) ->
                        runCatching { participant.restore(state[key]) }.onFailure {
                            rollbackFailed = true
                            if (it !== error) error.addSuppressed(it)
                        }
                    }
                    if (rollbackFailed) quarantine()
                    throw error
                }
                state
            }
        } catch (error: Throwable) {
            if (rollbackFailed) {
                quarantine()
            } else {
                // A veto can happen before detach, but retirement/gate fallback may already have
                // consumed an owner's queued callback. Restore its scheduling only after the old
                // generation is fully active again, including when pause itself timed out.
                runCatching { onMain { resumeRejectedPreparation() } }.onFailure {
                    quarantine()
                    if (it !== error) error.addSuppressed(it)
                }
            }
            throw error
        }
    }

    /** Recreate dynamic method hooks before checking coverage or modifying any old hook. */
    fun replaySavedState() {
        while (true) {
            val pending = synchronized(lock) {
                replays.entries.firstOrNull { it.key in imported && it.key !in replayed }
                    ?.let { it.key to it.value }
            } ?: break
            replayed += pending.first
            pending.second.replay(imported[pending.first])
        }
        val known = synchronized(lock) { participants.keys + replays.keys }
        check(known.containsAll(imported.keys)) { "A previous resource has no replacement owner" }
    }

    fun cancelPreparation(state: Map<String, Any?>) {
        try {
            onMain {
                isRetiring = false
                var failure: Throwable? = null
                synchronized(lock) { participants.toList() }.forEach { (key, participant) ->
                    if (key in state) runCatching { participant.restore(state[key]) }.onFailure {
                        if (failure == null) failure = it else if (failure !== it) failure!!.addSuppressed(it)
                    }
                }
                failure?.let {
                    quarantine()
                    throw it
                }
                resumeRejectedPreparation()
            }
        } catch (error: Throwable) {
            quarantine()
            throw error
        }
    }

    /**
     * Same-generation recovery, on the main thread, after every successful rollback or early veto.
     * Owners must idempotently re-arm still-owned work that retirement may have skipped. This is
     * not called after successful retirement or on a failed/quarantined replacement generation.
     */
    private fun resumeRejectedPreparation() {
        val owned = synchronized(lock) { participants.values.toList() }
        isRetiring = false
        gate.resume()
        try {
            owned.forEach { it.resumeAfterRejection() }
        } catch (error: Throwable) {
            quarantine()
            throw error
        }
    }

    private fun quarantine() {
        isRetiring = true
        gate.close()
    }

    fun complete() {
        onMain {
            val owned = synchronized(lock) { participants.toList() }
            owned.forEach { (key, participant) ->
                if (key in imported) participant.restore(imported[key])
            }
            // Restores can enqueue additional work, which must also complete before callbacks resume.
            while (true) {
                val actions = synchronized(lock) {
                    afterCommitActions.toList().also { afterCommitActions.clear() }
                }
                if (actions.isEmpty()) break
                actions.forEach { it() }
            }
            synchronized(lock) { staging = false }
            imported = emptyMap()
            gate.resume()
        }
    }

    /** A failed replacement remains quiescent instead of running partially initialized callbacks. */
    fun fail() {
        val drained = gate.pause()
        gate.close()
        isRetiring = true
        synchronized(lock) { afterCommitActions.clear() }
        // Do not tear down a resource underneath a callback that did not finish in time.
        if (!drained) return
        onMain {
            synchronized(lock) { participants.values.toList() }.asReversed().forEach {
                runCatching { it.detach() }
            }
        }
    }

    private fun <T> onMain(action: () -> T): T {
        if (mainDispatch != null) {
            var result: Result<T>? = null
            mainDispatch { result = runCatching(action) }
            return checkNotNull(result) { "Main dispatcher did not complete" }.getOrThrow()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) return action()
        val phase = AtomicInteger(0)
        val done = CountDownLatch(1)
        var outcome: Result<T>? = null
        val task = Runnable {
            if (!phase.compareAndSet(0, 1)) return@Runnable
            try { outcome = runCatching(action) } finally { done.countDown() }
        }
        check(main.post(task)) { "Main thread is unavailable" }
        if (!done.await(5, TimeUnit.SECONDS)) {
            if (phase.compareAndSet(0, 2)) {
                main.removeCallbacks(task)
                error("Main thread is busy; retry after restarting the scope")
            }
            // A running detach must finish; returning early could allow it to mutate a resumed generation.
            done.await()
        }
        return checkNotNull(outcome).getOrThrow()
    }

    private fun validateState(value: Any?) {
        val visited = IdentityHashMap<Any, Boolean>()
        val moduleLoader = HotReloadLifecycle::class.java.classLoader
        fun visit(item: Any?) {
            if (item == null || visited.put(item, true) != null) return
            check(item.javaClass.classLoader !== moduleLoader) {
                "Saved state contains a module-owned object: ${item.javaClass.name}"
            }
            when (item) {
                is Class<*> -> check(item.classLoader !== moduleLoader) { "Module-owned class in saved state" }
                is ClassLoader -> check(item !== moduleLoader) { "Module classloader in saved state" }
                is Executable -> check(item.declaringClass.classLoader !== moduleLoader) { "Module method in saved state" }
                is Map<*, *> -> item.forEach { (key, entry) -> visit(key); visit(entry) }
                is Collection<*> -> item.forEach(::visit)
                is Array<*> -> item.forEach(::visit)
            }
        }
        visit(value)
    }
}
