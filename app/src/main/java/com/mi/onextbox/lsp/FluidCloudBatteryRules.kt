package com.mi.onextbox.lsp

internal object FluidCloudBatteryRules {
    private const val PERCENT_OUTSIDE = 2
    private const val COMPACT_STYLE = 1
    private const val NORMAL_STYLE = 0

    fun keepOutsidePercentage(enabled: Boolean, percentStyle: Int?, iconStyle: Int?): Boolean =
        enabled && percentStyle == PERCENT_OUTSIDE && when (iconStyle) {
            0, 1, 3, 4, 5 -> true
            else -> false
        }

    fun forceShowStyle(requested: Int, enabled: Boolean, percentStyle: Int?, iconStyle: Int?): Int =
        if (requested == COMPACT_STYLE && keepOutsidePercentage(enabled, percentStyle, iconStyle)) {
            NORMAL_STYLE
        } else {
            requested
        }

    fun capsuleShowing(original: Boolean, enabled: Boolean, percentStyle: Int?, iconStyle: Int?): Boolean =
        original && !keepOutsidePercentage(enabled, percentStyle, iconStyle)
}
