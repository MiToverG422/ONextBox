package com.mi.onextbox.lsp

/** Snapshot containers must be platform classes even when the native SIM list is empty. */
internal object DataSimSignalReloadState {
    fun capture(context: Any?, controllers: Iterable<Pair<Any, List<Int>>>): Map<String, Any?> {
        val rows = ArrayList<ArrayList<Any>>()
        controllers.forEach { (target, source) ->
            rows += arrayListOf(target, ArrayList(source))
        }
        return linkedMapOf("context" to context, "controllers" to rows)
    }
}
