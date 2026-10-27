package com.mi.onextbox.lsp

import java.util.concurrent.locks.ReentrantReadWriteLock
import org.junit.Assert.*
import org.junit.Test

class ScreenTranslationBrowserVersionsTest {
    private class NativeConfig {
        companion object {
            @JvmField val lock = ReentrantReadWriteLock()
            @JvmField val versions = mapOf("versioned" to listOf(100L, 200L), "generic" to listOf(100L), "exact" to listOf(100L))
            @JvmField val classes = mapOf("generic" to "GenericWebView", "exact:300" to "ExactWebView", "other" to "unrelated")
            @JvmField val otherLists = mapOf("languages" to listOf("en", "zh"))
        }
    }
    private class AmbiguousConfig {
        companion object {
            @JvmField val lock = ReentrantReadWriteLock()
            @JvmField val first = mapOf("browser" to listOf(100L))
            @JvmField val second = mapOf("browser" to listOf(200L))
        }
    }
    private class UnlockedConfig {
        companion object { @JvmField val versions = mapOf("browser" to listOf(100L)) }
    }

    @Test fun readsCopiedVersionRulesWhileKeepingNativeMapsAndLockIntact() {
        val versions = ScreenTranslationBrowserVersions(NativeConfig::class.java)
        assertEquals(listOf(100L, 200L), versions.read("versioned", 300, "OldWebView"))
        assertEquals(listOf(100L, 200L), NativeConfig.versions["versioned"])
        assertEquals(0, NativeConfig.lock.readLockCount)
        assertNull(versions.read("languages"))
        assertNull(versions.read("unknown"))
    }

    @Test fun preservesUniversalAndExactVersionConfigurations() {
        val versions = ScreenTranslationBrowserVersions(NativeConfig::class.java)
        assertNull(versions.read("generic", 300, "GenericWebView"))
        assertNull(versions.read("exact", 300, "ExactWebView"))
    }

    @Test fun ambiguousOrUnsynchronizedSchemasRetainNativeBehavior() {
        assertNull(ScreenTranslationBrowserVersions(AmbiguousConfig::class.java).read("browser"))
        assertNull(ScreenTranslationBrowserVersions(UnlockedConfig::class.java).read("browser"))
        assertEquals(0, AmbiguousConfig.lock.readLockCount)
    }
}
