package com.mi.onextbox.diagnostics

import java.util.ArrayDeque

/** Bounded storage with constant-time appends; callers receive an independent snapshot. */
internal class DiagnosticBuffer<T>(private val capacity: Int) {
    init { require(capacity > 0) }
    private val entries = ArrayDeque<T>(capacity)

    @Synchronized fun add(value: T) {
        if (entries.size == capacity) entries.removeFirst()
        entries.addLast(value)
    }

    @Synchronized fun snapshot(): List<T> = entries.toList()
}

/** Only repeated informational events are throttled. Errors never use this limiter. */
internal class DiagnosticRateLimiter(private val capacity: Int = 256, private val intervalMs: Long = 5_000) {
    init { require(capacity > 0 && intervalMs >= 0) }
    private val lastEmission = LinkedHashMap<String, Long>()

    @Synchronized fun allow(key: String, nowMs: Long): Boolean {
        val previous = lastEmission[key]
        if (previous != null && nowMs >= previous && nowMs - previous < intervalMs) return false
        lastEmission.remove(key)
        if (lastEmission.size == capacity) lastEmission.remove(lastEmission.keys.first())
        lastEmission[key] = nowMs
        return true
    }
}
