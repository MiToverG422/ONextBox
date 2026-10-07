package com.mi.onextbox.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PreferenceRestoreTest {
    private class Store(initial: Map<String, *>, private var failOnce: Boolean = false) : PreferenceStore {
        private var values = initial.toMap()
        override fun read(): Map<String, *> = values
        override fun write(values: Map<String, *>): Boolean {
            this.values = values.toMap()
            if (failOnce) {
                failOnce = false
                return false
            }
            return true
        }
    }

    @Test
    fun restoresAllGroups() {
        val first = Store(mapOf("enabled" to false))
        val second = Store(mapOf("mode" to 1))
        restorePreferences(
            mapOf("first" to mapOf("enabled" to true), "second" to mapOf("mode" to 2)),
            mapOf("first" to first, "second" to second),
        )
        assertEquals(true, first.read()["enabled"])
        assertEquals(2, second.read()["mode"])
    }

    @Test
    fun rollsBackEarlierAndFailedGroup() {
        val first = Store(mapOf("enabled" to false))
        val second = Store(mapOf("mode" to 1), failOnce = true)
        assertThrows(IllegalStateException::class.java) {
            restorePreferences(
                mapOf("first" to mapOf("enabled" to true), "second" to mapOf("mode" to 2)),
                mapOf("first" to first, "second" to second),
            )
        }
        assertEquals(false, first.read()["enabled"])
        assertEquals(1, second.read()["mode"])
    }

    @Test
    fun rejectsUnknownGroupBeforeWriting() {
        val store = Store(mapOf("enabled" to false))
        assertThrows(IllegalArgumentException::class.java) {
            restorePreferences(mapOf("unknown" to mapOf("enabled" to true)), mapOf("known" to store))
        }
        assertEquals(false, store.read()["enabled"])
    }

    @Test
    fun rollsBackWhenStoreThrowsAfterUpdatingMemory() {
        var currentValues: Map<String, *> = mapOf("enabled" to false)
        var firstWrite = true
        val store = object : PreferenceStore {
            override fun read(): Map<String, *> = currentValues
            override fun write(values: Map<String, *>): Boolean {
                currentValues = values.toMap()
                if (firstWrite) {
                    firstWrite = false
                    error("Write interrupted")
                }
                return true
            }
        }
        assertThrows(IllegalStateException::class.java) {
            restorePreferences(mapOf("settings" to mapOf("enabled" to true)), mapOf("settings" to store))
        }
        assertEquals(false, currentValues["enabled"])
    }

    @Test
    fun retainsRollbackFailureOnOriginalError() {
        val store = object : PreferenceStore {
            override fun read(): Map<String, *> = emptyMap<String, Any>()
            override fun write(values: Map<String, *>): Boolean = false
        }
        val error = assertThrows(IllegalStateException::class.java) {
            restorePreferences(mapOf("settings" to mapOf("enabled" to true)), mapOf("settings" to store))
        }
        assertEquals("Failed to write settings", error.message)
        assertEquals("Failed to restore settings", error.suppressed.single().message)
    }
}
