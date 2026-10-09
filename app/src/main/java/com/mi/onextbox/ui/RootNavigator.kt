package com.mi.onextbox.ui

import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavKey

@Stable
internal class RootNavigator(
    private val backStack: MutableList<NavKey>,
) {
    fun push(key: NavKey) {
        // Ignore duplicate opens without waiting for the current transition to finish.
        if (current() != key) backStack.add(key)
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }

    fun popUntil(predicate: (NavKey) -> Boolean) {
        while (backStack.size > 1 && !predicate(backStack.last())) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun current(): NavKey? = backStack.lastOrNull()

    fun backStackSize(): Int = backStack.size
}
