package com.mi.onextbox.lsp

/** Retain native defaults; repeated setThreadNum calls would recreate DexKit's scheduler. */
internal class DexThreadPolicy {
    private var configured: Int? = null
    fun configure(requested: Int?, setThreads: (Int) -> Unit) {
        if (requested == null) return
        require(requested > 0)
        if (requested == configured) return
        setThreads(requested)
        configured = requested
    }
}
