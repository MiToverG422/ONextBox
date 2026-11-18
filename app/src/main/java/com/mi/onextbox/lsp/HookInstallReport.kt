package com.mi.onextbox.lsp

/** Installation diagnostics count actual handles, including shaded/optional targets that miss. */
internal class HookInstallReport {
    var handles: Int = 0
        private set
    var matchedGroups: Int = 0
        private set
    private val unmatched = linkedSetOf<String>()
    val missingTargets: Set<String> get() = unmatched.toSet()
    val status: String get() = when {
        handles == 0 -> "unmatched"
        unmatched.isEmpty() -> "matched"
        else -> "partial"
    }

    fun record(target: String, actualHandles: Int) {
        require(actualHandles >= 0)
        if (actualHandles == 0) unmatched += target
        else { handles += actualHandles; matchedGroups++ }
    }
    fun missing(target: String) { unmatched += target }
    fun summary() = "status=$status; methods=$handles; matched_groups=$matchedGroups; unmatched_candidates=${unmatched.size}" +
        if (unmatched.isEmpty()) "" else "; ${unmatched.joinToString()}"
}
