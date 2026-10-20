package com.mi.onextbox.lsp

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class RecentTaskHookReplayTest {
    @Test fun constructorsDiscoverEachNativeImplementationOnlyOnce() {
        val installed = arrayListOf<Class<*>>()
        val replay = RecentTaskHookReplay(NativeTasks::class.java, installed::add)
        repeat(3) { replay.discover(OrdinaryTasks::class.java) }
        replay.discover(PanoramaTasks::class.java)
        assertEquals(listOf(OrdinaryTasks::class.java, PanoramaTasks::class.java), installed)
        assertArrayEquals(installed.toTypedArray(), replay.capture())
    }

    @Test fun savedClassesAreReinstalledWithoutConstructingNativeTaskLists() {
        val old = RecentTaskHookReplay(NativeTasks::class.java) {}
        old.discover(OrdinaryTasks::class.java)
        old.discover(PanoramaTasks::class.java)
        val installed = arrayListOf<Class<*>>()
        val replacement = RecentTaskHookReplay(NativeTasks::class.java, installed::add)
        replacement.restore(old.capture())
        replacement.restore(old.capture())
        assertEquals(listOf(OrdinaryTasks::class.java, PanoramaTasks::class.java), installed)
    }

    @Test fun olderGenerationsCanBeRecoveredFromPreviousHostMethods() {
        val installed = arrayListOf<Class<*>>()
        val replacement = RecentTaskHookReplay(NativeTasks::class.java, installed::add)
        replacement.restore(null, listOf(
            OrdinaryTasks::class.java.getDeclaredMethod("copy", ArrayList::class.java),
            PanoramaTasks::class.java.getDeclaredMethod("panorama"),
            PanoramaTasks::class.java.getDeclaredMethod("copy", ArrayList::class.java),
        ))
        assertEquals(listOf(OrdinaryTasks::class.java, PanoramaTasks::class.java), installed)
    }

    @Test fun replayAndPreviousMethodsDeduplicateTheSameImplementations() {
        val installed = arrayListOf<Class<*>>()
        val replay = RecentTaskHookReplay(NativeTasks::class.java, installed::add)
        replay.restore(arrayOf(OrdinaryTasks::class.java), listOf(
            OrdinaryTasks::class.java.getDeclaredMethod("copy", ArrayList::class.java),
            PanoramaTasks::class.java.getDeclaredMethod("panorama"),
        ))
        assertEquals(listOf(OrdinaryTasks::class.java, PanoramaTasks::class.java), installed)
    }

    @Test fun unrelatedMethodsConstructorsAndLookalikesAreNotReplayed() {
        val installed = arrayListOf<Class<*>>()
        val replay = RecentTaskHookReplay(NativeTasks::class.java, installed::add)
        replay.restore(null, listOf(
            OrdinaryTasks::class.java.getDeclaredConstructor(),
            OrdinaryTasks::class.java.getDeclaredMethod("unrelated"),
            Lookalike::class.java.getDeclaredMethod("copy", ArrayList::class.java),
            OrdinaryTasks::class.java.getDeclaredMethod("staticCopy", ArrayList::class.java),
        ))
        assertTrue(installed.isEmpty())
        assertTrue(replay.capture().isEmpty())
    }

    @Test fun failedInstallationIsNotRememberedAndCanBeRetried() {
        val calls = AtomicInteger()
        val replay = RecentTaskHookReplay(NativeTasks::class.java) {
            if (calls.incrementAndGet() == 1) error("installation failed")
        }
        assertThrows(IllegalStateException::class.java) { replay.discover(OrdinaryTasks::class.java) }
        assertTrue(replay.capture().isEmpty())
        replay.discover(OrdinaryTasks::class.java)
        assertEquals(2, calls.get())
        assertArrayEquals(arrayOf(OrdinaryTasks::class.java), replay.capture())
    }

    @Test fun invalidSavedStateCannotInstallUnrelatedClassesOrInstances() {
        val installed = arrayListOf<Class<*>>()
        val replay = RecentTaskHookReplay(NativeTasks::class.java, installed::add)
        listOf<Any>(arrayOf(String::class.java), arrayOf(Any()), "not an array").forEach { saved ->
            assertThrows(IllegalStateException::class.java) { replay.restore(saved) }
        }
        assertTrue(installed.isEmpty())
    }

    @Test fun captureDoesNotExposeTheMutableClassRegistry() {
        val replay = RecentTaskHookReplay(NativeTasks::class.java) {}
        replay.discover(OrdinaryTasks::class.java)
        val snapshot = replay.capture()
        snapshot[0] = PanoramaTasks::class.java
        assertArrayEquals(arrayOf(OrdinaryTasks::class.java), replay.capture())
    }

    @Test fun concurrentConstructorCallbacksCannotCreateDuplicateHookSlots() {
        val calls = AtomicInteger()
        val replay = RecentTaskHookReplay(NativeTasks::class.java) { calls.incrementAndGet() }
        val start = CountDownLatch(1)
        val done = CountDownLatch(8)
        repeat(8) {
            Thread {
                try { start.await(); replay.discover(OrdinaryTasks::class.java) }
                finally { done.countDown() }
            }.start()
        }
        start.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(1, calls.get())
    }

    open class NativeTasks {
        open fun copy(tasks: ArrayList<Any>): ArrayList<Any> = tasks
        open fun panorama(): List<Any> = emptyList()
    }
    class OrdinaryTasks : NativeTasks() {
        override fun copy(tasks: ArrayList<Any>): ArrayList<Any> = tasks
        fun unrelated(): List<Any> = emptyList()
        companion object {
            @JvmStatic fun staticCopy(tasks: ArrayList<Any>): ArrayList<Any> = tasks
        }
    }
    class PanoramaTasks : NativeTasks() {
        override fun copy(tasks: ArrayList<Any>): ArrayList<Any> = tasks
        override fun panorama(): List<Any> = emptyList()
    }
    class Lookalike {
        fun copy(tasks: ArrayList<Any>): ArrayList<Any> = tasks
    }
}
