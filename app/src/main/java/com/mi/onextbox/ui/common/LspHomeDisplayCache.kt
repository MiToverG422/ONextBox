package com.mi.onextbox.ui.common

import android.content.Context

internal object LspHomeDisplayCache {
    private const val PREFERENCES_NAME = "lsp_home_display_cache"
    private const val KEY_SCHEMA = "schema"
    private const val KEY_STATUS = "status"
    private const val KEY_VERSION = "version"
    private const val KEY_MISSING_SCOPES = "missing_scopes"

    fun read(context: Context): LspHomeDisplay? = runCatching {
        val values = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).all
        val scopes = values[KEY_MISSING_SCOPES] as? Set<*> ?: return@runCatching null
        if (scopes.size > LspHomeDisplayCodec.MAX_SCOPES || scopes.any { it !is String }) {
            return@runCatching null
        }
        val version = values[KEY_VERSION]
        if (version != null && version !is String) return@runCatching null
        LspHomeDisplayCodec.decode(
            schema = values[KEY_SCHEMA] as? Int ?: -1,
            statusName = values[KEY_STATUS] as? String,
            version = version as? String,
            missingScopes = scopes.map { it as String }.toSet(),
        )
    }.getOrNull()

    fun write(context: Context, display: LspHomeDisplay) {
        val validated = LspHomeDisplayCodec.decode(
            schema = LspHomeDisplayCodec.SCHEMA_VERSION,
            statusName = display.status.name,
            version = display.frameworkVersionText,
            missingScopes = display.missingScopes,
        ) ?: return
        runCatching {
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_SCHEMA, LspHomeDisplayCodec.SCHEMA_VERSION)
                .putString(KEY_STATUS, validated.status.name)
                .putString(KEY_VERSION, validated.frameworkVersionText)
                .putStringSet(KEY_MISSING_SCOPES, validated.missingScopes)
                .apply()
        }
    }
}
