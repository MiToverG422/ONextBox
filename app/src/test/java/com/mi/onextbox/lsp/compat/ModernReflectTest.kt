package com.mi.onextbox.lsp.compat

import org.junit.Assert.assertEquals
import org.junit.Test

class ModernReflectTest {
    @Test
    fun resolvesPrivateInheritedMethodAndPrimitiveParameter() {
        val fixture = ChildFixture()

        val result = ModernReflect.callMethod(fixture, "combine", 7, "Hz")

        assertEquals("7Hz", result)
    }

    @Test
    fun resolvesMostSpecificOverload() {
        val fixture = ChildFixture()

        val result = ModernReflect.callMethod(fixture, "describe", "ONextBox")

        assertEquals("string:ONextBox", result)
    }

    @Test
    fun readsAndWritesInheritedPrivateField() {
        val fixture = ChildFixture()

        ModernReflect.setObjectField(fixture, "value", "changed")

        assertEquals("changed", ModernReflect.getObjectField(fixture, "value"))
    }

    private open class ParentFixture {
        @Suppress("unused")
        private var value: String = "initial"

        @Suppress("unused")
        private fun combine(number: Int, suffix: String): String = "$number$suffix"

        @Suppress("unused")
        fun describe(value: Any): String = "any:$value"

        @Suppress("unused")
        fun describe(value: String): String = "string:$value"
    }

    private class ChildFixture : ParentFixture()
}
