package com.mi.onextbox.refresh

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object RefreshRateControllerClient {
    private const val CONNECTION_TIMEOUT_MS = 8_000L

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var controller: IRefreshRateController? = null
    private var pendingConnection: CompletableDeferred<IRefreshRateController?>? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            completeConnection(IRefreshRateController.Stub.asInterface(service))
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            controller = null
        }

        override fun onBindingDied(name: ComponentName?) {
            controller = null
        }

        override fun onNullBinding(name: ComponentName?) {
            completeConnection(null)
        }
    }

    suspend fun getSupportedModes(context: Context): List<String>? = call(context) { service ->
        service.supportedModes?.toList().orEmpty()
    }

    suspend fun isRefreshRateDisplayEnabled(context: Context): Boolean? = call(context) { service ->
        service.isRefreshRateDisplayEnabled
    }

    suspend fun setRefreshRateDisplayEnabled(context: Context, enabled: Boolean): Boolean? =
        call(context) { service -> service.setRefreshRateDisplayEnabled(enabled) }

    suspend fun setRefreshRateMode(context: Context, modeIndex: Int): Boolean? =
        call(context) { service -> service.setRefreshRateMode(modeIndex) }

    suspend fun resetRefreshRateMode(context: Context): Boolean? = call(context) { service ->
        service.resetRefreshRateMode()
    }

    private suspend fun <T> call(context: Context, block: (IRefreshRateController) -> T): T? {
        return withContext(Dispatchers.IO) {
            val service = getController(context.applicationContext ?: context) ?: return@withContext null
            runCatching { block(service) }
                .onFailure { controller = null }
                .getOrNull()
        }
    }

    private suspend fun getController(context: Context): IRefreshRateController? {
        controller?.let { return it }

        var shouldBind = false
        val pending = synchronized(lock) {
            controller?.let { return it }
            pendingConnection ?: CompletableDeferred<IRefreshRateController?>().also {
                pendingConnection = it
                shouldBind = true
            }
        }
        if (shouldBind) {
            mainHandler.post {
                runCatching {
                    RootService.bind(
                        Intent(context, SurfaceFlingerRootService::class.java),
                        serviceConnection
                    )
                }.onFailure {
                    completeConnection(null)
                }
            }
        }
        val result = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) { pending.await() }
        if (result == null && !pending.isCompleted) {
            synchronized(lock) {
                if (pendingConnection === pending) {
                    pendingConnection = null
                }
            }
        }
        return result
    }

    private fun completeConnection(value: IRefreshRateController?) {
        val pending = synchronized(lock) {
            controller = value
            pendingConnection.also { pendingConnection = null }
        }
        if (pending?.isCompleted == false) {
            pending.complete(value)
        }
    }
}