package com.mi.onextbox.lsp

// Temporary drawer context for calls from the standalone category page.
internal class LauncherCategoryModeScope {
    private val drawerCall = ThreadLocal<Boolean>()
    val active: Boolean get() = drawerCall.get() == true

    fun <T> withDrawer(block: () -> T): T {
        val previous = drawerCall.get()
        drawerCall.set(true)
        try {
            return block()
        } finally {
            if (previous == null) drawerCall.remove() else drawerCall.set(previous)
        }
    }
}
