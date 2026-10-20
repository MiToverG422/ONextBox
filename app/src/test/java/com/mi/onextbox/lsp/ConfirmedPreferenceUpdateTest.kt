package com.mi.onextbox.lsp

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class ConfirmedPreferenceUpdateTest {
    private val key = "confirmed_boolean_test"

    @Test
    fun disablingReturnsOnlyAfterBothStoresContainFalse() {
        val local = FakePreferences(mapOf(key to true, "unrelated" to true))
        val remote = FakePreferences(mapOf(key to true, "unrelated" to false))
        commitConfirmedBooleanPreference(local.preferences, remote.preferences, key, false) { true }
        assertEquals(false, local.data[key])
        assertEquals(false, remote.data[key])
        assertEquals(true, local.data["unrelated"])
        assertEquals(false, remote.data["unrelated"])
        assertEquals(1, local.commits)
        assertEquals(1, remote.commits)
    }

    @Test
    fun enablingFromTheAbsentDefaultIsConfirmedInBothStores() {
        val local = FakePreferences()
        val remote = FakePreferences()
        commitConfirmedBooleanPreference(local.preferences, remote.preferences, key, true) { true }
        assertEquals(true, local.data[key])
        assertEquals(true, remote.data[key])
    }

    @Test
    fun disconnectedServiceDoesNotWriteEitherStore() {
        val local = FakePreferences(mapOf(key to true))
        val remote = FakePreferences(mapOf(key to true))
        expectFailure {
            commitConfirmedBooleanPreference(local.preferences, remote.preferences, key, false) { false }
        }
        assertEquals(0, local.commits)
        assertEquals(0, remote.commits)
        assertEquals(true, local.data[key])
        assertEquals(true, remote.data[key])
    }

    @Test
    fun failedRemoteCommitRestoresThePreviouslyEnabledChoice() {
        val local = FakePreferences(mapOf(key to true))
        val remote = FakePreferences(mapOf(key to true)).apply {
            onCommit = { attempt -> attempt != 1 }
        }
        expectFailure {
            commitConfirmedBooleanPreference(local.preferences, remote.preferences, key, false) { true }
        }
        assertEquals(true, local.data[key])
        assertEquals(true, remote.data[key])
        assertEquals(2, local.commits)
        assertEquals(2, remote.commits)
    }

    @Test
    fun failedEnableRestoresExplicitFalseInsteadOfLeavingConsentEnabled() {
        val local = FakePreferences(mapOf(key to false))
        val remote = FakePreferences(mapOf(key to false)).apply {
            onCommit = { attempt -> attempt != 1 }
        }
        expectFailure {
            commitConfirmedBooleanPreference(local.preferences, remote.preferences, key, true) { true }
        }
        assertEquals(false, local.data[key])
        assertEquals(false, remote.data[key])
    }

    @Test
    fun failedEnableRestoresAbsentKeysRatherThanCreatingAnExplicitDefault() {
        val local = FakePreferences()
        val remote = FakePreferences().apply { onCommit = { attempt -> attempt != 1 } }
        expectFailure {
            commitConfirmedBooleanPreference(local.preferences, remote.preferences, key, true) { true }
        }
        assertFalse(local.data.containsKey(key))
        assertFalse(remote.data.containsKey(key))
    }

    @Test
    fun failedLocalCommitNeverSendsTheRequestedNewValueToRemote() {
        val local = FakePreferences(mapOf(key to false)).apply {
            onCommit = { attempt -> attempt != 1 }
        }
        val remote = FakePreferences(mapOf(key to false))
        expectFailure {
            commitConfirmedBooleanPreference(local.preferences, remote.preferences, key, true) { true }
        }
        assertEquals(false, local.data[key])
        assertEquals(false, remote.data[key])
        assertEquals(listOf(false), remote.committedValues.map { it[key] })
        assertEquals(2, local.commits)
    }

    @Test
    fun aThrownRemoteErrorIsPreservedWhileBothStoresAreRestored() {
        val expected = IllegalStateException("Binder write failed")
        val local = FakePreferences(mapOf(key to false))
        val remote = FakePreferences(mapOf(key to false)).apply {
            onCommit = { attempt -> if (attempt == 1) throw expected else true }
        }
        val actual = expectFailure {
            commitConfirmedBooleanPreference(local.preferences, remote.preferences, key, true) { true }
        }
        assertSame(expected, actual)
        assertEquals(false, local.data[key])
        assertEquals(false, remote.data[key])
    }

    @Test
    fun reconnectionAfterLocalWriteRestoresLocalWithoutWritingTheOldBinding() {
        var bindingCurrent = true
        val local = FakePreferences(mapOf(key to true)).apply {
            onCommit = { attempt ->
                if (attempt == 1) bindingCurrent = false
                true
            }
        }
        val oldRemote = FakePreferences(mapOf(key to true))
        val newRemote = FakePreferences(mapOf(key to true))
        expectFailure {
            commitConfirmedBooleanPreference(local.preferences, oldRemote.preferences, key, false) {
                bindingCurrent
            }
        }
        assertEquals(true, local.data[key])
        assertEquals(0, oldRemote.commits)
        assertEquals(0, newRemote.commits)
        assertEquals(true, oldRemote.data[key])
        assertEquals(true, newRemote.data[key])
    }

    @Test
    fun reconnectionAfterRemoteWriteIsNotReportedAsConfirmedSuccess() {
        var bindingCurrent = true
        val local = FakePreferences(mapOf(key to true))
        val oldRemote = FakePreferences(mapOf(key to true)).apply {
            onCommit = {
                bindingCurrent = false
                true
            }
        }
        expectFailure {
            commitConfirmedBooleanPreference(local.preferences, oldRemote.preferences, key, false) {
                bindingCurrent
            }
        }
        assertEquals(true, local.data[key])
        // Do not use a stale connection for compensation; reconnection will mirror restored local state.
        assertEquals(1, oldRemote.commits)
        assertEquals(false, oldRemote.data[key])
    }

    @Test
    fun remoteReadbackMismatchRestoresThePreviousChoice() {
        val local = FakePreferences(mapOf(key to true))
        val remote = FakePreferences(mapOf(key to true)).apply {
            onCommit = { attempt ->
                if (attempt == 1) data[key] = true
                true
            }
        }
        expectFailure {
            commitConfirmedBooleanPreference(local.preferences, remote.preferences, key, false) { true }
        }
        assertEquals(true, local.data[key])
        assertEquals(true, remote.data[key])
        assertEquals(2, remote.commits)
    }

    @Test
    fun missingRemoteKeyCannotFalselyConfirmDisabledValue() {
        val local = FakePreferences()
        val remote = FakePreferences().apply {
            onCommit = { attempt ->
                if (attempt == 1) data.remove(key)
                true
            }
        }
        expectFailure {
            commitConfirmedBooleanPreference(local.preferences, remote.preferences, key, false) { true }
        }
        assertFalse(local.data.containsKey(key))
        assertFalse(remote.data.containsKey(key))
    }

    @Test
    fun restorationFailureIsSuppressedWithoutHidingTheOriginalFailure() {
        val original = IllegalStateException("Remote write failed")
        val localRestore = IllegalStateException("Local restore failed")
        val remoteRestore = IllegalStateException("Remote restore failed")
        val local = FakePreferences(mapOf(key to false)).apply {
            onCommit = { attempt -> if (attempt == 2) throw localRestore else true }
        }
        val remote = FakePreferences(mapOf(key to false)).apply {
            onCommit = { attempt -> if (attempt == 1) throw original else throw remoteRestore }
        }
        val actual = expectFailure {
            commitConfirmedBooleanPreference(local.preferences, remote.preferences, key, true) { true }
        }
        assertSame(original, actual)
        assertEquals(listOf(localRestore, remoteRestore), actual.suppressed.toList())
    }

    private fun expectFailure(action: () -> Unit): Throwable {
        try {
            action()
        } catch (error: Throwable) {
            return error
        }
        fail("The write must fail rather than report unconfirmed consent as saved")
        error("Unreachable")
    }

    /** Models commit's in-memory mutation even when persistence fails, without Android stub calls. */
    private class FakePreferences(initial: Map<String, Boolean> = emptyMap()) {
        val data = initial.toMutableMap()
        var commits = 0
        val committedValues = mutableListOf<Map<String, Boolean>>()
        var onCommit: (Int) -> Boolean = { true }

        val preferences: SharedPreferences = proxy(SharedPreferences::class.java) { _, name, args ->
            when (name) {
                "contains" -> data.containsKey(args[0] as String)
                "getBoolean" -> data[args[0] as String] ?: args[1] as Boolean
                "edit" -> editor()
                else -> error("Unexpected preference method: $name")
            }
        }

        private fun editor(): SharedPreferences.Editor {
            val changes = linkedMapOf<String, Boolean?>()
            return proxy(SharedPreferences.Editor::class.java) { editor, name, args ->
                when (name) {
                    "putBoolean" -> editor.also { changes[args[0] as String] = args[1] as Boolean }
                    "remove" -> editor.also { changes[args[0] as String] = null }
                    "commit" -> {
                        changes.forEach { (key, value) ->
                            if (value == null) data.remove(key) else data[key] = value
                        }
                        commits++
                        committedValues += data.toMap()
                        onCommit(commits)
                    }
                    else -> error("Unexpected editor method: $name")
                }
            }
        }

        private fun <T : Any> proxy(
            type: Class<T>,
            action: (Any, String, Array<out Any?>) -> Any?,
        ): T = checkNotNull(type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { instance, method, args ->
            when (method.name) {
                "equals" -> instance === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(instance)
                "toString" -> "Fake${type.simpleName}"
                else -> action(instance, method.name, args ?: emptyArray())
            }
        }))
    }
}
