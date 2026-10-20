package com.mi.onextbox.lsp

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class ConfirmedIntPreferenceUpdateTest {
    private val key = "settings_home_entry_position"

    @Test fun selectionIsConfirmedInBothStoresWithoutChangingOtherPreferences() {
        val local = Store(mapOf("legacy" to true))
        val remote = Store(mapOf("legacy" to false))
        commitConfirmedIntPreference(local.preferences, remote.preferences, key, 3) { true }
        assertEquals(3, local.data[key])
        assertEquals(3, remote.data[key])
        assertEquals(true, local.data["legacy"])
        assertEquals(false, remote.data["legacy"])
    }

    @Test fun failedRemoteWriteRestoresExistingSelection() {
        val local = Store(mapOf(key to 1))
        val remote = Store(mapOf(key to 1)).apply { onCommit = { it != 1 } }
        assertThrows(IllegalStateException::class.java) {
            commitConfirmedIntPreference(local.preferences, remote.preferences, key, 2) { true }
        }
        assertEquals(1, local.data[key])
        assertEquals(1, remote.data[key])
    }

    @Test fun failedFirstSelectionRestoresTheAbsentMigrationState() {
        val local = Store()
        val remote = Store().apply { onCommit = { it != 1 } }
        assertThrows(IllegalStateException::class.java) {
            commitConfirmedIntPreference(local.preferences, remote.preferences, key, 2) { true }
        }
        assertFalse(local.data.containsKey(key))
        assertFalse(remote.data.containsKey(key))
    }

    @Test fun missingRemoteKeyCannotConfirmDisabledSelection() {
        val local = Store()
        val remote = Store().apply { onCommit = { data.remove(key); true } }
        assertThrows(IllegalStateException::class.java) {
            commitConfirmedIntPreference(local.preferences, remote.preferences, key, 0) { true }
        }
        assertFalse(local.data.containsKey(key))
    }

    @Test fun disconnectedServiceDoesNotChangeEitherStore() {
        val local = Store(mapOf(key to 1))
        val remote = Store(mapOf(key to 1))
        assertThrows(IllegalStateException::class.java) {
            commitConfirmedIntPreference(local.preferences, remote.preferences, key, 3) { false }
        }
        assertEquals(0, local.commits)
        assertEquals(0, remote.commits)
    }

    @Test fun connectionChangeAfterLocalCommitDoesNotWriteToTheOldRemote() {
        var current = true
        val local = Store(mapOf(key to 1)).apply { onCommit = { current = false; true } }
        val remote = Store(mapOf(key to 1))
        assertThrows(IllegalStateException::class.java) {
            commitConfirmedIntPreference(local.preferences, remote.preferences, key, 3) { current }
        }
        assertEquals(1, local.data[key])
        assertEquals(0, remote.commits)
    }

    private class Store(initial: Map<String, Any> = emptyMap()) {
        val data = initial.toMutableMap()
        var commits = 0
        var onCommit: (Int) -> Boolean = { true }
        val preferences: SharedPreferences = proxy(SharedPreferences::class.java) { _, name, args ->
            when (name) {
                "contains" -> data.containsKey(args[0] as String)
                "getInt" -> data[args[0] as String] as? Int ?: args[1] as Int
                "edit" -> editor()
                else -> error("Unexpected preference method: $name")
            }
        }
        private fun editor(): SharedPreferences.Editor {
            val changes = linkedMapOf<String, Int?>()
            return proxy(SharedPreferences.Editor::class.java) { editor, name, args ->
                when (name) {
                    "putInt" -> editor.also { changes[args[0] as String] = args[1] as Int }
                    "remove" -> editor.also { changes[args[0] as String] = null }
                    "commit" -> {
                        changes.forEach { (key, value) -> if (value == null) data.remove(key) else data[key] = value }
                        commits++
                        onCommit(commits)
                    }
                    else -> error("Unexpected editor method: $name")
                }
            }
        }
        private fun <T : Any> proxy(type: Class<T>, action: (Any, String, Array<out Any?>) -> Any?): T =
            checkNotNull(type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { instance, method, args ->
                when (method.name) {
                    "equals" -> instance === args?.firstOrNull()
                    "hashCode" -> System.identityHashCode(instance)
                    "toString" -> "Fake${type.simpleName}"
                    else -> action(instance, method.name, args ?: emptyArray())
                }
            }))
    }
}
