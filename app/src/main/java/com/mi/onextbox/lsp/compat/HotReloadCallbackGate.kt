package com.mi.onextbox.lsp.compat

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport

/** Quiesce invocation callbacks without deadlocking or cutting an already-entered recursive call. */
internal class HotReloadCallbackGate {
    private val state = AtomicInteger(0)
    private val depth = ThreadLocal.withInitial { 0 }

    fun <T> invoke(fallback: () -> T, action: () -> T): T {
        val nested = depth.get() ?: 0
        if (nested > 0) {
            depth.set(nested + 1)
            return try { action() } finally { depth.set(nested) }
        }
        while (true) {
            val before = state.get()
            if (before < 0) return fallback()
            if (state.compareAndSet(before, before + 1)) break
        }
        depth.set(1)
        return try { action() } finally {
            // Keep the bootstrap Integer zero, not a module-owned object. Removing the entry
            // would allocate a new ThreadLocalMap.Entry on every top-level Hook invocation.
            depth.set(0)
            state.decrementAndGet()
        }
    }

    fun pause(timeoutMillis: Long = 500): Boolean {
        if ((depth.get() ?: 0) > 0) return false
        while (true) {
            val before = state.get()
            if (before < 0) return before == Int.MIN_VALUE
            if (state.compareAndSet(before, before or Int.MIN_VALUE)) break
        }
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        while (state.get() != Int.MIN_VALUE) {
            if (System.nanoTime() >= deadline) {
                resume()
                return false
            }
            LockSupport.parkNanos(1_000_000L)
        }
        return true
    }

    fun resume() {
        state.getAndUpdate { it and Int.MAX_VALUE }
    }

    /** Reject new callbacks even when an already-entered invocation cannot drain in time. */
    fun close() {
        state.getAndUpdate { it or Int.MIN_VALUE }
    }
}
