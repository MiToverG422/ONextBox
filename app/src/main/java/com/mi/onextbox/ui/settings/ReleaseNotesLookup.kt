package com.mi.onextbox.ui.settings

internal data class ReleaseNotesEntry(
    val tag: String,
    val name: String,
    val assetNames: List<String>,
    val body: String?,
    val draft: Boolean = false,
) {
    fun matches(version: String): Boolean {
        if (draft) return false
        val normalized = AppUpdateRules.versionNameRegex.find(version)?.value ?: return false
        if (tag.equals(AppUpdateRules.CI_RELEASE_TAG, ignoreCase = true)) {
            return assetNames.any { asset ->
                AppUpdateRules.releaseVersionName(tag, name, asset).equals(normalized, ignoreCase = true)
            }
        }
        val identities = listOf(tag, name) + assetNames
        if (identities.any { AppUpdateRules.isLegacyBeta(it) || AppUpdateRules.isCiVersion(it) }) return false
        val candidate = identities.mapNotNull { AppUpdateRules.versionNameRegex.find(it)?.value }
            .firstOrNull() ?: return false
        return candidate.equals(normalized, ignoreCase = true)
    }
}

internal object ReleaseNotesLookup {
    fun find(
        version: String,
        byTag: (repository: String, tag: String) -> ReleaseNotesEntry?,
        page: (repository: String, page: Int) -> List<ReleaseNotesEntry>,
    ): ReleaseNotesEntry? {
        val repository = AppUpdateRules.MAIN_REPOSITORY
        if (AppUpdateRules.isCiVersion(version)) {
            return byTag(repository, AppUpdateRules.CI_RELEASE_TAG)?.takeIf { it.matches(version) }
        }
        val normalized = version.trim().removePrefix("v").removePrefix("V")
        for (tag in listOf("V$normalized", "v$normalized", normalized).distinct()) {
            val release = byTag(repository, tag)
            if (release?.matches(version) == true) return release
        }
        for (number in 1..3) {
            val releases = page(repository, number)
            releases.firstOrNull { it.matches(version) }?.let { return it }
            if (releases.size < 100) break
        }
        return null
    }
}
