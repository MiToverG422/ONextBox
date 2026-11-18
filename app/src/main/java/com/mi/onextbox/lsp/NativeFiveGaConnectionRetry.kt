package com.mi.onextbox.lsp

/** Confined to the bridge worker. Updates share one pending attempt and never reset its budget. */
internal class NativeFiveGaConnectionRetry(
    private val enabled: () -> Boolean,
    private val post: (Runnable, Long) -> Unit,
    private val remove: (Runnable) -> Unit,
    private val attempt: () -> Boolean,
    private val suspended: () -> Unit,
) : AutoCloseable {
    private var pending = false
    private var running = false
    private var connected = false
    private var closed = false
    private var failures = 0
    private var exhausted = false
    private val task = Runnable {
        pending = false
        if (!closed && !exhausted && enabled()) {
            running = true
            val succeeded = try { runCatching(attempt).getOrDefault(false) } finally { running = false }
            if (succeeded && !closed && enabled()) {
                connected = true
                failures = 0
            } else if (!closed && enabled()) {
                failures++
                if (failures >= MAX_ATTEMPTS) {
                    exhausted = true
                    suspended()
                } else request((BASE_DELAY_MS shl (failures - 1)).coerceAtMost(MAX_DELAY_MS))
            }
        }
    }

    fun request(delayMs: Long = 0) {
        if (closed || exhausted || connected || pending || running || !enabled()) return
        pending = true
        post(task, delayMs)
    }

    fun reconnect(delayMs: Long = 0) {
        connected = false
        request(delayMs)
    }

    override fun close() {
        closed = true
        pending = false
        connected = false
        remove(task)
    }

    companion object {
        const val MAX_ATTEMPTS = 6
        private const val BASE_DELAY_MS = 3_000L
        private const val MAX_DELAY_MS = 60_000L
    }
}

/** A timed-out IPC must also release a provider client that only arrives after the timeout. */
internal class NativeFiveGaIpcLease<T : AutoCloseable> {
    private var value: T? = null
    private var abandoned = false

    fun offer(next: T) {
        val discard = synchronized(this) {
            if (abandoned) true else {
                check(value == null)
                value = next
                false
            }
        }
        if (discard) runCatching { next.close() }
    }

    @Synchronized fun take(): T? = value.also { value = null }

    fun abandon() {
        val discard = synchronized(this) {
            abandoned = true
            value.also { value = null }
        }
        discard?.let { runCatching { it.close() } }
    }
}
