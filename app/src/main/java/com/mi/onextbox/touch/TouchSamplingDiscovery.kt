package com.mi.onextbox.touch

/** Validates read-only discovery results before they become shell arguments. */
internal object TouchSamplingDiscovery {
    private const val MAX_LISTING_CHARS = 131_072
    private const val MAX_CONFIG_PATHS = 24
    private val serviceLine = Regex(
        "^\\s*\\d+\\s+(vendor\\.oplus\\.hardware\\.touch\\.IOplusTouch/" +
            "[A-Za-z0-9][A-Za-z0-9_.-]{0,63}):\\s*" +
            "\\[vendor\\.oplus\\.hardware\\.touch\\.IOplusTouch]\\s*$",
    )
    private val configRoots = listOf(
        "/data/vendor/touchconfig/",
        "/vendor/etc/touchconfig/",
        "/odm/etc/touchconfig/",
    )
    private val pathSegment = Regex("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,95}")
    private val kernelRoots = listOf(
        "/proc/touchpanel/",
        "/proc/touchpanel0/",
        "/proc/touchpanel1/",
        "/sys/class/touchpanel/",
    )
    private val kernelFiles = setOf("tp_index", "report_rate", "touch_report_rate", "sampling_rate")

    fun oplusServices(serviceList: String): List<String> = lines(serviceList)
        .mapNotNull { serviceLine.matchEntire(it)?.groupValues?.get(1) }
        .filter { !it.substringAfterLast('/').contains("..") }
        .distinct()
        .take(8)
        .toList()

    fun configPaths(listing: String): List<String> = lines(listing)
        .map(String::trim)
        .filter { path ->
            if (path.length > 256) return@filter false
            val root = configRoots.firstOrNull(path::startsWith) ?: return@filter false
            val segments = path.removePrefix(root).split('/')
            segments.size in 1..3 &&
                segments.all { it != "." && it != ".." && pathSegment.matches(it) } &&
                segments.last().endsWith(".xml")
        }
        .distinct()
        .take(MAX_CONFIG_PATHS)
        .toList()

    fun kernelNodes(listing: String): List<String> = lines(listing)
        .map(String::trim)
        .filter { path ->
            val root = kernelRoots.firstOrNull(path::startsWith) ?: return@filter false
            path.removePrefix(root) in kernelFiles
        }
        .distinct()
        .toList()

    fun panelIndex(text: String): Int? = when (text.trim()) {
        "0" -> 0
        "1" -> 1
        else -> null
    }

    fun safeShellQuote(value: String): String {
        require(value.none { it.code < 32 || it.code == 127 }) { "Invalid shell argument" }
        return "'" + value.replace("'", "'\\''") + "'"
    }

    private fun lines(text: String): Sequence<String> {
        val bounded = if (text.length <= MAX_LISTING_CHARS) text else {
            text.take(MAX_LISTING_CHARS).substringBeforeLast('\n', "")
        }
        return bounded.lineSequence()
    }
}
