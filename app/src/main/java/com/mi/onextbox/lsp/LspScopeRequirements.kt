package com.mi.onextbox.lsp

/** Declared scopes that are available to the current user. */
internal object LspScopeRequirements {
    private val packageName = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")

    fun parse(text: String): Set<String> {
        val scopes = text.removePrefix("\uFEFF").lineSequence()
            .map(String::trim).filter { it.isNotEmpty() && !it.startsWith('#') }.toSet()
        require(scopes.isNotEmpty()) { "Empty scope metadata" }
        require(scopes.all(packageName::matches)) { "Invalid scope metadata" }
        return scopes
    }

    fun installed(recommended: Set<String>, isInstalled: (String) -> Boolean): Set<String> =
        recommended.filterTo(linkedSetOf()) { it == "system" || isInstalled(it) }
}
