package com.mi.onextbox.lsp

import android.content.Context
import android.content.SharedPreferences
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.ModernRemotePreferences

/** Settings shared with the Google Messages process through the API 102 preference bridge. */
internal object GoogleMessagesConfig {
    private const val GROUP = "lsp_features"
    private const val KEY_GEMINI = "google_messages_gemini"
    private const val KEY_COPY_OTP = "google_messages_copy_otp"
    private val remote by lazy(LazyThreadSafetyMode.PUBLICATION) { ModernRemotePreferences(GROUP) }

    enum class Switch(val key: String) {
        Gemini(KEY_GEMINI),
        CopyOtp(KEY_COPY_OTP),
    }

    fun isEnabled(context: Context, feature: Switch): Boolean =
        preferences(context).getBoolean(feature.key, false)

    fun setEnabled(context: Context, feature: Switch, enabled: Boolean) {
        preferences(context).edit().putBoolean(feature.key, enabled).commit()
    }

    fun isEnabledInHook(feature: Switch): Boolean =
        HookConfigSnapshot.boolean(feature.key, false)
            ?: remote.getBoolean(feature.key, false)

    /** Migration tombstones only: no retired feature readers, writers or hooks remain. */
    fun removeRetiredPreferences(preferences: SharedPreferences) {
        val retired = listOf("google_messages_domestic_rcs", "google_messages_keyword_spam",
            "google_messages_spam_keywords", "google_messages_sender_identities")
        if (retired.none(preferences::contains)) return
        val editor = preferences.edit()
        retired.forEach(editor::remove)
        check(editor.commit()) { "Unable to remove retired Google Messages preferences" }
    }

    private fun preferences(context: Context) = context
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(GROUP, Context.MODE_PRIVATE)
}
