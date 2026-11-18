package com.mi.onextbox.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ConfigStateTest {
    @Test
    fun numericPreviewKeepsInFlightSaveAndOnlyTheLatestWaitingValue() = runBlocking {
        val writes = mutableListOf<Int>()
        val job = SupervisorJob()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val state = ConfigState(0, CoroutineScope(job + Dispatchers.Unconfined), conflateWrites = true) { value ->
            if (value == 1) {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            writes += value
        }
        try {
            state.value = 1
            assertTrue(started.await(5, TimeUnit.SECONDS))
            for (value in 2..100) state.value = value
            state.replaceFromSource(-1)
            assertEquals(100, state.value)
            release.countDown()
            job.children.toList().joinAll()
            assertEquals(listOf(1, 100), writes)
            state.replaceFromSource(42)
            assertEquals(42, state.value)
            assertEquals(listOf(1, 100), writes)
        } finally { release.countDown(); job.cancel() }
    }

    @Test
    fun conflatedWriterContinuesToLatestValueAfterAnEarlierSaveFails() = runBlocking {
        val writes = mutableListOf<Int>()
        var failures = 0
        val job = SupervisorJob()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val state = ConfigState(0, CoroutineScope(job + Dispatchers.Unconfined),
            conflateWrites = true, onWriteFailed = { failures++ }) { value ->
            if (value == 1) {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                error("Simulated failed save")
            }
            writes += value
        }
        try {
            state.value = 1
            assertTrue(started.await(5, TimeUnit.SECONDS))
            state.value = 2
            state.value = 3
            release.countDown()
            job.children.toList().joinAll()
            assertEquals(listOf(3), writes)
            assertEquals(1, failures)
            assertEquals(3, state.value)
        } finally { release.countDown(); job.cancel() }
    }

    @Test
    fun refreshingFromSourceDoesNotSave() {
        val writes = mutableListOf<Boolean>()
        val job = SupervisorJob()
        val state = ConfigState(false, CoroutineScope(job + Dispatchers.Unconfined), save = writes::add)
        state.replaceFromSource(true)
        assertEquals(true, state.value)
        assertEquals(emptyList<Boolean>(), writes)
        job.cancel()
    }

    @Test
    fun savesUserChangesInOrderAndIgnoresStaleRefresh() = runBlocking {
        val writes = mutableListOf<Boolean>()
        val job = SupervisorJob()
        val ready = CountDownLatch(1)
        val state = ConfigState(false, CoroutineScope(job + Dispatchers.Unconfined)) { value ->
            ready.await()
            writes += value
        }
        state.value = true
        state.value = false
        state.replaceFromSource(true)
        ready.countDown()
        job.children.toList().joinAll()
        assertEquals(listOf(true, false), writes)
        assertEquals(false, state.value)
        job.cancel()
    }
}
