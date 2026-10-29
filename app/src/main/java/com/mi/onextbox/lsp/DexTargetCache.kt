package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.HotReloadLifecycle

internal object DexTargetCache {
    private const val STATE = "developer:dex-cache-generation"
    private const val KEY = "developer_dex_cache_generation"
    private var generation: Int? = null

    @Synchronized fun restoredState(key: String): Any? {
        val current = generation ?: (HookConfigSnapshot.int(KEY, 0) ?: 0).also { value ->
            generation = value
            HotReloadLifecycle.register(STATE, capture = { value })
        }
        val previous = HotReloadLifecycle.restoredState(STATE) as? Int ?: 0
        return if (previous == current) HotReloadLifecycle.restoredState(key) else null
    }
}
