package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class NativeFiveGaConnectionRetryTest {
    private class Fixture {
        var enabled = true
        var succeeds = false
        var attempts = 0
        var suspensions = 0
        var now = 0L
        val times = arrayListOf<Long>()
        val queued = linkedMapOf<Runnable, Long>()
        var duringAttempt: () -> Unit = {}
        val retry = NativeFiveGaConnectionRetry(
            enabled = { enabled },
            post = { task, delay -> assertFalse("Only one pending request", task in queued); queued[task] = now + delay },
            remove = { queued.remove(it) },
            attempt = { attempts++; times += now; duringAttempt(); succeeds },
            suspended = { suspensions++ },
        )
        fun next() {
            val (task, at) = queued.entries.minBy { it.value }.let { it.key to it.value }
            queued.remove(task)
            now = at
            task.run()
        }
    }

    @Test fun disabledStartupNeverSchedulesDiscovery() {
        val f = Fixture().apply { enabled = false }
        repeat(1000) { f.retry.request(); f.retry.reconnect() }
        assertTrue(f.queued.isEmpty())
        assertEquals(0, f.attempts)
    }

    @Test fun queuedDiscoveryChecksTheSwitchAgainBeforeItRuns() {
        val f = Fixture()
        f.retry.request()
        f.enabled = false
        f.next()
        assertEquals(0, f.attempts)
        assertTrue(f.queued.isEmpty())
    }

    @Test fun networkUpdatesCannotMultiplyOrAccelerateRetries() {
        val f = Fixture()
        repeat(10_000) { f.retry.request() }
        assertEquals(1, f.queued.size)
        while (f.queued.isNotEmpty()) {
            f.next()
            repeat(10_000) { f.retry.request(); f.retry.reconnect() }
        }
        assertEquals(listOf(0L, 3000L, 9000L, 21000L, 45000L, 93000L), f.times)
        assertEquals(6, f.attempts)
        assertEquals(1, f.suspensions)
    }

    @Test fun reentrantUpdatesCannotScheduleASecondInFlightAttempt() {
        val f = Fixture()
        f.duringAttempt = { repeat(1000) { f.retry.request() } }
        f.retry.request()
        f.next()
        assertEquals(listOf(3000L), f.queued.values.toList())
    }

    @Test fun blockedDiscoverySuspendsUntilTheOwnerIsRecreated() {
        val f = Fixture()
        f.retry.request()
        repeat(6) { f.next() }
        f.succeeds = true
        repeat(1000) { f.retry.reconnect() }
        assertTrue(f.queued.isEmpty())
        val nextOwner = Fixture().apply { succeeds = true }
        nextOwner.retry.request(); nextOwner.next()
        assertEquals(1, nextOwner.attempts)
        assertEquals(0, nextOwner.suspensions)
    }

    @Test fun healthyDiscoveryDoesNotPollTheModuleAgain() {
        val f = Fixture().apply { succeeds = true }
        f.retry.request(); f.next()
        repeat(1000) { f.retry.request() }
        assertTrue(f.queued.isEmpty())
        assertEquals(1, f.attempts)
    }

    @Test fun deathOfAHealthyEndpointStartsOneFreshRetryBudget() {
        val f = Fixture()
        f.retry.request(); f.next()
        f.succeeds = true; f.next()
        f.succeeds = false
        repeat(1000) { f.retry.reconnect(500) }
        repeat(6) { f.next() }
        assertTrue(f.queued.isEmpty())
        assertEquals(8, f.attempts)
        assertEquals(1, f.suspensions)
    }

    @Test fun closeCancelsPendingWorkAndCannotBeResurrectedByCallbacks() {
        val f = Fixture()
        f.retry.request(); f.next()
        f.retry.close()
        repeat(1000) { f.retry.request(); f.retry.reconnect() }
        assertTrue(f.queued.isEmpty())
        assertEquals(1, f.attempts)
    }

    @Test fun ipcExceptionsAlsoConsumeTheFiniteBudget() {
        val f = Fixture()
        f.duringAttempt = { throw SecurityException("Association launch blocked") }
        f.retry.request()
        repeat(6) { f.next() }
        assertEquals(1, f.suspensions)
        assertTrue(f.queued.isEmpty())
    }

    @Test fun disablingDuringAnAttemptPreventsAnyFollowUp() {
        val f = Fixture()
        f.duringAttempt = { f.enabled = false }
        f.retry.request(); f.next()
        assertTrue(f.queued.isEmpty())
        assertEquals(0, f.suspensions)
    }

    private class Resource : AutoCloseable {
        val closes = AtomicInteger()
        override fun close() { closes.incrementAndGet() }
    }

    @Test fun timeoutBeforeIpcCompletionClosesTheLateProviderClient() {
        val lease = NativeFiveGaIpcLease<Resource>()
        val resource = Resource()
        lease.abandon(); lease.offer(resource); lease.abandon()
        assertEquals(1, resource.closes.get())
        assertNull(lease.take())
    }

    @Test fun timeoutAfterIpcCompletionAlsoClosesTheUnclaimedClient() {
        val lease = NativeFiveGaIpcLease<Resource>()
        val resource = Resource()
        lease.offer(resource); lease.abandon(); lease.abandon()
        assertEquals(1, resource.closes.get())
        assertNull(lease.take())
    }

    @Test fun successfulConnectionTransfersClientOwnershipToItsSession() {
        val lease = NativeFiveGaIpcLease<Resource>()
        val resource = Resource()
        lease.offer(resource)
        assertSame(resource, lease.take())
        lease.abandon()
        assertEquals(0, resource.closes.get())
        resource.close()
        assertEquals(1, resource.closes.get())
    }

    @Test fun completionRacingWithTimeoutReleasesExactlyOnce() {
        repeat(64) {
            val lease = NativeFiveGaIpcLease<Resource>()
            val resource = Resource()
            val ready = CountDownLatch(1)
            val delivery = Thread { ready.await(); lease.offer(resource) }
            delivery.start(); ready.countDown(); lease.abandon(); delivery.join()
            assertEquals(1, resource.closes.get())
            assertNull(lease.take())
        }
    }
}
