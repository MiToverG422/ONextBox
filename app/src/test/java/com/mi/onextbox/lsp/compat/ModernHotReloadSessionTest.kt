package com.mi.onextbox.lsp.compat

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModernHotReloadSessionTest {
    @Test
    fun stagingExposesOnlyACopyOfPreviousHostTargetsUntilCommit() {
        val installer = RecordingInstaller()
        val oldTargets = arrayListOf<Executable>(target("first"))
        val session = ModernHotReloadSession.Session(true, installer::install, oldTargets)
        oldTargets.clear()
        assertEquals(listOf(target("first")), session.previousTargets)
        session.install(target("first"), hooker("replacement"))
        session.commit(listOf(FakeHandle(target("first"), id(0))))
        assertTrue(session.previousTargets.isEmpty())
    }

    @Test
    fun initialAndFailedSessionsDoNotRetainPreviousTargets() {
        val targets = listOf<Executable>(target("first"))
        val initial = ModernHotReloadSession.Session(false, previousTargets = targets)
        assertTrue(initial.previousTargets.isEmpty())
        val staged = ModernHotReloadSession.Session(true, previousTargets = targets)
        assertEquals(targets, staged.previousTargets)
        staged.markFailed()
        assertTrue(staged.previousTargets.isEmpty())
    }

    @Test
    fun initialInstallAssignsStableIdsPerExecutable() {
        val installer = RecordingInstaller()
        val session = ModernHotReloadSession.Session(false, installer::install)
        val firstHooker = hooker("first")
        val secondHooker = hooker("second")
        val thirdHooker = hooker("third")

        val first = session.install(target("first"), firstHooker)
        val second = session.install(target("first"), secondHooker)
        val third = session.install(target("second"), thirdHooker)

        assertEquals(listOf(id(0), id(1), id(0)), installer.calls.map { it.id })
        assertEquals(listOf(target("first"), target("first"), target("second")),
            installer.calls.map { it.executable })
        assertSame(firstHooker, installer.calls[0].hooker)
        assertSame(secondHooker, installer.calls[1].hooker)
        assertSame(thirdHooker, installer.calls[2].hooker)
        assertEquals(id(0), first.id)
        assertEquals(id(1), second.id)
        assertEquals(id(0), third.id)
        assertTrue(session.healthy)
        assertTrue(session.hasCommittedHooks)
    }

    @Test
    fun stagingBuildsHandlesWithoutInstallingHooks() {
        val installer = RecordingInstaller()
        val session = ModernHotReloadSession.Session(true, installer::install)

        assertFalse(session.healthy)
        assertFalse(session.hasCommittedHooks)
        val pending = session.install(target("first"), hooker("pending"))

        assertEquals(target("first"), pending.executable)
        assertEquals(id(0), pending.id)
        assertTrue(installer.calls.isEmpty())
        assertTrue(session.healthy)
        assertFalse(session.hasCommittedHooks)
    }

    @Test
    fun commitAtomicallyReplacesPreviousHooksWithoutUnhookOrReinstall() {
        val installer = RecordingInstaller()
        val session = ModernHotReloadSession.Session(true, installer::install)
        val old = FakeHandle(target("first"), id(0))
        val replacementHooker = hooker("replacement")
        val pending = session.install(target("first"), replacementHooker)

        session.commit(listOf(old))

        assertEquals(1, old.replaceCalls.size)
        assertSame(replacementHooker, old.replaceCalls.single())
        assertFalse(old.active)
        assertNotNull(old.replacement)
        assertTrue(old.replacement!!.active)
        assertEquals(0, old.unhookCalls)
        assertTrue(installer.calls.isEmpty())
        assertTrue(session.healthy)
        assertTrue(session.hasCommittedHooks)

        // The staged handle now delegates to the new generation, never the invalid old handle.
        pending.unhook()
        pending.unhook()
        assertEquals(1, old.replacement!!.unhookCalls)
        assertEquals(0, old.unhookCalls)
    }

    @Test
    fun commitUsesInstallerOnlyForNewTargets() {
        val installer = RecordingInstaller()
        val session = ModernHotReloadSession.Session(true, installer::install)
        val old = FakeHandle(target("first"), id(0))
        val addedHooker = hooker("added")
        session.install(target("first"), hooker("replacement"))
        session.install(target("second"), addedHooker)

        session.commit(listOf(old))

        assertEquals(1, old.replaceCalls.size)
        assertEquals(1, installer.calls.size)
        assertEquals(target("second"), installer.calls.single().executable)
        assertEquals(id(0), installer.calls.single().id)
        assertSame(addedHooker, installer.calls.single().hooker)
        assertEquals(0, old.unhookCalls)
    }

    @Test
    fun missingPreviousTargetAbortsBeforeChangingAnyHook() {
        val installer = RecordingInstaller()
        val session = ModernHotReloadSession.Session(true, installer::install)
        val first = FakeHandle(target("first"), id(0))
        val missing = FakeHandle(target("second"), id(0))
        session.install(target("first"), hooker("replacement"))
        session.install(target("third"), hooker("addition"))

        assertThrows(IllegalStateException::class.java) { session.commit(listOf(first, missing)) }

        assertUntouched(first, missing)
        assertTrue(installer.calls.isEmpty())
        assertFalse(session.hasCommittedHooks)
        assertFalse(session.healthy)
    }

    @Test
    fun untaggedPreviousHandleAbortsBeforeChangingAnyHook() {
        val installer = RecordingInstaller()
        val session = ModernHotReloadSession.Session(true, installer::install)
        val tagged = FakeHandle(target("first"), id(0))
        val untagged = FakeHandle(target("second"), null)
        session.install(target("first"), hooker("replacement"))
        session.install(target("second"), hooker("replacement"))

        assertThrows(IllegalStateException::class.java) { session.commit(listOf(tagged, untagged)) }

        assertUntouched(tagged, untagged)
        assertTrue(installer.calls.isEmpty())
        assertFalse(session.hasCommittedHooks)
        assertFalse(session.healthy)
    }

    @Test
    fun duplicatePreviousKeysAbortBeforeChangingAnyHook() {
        val installer = RecordingInstaller()
        val session = ModernHotReloadSession.Session(true, installer::install)
        val first = FakeHandle(target("first"), id(0))
        val duplicate = FakeHandle(target("first"), id(0))
        session.install(target("first"), hooker("replacement"))

        assertThrows(IllegalStateException::class.java) { session.commit(listOf(first, duplicate)) }

        assertUntouched(first, duplicate)
        assertTrue(installer.calls.isEmpty())
        assertFalse(session.hasCommittedHooks)
        assertFalse(session.healthy)
    }

    @Test
    fun canceledStagedAdditionIsNotInstalledDuringCommit() {
        val installer = RecordingInstaller()
        val session = ModernHotReloadSession.Session(true, installer::install)
        val old = FakeHandle(target("first"), id(0))
        session.install(target("first"), hooker("replacement"))
        val canceled = session.install(target("second"), hooker("canceled"))

        canceled.unhook()
        canceled.unhook()
        session.commit(listOf(old))

        assertEquals(1, old.replaceCalls.size)
        assertTrue(installer.calls.isEmpty())
        assertTrue(session.healthy)
        assertTrue(session.hasCommittedHooks)
    }

    @Test
    fun cancelingARequiredReplacementDoesNotSatisfyCoverage() {
        val installer = RecordingInstaller()
        val session = ModernHotReloadSession.Session(true, installer::install)
        val required = FakeHandle(target("first"), id(0))
        val canceled = session.install(target("first"), hooker("canceled"))
        session.install(target("second"), hooker("addition"))
        canceled.unhook()

        assertThrows(IllegalStateException::class.java) { session.commit(listOf(required)) }

        assertUntouched(required)
        assertTrue(installer.calls.isEmpty())
        assertFalse(session.hasCommittedHooks)
    }

    @Test
    fun swallowedInitialInstallFailureStillMarksSessionUnhealthy() {
        val installer = RecordingInstaller()
        val session = ModernHotReloadSession.Session(false, installer::install)
        session.install(target("first"), hooker("working"))
        installer.failFor = target("second")

        val failed = runCatching { session.install(target("second"), hooker("failure")) }
        session.install(target("third"), hooker("later success"))

        assertTrue(failed.isFailure)
        assertSame(installer.failure, failed.exceptionOrNull())
        assertFalse(session.healthy)
        assertTrue(session.hasCommittedHooks)
        assertEquals(3, installer.calls.size)
        assertTrue(installer.installed.all { it.active && it.unhookCalls == 0 })
    }

    @Test
    fun explicitlyFailedStagingSessionDoesNotReplaceWorkingHooks() {
        val installer = RecordingInstaller()
        val session = ModernHotReloadSession.Session(true, installer::install)
        val old = FakeHandle(target("first"), id(0))
        session.install(target("first"), hooker("replacement"))
        session.markFailed()

        assertThrows(IllegalStateException::class.java) { session.commit(listOf(old)) }

        assertUntouched(old)
        assertTrue(installer.calls.isEmpty())
        assertFalse(session.hasCommittedHooks)
    }

    @Test
    fun midCommitFailurePreservesSuccessfulReplacementsAndUntouchedOldHooks() {
        val installer = RecordingInstaller()
        val session = ModernHotReloadSession.Session(true, installer::install)
        val first = FakeHandle(target("first"), id(0))
        val failing = FakeHandle(target("second"), id(0))
        val untouched = FakeHandle(target("third"), id(0))
        val failure = IllegalStateException("native replacement failed")
        failing.replaceFailure = failure
        session.install(target("first"), hooker("replacement one"))
        session.install(target("second"), hooker("replacement two"))
        session.install(target("third"), hooker("replacement three"))

        val thrown = assertThrows(IllegalStateException::class.java) {
            session.commit(listOf(first, failing, untouched))
        }

        assertSame(failure, thrown)
        assertFalse(first.active)
        assertTrue(first.replacement!!.active)
        assertEquals(0, first.unhookCalls)
        assertEquals(0, first.replacement!!.unhookCalls)
        assertTrue(failing.active)
        assertEquals(1, failing.replaceCalls.size)
        assertEquals(0, failing.unhookCalls)
        assertNull(failing.replacement)
        assertUntouched(untouched)
        assertTrue(installer.calls.isEmpty())
        assertTrue(session.hasCommittedHooks)
        assertFalse(session.healthy)

        // A failed partially committed generation cannot repeat replacement against invalid handles.
        assertThrows(IllegalStateException::class.java) {
            session.commit(listOf(first, failing, untouched))
        }
        assertEquals(1, first.replaceCalls.size)
        assertEquals(1, failing.replaceCalls.size)
        assertUntouched(untouched)
    }

    @Test
    fun newTargetInstallFailureDoesNotRemoveEarlierSuccessfulReplacements() {
        val installer = RecordingInstaller().apply { failFor = target("second") }
        val session = ModernHotReloadSession.Session(true, installer::install)
        val old = FakeHandle(target("first"), id(0))
        session.install(target("first"), hooker("replacement"))
        session.install(target("second"), hooker("addition"))

        assertThrows(IllegalStateException::class.java) { session.commit(listOf(old)) }

        assertTrue(old.replacement!!.active)
        assertEquals(0, old.unhookCalls)
        assertEquals(0, old.replacement!!.unhookCalls)
        assertEquals(1, installer.calls.size)
        assertTrue(session.hasCommittedHooks)
        assertFalse(session.healthy)
    }

    @Test
    fun aSuccessfulCommitCannotBeRepeated() {
        val installer = RecordingInstaller()
        val session = ModernHotReloadSession.Session(true, installer::install)
        val old = FakeHandle(target("first"), id(0))
        session.install(target("first"), hooker("replacement"))
        session.commit(listOf(old))

        assertThrows(IllegalStateException::class.java) { session.commit(listOf(old)) }

        assertEquals(1, old.replaceCalls.size)
        assertEquals(0, old.unhookCalls)
        assertTrue(installer.calls.isEmpty())
        assertTrue(session.healthy)
        assertTrue(session.hasCommittedHooks)
    }

    @Test
    fun emptyStagingSessionCannotCommit() {
        val installer = RecordingInstaller()
        val session = ModernHotReloadSession.Session(true, installer::install)

        assertThrows(IllegalStateException::class.java) { session.commit(emptyList()) }

        assertFalse(session.healthy)
        assertFalse(session.hasCommittedHooks)
        assertTrue(installer.calls.isEmpty())
    }

    private data class Installation(
        val executable: Executable,
        val id: String,
        val hooker: XposedInterface.Hooker,
    )

    private class RecordingInstaller {
        val calls = mutableListOf<Installation>()
        val installed = mutableListOf<FakeHandle>()
        val failure = IllegalStateException("installer failed")
        var failFor: Executable? = null

        fun install(executable: Executable, id: String, hooker: XposedInterface.Hooker): FakeHandle {
            calls += Installation(executable, id, hooker)
            if (executable == failFor) throw failure
            return FakeHandle(executable, id).also(installed::add)
        }
    }

    /** Models native handle invalidation: replacement failures leave the old hook active. */
    private class FakeHandle(
        private val target: Executable,
        private val hookId: String?,
    ) : XposedInterface.HookHandle {
        var active = true
            private set
        var unhookCalls = 0
            private set
        val replaceCalls = mutableListOf<XposedInterface.Hooker>()
        var replaceFailure: Throwable? = null
        var replacement: FakeHandle? = null
            private set

        override fun getExecutable(): Executable = target
        override fun getId(): String? = hookId

        override fun unhook() {
            unhookCalls++
            active = false
        }

        override fun replaceHook(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
            check(active) { "Native hook handle is no longer valid" }
            replaceCalls += hooker
            replaceFailure?.let { throw it }
            active = false
            return FakeHandle(target, hookId).also { replacement = it }
        }
    }

    private fun assertUntouched(vararg handles: FakeHandle) {
        handles.forEach {
            assertTrue(it.active)
            assertEquals(0, it.unhookCalls)
            assertTrue(it.replaceCalls.isEmpty())
            assertNull(it.replacement)
        }
    }

    private fun hooker(label: String): XposedInterface.Hooker = XposedInterface.Hooker { label }
    private fun target(name: String): Executable = Fixture::class.java.getDeclaredMethod(name)
    private fun id(slot: Int): String = "onextbox.reload.v1:$slot"

    @Suppress("unused")
    private class Fixture {
        fun first() = Unit
        fun second() = Unit
        fun third() = Unit
    }
}
