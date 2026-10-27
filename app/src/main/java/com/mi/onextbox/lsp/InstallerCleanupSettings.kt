package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object InstallerCleanupSettings {
    const val APP_DETAIL = "com.oplus.appdetail"
    const val PACKAGE_INSTALLER = "com.android.packageinstaller"
    const val SKIP_SCAN = "installer_skip_detection"
    const val REMOVE_ADS = "installer_remove_ads"
    const val NATIVE_UI = "installer_use_native_ui"
    const val APP_SKIP_SCAN = "appdetail_skip_detection"
    const val APP_REMOVE_ADS = "appdetail_remove_ads"
    const val APP_HIDE_COLLECTION_NOTICE = "appdetail_hide_collection_notice"
    const val APP_BLOCK_NETWORK = "appdetail_block_network"
    val packages = listOf(APP_DETAIL, PACKAGE_INSTALLER)
    val appKeys = linkedSetOf(APP_SKIP_SCAN, APP_REMOVE_ADS, APP_HIDE_COLLECTION_NOTICE, APP_BLOCK_NETWORK)
    val packageKeys = setOf(NATIVE_UI)
    val keys = (appKeys + packageKeys).toSet()
    val legacyKeys = setOf(SKIP_SCAN, REMOVE_ADS)
    private val legacy = mapOf(APP_SKIP_SCAN to SKIP_SCAN, APP_REMOVE_ADS to REMOVE_ADS)

    fun inherited(key: String, read: (String, Boolean) -> Boolean?): Boolean {
        val fallback = legacy[key]?.let { read(it, false) == true } ?: false
        return read(key, fallback) == true
    }
    fun enabled(context: Context, key: String): Boolean {
        val prefs = LspPreferenceStore.prefs(context)
        return inherited(key) { name, fallback -> prefs.getBoolean(name, fallback) }
    }
    fun active(key: String) = inherited(key, HookConfigSnapshot::boolean)
    fun state(context: Context): Map<String, Boolean> {
        val values = LspPreferenceStore.prefs(context).all
        return keys.associateWith { key -> inherited(key) { name, fallback -> values[name] as? Boolean ?: fallback } }
    }
    fun set(context: Context, key: String, enabled: Boolean) {
        require(key in keys || key in legacyKeys)
        ModernXposedPreferenceSync.setBooleanAndSync(context, key, enabled)
    }

    fun reset(context: Context) = (keys + legacyKeys).forEach { set(context, it, false) }
}

internal object InstallerCleanupRules {
    fun noticeCaller(stack: List<StackTraceElement>, factory: Pair<String, String>): Boolean =
        stack.any { it.className == factory.first && it.methodName == factory.second }
    fun legacyFile(scheme: String?, enabled: Boolean) = enabled && scheme == "file"
    fun routeCaller(stack: List<StackTraceElement>, factory: Pair<String, String>): Boolean =
        stack.firstOrNull { it.className.startsWith(InstallerCleanupSettings.APP_DETAIL + ".") }
            ?.let { it.className to it.methodName } == factory
    fun maySkip(children: Boolean, minors: Boolean) = !children && !minors
}
