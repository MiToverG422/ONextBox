package com.mi.onextbox.lsp

import java.util.WeakHashMap

/** Stores only host-owned state sinks and bundles, including across module replacement. */
internal class NativeLiveUpdateChipRelay(private val empty: Any, private val write: (Any, Any) -> Unit) {
    private val latest = WeakHashMap<Any, Any>()

    @Synchronized fun accept(sink: Any, bundle: Any, enabled: Boolean) {
        latest[sink] = bundle
        if (enabled) write(sink, bundle)
    }

    @Synchronized fun reconcile(enabled: Boolean) {
        latest.entries.toList().forEach { (sink, bundle) -> write(sink, if (enabled) bundle else empty) }
    }

    @Synchronized fun capture(): Any = java.util.ArrayList(latest.map { arrayOf(it.key, it.value) })

    @Synchronized fun restore(state: Any?, acceptsSink: (Any) -> Boolean) {
        (state as? Collection<*>)?.forEach {
            val pair = it as? Array<*> ?: return@forEach
            val sink = pair.getOrNull(0) ?: return@forEach
            val bundle = pair.getOrNull(1) ?: return@forEach
            if (acceptsSink(sink) && bundle.javaClass == empty.javaClass) latest[sink] = bundle
        }
    }

    @Synchronized fun clear() = latest.clear()
}
