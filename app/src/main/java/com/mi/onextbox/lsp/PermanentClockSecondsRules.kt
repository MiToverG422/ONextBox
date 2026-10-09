package com.mi.onextbox.lsp

/** Keep the user's native clock choice; remove only the temporary display restrictions. */
internal object PermanentClockSecondsRules {
    const val MODE_OFF = 0
    const val MODE_SECONDS = 1
    private val durationSuffix = Regex("\\s*[（(][^（）()]*[）)]\\s*$")

    fun effectiveDeadline(enabled: Boolean, original: Long): Long = if (enabled) 0L else original

    fun showSeconds(enabled: Boolean, mode: Int, legacy: Boolean, original: Boolean): Boolean =
        if (enabled) mode == MODE_SECONDS || mode == MODE_OFF && legacy else original

    fun checkedItem(mode: Int): Int = if (mode == MODE_SECONDS) 1 else 0

    fun hideSeconds(enabled: Boolean, original: Boolean): Boolean = original && !enabled

    fun optionLabel(original: String): String = original.replace(durationSuffix, "").trimEnd()
}
