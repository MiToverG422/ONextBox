package com.mi.onextbox.lsp

internal enum class LspDbSchema(val table: String, val perUser: Boolean) {
    MODERN("modules_state", true), LEGACY_USER("modules", true), LEGACY_OWNER("modules", false),
}

internal data class LspDbConfiguration(
    val moduleState: LspModuleState = LspModuleState.UNKNOWN,
    val scopes: Set<String>? = null,
    val unavailableReason: String = "config_unavailable",
)

/** Supported configuration database layouts. */
internal object LspDbRules {
    fun schema(tables: Map<String, Set<String>>, userId: Int): LspDbSchema? {
        val modern = tables["modules_state"]
        if (modern != null) {
            return LspDbSchema.MODERN.takeIf {
                modern.containsAll(setOf("module_pkg_name", "user_id", "enabled"))
            }
        }
        val legacy = tables["modules"] ?: return null
        if (!legacy.containsAll(setOf("module_pkg_name", "enabled"))) return null
        return when {
            "user_id" in legacy -> LspDbSchema.LEGACY_USER
            userId == 0 -> LspDbSchema.LEGACY_OWNER
            else -> null
        }
    }

    fun moduleState(rows: List<String?>): LspModuleState = when {
        rows.isEmpty() -> LspModuleState.DISABLED
        rows.size != 1 -> LspModuleState.UNKNOWN
        else -> when (rows.single()?.trim()?.lowercase()) {
            "1", "true" -> LspModuleState.ENABLED
            "0", "false" -> LspModuleState.DISABLED
            else -> LspModuleState.UNKNOWN
        }
    }

    fun query(schema: LspDbSchema): String =
        "SELECT enabled FROM ${schema.table} WHERE module_pkg_name = ?" +
            (if (schema.perUser) " AND user_id = ?" else "") + " LIMIT 2"

    fun scopeQuery(tables: Map<String, Set<String>>): String? {
        val scope = tables["scope"] ?: return null
        if (!scope.containsAll(setOf("app_pkg_name", "user_id"))) return null
        if ("module_pkg_name" in scope) {
            return "SELECT DISTINCT app_pkg_name FROM scope WHERE module_pkg_name = ?" +
                " AND (user_id = ? OR app_pkg_name = 'system')"
        }
        val modules = tables["modules"] ?: return null
        if ("mid" !in scope || !modules.containsAll(setOf("mid", "module_pkg_name"))) return null
        return "SELECT DISTINCT scope.app_pkg_name FROM scope" +
            " INNER JOIN modules ON scope.mid = modules.mid WHERE modules.module_pkg_name = ?" +
            " AND (scope.user_id = ? OR scope.app_pkg_name = 'system')"
    }

    fun scopes(rows: List<String?>): Set<String>? {
        if (rows.any { it.isNullOrBlank() }) return null
        return rows.map { it!!.trim() }.toSet()
    }
}
