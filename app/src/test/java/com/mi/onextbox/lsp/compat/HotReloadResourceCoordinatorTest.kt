package com.mi.onextbox.lsp.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HotReloadResourceCoordinatorTest {
    private fun coordinator() = HotReloadResourceCoordinator { it() }
    private fun hookResult(owner: HotReloadResourceCoordinator) =
        owner.withHook(fallback = { "original" }, action = { "module" })

    @Test fun retiredGenerationCannotRecaptureAlreadyReleasedResourcesOrResumeOldHooks() {
        val owner = coordinator()
        var captured = 0
        var detached = 0
        var resumed = 0
        owner.register("resource", capture = { captured++; "host-state" },
            detach = { detached++ }, resumeAfterRejection = { resumed++ })
        owner.prepare()
        assertThrows(IllegalStateException::class.java) { owner.prepare() }
        assertEquals(1, captured)
        assertEquals(1, detached)
        assertEquals(0, resumed)
        assertTrue(owner.isRetiring)
        assertEquals("original", hookResult(owner))
    }

    @Test fun quarantinedGenerationCannotRearmItselfThroughAnotherPreparation() {
        val owner = coordinator()
        var resumed = false
        owner.register("resource", resumeAfterRejection = { resumed = true })
        owner.fail()
        assertThrows(IllegalStateException::class.java) { owner.prepare() }
        assertFalse(resumed)
        assertTrue(owner.isRetiring)
        assertEquals("original", hookResult(owner))
    }

    @Test fun firstLoadAfterCommitRunsWithoutAndroidLooper() {
        val owner = coordinator()
        val events = ArrayList<String>()
        owner.begin()
        owner.afterCommit { events += "started" }
        assertEquals(listOf("started"), events)
        assertEquals("module", hookResult(owner))
        assertFalse(owner.isRetiring)
    }

    @Test fun stagingDefersEffectsAndRestoresBeforeStartingNewWorkers() {
        val owner = coordinator()
        val events = ArrayList<String>()
        owner.begin(hashMapOf("resource" to "host-state"))
        owner.register("resource", restore = {
            events += "restore:$it"
            assertEquals("original", hookResult(owner))
        })
        owner.afterCommit { events += "start" }
        assertTrue(events.isEmpty())
        assertEquals("original", hookResult(owner))
        owner.replaySavedState()
        assertTrue(events.isEmpty())
        owner.complete()
        assertEquals(listOf("restore:host-state", "start"), events)
        assertEquals("module", hookResult(owner))
        assertNull(owner.restoredState("resource"))
    }

    @Test fun afterCommitDrainsWorkEnqueuedDuringRestoreAndOtherActions() {
        val owner = coordinator()
        val events = ArrayList<String>()
        owner.begin(hashMapOf("resource" to null))
        owner.register("resource", restore = {
            events += "restore"
            owner.afterCommit { events += "restored-worker" }
        })
        owner.afterCommit {
            events += "initial-worker"
            owner.afterCommit { events += "nested-worker" }
        }
        owner.complete()
        assertEquals(listOf("restore", "initial-worker", "restored-worker", "nested-worker"), events)
    }

    @Test fun recursiveDynamicReplayCanRegisterAnotherReplayAndResourceOwner() {
        val owner = coordinator()
        val events = ArrayList<String>()
        owner.begin(hashMapOf("parent" to "loader-a", "child" to "loader-b", "resource" to "host"))
        owner.registerReplay("parent", capture = { null }, replay = {
            events += "parent:$it"
            owner.registerReplay("child", capture = { null }, replay = { child ->
                events += "child:$child"
                owner.register("resource", restore = { saved -> events += "restore:$saved" })
            })
        })
        owner.replaySavedState()
        owner.replaySavedState()
        assertEquals(listOf("parent:loader-a", "child:loader-b"), events)
        owner.complete()
        assertEquals(listOf("parent:loader-a", "child:loader-b", "restore:host"), events)
    }

    @Test fun unknownPreviousResourceRejectsBeforeEffectsOrHookActivation() {
        val owner = coordinator()
        var started = false
        owner.begin(hashMapOf("removed-resource" to "host"))
        owner.afterCommit { started = true }
        val error = assertThrows(IllegalStateException::class.java) { owner.replaySavedState() }
        assertTrue(error.message.orEmpty().contains("no replacement owner"))
        assertFalse(started)
        assertEquals("original", hookResult(owner))
    }

    @Test fun allBusyChecksRunBeforeAnyCaptureOrDetach() {
        val owner = coordinator()
        val events = ArrayList<String>()
        owner.register("first", canReload = { events += "check-first"; true },
            capture = { events += "capture-first"; null }, detach = { events += "detach-first" })
        owner.register("busy", canReload = { events += "check-busy"; false },
            capture = { events += "capture-busy"; null }, detach = { events += "detach-busy" })
        assertThrows(IllegalStateException::class.java) { owner.prepare() }
        assertEquals(listOf("check-first", "check-busy"), events)
        assertFalse(owner.isRetiring)
        assertEquals("module", hookResult(owner))
    }

    @Test fun preparationCapturesWhileRetiringThenDetachesInReverseRegistrationOrder() {
        val owner = coordinator()
        val events = ArrayList<String>()
        listOf("a", "b").forEach { key ->
            owner.register(key,
                capture = {
                    assertTrue(owner.isRetiring)
                    assertEquals("original", hookResult(owner))
                    events += "capture:$key"
                    "state:$key"
                },
                detach = { events += "detach:$key" },
            )
        }
        val state = owner.prepare()
        assertEquals(mapOf("a" to "state:a", "b" to "state:b"), state)
        assertEquals(listOf("capture:a", "capture:b", "detach:b", "detach:a"), events)
        assertTrue(owner.isRetiring)
        assertEquals("original", hookResult(owner))
    }

    @Test fun partialDetachFailureRestoresOnlyTouchedResourcesInReverseDetachOrder() {
        val owner = coordinator()
        val events = ArrayList<String>()
        val failure = IllegalStateException("detach failed")
        listOf("a", "b", "c").forEach { key ->
            owner.register(key, capture = { key },
                detach = { events += "detach:$key"; if (key == "b") throw failure },
                restore = { saved ->
                    assertFalse(owner.isRetiring)
                    assertEquals("original", hookResult(owner))
                    events += "restore:$saved"
                },
            )
        }
        assertSame(failure, assertThrows(IllegalStateException::class.java) { owner.prepare() })
        assertEquals(listOf("detach:c", "detach:b", "restore:b", "restore:c"), events)
        assertFalse(owner.isRetiring)
        assertEquals("module", hookResult(owner))
    }

    @Test fun captureFailureDoesNotDetachAnythingAndResumesTheOldGeneration() {
        val owner = coordinator()
        var detached = false
        owner.register("first", capture = { "host" }, detach = { detached = true })
        owner.register("failed", capture = { error("capture failed") })
        assertThrows(IllegalStateException::class.java) { owner.prepare() }
        assertFalse(detached)
        assertFalse(owner.isRetiring)
        assertEquals("module", hookResult(owner))
    }

    @Test fun duplicateResourceAndReplayKeyRejectsBeforeDetach() {
        val owner = coordinator()
        var detached = false
        owner.register("same", capture = { "host" }, detach = { detached = true })
        owner.registerReplay("same", capture = { "loader" }, replay = {})
        assertThrows(IllegalStateException::class.java) { owner.prepare() }
        assertFalse(detached)
        assertEquals("module", hookResult(owner))
    }

    @Test fun moduleOwnedObjectsAreRejectedEvenInsideNeutralContainers() {
        val owner = coordinator()
        var detached = false
        val neutralContainer = hashMapOf<String, Any?>(
            "nested" to arrayOf<Any?>("valid", arrayListOf<Any?>(ModuleOwnedState())),
        )
        owner.register("resource", capture = { neutralContainer }, detach = { detached = true })
        val error = assertThrows(IllegalStateException::class.java) { owner.prepare() }
        assertTrue(error.message.orEmpty().contains("module-owned object"))
        assertFalse(detached)
        assertEquals("module", hookResult(owner))
    }

    @Test fun moduleClassesAndMethodsCannotCrossGenerations() {
        listOf<Any>(ModuleOwnedState::class.java, ModuleOwnedState::class.java.getDeclaredConstructor()).forEach { value ->
            val owner = coordinator()
            owner.register("resource", capture = { value })
            assertThrows(IllegalStateException::class.java) { owner.prepare() }
            assertEquals("module", hookResult(owner))
        }
    }

    @Test fun neutralArraysMapsCollectionsHostObjectsAndCyclesAreAccepted() {
        val owner = coordinator()
        val host = Any()
        val neutral = HashMap<String, Any?>()
        neutral["self"] = neutral
        neutral["data"] = arrayOf<Any?>("text", 42, true, null, host,
            arrayListOf<Any?>(String::class.java, String::class.java.getDeclaredMethod("length")))
        owner.register("resource", capture = { neutral })
        val saved = owner.prepare()
        assertSame(neutral, saved["resource"])
        assertSame(host, ((saved["resource"] as Map<*, *>)["data"] as Array<*>)[4])
        assertTrue(owner.isRetiring)
    }

    @Test fun cancelPreparationRestoresOldOwnersBeforeReenablingCallbacks() {
        val owner = coordinator()
        val events = ArrayList<String>()
        owner.register("resource", capture = { "host" }, detach = { events += "detach" }, restore = {
            assertFalse(owner.isRetiring)
            assertEquals("original", hookResult(owner))
            events += "restore:$it"
        })
        val saved = owner.prepare()
        owner.cancelPreparation(saved)
        assertEquals(listOf("detach", "restore:host"), events)
        assertEquals("module", hookResult(owner))
        assertFalse(owner.isRetiring)
    }

    @Test fun failureDiscardsDeferredEffectsAndKeepsTheGenerationQuiescent() {
        val owner = coordinator()
        val events = ArrayList<String>()
        owner.begin(hashMapOf())
        owner.register("a", detach = { events += "detach:a" })
        owner.register("b", detach = { events += "detach:b"; error("cleanup failed") })
        owner.afterCommit { events += "must-not-start" }
        owner.fail()
        assertEquals(listOf("detach:b", "detach:a"), events)
        assertTrue(owner.isRetiring)
        assertEquals("original", hookResult(owner))
        assertThrows(IllegalStateException::class.java) { owner.register("too-late") }
    }

    @Test fun failureBeforeStagingAlsoStopsModuleCallbacks() {
        val owner = coordinator()
        assertEquals("module", hookResult(owner))
        owner.fail()
        assertTrue(owner.isRetiring)
        assertEquals("original", hookResult(owner))
    }

    @Test fun resourcesNotPreviouslyLoadedAreNotRestoredWithInventedNullState() {
        val owner = coordinator()
        var restored = false
        owner.begin(hashMapOf())
        owner.register("new-resource", restore = { restored = true })
        owner.replaySavedState()
        owner.complete()
        assertFalse(restored)
        assertEquals("module", hookResult(owner))
    }

    @Test fun cancelPreparationAttemptsEveryRestoreButQuarantinesOnAnyFailure() {
        val owner = coordinator()
        val events = ArrayList<String>()
        val firstFailure = IllegalStateException("first restore failed")
        val laterFailure = IllegalArgumentException("later restore failed")
        listOf("a", "b", "c").forEach { key ->
            owner.register(key, capture = { key }, restore = {
                assertFalse(owner.isRetiring)
                assertEquals("original", hookResult(owner))
                events += "restore:$it"
                if (key == "a") throw firstFailure
                if (key == "c") throw laterFailure
            })
        }
        val saved = owner.prepare()
        val thrown = assertThrows(IllegalStateException::class.java) { owner.cancelPreparation(saved) }
        assertSame(firstFailure, thrown)
        assertEquals(listOf(laterFailure), thrown.suppressed.toList())
        assertEquals(listOf("restore:a", "restore:b", "restore:c"), events)
        assertTrue(owner.isRetiring)
        assertEquals("original", hookResult(owner))
        assertThrows(IllegalStateException::class.java) { owner.register("unsafe") }
    }

    @Test fun failedRollbackStillAttemptsRemainingRestoresAndDoesNotResumeCallbacks() {
        val owner = coordinator()
        val events = ArrayList<String>()
        val detachFailure = IllegalStateException("detach failed")
        val rollbackFailure = IllegalArgumentException("rollback failed")
        listOf("a", "b", "c").forEach { key ->
            owner.register(key, capture = { key },
                detach = { events += "detach:$key"; if (key == "b") throw detachFailure },
                restore = {
                    assertFalse(owner.isRetiring)
                    events += "restore:$it"
                    if (key == "b") throw rollbackFailure
                },
            )
        }
        val thrown = assertThrows(IllegalStateException::class.java) { owner.prepare() }
        assertSame(detachFailure, thrown)
        assertEquals(listOf(rollbackFailure), thrown.suppressed.toList())
        assertEquals(listOf("detach:c", "detach:b", "restore:b", "restore:c"), events)
        assertTrue(owner.isRetiring)
        assertEquals("original", hookResult(owner))
        assertThrows(IllegalStateException::class.java) { owner.register("unsafe") }
    }

    @Test fun busyRejectionRearmsWorkOnlyAfterTheOldGenerationAndGateResume() {
        val owner = coordinator()
        val events = ArrayList<String>()
        var polling = true
        owner.register("poller", canReload = {
            assertTrue(owner.isRetiring)
            assertEquals("original", hookResult(owner))
            polling = false
            events += "skipped-poll"
            true
        }, resumeAfterRejection = {
            assertFalse(owner.isRetiring)
            assertEquals("module", hookResult(owner))
            polling = true
            events += "rearm"
        })
        owner.register("busy", canReload = { false })
        repeat(2) {
            assertThrows(IllegalStateException::class.java) { owner.prepare() }
            assertTrue(polling)
        }
        assertEquals(listOf("skipped-poll", "rearm", "skipped-poll", "rearm"), events)
    }

    @Test fun captureAndStateValidationRejectionsResumeEveryOwnerWithoutDetach() {
        listOf(false, true).forEach { invalidState ->
            val owner = coordinator()
            val events = ArrayList<String>()
            owner.register("first", capture = {
                if (invalidState) ModuleOwnedState() else error("capture failed")
            }, detach = { events += "detach" }, resumeAfterRejection = {
                assertEquals("module", hookResult(owner))
                events += "resume-first"
            })
            owner.register("last", resumeAfterRejection = { events += "resume-last" })
            assertThrows(IllegalStateException::class.java) { owner.prepare() }
            assertEquals(listOf("resume-first", "resume-last"), events)
        }
    }

    @Test fun partialDetachRollbackThenRearmsTouchedAndUntouchedOwners() {
        val owner = coordinator()
        val events = ArrayList<String>()
        listOf("a", "b", "c").forEach { key ->
            owner.register(key, capture = { key }, detach = {
                events += "detach:$key"
                if (key == "b") error("detach failed")
            }, restore = {
                assertEquals("original", hookResult(owner))
                events += "restore:$key"
            }, resumeAfterRejection = {
                assertFalse(owner.isRetiring)
                assertEquals("module", hookResult(owner))
                events += "resume:$key"
            })
        }
        assertThrows(IllegalStateException::class.java) { owner.prepare() }
        assertEquals(listOf("detach:c", "detach:b", "restore:b", "restore:c",
            "resume:a", "resume:b", "resume:c"), events)
    }

    @Test fun cancelledPreparationRearmsOnlyAfterAllResourcesAreRestored() {
        val owner = coordinator()
        val events = ArrayList<String>()
        listOf("a", "b").forEach { key ->
            owner.register(key, capture = { key }, restore = {
                assertEquals("original", hookResult(owner))
                events += "restore:$key"
            }, resumeAfterRejection = {
                assertEquals("module", hookResult(owner))
                events += "resume:$key"
            })
        }
        owner.cancelPreparation(owner.prepare())
        assertEquals(listOf("restore:a", "restore:b", "resume:a", "resume:b"), events)
    }

    @Test fun failedRestoreNeverRearmsTasksOnAnUnsafeGeneration() {
        listOf(false, true).forEach { cancel ->
            val owner = coordinator()
            var resumed = false
            owner.register("resource", detach = { if (!cancel) error("detach failed") },
                restore = { error("restore failed") }, resumeAfterRejection = { resumed = true })
            assertThrows(IllegalStateException::class.java) {
                if (cancel) owner.cancelPreparation(owner.prepare()) else owner.prepare()
            }
            assertFalse(resumed)
            assertTrue(owner.isRetiring)
            assertEquals("original", hookResult(owner))
        }
    }

    @Test fun rejectionResumeFailureIsSuppressedAndQuarantinesTheOldGeneration() {
        val owner = coordinator()
        val rejection = IllegalStateException("capture failed")
        val resumeFailure = IllegalArgumentException("poller could not resume")
        var laterResumed = false
        owner.register("first", capture = { throw rejection }, resumeAfterRejection = {
            assertFalse(owner.isRetiring)
            assertEquals("module", hookResult(owner))
            throw resumeFailure
        })
        owner.register("later", resumeAfterRejection = { laterResumed = true })
        val thrown = assertThrows(IllegalStateException::class.java) { owner.prepare() }
        assertSame(rejection, thrown)
        assertEquals(listOf(resumeFailure), thrown.suppressed.toList())
        assertFalse(laterResumed)
        assertTrue(owner.isRetiring)
        assertEquals("original", hookResult(owner))
    }

    @Test fun cancellationResumeFailureAlsoQuarantinesAfterSuccessfulRestore() {
        val owner = coordinator()
        val failure = IllegalStateException("rearm failed")
        var restored = false
        owner.register("resource", restore = { restored = true }, resumeAfterRejection = {
            assertTrue(restored)
            assertEquals("module", hookResult(owner))
            throw failure
        })
        val saved = owner.prepare()
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            owner.cancelPreparation(saved)
        })
        assertTrue(owner.isRetiring)
        assertEquals("original", hookResult(owner))
    }

    @Test fun successfulRetirementAndReplacementNeverRunRejectionCallbacks() {
        val old = coordinator()
        var oldResumed = false
        old.register("resource", resumeAfterRejection = { oldResumed = true })
        val next = coordinator()
        next.begin(old.prepare())
        var nextResumed = false
        next.register("resource", resumeAfterRejection = { nextResumed = true })
        next.replaySavedState()
        next.complete()
        assertFalse(oldResumed)
        assertFalse(nextResumed)
        assertTrue(old.isRetiring)
        assertEquals("module", hookResult(next))
    }

    @Test fun failedPreparationDispatchStillResumesOwnersThroughMainDispatcher() {
        var dispatches = 0
        var dispatching = false
        val owner = HotReloadResourceCoordinator { action ->
            dispatches++
            if (dispatches == 1) error("main unavailable for preparation")
            dispatching = true
            try { action() } finally { dispatching = false }
        }
        var resumed = false
        owner.register("resource", resumeAfterRejection = {
            assertTrue(dispatching)
            assertEquals("module", hookResult(owner))
            resumed = true
        })
        assertThrows(IllegalStateException::class.java) { owner.prepare() }
        assertEquals(2, dispatches)
        assertTrue(resumed)
        assertFalse(owner.isRetiring)
    }

    @Test fun failedResumeDispatchQuarantinesInsteadOfSilentlyLosingTimers() {
        val owner = HotReloadResourceCoordinator { error("main unavailable") }
        var resumed = false
        owner.register("resource", resumeAfterRejection = { resumed = true })
        val thrown = assertThrows(IllegalStateException::class.java) { owner.prepare() }
        assertEquals(1, thrown.suppressed.size)
        assertFalse(resumed)
        assertTrue(owner.isRetiring)
        assertEquals("original", hookResult(owner))
    }

    @Test fun hookDrainTimeoutRearmsCallbacksSkippedByTheTemporarilyPausedGate() {
        val owner = coordinator()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var resumed = false
        var detached = false
        owner.register("resource", detach = { detached = true }, resumeAfterRejection = {
            assertFalse(owner.isRetiring)
            assertEquals("module", hookResult(owner))
            resumed = true
        })
        val worker = Thread {
            owner.withHook(fallback = {}, action = {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                Unit
            })
        }
        worker.start()
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertThrows(IllegalStateException::class.java) { owner.prepare() }
            assertTrue(resumed)
            assertFalse(detached)
            assertFalse(owner.isRetiring)
        } finally {
            release.countDown()
            worker.join(2_000)
        }
        assertFalse(worker.isAlive)
    }

    private class ModuleOwnedState
}
