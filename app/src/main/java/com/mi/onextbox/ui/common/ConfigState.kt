package com.mi.onextbox.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.mi.onextbox.lsp.ConfigSyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Writes user changes without writing back source refreshes. */
internal class ConfigState<T>(
    initialValue: T,
    private val scope: CoroutineScope,
    private val conflateWrites: Boolean = false,
    private val onWriteFailed: () -> Unit = { ConfigSyncStatus.failed("feature preference") },
    private val save: (T) -> Unit,
) : MutableState<T> {
    private val state = mutableStateOf(initialValue)
    private val writeLock = Mutex()
    private var pendingWrites = 0
    private class Pending<T>(val value: T)
    private var latestWrite: Pending<T>? = null
    private var writerRunning = false

    override var value: T
        get() = state.value
        set(value) {
            if (value == state.value) return
            state.value = value
            if (conflateWrites) {
                saveLatest(value)
                return
            }
            pendingWrites++
            scope.launch {
                try {
                    writeLock.withLock { withContext(Dispatchers.IO) { save(value) } }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    onWriteFailed()
                } finally {
                    pendingWrites--
                }
            }
        }

    fun replaceFromSource(value: T) {
        if (pendingWrites == 0 && !writerRunning) state.value = value
    }

    // Keep live preview and the in-flight save; discard only obsolete values waiting behind it.
    private fun saveLatest(value: T) {
        latestWrite = Pending(value)
        if (writerRunning) return
        writerRunning = true
        scope.launch {
            try {
                while (true) {
                    val request = latestWrite ?: break
                    latestWrite = null
                    try {
                        withContext(Dispatchers.IO) { save(request.value) }
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        onWriteFailed()
                    }
                }
            } finally {
                writerRunning = false
            }
        }
    }

    override fun component1(): T = value
    override fun component2(): (T) -> Unit = { value = it }
}

@Composable
internal fun <T> rememberConfigState(initialValue: T, conflateWrites: Boolean = false, save: (T) -> Unit): ConfigState<T> {
    val scope = rememberCoroutineScope()
    val currentSave = rememberUpdatedState(save)
    return remember { ConfigState(initialValue, scope, conflateWrites) { currentSave.value(it) } }
}
