package com.mi.onextbox.lsp

internal object NativeFiveGaPhonePolicy {
    const val FEATURE = "oplus.software.radio.icon_nrad_5ga"
    const val THRESHOLDS = "carrier_oplus_5ga_cell_count_bandwidth_string_array"
    const val SMOOTHING = "carrier_oplus_5ga_smooth_timeout_th"
    private val profiles = setOf("1001_CMCC", "1001S1DT_CMCC", "1002_CU", "1003_CT", "1004_CBN")

    fun supportedProfile(profile: String) = profile in profiles

    fun configReady(applied: Boolean, thresholds: Array<String>?): Boolean = applied &&
        !thresholds.isNullOrEmpty() && thresholds.all { row ->
            val parts = row.split(',')
            parts.size >= 2 && (parts[0].trim().toIntOrNull() ?: 0) > 0 &&
                (parts[1].trim().toIntOrNull() ?: 0) > 0
        }

    fun mayRestore(enabled: Boolean, blocked: Boolean, ready: Boolean, owned: Boolean) =
        enabled && !blocked && ready && owned

    fun certified(
        live: Boolean, fresh: Boolean, nativeBranch: Boolean, nativeState: Boolean,
        network: Int, override: Int,
    ) = live && fresh && nativeBranch && nativeState && network == 20 && override == 5
}
