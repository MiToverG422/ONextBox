package com.mi.onextbox.lsp

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass
import org.junit.Assert.*
import org.junit.Test

class RemotePreferenceValuesTest {
    @Test fun collectionsCanBeReadWithoutModuleClasses() {
        for (source in listOf(emptySet(), setOf("one"), setOf("one", "two"))) {
            val value = frameworkStringSet(source)
            assertEquals(java.util.HashSet::class.java, value.javaClass)
            val bytes = ByteArrayOutputStream().apply {
                ObjectOutputStream(this).use { it.writeObject(hashMapOf("setting" to value)) }
            }.toByteArray()
            val decoded = object : ObjectInputStream(ByteArrayInputStream(bytes)) {
                override fun resolveClass(desc: ObjectStreamClass): Class<*> {
                    check(desc.name.startsWith("java.")) { "Module class crossed Binder: ${desc.name}" }
                    return super.resolveClass(desc)
                }
            }.use { it.readObject() }
            assertEquals(mapOf("setting" to source), decoded)
        }
    }

    @Test fun copiesAndFiltersSource() {
        val source = mutableSetOf<Any>("valid", 123)
        val result = frameworkStringSet(source)
        source.clear()
        assertEquals(setOf("valid"), result)
    }
}
