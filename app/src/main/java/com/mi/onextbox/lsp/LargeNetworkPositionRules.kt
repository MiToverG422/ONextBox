package com.mi.onextbox.lsp

internal object LargeNetworkPositionRules {
    const val LEFT = 0
    const val RIGHT = 1
    fun normalize(position: Int) = if (position == LEFT) LEFT else RIGHT

    /** The native C17 Row ends with the network label, following the signal. */
    fun <T> arrange(children: List<T>, enabled: Boolean, position: Int, rtl: Boolean = false): List<T> {
        if (!enabled || children.size !in 2..3 || (normalize(position) == LEFT) == rtl) return children
        // Leave the optional roaming label at the beginning; swap only type/signal.
        return children.toMutableList().apply {
            val label = removeAt(lastIndex)
            add(size - 1, label)
        }
    }
}
