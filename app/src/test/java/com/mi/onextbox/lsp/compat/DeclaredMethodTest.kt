package com.mi.onextbox.lsp.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeclaredMethodTest {
    open class Parent {
        fun inherited(): String = "parent"
    }

    class Child : Parent() {
        private fun declared(): String = "child"
    }

    @Test
    fun findsAndOpensDeclaredMethod() {
        val method = ModernReflect.findDeclaredMethodExact(Child::class.java, "declared")
        assertEquals("child", method.invoke(Child()))
    }

    @Test
    fun doesNotSilentlyFallBackToParent() {
        assertThrows(NoSuchMethodException::class.java) {
            ModernReflect.findDeclaredMethodExact(Child::class.java, "inherited")
        }
    }
}
