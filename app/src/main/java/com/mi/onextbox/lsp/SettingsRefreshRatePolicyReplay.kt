package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.HotReloadResourceCoordinator

/** Rebuild OEM caches on a later main-loop turn, after the replacement hook gate opens. */
internal class SettingsRefreshRatePolicyReplay(
    private val lifecycle: HotReloadResourceCoordinator,
    private val post: (Runnable) -> Boolean,
    private val remove: (Runnable) -> Unit,
    private val reapply: () -> Unit,
) {
    private var detached = false
    private val task = Runnable {
        lifecycle.withHook(fallback = {}, action = {
            if (!detached && !lifecycle.isRetiring) reapply()
        })
    }

    fun schedule() {
        lifecycle.afterCommit {
            if (!detached && !lifecycle.isRetiring) {
                remove(task)
                check(post(task)) { "Refresh-rate policy main queue is unavailable" }
            }
        }
    }

    fun detach() {
        detached = true
        remove(task)
    }
}
