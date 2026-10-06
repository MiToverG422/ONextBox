package com.mi.onextbox.lsp

/** Advance a captured native reveal by elapsed time, never restart it from its first frame */
internal object FaceTapWakeRules {
    data class Position(val index: Int, val remainingMs: Int)

    fun position(durations: List<Int>, elapsedMs: Long): Position? {
        if (elapsedMs < 0L || durations.isEmpty() || durations.size > 90 ||
            durations.any { it <= 0 } || durations.sumOf { it.toLong() } > 2500L) return null
        var elapsed = elapsedMs
        durations.forEachIndexed { index, duration ->
            if (elapsed < duration) return Position(index, (duration - elapsed).toInt())
            elapsed -= duration
        }
        return null
    }
}
