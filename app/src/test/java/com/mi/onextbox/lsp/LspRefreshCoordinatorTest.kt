package com.mi.onextbox.lsp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LspRefreshCoordinatorTest {
    @Test(timeout = 5_000)
    fun concurrentOrdinaryCallersShareOneReadAndOnePublication() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var reads = 0
            var refreshStarts = 0
            val published = mutableListOf<String>()
            val coordinator = LspRefreshCoordinator(
                scope, load = {
                    reads++
                    started.complete(Unit)
                    release.await()
                    "enabled"
                }, onRefreshing = { refreshStarts++ }, onResult = { published += it },
            )
            val first = async { coordinator.awaitSnapshot() }
            started.await()
            val others = List(8) { async { coordinator.awaitSnapshot() } }
            repeat(8) { coordinator.refresh() }
            yield()
            assertEquals(1, reads)
            release.complete(Unit)
            assertEquals("enabled", first.await())
            others.forEach { assertEquals("enabled", it.await()) }
            assertEquals(listOf("enabled"), published)
            assertEquals(1, refreshStarts)
        } finally {
            scope.cancel()
        }
    }

    @Test(timeout = 5_000)
    fun aForceRequestDuringReadDiscardsEarlierEnabledStateForAllWaiters() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val firstStarted = CompletableDeferred<Unit>()
            val secondStarted = CompletableDeferred<Unit>()
            val firstRelease = CompletableDeferred<Unit>()
            val secondRelease = CompletableDeferred<Unit>()
            val published = mutableListOf<String>()
            var reads = 0
            var refreshStarts = 0
            val coordinator = LspRefreshCoordinator(
                scope, load = {
                    when (++reads) {
                        1 -> { firstStarted.complete(Unit); firstRelease.await(); "enabled" }
                        else -> { secondStarted.complete(Unit); secondRelease.await(); "disabled" }
                    }
                }, onRefreshing = { refreshStarts++ }, onResult = { published += it },
            )
            val first = async { coordinator.awaitSnapshot() }
            firstStarted.await()
            val forced = async { coordinator.awaitSnapshot(invalidate = true) }
            yield()
            repeat(8) { coordinator.refresh(invalidate = true) }
            firstRelease.complete(Unit)
            secondStarted.await()
            assertTrue(published.isEmpty())
            assertFalse(first.isCompleted)
            assertFalse(forced.isCompleted)
            secondRelease.complete(Unit)
            assertEquals("disabled", first.await())
            assertEquals("disabled", forced.await())
            assertEquals(2, reads)
            assertEquals(1, refreshStarts)
            assertEquals(listOf("disabled"), published)
        } finally {
            scope.cancel()
        }
    }

    @Test(timeout = 5_000)
    fun aForceRequestDuringTheLatestReadStillCannotPublishObsoleteScopes() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val started = List(3) { CompletableDeferred<Unit>() }
            val release = List(3) { CompletableDeferred<Unit>() }
            var reads = 0
            val published = mutableListOf<Int>()
            val coordinator = LspRefreshCoordinator(
                scope, load = {
                    val read = reads++
                    started[read].complete(Unit)
                    release[read].await()
                    read
                }, onRefreshing = {}, onResult = { published += it },
            )
            val waiter = async { coordinator.awaitSnapshot() }
            started[0].await()
            coordinator.refresh(invalidate = true)
            release[0].complete(Unit)
            started[1].await()
            coordinator.refresh(invalidate = true)
            release[1].complete(Unit)
            started[2].await()
            assertTrue(published.isEmpty())
            release[2].complete(Unit)
            assertEquals(2, waiter.await())
            assertEquals(listOf(2), published)
        } finally {
            scope.cancel()
        }
    }

    @Test(timeout = 5_000)
    fun ordinaryRequestsReusePublicationButAForceAlwaysReadsAgain() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            var reads = 0
            var refreshStarts = 0
            val coordinator = LspRefreshCoordinator(
                scope, load = { ++reads }, onRefreshing = { refreshStarts++ }, onResult = {},
            )
            assertEquals(1, coordinator.awaitSnapshot())
            repeat(8) {
                coordinator.refresh()
                assertEquals(1, coordinator.awaitSnapshot())
            }
            assertEquals(1, reads)
            assertEquals(1, refreshStarts)
            assertEquals(2, coordinator.awaitSnapshot(invalidate = true))
            assertEquals(2, reads)
            assertEquals(2, refreshStarts)
        } finally {
            scope.cancel()
        }
    }

    @Test(timeout = 5_000)
    fun aCancelledWaiterDoesNotCancelSharedBackgroundRead() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var reads = 0
            val coordinator = LspRefreshCoordinator(
                scope, load = { reads++; started.complete(Unit); release.await(); "current" },
                onRefreshing = {}, onResult = {},
            )
            val cancelled = async { coordinator.awaitSnapshot() }
            started.await()
            val continuing = async { coordinator.awaitSnapshot() }
            cancelled.cancel()
            release.complete(Unit)
            assertEquals("current", continuing.await())
            assertEquals(1, reads)
        } finally {
            scope.cancel()
        }
    }

    @Test(timeout = 5_000)
    fun aFailedReadDoesNotLeaveFutureRefreshesJoiningADeadTask() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            var reads = 0
            val coordinator = LspRefreshCoordinator(
                scope, load = { if (++reads == 1) error("Read failed") else "recovered" },
                onRefreshing = {}, onResult = {},
            )
            assertTrue(runCatching { coordinator.awaitSnapshot() }.isFailure)
            assertEquals("recovered", coordinator.awaitSnapshot(invalidate = true))
            assertEquals(2, reads)
        } finally {
            scope.cancel()
        }
    }
}
