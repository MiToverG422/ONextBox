package com.mi.onextbox.ui.screens

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class VersionedCatalogCacheTest {
    @Test fun sharesValuesUntilLocaleOrPackageRevisionChanges() {
        val cache = VersionedCatalogCache<String, Int>()
        var loads = 0
        assertEquals(1, cache.get("zh-CN") { ++loads })
        assertEquals(1, cache.get("zh-CN") { ++loads })
        assertEquals(2, cache.get("en") { ++loads })
        cache.invalidate()
        assertEquals(3, cache.get("en") { ++loads })
    }

    @Test fun invalidationDuringQueryDoesNotPublishStaleAppsOrBlockTheReceiver() {
        val cache = VersionedCatalogCache<String, Int>()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val worker = Executors.newSingleThreadExecutor()
        try {
            var loads = 0
            val result = worker.submit<Int> { cache.get("zh") {
                val value = ++loads
                if (value == 1) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
                value
            } }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(1L, cache.invalidate())
            release.countDown()
            assertEquals(2, result.get(5, TimeUnit.SECONDS))
            assertEquals(2, cache.get("zh") { error("Should reuse the fresh query") })
        } finally { release.countDown(); worker.shutdownNow() }
    }

    @Test fun failedQueriesCanBeRetried() {
        val cache = VersionedCatalogCache<String, String>()
        assertThrows(IllegalStateException::class.java) { cache.get("zh") { error("PM unavailable") } }
        assertEquals("retried", cache.get("zh") { "retried" })
    }
}
