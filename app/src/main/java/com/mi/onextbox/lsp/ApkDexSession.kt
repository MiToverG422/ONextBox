package com.mi.onextbox.lsp

import org.luckypray.dexkit.DexKitBridge

internal object ApkDexSession {
    fun <T> withSession(apkPath: String, action: (() -> DexKitBridge) -> T): T {
        val started = android.os.SystemClock.elapsedRealtime()
        var bridge: DexKitBridge? = null
        fun open(): DexKitBridge {
            bridge?.let { return it }
            System.loadLibrary("dexkit")
            return DexKitBridge.create(apkPath).also { bridge = it }
        }
        return try { action(::open) } finally {
            bridge?.close()
            if (bridge != null) com.mi.onextbox.diagnostics.RuntimeDiagnostics.event("dex", "DexKit session",
                "Includes target queries and session cleanup", android.os.SystemClock.elapsedRealtime() - started)
        }
    }
}
