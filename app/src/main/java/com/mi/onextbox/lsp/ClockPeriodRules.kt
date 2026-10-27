package com.mi.onextbox.lsp

internal object ClockPeriodRules {
    fun normalizeFormat(format: Int) = if (format == 0) 0 else 1
    fun periodPattern(format: Int) = if (format == 0) "a" else "BBBB"

    fun timePattern(native: String): String {
        val out = StringBuilder()
        var quoted = false
        var index = 0
        while (index < native.length) {
            val c = native[index++]
            if (c == '\'') {
                out.append(c)
                if (index < native.length && native[index] == '\'') out.append(native[index++])
                else quoted = !quoted
            } else if (c != '\uef00' && c != '\uef01' && (quoted || c !in "abB")) out.append(c)
        }
        return out.toString().trim { it.isWhitespace() || it == '\u202f' || it == '\u00a0' }
    }
}
