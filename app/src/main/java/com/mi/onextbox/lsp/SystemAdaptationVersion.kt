package com.mi.onextbox.lsp

internal enum class SystemAdaptationVersion(
    val value: Int,
    val label: String,
    val selectable: Boolean,
) {
    ColorOs17(0, "ColorOS 17", true),
    ColorOs16(1, "ColorOS 16", false),
    HyperOs3(4, "HyperOS 3", true);

    val hasColorOs17Pages: Boolean get() = this == ColorOs17

    companion object {
        private val versionPattern = Regex("""^(?:ColorOS\s*|V)?(16|17)(?:\D|$)""", RegexOption.IGNORE_CASE)

        fun visibleVersions(allowHyperOs: Boolean): List<SystemAdaptationVersion> =
            entries.filter { it != HyperOs3 || allowHyperOs }

        fun resolve(savedValue: Int?, systemVersion: String?, allowHyperOs: Boolean = false): SystemAdaptationVersion {
            visibleVersions(allowHyperOs).firstOrNull { it.value == savedValue && it.selectable }?.let { return it }
            val detected = when (versionPattern.find(systemVersion.orEmpty().trim())?.groupValues?.get(1)) {
                "16" -> ColorOs16
                else -> ColorOs17
            }
            return detected.takeIf { it.selectable } ?: ColorOs17
        }
    }
}
