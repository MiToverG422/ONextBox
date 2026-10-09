package com.mi.onextbox.ui

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class RootNavigatorTest {
    private enum class Route : NavKey { Main, Parent, Child }

    @Test fun enteringPageCanReturnImmediatelyAndReopen() {
        val stack = mutableListOf<NavKey>(Route.Main)
        val navigator = RootNavigator(stack)
        navigator.push(Route.Parent)
        navigator.pop()
        assertEquals(listOf(Route.Main), stack)
        navigator.push(Route.Parent)
        assertEquals(listOf(Route.Main, Route.Parent), stack)
    }

    @Test fun enteringPageCanNavigateDeeperWithoutWaiting() {
        val stack = mutableListOf<NavKey>(Route.Main)
        val navigator = RootNavigator(stack)
        navigator.push(Route.Parent)
        navigator.push(Route.Child)
        assertEquals(Route.Child, navigator.current())
        navigator.pop()
        assertEquals(Route.Parent, navigator.current())
    }

    @Test fun repeatedOpenDoesNotDuplicateTheTopPage() {
        val stack = mutableListOf<NavKey>(Route.Main)
        val navigator = RootNavigator(stack)
        repeat(3) { navigator.push(Route.Parent) }
        assertEquals(listOf(Route.Main, Route.Parent), stack)
    }

    @Test fun repeatedBackNeverRemovesTheMainPage() {
        val stack = mutableListOf<NavKey>(Route.Main, Route.Parent, Route.Child)
        val navigator = RootNavigator(stack)
        repeat(4) { navigator.pop() }
        assertEquals(Route.Main, navigator.current())
        assertEquals(1, navigator.backStackSize())
    }
}
