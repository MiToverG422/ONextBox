package com.mi.onextbox.lsp.compat

internal data class HotReloadHookKey<T>(val target: T, val id: String)

/**
 * IDs are scoped to an executable by API 102; repeated hooks retain separate ordered slots.
 * Changing the meaning/order of slots requires a reload protocol bump in OosLspModuleEntry.
 */
internal class HotReloadHookIds<T> {
    private val counts = HashMap<T, Int>()

    @Synchronized
    fun next(target: T): String {
        val slot = counts[target] ?: 0
        counts[target] = slot + 1
        return idForSlot(slot)
    }

    companion object {
        fun idForSlot(slot: Int): String = "onextbox.reload.v1:$slot"
    }
}

/** Missing hooks (including silently skipped OEM signatures) must not replace a working generation. */
internal fun <T> preservesHookCoverage(
    oldTargets: List<HotReloadHookKey<T>>,
    newTargets: List<HotReloadHookKey<T>>,
): Boolean = oldTargets.distinct().size == oldTargets.size &&
    newTargets.distinct().size == newTargets.size && newTargets.containsAll(oldTargets)
