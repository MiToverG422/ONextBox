package com.mi.onextbox.lsp

/** Never edit the OEM's retained bean; only the outgoing configuration is overridden. */
internal class SettingsFixedRefreshIdleConfig(type: Class<*>) {
    private val copy = type.getDeclaredConstructor(type).apply { isAccessible = true }
    private val switches = listOf("setEnable", "setHwEnable", "setSwEnable", "setAdfrEnable").map {
        type.getDeclaredMethod(it, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
    }

    fun outgoing(original: Any, blockIdle: Boolean): Any {
        if (!blockIdle) return original
        return copy.newInstance(original).also { clone -> switches.forEach { it.invoke(clone, false) } }
    }
}
