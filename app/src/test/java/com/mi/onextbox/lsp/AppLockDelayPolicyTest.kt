package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AppLockDelayPolicyTest {
    private val app = AppLockDelayPolicy.Key(0, "test.app")
    @Test fun neverCreatesADeadlineForUnauthenticatedOrInvalidRequests() {
        val policy = AppLockDelayPolicy()
        assertNull(policy.defer(app, 100, 60_000, false))
        for (delay in listOf(-1L, 0L, 30_000L, Long.MAX_VALUE)) assertNull(policy.defer(app, 100, delay, true))
        assertNull(policy.defer(AppLockDelayPolicy.Key(-1, "test.app"), 100, 60_000, true))
        assertNull(policy.defer(AppLockDelayPolicy.Key(0, ""), 100, 60_000, true))
        assertTrue(policy.snapshot().isEmpty())
    }
    @Test fun duplicateInvisibleCallbacksNeverExtendTheOriginalDeadline() {
        val policy = AppLockDelayPolicy()
        val lease = policy.defer(app, 100, 60_000, true)!!
        repeat(20) { assertSame(lease, policy.defer(app, 1000L + it, 60_000, true)) }
        assertEquals(60_100, lease.deadline)
        assertFalse(lease.expired(60_099))
        assertTrue(lease.expired(60_100))
        assertNull(policy.defer(app, 60_100, 60_000, true))
    }
    @Test fun resumingThenLeavingStartsANewDeadlineAndStaleTimerCannotMatchIt() {
        val policy = AppLockDelayPolicy()
        val old = policy.defer(app, 100, 60_000, true)!!
        policy.cancel(app)
        val new = policy.defer(app, 50_000, 60_000, true)!!
        assertNotEquals(old, new)
        assertNotEquals(old.serial, new.serial)
        assertEquals(110_000, new.deadline)
    }
    @Test fun usersAndPackagesNeverShareAuthenticationWindows() {
        val policy = AppLockDelayPolicy()
        val otherUser = app.copy(user = 999)
        val otherPackage = app.copy(pkg = "other.app")
        policy.defer(app, 100, 60_000, true)
        policy.defer(otherUser, 100, 300_000, true)
        policy.defer(otherPackage, 100, 300_000, true)
        policy.cancel(app)
        assertNull(policy.current(app))
        assertNotNull(policy.current(otherUser))
        assertNotNull(policy.current(otherPackage))
    }
    @Test fun nativeScreenLockRevocationRemovesAllPendingWindowsImmediately() {
        val policy = AppLockDelayPolicy()
        policy.defer(app, 100, 60_000, true)
        policy.defer(app.copy(user = 10), 100, 300_000, true)
        policy.forget(0, null, true)
        assertTrue(policy.snapshot().isEmpty())
    }
    @Test fun elapsedClockRollbackAndOverflowFailClosed() {
        val policy = AppLockDelayPolicy()
        val lease = policy.defer(app, 100, 60_000, true)!!
        assertTrue(lease.expired(99))
        assertNull(AppLockDelayPolicy().defer(app, Long.MAX_VALUE - 10, 60_000, true))
        assertNull(AppLockDelayPolicy().defer(app, -1, 60_000, true))
    }
    @Test fun boundedCapacityFallsBackToNativeRelockWithoutEvictingAnotherLease() {
        val policy = AppLockDelayPolicy(capacity = 1)
        val lease = policy.defer(app, 100, 60_000, true)
        assertNull(policy.defer(app.copy(pkg = "other.app"), 100, 60_000, true))
        assertSame(lease, policy.current(app))
    }
    @Test fun explicitRevocationAndOtherNativeReasonsAreNeverDelayed() {
        assertTrue(AppLockDelayPolicy.isVisibilityExit("notVisible"))
        for (reason in listOf(null, "app-request", "screenOff", "keyguard", "user-switch", "package-removed")) {
            assertFalse(reason, AppLockDelayPolicy.isVisibilityExit(reason))
        }
    }
    @Test fun changingTheDelayOrDisablingTheFeatureImmediatelyInvalidatesTheOldWindow() {
        val policy = AppLockDelayPolicy()
        val lease = policy.defer(app, 100, 60_000, true)!!
        assertFalse(lease.requiresRelock(200, 60_000))
        for (selection in listOf(0L, 300_000L, -1L, Long.MAX_VALUE)) assertTrue(lease.requiresRelock(200, selection))
        assertTrue(lease.requiresRelock(60_100, 60_000))
        assertNull(policy.defer(app, 200, 300_000, true))
    }
    @Test fun backendOnlyRevokesExistingGrantsAndDoesNotOverrideAuthenticationOrPermissions() {
        val source = File("src/main/java/com/mi/onextbox/lsp/AppLockDelaySystemHooker.kt").readText()
        assertFalse(source.contains("param.result = true"))
        assertFalse(source.contains("callMethod(owner, \"addEncryptPass\""))
        assertFalse(source.contains("mAccessControlPassPackages.put"))
        assertFalse(source.contains("getDeclaredMethod(\"checkPermission\""))
        assertTrue(source.contains("AppLockDelayPolicy.isVisibilityExit(chain.getArg(3))"))
        assertTrue(source.contains("removeAccessControlPassAsUser"))
        assertTrue(source.contains("nativeClear.invoke(owner, key.pkg, key.user)"))
        assertTrue(source.contains("getDeclaredMethod(\"setVisibilityInner\""))
        assertTrue(source.contains("Reflect.getObjectField(record, \"mVisibleRequested\") != true"))
        val resume = source.substringAfter("prefix + \"resume\"").substringBefore("prefix + \"visible\"")
        assertFalse(resume.contains("cancel(key)"))
    }
}
