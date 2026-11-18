package com.mi.onextbox.lsp

import java.util.concurrent.Executor
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/** Reserve before acquiring a provider: even all abandoned leases fit the cleanup queue. */
internal class NativeFiveGaCleanup(private val executor: Executor, capacity: Int = CAPACITY) {
    private val slots = Semaphore(capacity)

    fun reserve(): Reservation {
        check(slots.tryAcquire()) { "Native network cleanup capacity exhausted" }
        return Reservation()
    }

    inner class Reservation internal constructor() {
        private val completed = AtomicBoolean(false)
        private val released = AtomicBoolean(false)
        private fun release() { if (released.compareAndSet(false, true)) slots.release() }

        fun cancel() { if (completed.compareAndSet(false, true)) release() }

        fun finish(cleanup: () -> Unit) {
            if (!completed.compareAndSet(false, true)) return
            try {
                executor.execute { try { cleanup() } finally { release() } }
            } catch (error: Exception) {
                release()
                throw error
            }
        }
    }

    companion object { const val CAPACITY = 64 }
}
