package com.mi.onextbox.touch

/** Resolves the panel without trying arbitrary HAL indexes. */
internal object TouchSamplingPolicy {
    private const val LEGACY_CONFIG = "/data/vendor/touchconfig/vnd_custom_config_main.xml"
    private const val DEFAULT_SERVICE = "vendor.oplus.hardware.touch.IOplusTouch/default"

    fun panelIndex(
        indexes: List<Int>,
        profiles: List<TouchConfigProfile>,
        services: List<String>,
    ): Int? {
        val observed = indexes.distinct()
        if (observed.size > 1) return null
        observed.singleOrNull()?.let { return it.takeIf { panel -> panel in 0..1 } }
        val known = profiles.filter { it.node == 182 }
        val scoped = known.mapNotNull { it.panelIndex }.distinct()
        if (scoped.isNotEmpty()) return scoped.singleOrNull()?.takeIf { it in 0..1 }
        // Existing main-panel configuration uses the known default/panel-0 contract.
        return 0.takeIf {
            services == listOf(DEFAULT_SERVICE) && known.any {
                it.panelIndex == null && it.node == 182 && it.sourceLabel == LEGACY_CONFIG
            }
        }
    }

    fun matches(profile: TouchConfigProfile, mode: Pair<Int, Int>?): Boolean = when {
        mode == null -> false
        mode.first == 0 -> mode.second == profile.defaultChipValue
        else -> profile.presets.any { it.index == mode.first && it.chipValue == mode.second }
    }

    fun bindingMatches(actual: String?, expected: String?): Boolean =
        actual != null && expected != null && actual == expected
}
