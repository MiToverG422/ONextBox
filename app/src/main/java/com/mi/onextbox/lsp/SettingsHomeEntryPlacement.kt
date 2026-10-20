package com.mi.onextbox.lsp

internal object SettingsHomeEntryPlacement {
    fun orderFor(position: SettingsEntryPosition, networkOrder: Int, groupOrders: List<Int>, footerOrder: Int?): Int {
        require(position != SettingsEntryPosition.Disabled)
        val groups = groupOrders.filter { it >= networkOrder && it < Int.MAX_VALUE }.distinct().sorted()
        return when (position) {
            SettingsEntryPosition.Top -> networkOrder
            SettingsEntryPosition.Middle -> groups.getOrNull(groups.size / 2) ?: networkOrder
            SettingsEntryPosition.Bottom -> {
                val last = groups.lastOrNull() ?: networkOrder
                if (footerOrder != null && footerOrder > last && footerOrder < Int.MAX_VALUE) footerOrder
                else { require(last < Int.MAX_VALUE - 1); last + 1 }
            }
            SettingsEntryPosition.Disabled -> error("Disabled entries have no placement")
        }
    }

    fun shouldShift(order: Int, anchorOrder: Int): Boolean = order >= anchorOrder && order < Int.MAX_VALUE

    fun shouldRestore(current: Int, original: Int): Boolean = original < Int.MAX_VALUE && current == original + 1

    /** A native group receives its PreferenceManager only when attached to its parent. */
    fun attach(attachGroup: () -> Boolean, attachEntry: () -> Boolean, rollback: () -> Unit) {
        try {
            check(attachGroup()) { "Could not attach Settings entry group" }
            check(attachEntry()) { "Could not attach Settings entry row" }
        } catch (error: Throwable) {
            runCatching(rollback).onFailure { if (it !== error) error.addSuppressed(it) }
            throw error
        }
    }
}
