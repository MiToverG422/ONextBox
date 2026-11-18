package com.mi.onextbox.diagnostics

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors

class DiagnosticBufferTest {
    @Test fun retainsNewestEntriesAndSnapshotsDoNotChange() {
        val buffer = DiagnosticBuffer<Int>(3)
        buffer.add(1)
        val first = buffer.snapshot()
        (2..8).forEach(buffer::add)
        assertEquals(listOf(1), first)
        assertEquals(listOf(6, 7, 8), buffer.snapshot())
    }

    @Test fun concurrentAppendsRemainBounded() {
        val buffer = DiagnosticBuffer<Int>(50)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val jobs = (0..3).map { n -> pool.submit { repeat(2_000) { buffer.add(n * 2_000 + it) } } }
            jobs.forEach { it.get() }
            assertEquals(50, buffer.snapshot().size)
            assertEquals(50, buffer.snapshot().toSet().size)
        } finally { pool.shutdownNow() }
    }

    @Test fun suppressesDuplicatesButAllowsChangesAndClockReset() {
        val limiter = DiagnosticRateLimiter(capacity = 2, intervalMs = 100)
        assertTrue(limiter.allow("a", 100))
        assertFalse(limiter.allow("a", 150))
        assertTrue(limiter.allow("b", 150))
        assertTrue(limiter.allow("a", 200))
        assertTrue(limiter.allow("a", 10))
        assertTrue(limiter.allow("c", 20))
        assertTrue(limiter.allow("b", 21))
    }
}
