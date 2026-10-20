package com.mi.onextbox.lsp

import android.content.Context
import android.provider.Settings
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object AppLockDelaySettings {
    const val PACKAGE = "com.oplus.safecenter"
    const val ENABLED = "app_lock_delay_enabled"
    const val NATIVE_MODE = "access_control_lock_mode"
    const val DELAY = "onextbox_app_lock_delay_ms"
    const val ROW = "key_app_lock_time"
    val durations = listOf(60_000L, 300_000L)
    fun token(delay: Long) = "onextbox_app_lock_after_$delay"
    fun fromToken(value: Any?): Long? = durations.firstOrNull { token(it) == value }
    fun valid(delay: Long) = delay.takeIf { it in durations } ?: 0L
    fun enabled(context: Context) = LspPreferenceStore.prefs(context).getBoolean(ENABLED, false)
    fun active() = HookConfigSnapshot.boolean(ENABLED, false) == true
    fun set(context: Context, enabled: Boolean) = ModernXposedPreferenceSync.setBooleanAndSync(context, ENABLED, enabled)
    fun selected(context: Context): Long = if (Settings.Secure.getInt(context.contentResolver, NATIVE_MODE, 1) == 0)
        valid(Settings.Secure.getLong(context.contentResolver, DELAY, 0L)) else 0L

    fun label(context: Context, delay: Long) = text(context, "app_lock_delay_after_minutes", "${delay / 60_000} min after leaving app", (delay / 60_000).toInt())
    fun saveError(context: Context) = text(context, "app_lock_delay_save_failed", "Could not save app lock timeout")
    private fun text(context: Context, name: String, fallback: String, vararg args: Any): String = runCatching {
        val module = context.createPackageContext("com.mi.onextbox", Context.CONTEXT_IGNORE_SECURITY)
            .createConfigurationContext(context.resources.configuration)
        val id = module.resources.getIdentifier(name, "string", "com.mi.onextbox")
        check(id != 0)
        module.getString(id, *args)
    }.getOrElse { fallback }
}
