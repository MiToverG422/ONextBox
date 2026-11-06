package com.mi.onextbox.notifications

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class NotificationBatchEngineTest {
    private val target = NotificationAppTarget("com.test.alpha", 10123)
    private val kinds = setOf(NotificationPermissionKind.Notifications, NotificationPermissionKind.Banner, NotificationPermissionKind.LockScreen)
    private class Backend : NotificationBatchBackend {
        val values = NotificationPermissionKind.entries.associateWith { 0 }.toMutableMap()
        val writes = mutableListOf<Pair<NotificationPermissionKind, Int>>()
        var issue: NotificationBatchIssue? = null
        var unreadable: NotificationPermissionKind? = null
        var rejectWrite: NotificationPermissionKind? = null
        override fun guard(target: NotificationAppTarget) = issue
        override fun read(target: NotificationAppTarget, kind: NotificationPermissionKind): Int {
            check(kind != unreadable)
            return values.getValue(kind)
        }
        override fun write(target: NotificationAppTarget, kind: NotificationPermissionKind, value: Int) {
            check(kind != rejectWrite)
            writes += kind to value
            values[kind] = value
        }
    }

    @Test fun inspectIsReadOnly() {
        val backend = Backend()
        val state = NotificationBatchEngine(backend, 0).inspect(target)
        assertNull(state.issue)
        assertEquals(NotificationPermissionKind.entries.size, state.values.size)
        assertTrue(backend.writes.isEmpty())
    }

    @Test fun planOnlyTouchesSelectedAppsAndKinds() {
        val backend = Backend()
        val engine = NotificationBatchEngine(backend, 0)
        val plan = NotificationBatchEngine.plan(listOf(engine.inspect(target), engine.inspect(target.copy(packageName = "com.test.beta"))), setOf(target.packageName), setOf(NotificationPermissionKind.Banner), true)
        assertEquals(listOf(NotificationMutation(target, NotificationPermissionKind.Banner, 0, 1)), plan)
        assertEquals(1, engine.apply(plan).changes.size)
        assertEquals(listOf(NotificationPermissionKind.Banner to 1), backend.writes)
    }

    @Test fun lockScreenEnablingPreservesHiddenContent() {
        val state = NotificationAppState(target, mapOf(NotificationPermissionKind.LockScreen to 0))
        assertEquals(0, NotificationBatchEngine.plan(listOf(state), setOf(target.packageName), kinds, true).single().after)
    }

    @Test fun disableUsesNativeLockScreenValue() {
        val backend = Backend()
        backend.values.keys.forEach { backend.values[it] = 1 }
        val engine = NotificationBatchEngine(backend, 0)
        val result = engine.apply(NotificationBatchEngine.plan(listOf(engine.inspect(target)), setOf(target.packageName), kinds, false))
        assertEquals(3, result.changes.size)
        assertEquals(-1, backend.values[NotificationPermissionKind.LockScreen])
        assertEquals(0, backend.values[NotificationPermissionKind.Notifications])
    }

    @Test fun protectedAppsAreNeverWritten() {
        val backend = Backend().apply { issue = NotificationBatchIssue.Protected }
        val engine = NotificationBatchEngine(backend, 0)
        assertEquals(NotificationBatchIssue.Protected, engine.apply(listOf(NotificationMutation(target, NotificationPermissionKind.Banner, 0, 1))).results.single().issue)
        assertTrue(backend.writes.isEmpty())
        assertTrue(NotificationBatchEngine.plan(listOf(engine.inspect(target)), setOf(target.packageName), kinds, true).isEmpty())
    }

    @Test fun wrongUserCriticalUidAndInvalidPackageAreRejected() {
        val backend = Backend()
        val engine = NotificationBatchEngine(backend, 0)
        assertEquals(NotificationBatchIssue.IdentityChanged, engine.inspect(target.copy(uid = 110123)).issue)
        assertEquals(NotificationBatchIssue.Protected, engine.inspect(target.copy(uid = 1000)).issue)
        assertEquals(NotificationBatchIssue.IdentityChanged, engine.inspect(target.copy(packageName = "not a package")).issue)
        assertTrue(backend.writes.isEmpty())
    }

    @Test fun secondaryUserIsIsolated() {
        val backend = Backend()
        assertNull(NotificationBatchEngine(backend, 10).inspect(target.copy(uid = 1010123)).issue)
        assertEquals(NotificationBatchIssue.IdentityChanged, NotificationBatchEngine(backend, 10).inspect(target).issue)
    }

    @Test fun staleSnapshotIsNotOverwritten() {
        val backend = Backend().apply { values[NotificationPermissionKind.LockScreen] = 0 }
        val result = NotificationBatchEngine(backend, 0).apply(listOf(NotificationMutation(target, NotificationPermissionKind.LockScreen, 1, -1)))
        assertEquals(NotificationBatchIssue.StateChanged, result.results.single().issue)
        assertTrue(backend.writes.isEmpty())
    }

    @Test fun partialFailureDoesNotStopOtherFieldsOrCountAsSuccess() {
        val backend = Backend().apply { rejectWrite = NotificationPermissionKind.Banner }
        val engine = NotificationBatchEngine(backend, 0)
        val result = engine.apply(NotificationBatchEngine.plan(listOf(engine.inspect(target)), setOf(target.packageName), kinds, true))
        assertEquals(1, result.changes.size)
        assertEquals(1, result.unchanged)
        assertEquals(1, result.failed)
    }

    @Test fun readFailureIsUnknownNotDisabled() {
        val backend = Backend().apply { unreadable = NotificationPermissionKind.Banner }
        val engine = NotificationBatchEngine(backend, 0)
        val state = engine.inspect(target)
        assertNull(state.issue)
        assertFalse(state.values.containsKey(NotificationPermissionKind.Banner))
        assertEquals(NotificationBatchIssue.Unavailable, engine.apply(listOf(NotificationMutation(target, NotificationPermissionKind.Banner, 0, 1))).results.single().issue)
        assertTrue(backend.writes.isEmpty())
    }

    @Test fun undoRestoresExactPrivateAndDefaultValues() {
        listOf(0, -1000).forEach { old ->
            val backend = Backend().apply { values[NotificationPermissionKind.LockScreen] = old }
            val engine = NotificationBatchEngine(backend, 0)
            val changed = engine.apply(listOf(NotificationMutation(target, NotificationPermissionKind.LockScreen, old, -1)))
            assertEquals(1, engine.apply(NotificationBatchEngine.undo(changed.changes)).changes.size)
            assertEquals(old, backend.values[NotificationPermissionKind.LockScreen])
        }
    }

    @Test fun undoDoesNotOverwriteNewManualChanges() {
        val backend = Backend().apply { values[NotificationPermissionKind.LockScreen] = 0 }
        val saved = NotificationMutation(target, NotificationPermissionKind.LockScreen, 1, -1)
        val result = NotificationBatchEngine(backend, 0).apply(NotificationBatchEngine.undo(listOf(saved)))
        assertEquals(NotificationBatchIssue.StateChanged, result.results.single().issue)
        assertTrue(backend.writes.isEmpty())
    }

    @Test fun unchangedValuesDoNotCauseBinderWrites() {
        val backend = Backend().apply { values[NotificationPermissionKind.Banner] = 1 }
        val result = NotificationBatchEngine(backend, 0).apply(listOf(NotificationMutation(target, NotificationPermissionKind.Banner, 0, 1)))
        assertEquals(1, result.unchanged)
        assertTrue(result.changes.isEmpty())
        assertTrue(backend.writes.isEmpty())
    }

    @Test fun duplicateFieldsAndOversizedRequestsAreRejected() {
        val mutation = NotificationMutation(target, NotificationPermissionKind.Banner, 0, 1)
        val engine = NotificationBatchEngine(Backend(), 0)
        assertThrows(IllegalArgumentException::class.java) { engine.apply(listOf(mutation, mutation)) }
        assertThrows(IllegalArgumentException::class.java) { engine.apply(List(1501) { mutation }) }
    }

    @Test fun serializationRetainsExactUndoStateAndResults() {
        val result = NotificationBatchReport(listOf(NotificationMutationResult(NotificationMutation(target, NotificationPermissionKind.LockScreen, -1000, -1), true)))
        assertEquals(result, Json.decodeFromString<NotificationBatchReport>(Json.encodeToString(result)))
    }
    @Test fun silentUsesNativeValuesAndUndoRestoresDefault() {
        val backend = Backend()
        val engine = NotificationBatchEngine(backend, 0)
        val kind = setOf(NotificationPermissionKind.Silent)
        val enabled = engine.apply(NotificationBatchEngine.plan(listOf(engine.inspect(target)), setOf(target.packageName), kind, true))
        assertEquals(-1, backend.values[NotificationPermissionKind.Silent])
        assertEquals(1, enabled.changes.size)
        val disabled = NotificationBatchEngine.plan(listOf(engine.inspect(target)), setOf(target.packageName), kind, false)
        assertEquals(1, disabled.single().after)
        engine.apply(NotificationBatchEngine.undo(enabled.changes))
        assertEquals(0, backend.values[NotificationPermissionKind.Silent])
        assertTrue(backend.writes.all { it.first == NotificationPermissionKind.Silent })
    }

    @Test fun missingLiveActivitySupportPreservesOtherOperations() {
        val backend = Backend().apply { unreadable = NotificationPermissionKind.LiveActivity }
        val engine = NotificationBatchEngine(backend, 0)
        val state = engine.inspect(target)
        assertNull(state.issue)
        val plan = NotificationBatchEngine.plan(listOf(state), setOf(target.packageName),
            setOf(NotificationPermissionKind.Banner, NotificationPermissionKind.LiveActivity), true)
        assertEquals(NotificationPermissionKind.Banner, plan.single().kind)
        assertEquals(1, engine.apply(plan).changes.size)
    }

    @Test fun liveActivityCanBeDisabledAndUndoneWithoutChangingNotifications() {
        val backend = Backend().apply { values[NotificationPermissionKind.LiveActivity] = 1 }
        val engine = NotificationBatchEngine(backend, 0)
        val plan = NotificationBatchEngine.plan(listOf(engine.inspect(target)), setOf(target.packageName),
            setOf(NotificationPermissionKind.LiveActivity), false)
        val report = engine.apply(plan)
        assertEquals(0, backend.values[NotificationPermissionKind.LiveActivity])
        engine.apply(NotificationBatchEngine.undo(report.changes))
        assertEquals(1, backend.values[NotificationPermissionKind.LiveActivity])
        assertTrue(backend.writes.all { it.first == NotificationPermissionKind.LiveActivity })
    }

}
