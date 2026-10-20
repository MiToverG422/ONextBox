package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object SystemAdaptationSettings {
    const val KEY = "system_adaptation_version"
    const val ALLOW_HYPER_OS = "developer_allow_hyperos3"
    private const val SYSTEM_VERSION_PROPERTY = "ro.build.version.oplusrom"

    fun hyperOsAllowed(context: Context): Boolean =
        LspPreferenceStore.prefs(context).all[ALLOW_HYPER_OS] as? Boolean ?: false

    fun selectedVersion(context: Context): SystemAdaptationVersion {
        val values = LspPreferenceStore.prefs(context).all
        return SystemAdaptationVersion.resolve(
            values[KEY] as? Int,
            LspPreferenceStore.readSystemPropertyValue(SYSTEM_VERSION_PROPERTY),
            values[ALLOW_HYPER_OS] as? Boolean ?: false,
        )
    }

    @Synchronized
    fun setVersion(context: Context, version: SystemAdaptationVersion) {
        require(version.selectable) { "This system adaptation version is not available" }
        require(version != SystemAdaptationVersion.HyperOs3 || hyperOsAllowed(context))
        check(LspPreferenceStore.prefs(context).edit().putInt(KEY, version.value).commit())
        ModernXposedPreferenceSync.syncNow(context)
    }

    @Synchronized
    fun setHyperOsAllowed(context: Context, allowed: Boolean) {
        val prefs = LspPreferenceStore.prefs(context)
        val previouslyAllowed = prefs.all[ALLOW_HYPER_OS] as? Boolean ?: false
        val edit = prefs.edit().putBoolean(ALLOW_HYPER_OS, allowed)
        if (!allowed || !previouslyAllowed) edit.putInt(KEY, SystemAdaptationVersion.ColorOs17.value)
        check(edit.commit())
        ModernXposedPreferenceSync.syncNow(context)
    }

    fun selectedVersionXposed(): SystemAdaptationVersion = SystemAdaptationVersion.resolve(
        HookConfigSnapshot.int(KEY, -1),
        LspPreferenceStore.readSystemPropertyValue(SYSTEM_VERSION_PROPERTY),
        HookConfigSnapshot.boolean(ALLOW_HYPER_OS, false) == true,
    )
}
