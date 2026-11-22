package com.mi.onextbox.lsp

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.security.MessageDigest

internal object CameraCompositionSupport {
    private const val CONFIG = "/odm/etc/camera/ai_composition/composition_lite2_config.yaml"
    private const val BRIDGE = "/product/lib64/libApsAICompositionJni.so"
    private const val LITE2_BRIDGE = "ee2ec4836f3aff8d57123c1d04e3c089661697aa88fb84c776a16853b6746357"

    // Only identify a missing model for the inspected bridge. Other releases can use other engines.
    val missingSceneEngine: Boolean by lazy {
        val missing = try {
            Os.stat(CONFIG)
            false
        } catch (error: ErrnoException) {
            error.errno == OsConstants.ENOENT || error.errno == OsConstants.ENOTDIR
        }
        missing && runCatching {
            val bridge = File(BRIDGE)
            bridge.length() == 358256L && MessageDigest.getInstance("SHA-256")
                .digest(bridge.readBytes()).toHexString() == LITE2_BRIDGE
        }.getOrDefault(false)
    }
}
