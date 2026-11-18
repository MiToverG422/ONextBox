package com.mi.onextbox.ui.screens

import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_READY_KEY
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_REVISION_KEY

/** Publishing metadata does not change the explicitly configured feature inventory. */
internal fun isFeatureInventoryPreference(key: String?): Boolean =
    key != MODERN_PREFERENCES_READY_KEY && key != MODERN_PREFERENCES_REVISION_KEY

/** Include all recursive parents/exclusions, including cycles, before narrowing a C16 scan. */
internal fun featureInventoryDependencyClosure(requested: Set<Int>, dependencies: Map<Int, Set<Int>>): Set<Int> {
    val result = requested.toMutableSet()
    val pending = ArrayDeque(requested)
    while (pending.isNotEmpty()) {
        dependencies[pending.removeFirst()].orEmpty().forEach { if (result.add(it)) pending.addLast(it) }
    }
    return result
}
