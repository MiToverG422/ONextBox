package com.mi.onextbox.lsp

import org.luckypray.dexkit.DexKitBridge

internal object ApkDexSession {
    fun <T> withSession(apkPath: String, action: (() -> DexKitBridge) -> T): T {
        var bridge: DexKitBridge? = null
        fun open(): DexKitBridge {
            bridge?.let { return it }
            System.loadLibrary("dexkit")
            return DexKitBridge.create(apkPath).also { bridge = it }
        }
        return try { action(::open) } finally { bridge?.close() }
    }
}
