package com.mi.onextbox.lsp

enum class LspModuleState { UNKNOWN, ENABLED, DISABLED }

enum class LspStatus {
    CHECKING, UNKNOWN, DISABLED, WAITING_CONNECTION, MISSING_SCOPE,
    WAITING_RESTART, API_UNSUPPORTED, READY,
}

internal data class LspDetectionResult(
    val moduleState: LspModuleState,
    val status: LspStatus,
    val serviceConnected: Boolean,
    val scopes: Set<String>?,
    val missingScopes: Set<String>,
    val frameworkVersionText: String?,
    val source: String,
    val reason: String,
)

/** Current configuration and live framework evidence. */
internal object LspDetectionPolicy {
    fun evaluate(
        moduleState: LspModuleState,
        framework: LspFrameworkSnapshot?,
        userId: Int,
        configuredScopes: Set<String>?,
        requiredScopes: Set<String> = emptySet(),
    ): LspDetectionResult {
        val scopes = configuredScopes?.map(String::trim)?.filter(String::isNotEmpty)?.toSet()
        val missing = if (scopes == null) emptySet() else requiredScopes - scopes
        fun result(status: LspStatus, reason: String) = LspDetectionResult(
            moduleState, status, framework != null, scopes, missing, framework?.frameworkVersionText,
            if (framework == null) "config_db" else "config_db+framework_service", reason,
        )
        if (moduleState == LspModuleState.DISABLED) return result(LspStatus.DISABLED, "config_disabled")
        if (framework != null && framework.apiVersion < 102) return result(LspStatus.API_UNSUPPORTED, "api_unsupported")
        if (moduleState == LspModuleState.UNKNOWN) return result(LspStatus.UNKNOWN, "config_unavailable")
        if (scopes == null) return result(LspStatus.UNKNOWN, "scope_unavailable")
        if (scopes.isEmpty()) return result(LspStatus.MISSING_SCOPE, "scope_empty")
        if (missing.isNotEmpty()) return result(LspStatus.MISSING_SCOPE, "scope_incomplete")
        if (framework == null) return result(LspStatus.WAITING_CONNECTION, "service_unavailable")
        val running = framework.runningTargets ?: return result(LspStatus.UNKNOWN, "targets_unavailable")
        val scoped = running.filter { target ->
            target.uid >= 0 && (if (isSystem(target.processName)) target.uid == 1000
                else target.uid < 10_000 || target.uid / 100_000 == userId) &&
                if (isSystem(target.processName)) "system" in scopes
                else target.processName.substringBefore(':') in scopes
        }
        if (scoped.isEmpty()) return result(LspStatus.WAITING_RESTART, "targets_not_loaded")
        if ("system" in scopes && scoped.none { isSystem(it.processName) }) {
            return result(LspStatus.WAITING_RESTART, "system_not_loaded")
        }
        if ("com.android.systemui" in scopes && scoped.none { it.processName == "com.android.systemui" }) {
            return result(LspStatus.WAITING_RESTART, "systemui_not_loaded")
        }
        if (scoped.any { !it.current }) {
            return result(LspStatus.WAITING_RESTART, "target_stale")
        }
        return result(LspStatus.READY, "ready")
    }

    private fun isSystem(process: String) = process == "system" || process == "system_server"
}
