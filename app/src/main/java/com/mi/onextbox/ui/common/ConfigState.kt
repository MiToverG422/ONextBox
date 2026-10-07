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
    private val save: (T) -> Unit,
) : MutableState<T> {
    private val state = mutableStateOf(initialValue)
    private val writeLock = Mutex()
    private var pendingWrites = 0

    override var value: T
        get() = state.value
        set(value) {
            if (value == state.value) return
            state.value = value
            pendingWrites++
            scope.launch {
                try {
                    writeLock.withLock { withContext(Dispatchers.IO) { save(value) } }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    ConfigSyncStatus.failed("feature preference")
                } finally {
                    pendingWrites--
                }
            }
        }

    fun replaceFromSource(value: T) {
        if (pendingWrites == 0) state.value = value
    }

    override fun component1(): T = value
    override fun component2(): (T) -> Unit = { value = it }
}

@Composable
internal fun <T> rememberConfigState(initialValue: T, save: (T) -> Unit): ConfigState<T> {
    val scope = rememberCoroutineScope()
    val currentSave = rememberUpdatedState(save)
    return remember { ConfigState(initialValue, scope) { currentSave.value(it) } }
}
