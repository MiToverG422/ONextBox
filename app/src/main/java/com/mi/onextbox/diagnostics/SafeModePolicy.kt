package com.mi.onextbox.diagnostics

internal object SafeModePolicy {
    const val MANUAL = "developer_safe_mode"
    const val AUTOMATIC = "developer_auto_safe_mode"
    const val WINDOW_MS = 120_000L
    const val LIMIT = 3

    data class State(val boot: String, val crashes: List<Long> = emptyList(), val blocked: Boolean = false)

    fun forBoot(state: State?, boot: String): State =
        state?.takeIf { it.boot == boot } ?: State(boot)

    fun crash(state: State, now: Long): State {
        if (state.blocked) return state
        val recent = (state.crashes.filter { it <= now && now - it <= WINDOW_MS } + now).takeLast(LIMIT)
        return state.copy(crashes = recent, blocked = recent.size >= LIMIT)
    }

    fun scope(process: String, system: Boolean): String? = when {
        system -> "system"
        process == "com.android.systemui" -> process
        process in setOf("com.android.launcher", "com.android.launcher3", "com.oplus.launcher", "com.oneplus.launcher") -> process
        else -> null
    }
}
