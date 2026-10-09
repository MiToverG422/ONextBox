package com.mi.onextbox.lsp

/** One native dispatch decision per background draw, without changing the global blur policy. */
internal class FluidCloudPanelDrawGate {
    private val pending = ThreadLocal<Boolean>()

    fun <T> duringDraw(useNotificationMaterial: Boolean, draw: () -> T): T {
        val previous = pending.get()
        pending.set(useNotificationMaterial)
        return try {
            draw()
        } finally {
            if (previous == null) pending.remove() else pending.set(previous)
        }
    }

    fun consumeCustomBackgroundBypass(): Boolean {
        if (pending.get() != true) return false
        pending.set(false)
        return true
    }
}
