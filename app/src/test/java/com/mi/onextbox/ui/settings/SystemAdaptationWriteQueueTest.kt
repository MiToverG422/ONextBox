package com.mi.onextbox.ui.settings

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class SystemAdaptationWriteQueueTest {
    @Test fun stalledFeatureWriteDoesNotBlockProfileSwitches() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocked = FeatureSettingWriteQueue.application.enqueue {
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val written = mutableListOf<Int>()
            val pending = (0 until 100).map { index ->
                SystemAdaptationWriteQueue.writes.enqueue { written.add(index % 2) }
            }
            withTimeout(3_000) { pending.forEach { it.await() } }
            assertEquals((0 until 100).map { it % 2 }, written)
            assertFalse(blocked.isCompleted)
        } finally {
            release.countDown()
            withTimeout(5_000) { blocked.await() }
        }
    }
}
