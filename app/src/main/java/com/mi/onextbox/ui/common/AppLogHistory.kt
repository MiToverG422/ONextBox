package com.mi.onextbox.ui.common

/** Existing live IDs remain stable while history is loaded; historical IDs are negative. */
internal fun mergeAppLogHistory(
    history: List<AppLogEntry>,
    live: List<AppLogEntry>,
    cleared: Boolean,
    removedPrefixes: Set<String>,
    capacity: Int,
): List<AppLogEntry> {
    val retained = if (cleared) emptyList() else history.filterNot { entry ->
        entry.tag.startsWith("Hook/") || removedPrefixes.any(entry.tag::startsWith)
    }.mapIndexed { index, entry -> entry.copy(id = index.toLong() - history.size) }
    return (retained + live).takeLast(capacity)
}
