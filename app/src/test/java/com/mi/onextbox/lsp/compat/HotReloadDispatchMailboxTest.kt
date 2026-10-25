package com.mi.onextbox.lsp.compat

import com.mi.onextbox.lsp.SystemAdaptationVersion
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import org.junit.Assert.*
import org.junit.Test

class HotReloadDispatchMailboxTest {
    private fun event(value: String): Array<Any> = arrayOf(value)

    @Test fun eventsBeforeBindingDrainInOrderAndLaterEventsDispatchImmediately() {
        val mailbox = HotReloadDispatchMailbox.create()
        val received = ArrayList<String>()
        HotReloadDispatchMailbox.submit(mailbox, event("first"))
        HotReloadDispatchMailbox.submit(mailbox, event("second"))
        HotReloadDispatchMailbox.bind(mailbox, Consumer { received += it[0] as String })
        HotReloadDispatchMailbox.submit(mailbox, event("third"))
        assertEquals(listOf("first", "second", "third"), received)
    }

    @Test fun initialEventsWaitForReadyConfigurationBeforeBindingTheColorOs16Profile() {
        val mailbox = HotReloadDispatchMailbox.create()
        var ready = false
        var profileReads = 0
        val profile = AdaptationProfileGate {
            profileReads++
            SystemAdaptationVersion.ColorOs16
        }
        val dispatched = hashSetOf<String>()
        val received = ArrayList<Pair<String, SystemAdaptationVersion>>()
        lateinit var receiver: Consumer<Array<Any>>
        receiver = Consumer { row ->
            if (!ready) HotReloadDispatchMailbox.defer(mailbox, receiver, row)
            else if (dispatched.add(row[0] as String)) received += row[0] as String to profile.bind()
        }
        HotReloadDispatchMailbox.bind(mailbox, receiver)
        HotReloadDispatchMailbox.submit(mailbox, event("com.android.launcher"))
        HotReloadDispatchMailbox.bind(mailbox, receiver)
        assertEquals(0, profileReads)
        assertTrue(dispatched.isEmpty())
        assertTrue(received.isEmpty())

        ready = true
        HotReloadDispatchMailbox.bind(mailbox, receiver)
        HotReloadDispatchMailbox.bind(mailbox, receiver)
        HotReloadDispatchMailbox.submit(mailbox, event("com.android.launcher"))

        assertEquals(1, profileReads)
        assertEquals(listOf("com.android.launcher" to SystemAdaptationVersion.ColorOs16), received)
    }

    @Test fun unreadyLifecycleEventsSurviveHandoverAndDrainOnlyInTheReadySuccessor() {
        val parent = HotReloadDispatchMailbox.create()
        lateinit var oldReceiver: Consumer<Array<Any>>
        oldReceiver = Consumer { HotReloadDispatchMailbox.defer(parent, oldReceiver, it) }
        HotReloadDispatchMailbox.bind(parent, oldReceiver)
        HotReloadDispatchMailbox.submit(parent, event("before-ready"))
        val child = HotReloadDispatchMailbox.retire(parent)
        HotReloadDispatchMailbox.submit(parent, event("during-handover"))

        var ready = false
        val received = ArrayList<String>()
        lateinit var replacement: Consumer<Array<Any>>
        replacement = Consumer { row ->
            if (!ready) HotReloadDispatchMailbox.defer(child, replacement, row)
            else received += row[0] as String
        }
        HotReloadDispatchMailbox.bind(child, replacement)
        assertTrue(received.isEmpty())
        ready = true
        HotReloadDispatchMailbox.bind(child, replacement)
        HotReloadDispatchMailbox.submit(parent, event("after-ready"))
        HotReloadDispatchMailbox.bind(child, replacement)

        assertEquals(listOf("before-ready", "during-handover", "after-ready"), received)
    }

    @Test fun readinessRacingTheDeferredEnqueueCannotStrandTheInitialEvent() {
        val mailbox = HotReloadDispatchMailbox.create()
        var ready = false
        val received = ArrayList<String>()
        lateinit var receiver: Consumer<Array<Any>>
        receiver = Consumer { row ->
            if (!ready) {
                // The READY notification drains before the unavailable dispatch has enqueued.
                ready = true
                HotReloadDispatchMailbox.bind(mailbox, receiver)
                HotReloadDispatchMailbox.defer(mailbox, receiver, row)
                // Match the dispatcher's bounded recheck after enqueue.
                if (ready) HotReloadDispatchMailbox.bind(mailbox, receiver)
            } else received += row[0] as String
        }
        HotReloadDispatchMailbox.bind(mailbox, receiver)
        HotReloadDispatchMailbox.submit(mailbox, event("initial"))

        assertEquals(listOf("initial"), received)
        HotReloadDispatchMailbox.bind(mailbox, receiver)
        assertEquals(listOf("initial"), received)
    }

