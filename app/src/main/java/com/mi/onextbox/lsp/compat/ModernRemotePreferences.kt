package com.mi.onextbox.lsp.compat

/** Read-only preference facade used inside hooked processes. */
internal class ModernRemotePreferences(
    private val group: String,
) {
    fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        ModernHookRuntime.remotePreferences(group)?.getBoolean(key, defaultValue) ?: defaultValue

    fun getInt(key: String, defaultValue: Int): Int =
        ModernHookRuntime.remotePreferences(group)?.getInt(key, defaultValue) ?: defaultValue

    fun getFloat(key: String, defaultValue: Float): Float =
        ModernHookRuntime.remotePreferences(group)?.getFloat(key, defaultValue) ?: defaultValue

    fun getString(key: String, defaultValue: String?): String? =
        ModernHookRuntime.remotePreferences(group)?.getString(key, defaultValue) ?: defaultValue

    fun getStringSet(key: String, defaultValue: Set<String>): Set<String> =
        ModernHookRuntime.remotePreferences(group)?.getStringSet(key, defaultValue) ?: defaultValue
}
