package com.mi.onextbox.lsp

import java.lang.reflect.Field

internal object SettingsHomeCleanupRules {
    const val ACCOUNT_CATEGORY = "user_center_category"
    val accountKeys = setOf("member_sign", "usercenter_preference", "usercenter_guide")

    fun isUpdateReminder(owner: String?, target: String?, entrance: String?): Boolean =
        owner in otaPackages || target in otaPackages || entrance == "ota_settings"

    private val otaPackages = setOf("com.oplus.ota", "com.coloros.ota")

    fun shouldHideAccountCategory(children: List<Pair<String?, Boolean>>): Boolean =
        children.none { (key, visible) -> key !in accountKeys && visible }

    fun <T> filterUpdates(entries: List<T>, isUpdate: (T) -> Boolean): List<T> {
        val filtered = entries.filterNot(isUpdate)
        // Preserve native identity when nothing is hidden, and never mutate its original list.
        return if (filtered.size == entries.size) entries else ArrayList(filtered)
    }

    fun <T> withDisplayMessages(owner: Any, field: Field, displayed: List<*>, action: () -> T): T {
        val original = field.get(owner)
        if (original === displayed) return action()
        field.set(owner, displayed)
        return try { action() } finally {
            // A genuine native replacement wins over the old snapshot.
            if (field.get(owner) === displayed) field.set(owner, original)
        }
    }
}

/** Remember native visibility changes while our UI-only override is active. */
internal class SettingsHomeVisibility(@Volatile var nativeVisible: Boolean) {
    fun requested(visible: Boolean, hidden: Boolean): Boolean {
        nativeVisible = visible
        return displayed(hidden)
    }

    fun displayed(hidden: Boolean): Boolean = nativeVisible && !hidden
}