    @Test fun retiredChildExportsOnlyPlatformQueueAndHasNoParentOrOldReceiver() {
        val parent = HotReloadDispatchMailbox.create()
        val oldReceiver = Consumer<Array<Any>> { fail("Retired receiver invoked") }
        HotReloadDispatchMailbox.bind(parent, oldReceiver)
        HotReloadDispatchMailbox.defer(parent, oldReceiver, event("pending"))
        val child = HotReloadDispatchMailbox.retire(parent)
        assertNotSame(parent, child)
        assertSame(child, parent[1])
        assertNull(parent[2])
        assertTrue(child[0] is ConcurrentLinkedQueue<*>)
        assertNull(child[1])
        assertNull(child[2])
        val received = ArrayList<String>()
        HotReloadDispatchMailbox.submit(parent, event("during-update"))
        HotReloadDispatchMailbox.bind(child, Consumer { received += it[0] as String })
        assertEquals(listOf("pending", "during-update"), received)
    }

    @Test fun fallbackFromPausedCurrentReceiverQueuesWithoutRecursiveConsumption() {
        val mailbox = HotReloadDispatchMailbox.create()
        lateinit var receiver: Consumer<Array<Any>>
        var invocations = 0
        receiver = Consumer {
            invocations++
            HotReloadDispatchMailbox.defer(mailbox, receiver, it)
        }
        HotReloadDispatchMailbox.bind(mailbox, receiver)
        HotReloadDispatchMailbox.submit(mailbox, event("paused"))
        assertEquals(1, invocations)
        val received = ArrayList<String>()
        HotReloadDispatchMailbox.bind(mailbox, Consumer { received += it[0] as String })
        assertEquals(listOf("paused"), received)
    }

    @Test fun lateFallbackAfterSuccessorBindingForwardsInsteadOfStrandingEvent() {
        val parent = HotReloadDispatchMailbox.create()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        lateinit var oldReceiver: Consumer<Array<Any>>
        oldReceiver = Consumer {
            entered.countDown()
            check(release.await(2, TimeUnit.SECONDS))
            HotReloadDispatchMailbox.defer(parent, oldReceiver, it)
        }
        HotReloadDispatchMailbox.bind(parent, oldReceiver)
        val worker = Thread { HotReloadDispatchMailbox.submit(parent, event("late")) }
        worker.start()
        val received = ConcurrentLinkedQueue<String>()
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val child = HotReloadDispatchMailbox.retire(parent)
            HotReloadDispatchMailbox.bind(child, Consumer { received.add(it[0] as String) })
        } finally {
            release.countDown()
            worker.join(2000)
        }
        assertFalse(worker.isAlive)
        assertEquals(listOf("late"), received.toList())
    }

    @Test fun manyRetirementsForwardOldReferencesOnlyToLatestGeneration() {
        val oldest = HotReloadDispatchMailbox.create()
        val child = HotReloadDispatchMailbox.retire(oldest)
        HotReloadDispatchMailbox.submit(oldest, event("first"))
        val newest = HotReloadDispatchMailbox.retire(child)
        HotReloadDispatchMailbox.submit(child, event("second"))
        val received = ArrayList<String>()
        HotReloadDispatchMailbox.bind(newest, Consumer { received += it[0] as String })
        HotReloadDispatchMailbox.submit(oldest, event("third"))
        assertEquals(listOf("first", "second", "third"), received)
    }

    @Test fun failedPreparationCanBindOldReceiverToChildAfterGateResumes() {
        val mailbox = HotReloadDispatchMailbox.create()
        val received = ArrayList<String>()
        val oldReceiver = Consumer<Array<Any>> { received += it[0] as String }
        HotReloadDispatchMailbox.bind(mailbox, oldReceiver)
        val child = HotReloadDispatchMailbox.retire(mailbox)
        HotReloadDispatchMailbox.submit(mailbox, event("during-prepare"))
        HotReloadDispatchMailbox.bind(child, oldReceiver)
        HotReloadDispatchMailbox.submit(mailbox, event("after-rollback"))
        assertEquals(listOf("during-prepare", "after-rollback"), received)
    }

    @Test fun receiverNeverRunsUnderMailboxMonitor() {
        val mailbox = HotReloadDispatchMailbox.create()
        val parent = HotReloadDispatchMailbox.create()
        val child = HotReloadDispatchMailbox.retire(parent)
        HotReloadDispatchMailbox.submit(mailbox, event("queued"))
        val receiver = Consumer<Array<Any>> {
            assertFalse(Thread.holdsLock(mailbox))
            assertFalse(Thread.holdsLock(parent))
            assertFalse(Thread.holdsLock(child))
        }
        HotReloadDispatchMailbox.bind(mailbox, receiver)
        HotReloadDispatchMailbox.submit(mailbox, event("direct"))
        HotReloadDispatchMailbox.bind(child, receiver)
        HotReloadDispatchMailbox.submit(parent, event("forwarded"))
    }

    @Test fun failingQueuedDispatchDoesNotDropOtherQueuedEvents() {
        val mailbox = HotReloadDispatchMailbox.create()
        HotReloadDispatchMailbox.submit(mailbox, event("fails"))
        HotReloadDispatchMailbox.submit(mailbox, event("still-dispatched"))
        val received = ArrayList<String>()
        val error = IllegalStateException("dispatch failed")
        val thrown = assertThrows(IllegalStateException::class.java) {
            HotReloadDispatchMailbox.bind(mailbox, Consumer {
                val value = it[0] as String
                if (value == "fails") throw error
                received += value
            })
        }
        assertSame(error, thrown)
        assertEquals(listOf("still-dispatched"), received)
    }
}
