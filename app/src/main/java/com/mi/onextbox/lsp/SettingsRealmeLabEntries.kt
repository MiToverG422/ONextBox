package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect

internal object SettingsRealmeLabEntries {
    const val GROUP = "onextbox_realme_lab_features"
    data class Entry(val feature: SettingsRealmeFeature, val key: String, val title: String, val fragment: String? = null)
    val entries = listOf(
        Entry(SettingsRealmeFeature.SleepCapsule, "lab_well_being", "string_well_being_title"),
        Entry(SettingsRealmeFeature.HeartRate, "heart_rate_detection", "heart_rate_detection"),
        Entry(SettingsRealmeFeature.DcDimming, "key_dc_backlight_in_mode", "settings_dc_backlight_mode_title_new_DC",
            "com.oplus.settings.feature.laboratory.RmDcDimmingFragment"),
        Entry(SettingsRealmeFeature.DualMusic, "lab_double_ear", "dual_earphone_setting_title",
            "com.oplus.settings.doubleears.DoubleEarphoneSettingFragment"),
    )

    fun requested(active: (SettingsRealmeFeature) -> Boolean, available: (SettingsRealmeFeature) -> Boolean) =
        entries.filter { active(it.feature) && available(it.feature) }

    fun refresh(loader: ClassLoader, fragment: Any, active: (SettingsRealmeFeature) -> Boolean) {
        val screen = Reflect.callMethod(fragment, "getPreferenceScreen") ?: return
        val context = Reflect.callMethod(fragment, "getContext") as? Context ?: return
        val requested = requested(active) { SettingsRealmeDependencies.available(context, it) }
        reconcile(screen, requested,
            createGroup = { Reflect.newInstance(Reflect.findClass("com.oplus.settings.widget.preference.SettingsPreferenceCategory", loader), context) },
            createRow = { Reflect.newInstance(Reflect.findClass("com.oplus.settings.widget.preference.SettingsSimpleJumpPreference", loader), context) },
            bind = { row, entry ->
                val title = context.resources.getIdentifier(entry.title, "string", "com.android.settings")
                check(title != 0) { "Missing native Laboratory title: ${entry.title}" }
                Reflect.callMethod(row, "setTitle", context.getString(title))
                Reflect.callMethod(row, "setPersistent", false)
                entry.fragment?.let { Reflect.callMethod(row, "setFragment", it) }
                SettingsRealmeDependencies.packageName(entry.feature)?.let {
                    Reflect.callMethod(row, "setIntent", checkNotNull(SettingsRealmeDependencies.launchIntent(context, entry.feature)))
                }
            },
        )
    }

    // Own only this category, never replace the host screen or remove stock rows.
    fun reconcile(screen: Any, requested: List<Entry>, createGroup: () -> Any, createRow: () -> Any, bind: (Any, Entry) -> Unit) {
        var group = Reflect.callMethod(screen, "findPreference", GROUP)
        if (requested.isEmpty()) { removeOwnedGroup(screen); return }
        if (group == null) {
            group = createGroup()
            Reflect.callMethod(group, "setKey", GROUP)
            Reflect.callMethod(group, "setOrder", 0)
            check(Reflect.callMethod(screen, "addPreference", group) == true)
        }
        for (entry in entries) {
            val ownRow = Reflect.callMethod(group, "findPreference", entry.key)
            if (entry !in requested) {
                if (ownRow != null) Reflect.callMethod(group, "removePreference", ownRow)
                continue
            }
            // A different system build may already provide this native row.
            if (ownRow == null && Reflect.callMethod(screen, "findPreference", entry.key) != null) continue
            val row = ownRow ?: createRow().also {
                Reflect.callMethod(it, "setKey", entry.key)
                Reflect.callMethod(it, "setOrder", entries.indexOf(entry))
                bind(it, entry)
                check(Reflect.callMethod(group, "addPreference", it) == true)
            }
            if (ownRow != null) bind(row, entry)
            Reflect.callMethod(row, "setVisible", true)
        }
        if (Reflect.callMethod(group, "getPreferenceCount") == 0) removeOwnedGroup(screen)
    }

    fun removeOwnedGroup(screen: Any?) {
        if (screen == null) return
        Reflect.callMethod(screen, "findPreference", GROUP)?.let { Reflect.callMethod(screen, "removePreference", it) }
    }
}
