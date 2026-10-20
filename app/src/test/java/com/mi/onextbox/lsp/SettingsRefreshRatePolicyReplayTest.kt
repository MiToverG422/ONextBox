package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.HotReloadResourceCoordinator
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SettingsRefreshRatePolicyReplayTest {
    private class MainQueue {
        val tasks = ArrayList<Runnable>()
        fun post(task: Runnable): Boolean = tasks.add(task)
        fun remove(task: Runnable) { tasks.removeAll { it === task } }
        fun drain() {
            while (tasks.isNotEmpty()) tasks.removeAt(0).run()
        }
    }

    private fun coordinator() = HotReloadResourceCoordinator { it() }

    @Test fun restoring144HzDoesNotCacheTheUnhooked120HzDefault() {
        val owner = coordinator()
        val queue = MainQueue()
        var cachedRate = 120
        var selectedRate = 144
        val replay = SettingsRefreshRatePolicyReplay(owner, queue::post, queue::remove) {
            cachedRate = owner.withHook(fallback = { 120 }, action = { selectedRate })
        }
        owner.begin(hashMapOf("policy" to Any()))
        owner.register("policy", restore = {
            // This is the native getter's result during restore, even with new hooks installed.
            assertEquals(120, owner.withHook(fallback = { 120 }, action = { selectedRate }))
            replay.schedule()
        })
        owner.replaySavedState()
        owner.complete()
        assertEquals(120, cachedRate)
        assertEquals(1, queue.tasks.size)
        queue.drain()
        assertEquals(144, cachedRate)
        // Reapply reads the latest choice instead of replaying a stale 144 Hz snapshot.
        selectedRate = 120
        replay.schedule()
        queue.drain()
        assertEquals(120, cachedRate)
    }

    @Test fun repeatedReplacementsEachRebuildTheCacheWithTheirOwnActiveHooks() {
        var cachedRate = 120
        var old = coordinator()
        val queue = MainQueue()
        old.register("policy", capture = { Any() })
        for (selectedRate in listOf(144, 165, 144)) {
            val next = coordinator()
            next.begin(old.prepare())
            val replay = SettingsRefreshRatePolicyReplay(next, queue::post, queue::remove) {
                cachedRate = next.withHook(fallback = { 120 }, action = { selectedRate })
            }
            next.register("policy", capture = { Any() }, detach = replay::detach,
                restore = { replay.schedule() })
            next.replaySavedState()
            next.complete()
            queue.drain()
            assertEquals(selectedRate, cachedRate)
            old = next
        }
    }

    @Test fun coldStartAndDuplicateRequestsUseOneOwnedMainQueueTask() {
        val owner = coordinator()
        val queue = MainQueue()
        var calls = 0
        val replay = SettingsRefreshRatePolicyReplay(owner, queue::post, queue::remove) { calls++ }
        repeat(3) { replay.schedule() }
        assertEquals(0, calls)
        assertEquals(1, queue.tasks.size)
        queue.drain()
        assertEquals(1, calls)
    }

    @Test fun retirementRemovesQueuedWorkAndRejectsAlreadyDequeuedTasks() {
        val owner = coordinator()
        val queue = MainQueue()
        var calls = 0
        val replay = SettingsRefreshRatePolicyReplay(owner, queue::post, queue::remove) { calls++ }
        replay.schedule()
        val dequeued = queue.tasks.single()
        replay.detach()
        assertTrue(queue.tasks.isEmpty())
        dequeued.run()
        replay.schedule()
        assertTrue(queue.tasks.isEmpty())
        assertEquals(0, calls)
    }

    @Test fun failedReplacementCannotRunStagedOrQueuedPolicyCallbacks() {
        for (failBeforeCommit in listOf(true, false)) {
            val owner = coordinator()
            val queue = MainQueue()
            var calls = 0
            val replay = SettingsRefreshRatePolicyReplay(owner, queue::post, queue::remove) { calls++ }
            owner.begin(hashMapOf("policy" to Any()))
            owner.register("policy", detach = replay::detach, restore = { replay.schedule() })
            if (!failBeforeCommit) owner.complete()
            owner.fail()
            queue.drain()
            assertEquals(0, calls)
        }
    }

    @Test fun rejectedPreparationRearmsARequestConsumedWhileTheGateWasPaused() {
        val owner = coordinator()
        val queue = MainQueue()
        var calls = 0
        val replay = SettingsRefreshRatePolicyReplay(owner, queue::post, queue::remove) {
            assertFalse(owner.isRetiring)
            calls++
        }
        owner.register("policy", capture = { Any() }, detach = replay::detach,
            resumeAfterRejection = replay::schedule)
        owner.register("busy", canReload = {
            queue.drain()
            assertEquals(0, calls)
            false
        })
        replay.schedule()
        assertThrows(IllegalStateException::class.java) { owner.prepare() }
        queue.drain()
        assertEquals(1, calls)
    }

    @Test fun rollbackRebindsBeforeReapplyingWithTheOldHooksResumed() {
        val owner = coordinator()
        val queue = MainQueue()
        var cachedRate = 120
        fun createReplay() = SettingsRefreshRatePolicyReplay(owner, queue::post, queue::remove) {
            cachedRate = owner.withHook(fallback = { 120 }, action = { 144 })
        }
        var replay = createReplay()
        owner.register("policy", capture = { Any() }, detach = { replay.detach() }, restore = {
            replay = createReplay()
            replay.schedule()
        }, resumeAfterRejection = { replay.schedule() })
        val saved = owner.prepare()
        owner.cancelPreparation(saved)
        assertEquals(1, queue.tasks.size)
        queue.drain()
        assertEquals(144, cachedRate)
    }

    @Test fun aRejectedMainQueueIsNotSilentlyReportedAsScheduled() {
        val replay = SettingsRefreshRatePolicyReplay(coordinator(), { false }, {}) {
            fail("No callback should run on a rejected queue")
        }
        assertThrows(IllegalStateException::class.java) { replay.schedule() }
    }

    @Test fun actualPolicyUsesQueuedForcedRecalculationWithoutRewritingTheUserChoice() {
        val source = listOf(File("src/main/java/com/mi/onextbox/lsp/SettingsGlobalExtremeRefreshRateHooker.kt"),
            File("app/src/main/java/com/mi/onextbox/lsp/SettingsGlobalExtremeRefreshRateHooker.kt"))
            .first { it.isFile }.readText()
        assertTrue(source.contains("runCatching { refreshTarget(force = true) }"))
        assertTrue(source.contains("post = { handler.post(it) }"))
        assertTrue(source.contains("remove = { handler.removeCallbacks(it) }"))
        assertTrue(source.contains("if (!force && next == target && !fixedChanged) return"))
        val initial = source.substringAfter("target = resolveTarget(oemPolicy, oemContext)")
            .substringBefore("private fun resolveTarget")
        assertFalse(initial.contains("callback.invoke(listener)"))
        assertFalse(source.contains("Settings.Secure.put"))
        assertFalse(source.contains("Settings.Global.put"))
        assertFalse(source.contains("Settings.System.put"))
    }
}
