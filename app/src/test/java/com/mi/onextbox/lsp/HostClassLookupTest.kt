package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class HostClassLookupTest {
    @Test fun forwardsTheUnmodifiedNameToTheExplicitHostLoader() {
        val names = mutableListOf<String>()
        val host = object : ClassLoader(null) {
            override fun loadClass(name: String): Class<*> {
                names += name
                if (name == "java.lang.String") return String::class.java
                throw ClassNotFoundException(name)
            }
        }
        assertSame(String::class.java, HostClassLookup.load("java.lang.String", false, host))
        assertEquals(listOf("java.lang.String"), names)
    }

    @Test fun missingHostClassesRemainFailuresInsteadOfUsingTheModuleCopy() {
        val host = object : ClassLoader(null) {
            override fun loadClass(name: String): Class<*> = throw ClassNotFoundException(name)
        }
        assertThrows(ClassNotFoundException::class.java) { HostClassLookup.load("androidx.compose.runtime.Composer", false, host) }
    }
}
