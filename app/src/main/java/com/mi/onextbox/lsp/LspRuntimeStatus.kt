package com.mi.onextbox.lsp

object LspRuntimeStatus {
    private const val PROP_SCOPE_SYSTEM = "debug.oost.lsp.scope.system"
    private const val PROP_SCOPE_SYSTEMUI = "debug.oost.lsp.scope.systemui"
    private val legacySystemKeys = listOf("oost.lsp.scope.system")
    private val legacySystemUiKeys = listOf("oost.lsp.scope.systemui")

    fun markSystemScopeActive() {
        setSystemProperty(PROP_SCOPE_SYSTEM, "1")
    }

    fun markSystemUiScopeActive() {
        setSystemProperty(PROP_SCOPE_SYSTEMUI, "1")
    }

    fun isSystemScopeActive(): Boolean = readPropertyAsBoolean(PROP_SCOPE_SYSTEM) ||
        legacySystemKeys.any { readPropertyAsBoolean(it) }

    fun isSystemUiScopeActive(): Boolean = readPropertyAsBoolean(PROP_SCOPE_SYSTEMUI) ||
        legacySystemUiKeys.any { readPropertyAsBoolean(it) }

    fun hasRequiredScopes(): Boolean = isSystemScopeActive() && isSystemUiScopeActive()

    private fun readPropertyAsBoolean(key: String): Boolean {
        return when (readSystemProperty(key).trim().lowercase()) {
            "1", "true", "on", "enabled" -> true
            else -> false
        }
    }

    private fun readSystemProperty(key: String): String {
        return runCatching {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val getMethod = systemProperties.getMethod("get", String::class.java, String::class.java)
            (getMethod.invoke(null, key, "") as String)
        }.getOrDefault("")
    }

    private fun setSystemProperty(key: String, value: String) {
        runCatching {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val setMethod = systemProperties.getMethod("set", String::class.java, String::class.java)
            setMethod.invoke(null, key, value)
        }
    }
}
