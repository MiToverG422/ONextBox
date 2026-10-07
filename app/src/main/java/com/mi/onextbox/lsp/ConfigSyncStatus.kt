package com.mi.onextbox.lsp

import com.mi.onextbox.ui.common.AppLogStore
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Reports failed preference or system-mirror writes. */
internal object ConfigSyncStatus {
    private val pending = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val failures = pending.asSharedFlow()

    fun failed(operation: String) {
        AppLogStore.w("ConfigSync", "Write failed: $operation")
        pending.tryEmit(Unit)
    }
}
