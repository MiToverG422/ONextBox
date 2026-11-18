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

    @Test fun cachedCandidatesKeepNullPrimitiveAndStaticDispatchSemantics() {
        val fixture = Overloads()
        repeat(50) {
            assertEquals("text", ModernReflect.callMethod(fixture, "pick", "value"))
            assertEquals("number", ModernReflect.callMethod(fixture, "pick", 4))
            assertEquals("object", ModernReflect.callMethod(fixture, "pick", Any()))
            assertEquals("static", ModernReflect.callStaticMethod(Overloads::class.java, "staticOnly", "value"))
            assertEquals("nullable", ModernReflect.callMethod(fixture, "nullable", null))
            org.junit.Assert.assertThrows(NoSuchMethodException::class.java) {
                ModernReflect.callMethod(fixture, "primitive", null)
            }
            org.junit.Assert.assertThrows(NoSuchMethodException::class.java) {
                ModernReflect.callStaticMethod(Overloads::class.java, "pick", "value")
            }
        }
    }

    @Test fun constructorResolutionRetainsSpecificOverloads() {
        assertEquals("string", (ModernReflect.newInstance(Constructed::class.java, "value") as Constructed).selected)
        assertEquals("number", (ModernReflect.newInstance(Constructed::class.java, 1) as Constructed).selected)
    }

    @Suppress("UNUSED_PARAMETER")
    private class Overloads {
        fun pick(value: String) = "text"
        fun pick(value: Int) = "number"
        fun pick(value: Any) = "object"
        fun primitive(value: Int) = "int"
        fun nullable(value: String?) = "nullable"
        companion object { @JvmStatic fun staticOnly(value: String) = "static" }
    }
    @Suppress("UNUSED_PARAMETER")
    private class Constructed {
        val selected: String
        constructor(value: String) { selected = "string" }
        constructor(value: Int) { selected = "number" }
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
