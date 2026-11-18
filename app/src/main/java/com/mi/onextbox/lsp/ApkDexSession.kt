package com.mi.onextbox.lsp

import org.luckypray.dexkit.DexKitBridge

internal object ApkDexSession {
    private class Session(val path: String) {
        val started = android.os.SystemClock.elapsedRealtime()
        var bridge: DexKitBridge? = null
        private val threading = DexThreadPolicy()
        val fingerprint by lazy { DexDescriptorStore.fingerprint(java.io.File(path)) }
        fun open(threadCount: Int?): DexKitBridge {
            val loaded = bridge ?: run {
                System.loadLibrary("dexkit")
                DexKitBridge.create(path).also { bridge = it }
            }
            threading.configure(threadCount, loaded::setThreadNum)
            return loaded
        }
    }
    private val current = ThreadLocal<Session?>()

    fun fingerprint(path: String): String = current.get()?.takeIf { it.path == path }?.fingerprint
        ?: DexDescriptorStore.fingerprint(java.io.File(path))

    fun <T> withSession(apkPath: String, threadCount: Int? = null, action: (() -> DexKitBridge) -> T): T {
        val previous = current.get()
        if (previous?.path == apkPath) return action { previous.open(threadCount) }
        val session = Session(apkPath)
        current.set(session)
        return try { action { session.open(threadCount) } } finally {
            try { session.bridge?.close() } finally {
                if (previous == null) current.remove() else current.set(previous)
            }
            if (session.bridge != null) com.mi.onextbox.diagnostics.RuntimeDiagnostics.event("dex", "DexKit session",
                "Shared APK queries and session cleanup", android.os.SystemClock.elapsedRealtime() - session.started)
        }
    }
}
