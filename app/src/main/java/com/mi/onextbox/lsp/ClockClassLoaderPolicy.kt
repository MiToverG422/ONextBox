package com.mi.onextbox.lsp

internal object ClockClassLoaderPolicy {
    fun hostFirst(name: String): Boolean {
        if (name.contains("ExternalSynthetic")) return false
        return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("android.") ||
            name.startsWith("dalvik.") || name.startsWith("kotlin.jvm.functions.") || name == "kotlin.Unit" ||
            name == "kotlin.jvm.internal.DefaultConstructorMarker" || name.startsWith("androidx.compose.") ||
            name.startsWith("androidx.constraintlayout.widget.") || name.startsWith("com.android.systemui.plugins.") ||
            name.startsWith("com.android.systemui.log.") || name.startsWith("com.android.systemui.common.") ||
            name.startsWith("com.android.compose.animation.scene.")
    }
}
