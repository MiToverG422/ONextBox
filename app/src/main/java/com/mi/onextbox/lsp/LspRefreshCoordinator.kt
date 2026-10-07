package com.mi.onextbox.lsp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** One shared read, invalidated reads continue with the latest generation. */
internal class LspRefreshCoordinator<T>(
    private val scope: CoroutineScope,
    private val load: suspend () -> T,
    private val onRefreshing: () -> Unit,
    private val onResult: (T) -> Unit,
) {
    private val lock = Any()
    private var generation = 0L
    private var inFlight: Deferred<Publication<T>>? = null
    private var published: Publication<T>? = null

    fun refresh(invalidate: Boolean = false) {
        request(invalidate)
    }

    suspend fun awaitSnapshot(invalidate: Boolean = false): T {
        var task = request(invalidate)
        while (true) {
            task.await()
            task = synchronized(lock) {
                inFlight ?: run {
                    published?.takeIf { it.generation == generation }?.let { return it.value }
                    request(false)
                }
            }
        }
    }

    private fun request(invalidate: Boolean): Deferred<Publication<T>> = synchronized(lock) {
        if (invalidate) generation++
        inFlight?.let { return@synchronized it }
        published?.takeIf { it.generation == generation }?.let {
            return@synchronized CompletableDeferred(it)
        }
        lateinit var task: Deferred<Publication<T>>
        task = scope.async(start = CoroutineStart.LAZY) {
            try {
                while (true) {
                    val requestedGeneration = synchronized(lock) { generation }
                    val value = load()
                    synchronized(lock) {
                        if (requestedGeneration == generation) {
                            val result = Publication(requestedGeneration, value)
                            published = result
                            inFlight = null
                            onResult(value)
                            return@async result
                        }
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                error("No snapshot")
            } finally {
                synchronized(lock) {
                    if (inFlight === task) inFlight = null
                }
            }
        }
        inFlight = task
        onRefreshing()
        task.start()
        task
    }

    private data class Publication<T>(val generation: Long, val value: T)
}
