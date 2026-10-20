package com.mi.onextbox.lsp

/** Tracks only transient key positions, never password text. */
internal class PinSwipeGesture(private val first: Int) {
    var sliding = false
        private set
    private var last = first
    private var leftFirst = false
    fun move(target: Int, pastSlop: Boolean): List<Int> {
        if (!sliding) {
            if (target < 0 && pastSlop) leftFirst = true
            if ((!pastSlop && !leftFirst) || target < 0 || (target == first && !leftFirst)) return emptyList()
            sliding = true
            last = target
            return listOf(first, target)
        }
        if (target == last) return emptyList()
        last = target
        return if (target < 0) emptyList() else listOf(target)
    }
}
