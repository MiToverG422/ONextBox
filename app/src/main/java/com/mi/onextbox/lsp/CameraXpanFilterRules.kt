package com.mi.onextbox.lsp

internal object CameraXpanFilterRules {
    const val LEGACY_MONO = "Delta400.3dl.rgb.bin"
    private val monochrome = listOf(LEGACY_MONO, "blackandwhite_HC_master.bin", "blackandwhite_HC_normal.bin",
        "blackandwhite.cube.rgb.bin", "tx400_normal.bin", "gr.bw.rgba.bin")

    fun resolve(requested: String?, available: List<String>): String {
        if (requested in available) return requested!!
        if (requested == LEGACY_MONO) monochrome.firstOrNull(available::contains)?.let { return it }
        return "default".takeIf(available::contains) ?: available.firstOrNull() ?: "default"
    }

    fun selected(index: Int, available: List<String>, legacy: Boolean): String =
        available.getOrNull(index) ?: resolve(if (index == -1 && legacy) LEGACY_MONO else "default", available)

    fun canApply(lensAlgorithms: Array<*>?, apsVersion: Int, capturing: Boolean): Boolean =
        apsVersion < 5 || (lensAlgorithms?.contains("aps_algo_filter") ?: !capturing)

    fun hasValidEntries(types: List<*>?, names: List<*>?): Boolean =
        types != null && names != null && types.size == names.size &&
            types.all { it is String && it.isNotEmpty() } && names.all { it is Int && it > 0 }
}
