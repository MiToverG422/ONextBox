package com.mi.onextbox.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch

class ConfigStateTest {
    @Test
    fun refreshingFromSourceDoesNotSave() {
        val writes = mutableListOf<Boolean>()
        val job = SupervisorJob()
        val state = ConfigState(false, CoroutineScope(job + Dispatchers.Unconfined), writes::add)
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
