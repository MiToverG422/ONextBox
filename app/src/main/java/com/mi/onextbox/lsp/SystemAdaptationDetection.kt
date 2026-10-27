package com.mi.onextbox.lsp

internal object SystemAdaptationDetection {
    val romProperties = listOf(
        "ro.build.version.oplusrom.display",
        "ro.build.version.oplusrom",
    )
    val displayProperties = listOf("ro.build.display.id.show", "ro.build.display.id")
    private val romVersion = Regex("^(?:ColorOS\\s*|V)?(\\d+)(?:\\.|$)", RegexOption.IGNORE_CASE)
    private val firmwareVersion = Regex("^[A-Za-z0-9]+_(\\d+)\\.\\d+\\.")

    fun detect(property: (String) -> String?, buildDisplay: String? = null): SystemAdaptationVersion? {
        fun read(key: String) = runCatching { property(key)?.trim() }.getOrNull()
        val confidential = listOf("ro.version.confidential", "persist.version.confidential")
            .any { read(it).equals("true", ignoreCase = true) }
        val keys = if (confidential) romProperties + "ro.build.version.oplusrom.confidential"
        else romProperties
        for (key in keys) {
            val major = romVersion.find(read(key).orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: continue
            fromMajor(major)?.let { return it }
        }
        for (value in displayProperties.map(::read) + buildDisplay) {
            val major = firmwareVersion.find(value.orEmpty().trim())?.groupValues?.get(1)?.toIntOrNull() ?: continue
            fromMajor(major)?.let { return it }
        }
        return null
    }

    fun resolve(
        detected: SystemAdaptationVersion?,
        supports: (SystemAdaptationVersion) -> Boolean,
    ): SystemAdaptationVersion {
        if (detected != null) return detected
        for (candidate in listOf(SystemAdaptationVersion.ColorOs17, SystemAdaptationVersion.ColorOs16)) {
            if (runCatching { supports(candidate) }.getOrDefault(false)) return candidate
        }
        return SystemAdaptationVersion.ColorOs17
    }

    private fun fromMajor(major: Int) = when (major) {
        16 -> SystemAdaptationVersion.ColorOs16
        17 -> SystemAdaptationVersion.ColorOs17
        else -> null
    }
}
