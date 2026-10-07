package com.mi.onextbox.push

internal const val MCS_PACKAGE = "com.heytap.mcs"

internal data class PushApp(
    val packageName: String,
    val label: String,
    val registration: PushRegistration,
    val lastPush: Long = 0,
)

internal enum class PushRegistration { Registered, Unregistered, Unknown, SystemService }
internal enum class PushFilter { All, Registered, Unregistered }

internal data class PushSnapshot(
    val time: Long = 0,
    val complete: Boolean = false,
    val requestId: String = "",
    val rows: Map<String, Boolean> = emptyMap(),
    val times: Map<String, Long> = emptyMap(),
)

/** Registration states, caller checks and bounded local records. */
internal object PushMonitorRules {
    const val MAX_APPS = 3000
    const val MAX_WIRE_CHARS = 128_000
    const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")

    fun validPackage(value: String): Boolean = value.length <= 255 && packagePattern.matches(value)

    fun canRead(callerUid: Int, ownerUid: Int): Boolean = callerUid == ownerUid

    // MCS may share its UID with other system packages, this is a UID boundary.
    fun canWrite(callerUid: Int, ownerUid: Int, mcsUid: Int?): Boolean =
        mcsUid != null && callerUid == mcsUid && callerUid != ownerUid &&
            callerUid / 100_000 == ownerUid / 100_000

    fun sanitizeRows(input: List<Map<String, Any?>>): Map<String, Boolean> {
        require(input.size <= MAX_APPS) { "Too many registrations" }
        val result = linkedMapOf<String, Boolean>()
        input.forEach { row ->
            val pkg = row["package"] as? String ?: error("Invalid package")
            require(validPackage(pkg)) { "Invalid package" }
            val registered = row["registered"] as? Boolean ?: error("Invalid registration")
            result[pkg] = result[pkg] == true || registered
        }
        return result
    }

    fun boundedRows(rows: Map<String, Boolean>): Map<String, Boolean> {
        var chars = 2
        return rows.entries.take(MAX_APPS).takeWhile { entry ->
            chars += entry.key.length + 36
            chars <= MAX_WIRE_CHARS - 1024
        }.associate { it.toPair() }
    }

    fun recordPushes(existing: Map<String, Long>, packages: List<String>, now: Long): Map<String, Long> {
        require(packages.size <= MAX_APPS && packages.all(::validPackage)) { "Invalid packages" }
        val retained = existing.filter { (pkg, time) ->
            validPackage(pkg) && time > 0 && time <= now && now - time <= RETENTION_MS
        }.toMutableMap()
        packages.forEach { retained[it] = now }
        var chars = 2
        return retained.entries.sortedByDescending { it.value }.take(MAX_APPS).takeWhile { entry ->
            chars += entry.key.length + entry.value.toString().length + 4
            chars <= MAX_WIRE_CHARS
        }.associate { it.toPair() }
    }

    fun registration(pkg: String, snapshot: PushSnapshot): PushRegistration = when {
        pkg == MCS_PACKAGE -> PushRegistration.SystemService
        snapshot.rows[pkg] == true -> PushRegistration.Registered
        snapshot.time > 0 && snapshot.complete -> PushRegistration.Unregistered
        else -> PushRegistration.Unknown
    }

    fun shown(apps: List<PushApp>, query: String, filter: PushFilter): List<PushApp> = apps
        .filter { app ->
            (filter == PushFilter.All ||
                filter == PushFilter.Registered && app.registration == PushRegistration.Registered ||
                filter == PushFilter.Unregistered && app.registration == PushRegistration.Unregistered) &&
                (query.isBlank() || app.label.contains(query.trim(), true) || app.packageName.contains(query.trim(), true))
        }
        .sortedWith(compareByDescending<PushApp> { it.packageName == MCS_PACKAGE }
            .thenBy {
                when (it.registration) {
                    PushRegistration.Registered -> 0
                    PushRegistration.Unknown, PushRegistration.SystemService -> 1
                    PushRegistration.Unregistered -> 2
                }
            }
            .thenByDescending { it.lastPush.coerceAtLeast(0) }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
            .thenBy { it.packageName })
}
