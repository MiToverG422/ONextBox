package com.mi.onextbox.lsp

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class NativeFiveGaCleanupTest {
    @Test fun saturatedDiscoveryWorkersDoNotBlockRegistrationCleanup() {
        val io = ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS, SynchronousQueue())
        val cleaner = ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, ArrayBlockingQueue<Runnable>(64))
        val started = CountDownLatch(2)
        val unblock = CountDownLatch(1)
        val cleaned = CountDownLatch(1)
        try {
            repeat(2) { io.execute { started.countDown(); unblock.await() } }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertThrows(RejectedExecutionException::class.java) { io.execute {} }
            NativeFiveGaCleanup(cleaner).reserve().finish { cleaned.countDown() }
            assertTrue(cleaned.await(5, TimeUnit.SECONDS))
        } finally {
            unblock.countDown(); io.shutdownNow(); cleaner.shutdown()
            assertTrue(io.awaitTermination(5, TimeUnit.SECONDS))
            assertTrue(cleaner.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test fun everyReservedLeaseFitsTheQueueEvenWhenAllCleanupWorkersAreSlow() {
        val capacity = NativeFiveGaCleanup.CAPACITY
        val executor = ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, ArrayBlockingQueue<Runnable>(capacity))
        val unblock = CountDownLatch(1)
        val done = CountDownLatch(capacity)
        try {
            val cleanup = NativeFiveGaCleanup(executor)
            val leases = List(capacity) { cleanup.reserve() }
            assertThrows(IllegalStateException::class.java) { cleanup.reserve() }
            leases.forEach { it.finish { unblock.await(); done.countDown() } }
            assertThrows(IllegalStateException::class.java) { cleanup.reserve() }
            unblock.countDown()
            assertTrue(done.await(5, TimeUnit.SECONDS))
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            val reusable = List(capacity) { cleanup.reserve() }
            assertThrows(IllegalStateException::class.java) { cleanup.reserve() }
            reusable.forEach { it.cancel() }
        } finally { unblock.countDown(); executor.shutdownNow() }
    }

    @Test fun repeatedCloseAndCancelExecuteCleanupOnlyOnce() {
        val cleanup = NativeFiveGaCleanup(Executor { it.run() }, 1)
        var calls = 0
        val lease = cleanup.reserve()
        lease.finish { calls++ }; lease.finish { calls++ }; lease.cancel()
        assertEquals(1, calls)
        val next = cleanup.reserve()
        assertThrows(IllegalStateException::class.java) { cleanup.reserve() }
        next.cancel(); next.cancel(); next.finish { calls++ }
        assertEquals(1, calls)
    }

    @Test fun failedDispatchAndFailedCleanupReleaseCapacityWithoutDoubleRelease() {
        for (executor in listOf(Executor { throw RejectedExecutionException() }, Executor { it.run() })) {
            val cleanup = NativeFiveGaCleanup(executor, 1)
            assertThrows(RuntimeException::class.java) { cleanup.reserve().finish { error("Binder failed") } }
            val next = cleanup.reserve()
            assertThrows(IllegalStateException::class.java) { cleanup.reserve() }
            next.cancel()
        }
    }
}
