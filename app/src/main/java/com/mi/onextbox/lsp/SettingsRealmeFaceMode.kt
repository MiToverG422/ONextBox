package com.mi.onextbox.lsp

import java.util.WeakHashMap

/** Native face controllers choose their preference keys at construction time. */
internal class SettingsRealmeFaceMode {
    private val owners = WeakHashMap<Any, Boolean>()

    @Synchronized fun acquire(owner: Any, selected: Boolean) {
        if (!owners.containsKey(owner)) owners[owner] = owners.values.firstOrNull() ?: selected
    }
    @Synchronized fun acquireExisting(owner: Any, selected: () -> Boolean) {
        if (!owners.containsKey(owner)) acquire(owner, selected())
    }
    @Synchronized fun forced(selected: Boolean): Boolean = owners.values.firstOrNull() ?: selected
    @Synchronized fun release(owner: Any) { owners.remove(owner) }
    @Synchronized fun clear() { owners.clear() }
    @Synchronized fun capture(): Any = owners.entries.map { arrayOf(it.key, it.value) }.toTypedArray()
    @Synchronized fun restore(state: Any?) {
        owners.clear()
        (state as? Array<*>)?.forEach { item ->
            val pair = item as? Array<*> ?: return@forEach
            val owner = pair.getOrNull(0) ?: return@forEach
            val forced = pair.getOrNull(1) as? Boolean ?: return@forEach
            owners[owner] = forced
        }
    }
}
