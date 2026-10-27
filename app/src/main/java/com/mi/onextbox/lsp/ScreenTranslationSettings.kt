package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object ScreenTranslationSettings {
    const val PACKAGE_NAME = "com.coloros.translate"
    const val ALLOW_FLOATING = "translation_allow_floating_screen"
    const val BYPASS_APP_BLACKLIST = "translation_bypass_app_blacklist"
    const val ALLOW_RESTRICTED_PAGES = "translation_allow_restricted_pages"
    const val ALLOW_LANDSCAPE = "translation_allow_landscape"
    const val UNLOCK_BROWSER = "translation_unlock_browser"
    val keys = linkedSetOf(ALLOW_FLOATING, BYPASS_APP_BLACKLIST, ALLOW_RESTRICTED_PAGES, ALLOW_LANDSCAPE, UNLOCK_BROWSER)

    fun enabled(context: Context, key: String = ALLOW_FLOATING) = LspPreferenceStore.prefs(context).getBoolean(key, false)
    fun state(context: Context): Map<String, Boolean> {
        val values = LspPreferenceStore.prefs(context).all
        return keys.associateWith { values[it] == true }
    }
    fun active(key: String = ALLOW_FLOATING) = HookConfigSnapshot.boolean(key, false) == true
    fun set(context: Context, enabled: Boolean) =
        set(context, ALLOW_FLOATING, enabled)
    fun set(context: Context, key: String, enabled: Boolean) {
        require(key in keys)
        ModernXposedPreferenceSync.setBooleanAndSync(context, key, enabled)
    }
}

internal object ScreenTranslationRules {
    fun isFloatingDetector(result: String, parameters: List<String>, isStatic: Boolean): Boolean =
        result == "boolean" && parameters.isEmpty() && isStatic

    fun isLandscapeCaller(stack: List<StackTraceElement>, helper: Pair<String, String>, entries: Set<Pair<String, String>>): Boolean {
        val caller = stack.firstOrNull { frame ->
            frame.className.startsWith(ScreenTranslationSettings.PACKAGE_NAME + ".") &&
                (frame.className to frame.methodName) != helper
        } ?: return false
        return (caller.className to caller.methodName) in entries
    }

    fun browserClass(nativeClass: String?, version: Long, knownVersions: List<Long>?): String? {
        if (nativeClass.isNullOrBlank() || version <= 0) return nativeClass
        val lastVersion = knownVersions?.filter { it > 0 }?.maxOrNull() ?: return nativeClass
        // An empty class keeps the native extractor's automatic WebView discovery.
        return if (version > lastVersion) "" else nativeClass
    }
}
