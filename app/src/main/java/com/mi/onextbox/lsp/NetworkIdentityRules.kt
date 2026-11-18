package com.mi.onextbox.lsp

/** Display identity only; never contains subscriber identifiers or radio configuration. */
internal object NetworkIdentityRules {
    data class Profile(val region: String, val numeric: String, val name: String)
    val regions = listOf("tw", "hk", "cn", "us")
    val properties = listOf("gsm.sim.operator.numeric", "gsm.sim.operator.alpha", "gsm.sim.operator.iso-country",
        "gsm.operator.numeric", "gsm.operator.alpha", "gsm.operator.iso-country")
    private val numericPattern = Regex("[0-9]{5,6}")

    fun valid(profile: Profile): Boolean = profile.region in regions &&
        numericPattern.matches(profile.numeric) && when (profile.region) {
            "tw" -> profile.numeric.startsWith("466")
            "hk" -> profile.numeric.startsWith("454")
            "cn" -> profile.numeric.startsWith("460")
            "us" -> profile.numeric.length == 6 && profile.numeric.take(3).toInt() in 310..316
            else -> false
        } && profile.name.isNotBlank() && profile.name.toByteArray().size <= 40 &&
        profile.name.none { it.isISOControl() }

    // Android sysprop lists escape both commas and backslashes; preserve empty SIM slots.
    fun decode(value: String): List<String> {
        if (value.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        val part = StringBuilder()
        var escaped = false
        value.forEach { c ->
            when {
                escaped -> { part.append(c); escaped = false }
                c == '\\' -> escaped = true
                c == ',' -> { result += part.toString(); part.clear() }
                else -> part.append(c)
            }
        }
        if (escaped) part.append('\\')
        result += part.toString()
        return result
    }
    fun encode(parts: List<String>) = parts.joinToString(",") { it.replace("\\", "\\\\").replace(",", "\\,") }

    fun transform(original: Map<String, String>, profile: Profile?): Map<String, String> {
        if (profile == null || !valid(profile)) return original
        val count = original.values.maxOfOrNull { decode(it).size } ?: 0
        return transformSlots(original, (0 until count).associateWith { profile })
    }

    fun transformSlots(original: Map<String, String>, profiles: Map<Int, Profile>): Map<String, String> {
        if (profiles.isEmpty()) return original
        return properties.associateWith { key ->
            val prefix = if (key.startsWith("gsm.sim.")) "gsm.sim.operator" else "gsm.operator"
            val numeric = decode(original["$prefix.numeric"].orEmpty())
            val values = decode(original[key].orEmpty()).toMutableList()
            numeric.forEachIndexed { slot, real ->
                val profile = profiles[slot]?.takeIf(::valid) ?: return@forEachIndexed
                val replacement = when {
                    key.endsWith("numeric") -> profile.numeric
                    key.endsWith("alpha") -> profile.name
                    else -> profile.region
                }
                if (numericPattern.matches(real) && real.any { it != '0' }) {
                    while (values.size <= slot) values += ""
                    values[slot] = replacement
                }
            }
            // Android mutable system properties have a 91-byte value limit.
            encode(values).also { require(it.toByteArray().size <= 91) { "Identity exceeds the system property limit" } }
        }
    }
}

/** Journaled, compare-before-restore property transaction. Callers serialize all operations. */
internal class NetworkIdentityProperties(
    private val read: (String) -> String,
    private val write: (String, String) -> Unit,
    private val persist: (Map<String, Record>) -> Unit,
    initial: Map<String, Record> = emptyMap(),
) {
    data class Record(val original: String, val applied: String, val previous: String? = null)
    private val records = initial.filterKeys { it in NetworkIdentityRules.properties }.toMutableMap()
    val hasOverrides: Boolean get() = records.isNotEmpty()
    fun actual(key: String, observed: String): String = records[key]?.let {
        if (observed == it.applied || observed == it.previous) it.original else observed
    } ?: observed

    /** Record only after the platform write succeeds; never journal an uncommitted native value. */
    fun afterNativeWrite(key: String, value: String) {
        val old = records[key] ?: return
        if (old.original == value) return
        val next = records.toMutableMap().apply { put(key, old.copy(original = value)) }
        persist(next)
        records.clear(); records.putAll(next)
    }

    /** One WAL transaction instead of persisting a native update and then another publication. */
    fun refreshAfterNativeWrite(key: String, value: String, profiles: Map<Int, NetworkIdentityRules.Profile>) {
        // Native set() has succeeded. Keep its genuine value even if transformation or storage
        // fails, so the caller's recovery path never restores an obsolete native value.
        records[key]?.let { records[key] = it.copy(original = value) }
        refreshSlots(profiles)
    }

    fun refresh(profile: NetworkIdentityRules.Profile?) {
        if (profile == null && !hasOverrides) return
        refreshWith { NetworkIdentityRules.transform(it, profile) }
    }
    fun refreshSlots(profiles: Map<Int, NetworkIdentityRules.Profile>) {
        if (profiles.isEmpty() && !hasOverrides) return
        refreshWith { NetworkIdentityRules.transformSlots(it, profiles) }
    }

    private fun refreshWith(transform: (Map<String, String>) -> Map<String, String>) {
        val current = NetworkIdentityRules.properties.associateWith(read)
        val original = current.mapValues { (key, value) -> actual(key, value) }
        val target = transform(original)
        val journal = original.mapNotNull { (key, value) ->
            if (value == target[key]) null else key to Record(value, target.getValue(key))
        }.toMap()
        if (current == target && records == journal) return
        // Keep old applied values until a restore has actually completed. A crash at any point
        // can therefore recover the last owned value without overwriting a newer native value.
        val pending = records.toMutableMap().apply {
            journal.forEach { (key, value) -> put(key, value.copy(previous = current[key])) }
        }
        persist(pending)
        records.clear(); records.putAll(pending)
        try {
            target.forEach { (key, value) -> if (current[key] != value) {
                write(key, value)
                check(read(key) == value) { "Property update was not retained: $key" }
            } }
            persist(journal)
            records.clear(); records.putAll(journal)
        } catch (error: Exception) {
            // Leave the write-ahead journal intact for a subsequent restore/retry.
            throw error
        }
    }
}
