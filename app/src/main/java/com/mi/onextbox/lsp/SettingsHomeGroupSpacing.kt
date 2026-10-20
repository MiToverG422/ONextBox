package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernReflect

/** Share the host's first-group padding decision between entry insertion and account hiding. */
internal object SettingsHomeGroupSpacing {
    const val ENTRY_KEY = "onextbox_home_entry_group"
    private val keys = setOf("user_center_category", ENTRY_KEY, "connect_settings_category")
    private const val MESSAGES_KEY = "color_message_preference"

    data class Group(val key: String, val order: Int, val visible: Boolean)

    fun firstGroup(groups: List<Group>, hasMessages: Boolean): String? =
        if (hasMessages) null else groups.filter { it.visible }.minByOrNull { it.order }?.key

    fun apply(home: Any) {
        if (HotReloadLifecycle.isRetiring) return
        val screen = call(home, "getPreferenceScreen") ?: return
        if (call(screen, "findPreference", ENTRY_KEY) == null &&
            !SettingsHomeCleanupSettings.enabledXposed(SettingsHomeCleanupSettings.ACCOUNT)) return
        val count = call(screen, "getPreferenceCount") as Int
        // Iterate the actual native siblings; insertion positions and hidden groups matter.
        val groups = (0 until count).mapNotNull { index ->
            val preference = call(screen, "getPreference", index) ?: return@mapNotNull null
            val key = call(preference, "getKey") as? String ?: return@mapNotNull null
            if (key !in keys) return@mapNotNull null
            preference to Group(key, call(preference, "getOrder") as Int, call(preference, "isVisible") == true)
        }
        val messages = call(screen, "findPreference", MESSAGES_KEY)
        // Use the displayed banner, not the controller's retained unfiltered message data.
        val hasMessages = messages != null && call(messages, "isVisible") == true
        val first = firstGroup(groups.map { it.second }, hasMessages)
        groups.forEach { (preference, group) ->
            // HomepageTopCategory supplies its own density/theme-aware native padding.
            call(preference, "setIsFirstGroup", group.visible && group.key == first)
        }
    }

    private fun call(owner: Any, name: String, vararg args: Any?) = ModernReflect.callMethod(owner, name, *args)
}
