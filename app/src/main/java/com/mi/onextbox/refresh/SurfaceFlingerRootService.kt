package com.mi.onextbox.refresh

import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.mi.onextbox.tools.SurfaceFlingerModeTool
import com.topjohnwu.superuser.ipc.RootService

class SurfaceFlingerRootService : RootService() {
    private val controller = object : IRefreshRateController.Stub() {
        override fun getSupportedModes(): MutableList<String> {
            return runCatching {
                SurfaceFlingerModeTool.getSupportedModeLines(this@SurfaceFlingerRootService)
                    .toMutableList()
            }.onFailure(::logFailure).getOrDefault(mutableListOf())
        }

        override fun isRefreshRateDisplayEnabled(): Boolean {
            return runCatching {
                SurfaceFlingerModeTool.isForceRefreshEnabled()
            }.onFailure(::logFailure).getOrDefault(false)
        }

        override fun setRefreshRateDisplayEnabled(enabled: Boolean): Boolean {
            return runCatching {
                SurfaceFlingerModeTool.setForceRefreshEnabled(enabled)
                true
            }.onFailure(::logFailure).getOrDefault(false)
        }

        override fun setRefreshRateMode(modeIndex: Int): Boolean {
            return runCatching {
                SurfaceFlingerModeTool.setModeIndex(modeIndex)
                true
            }.onFailure(::logFailure).getOrDefault(false)
        }

        override fun resetRefreshRateMode(): Boolean {
            return runCatching {
                SurfaceFlingerModeTool.resetMode()
                true
            }.onFailure(::logFailure).getOrDefault(false)
        }
    }

    override fun onBind(intent: Intent): IBinder = controller

    private fun logFailure(error: Throwable) {
        Log.e(TAG, "SurfaceFlinger root operation failed", error)
    }

    private companion object {
        const val TAG = "ONextBox-RefreshRate"
    }
}