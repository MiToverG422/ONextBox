package com.mi.onextbox.lsp

internal object BatteryCycleCountRules {
    fun parse(raw: String?): Int? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() && it.all { ch -> ch in '0'..'9' } }
            ?: return null
        return text.toIntOrNull()
    }

    // Android's health HAL and the Linux power-supply node report complete cycles.
    // Some OEM diagnostic services return a default zero despite these sources being populated.
    fun resolve(broadcast: Int?, kernel: () -> String?, diagnostic: () -> String?): Int? =
        broadcast?.takeIf { it >= 0 } ?: parse(kernel()) ?: parse(diagnostic())
}
