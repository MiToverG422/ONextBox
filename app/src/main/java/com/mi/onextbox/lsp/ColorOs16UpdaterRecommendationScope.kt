package com.mi.onextbox.lsp

/** The C16 callbacks also dismiss the original dialog and record the user's choice. */
internal class ColorOs16UpdaterRecommendationScope {
    private val active = ThreadLocal<Boolean>()

    fun replacement(key: String?, enabled: Boolean): Int? =
        if (active.get() == true && enabled && key in setOf("auto_download_network_type", "can_update_at_night")) 1
        else null

    fun <T> enter(block: () -> T): T {
        val previous = active.get()
        active.set(true)
        return try { block() }
        finally { if (previous == null) active.remove() else active.set(previous) }
    }
}
