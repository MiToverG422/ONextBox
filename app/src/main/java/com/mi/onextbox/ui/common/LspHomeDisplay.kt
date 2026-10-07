package com.mi.onextbox.ui.common

import com.mi.onextbox.lsp.LspStatus
import java.util.Collections

internal data class LspHomeDisplay(
    val status: LspStatus,
    val frameworkVersionText: String? = null,
    val missingScopes: Set<String> = emptySet(),
)

internal object LspHomeDisplayCodec {
    const val SCHEMA_VERSION = 1
    const val MAX_VERSION_LENGTH = 256
    const val MAX_SCOPE_LENGTH = 256
    const val MAX_SCOPES = 128
    private val scopeName = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*")

    fun decode(
        schema: Int,
        statusName: String?,
        version: String?,
        missingScopes: Set<String>,
    ): LspHomeDisplay? {
        if (schema != SCHEMA_VERSION || statusName == null || statusName.length > 32) return null
        val status = runCatching { LspStatus.valueOf(statusName) }.getOrNull() ?: return null
        if (status == LspStatus.CHECKING) return null
        if (version != null &&
            (version.length > MAX_VERSION_LENGTH || version.any(Char::isISOControl))) return null
        if (missingScopes.size > MAX_SCOPES || missingScopes.any {
                it.length > MAX_SCOPE_LENGTH || !scopeName.matches(it)
            }) return null
        return LspHomeDisplay(
            status = status,
            frameworkVersionText = version?.trim()?.takeIf(String::isNotEmpty),
            missingScopes = Collections.unmodifiableSet(LinkedHashSet(missingScopes)),
        )
    }
}
