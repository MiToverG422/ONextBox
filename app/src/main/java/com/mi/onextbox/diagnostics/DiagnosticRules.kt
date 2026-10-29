package com.mi.onextbox.diagnostics

import java.security.MessageDigest

internal object DiagnosticRules {
    fun featureValues(values: Map<String, *>): Map<String, *> = values.filterKeys {
        !it.startsWith("__") && !it.startsWith("developer_")
    }
    fun fingerprint(values: Map<String, *>): String {
        val text = featureValues(values).toSortedMap().entries.joinToString("\n") { (k, v) ->
            val value = if (v is Set<*>) v.map { it.toString() }.sorted().joinToString("\u0000") else v.toString()
            "${k.length}:$k:${if (v is Set<*>) "Set" else v?.javaClass?.simpleName}:$value"
        }
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it) }
    }
    fun temporaryLogging(until: Long, now: Long, tags: Set<String>, tag: String): Boolean =
        until > now && until - now <= 30 * 60_000L && ("*" in tags || tag in tags)

    fun redact(text: String): String = text
        .replace(Regex("(?i)bearer\\s+[A-Za-z0-9._~+/-]+=*"), "Bearer [REDACTED]")
        .replace(Regex("(?i)[\"'](token|password|passwd|secret|authorization|cookie|imei|imsi|serial|android_id)[\"']\\s*:\\s*[\"'][^\"']*[\"']"), "$1=[REDACTED]")
        .replace(Regex("(?i)(token|password|passwd|secret|authorization|cookie|imei|imsi|serial|android_id)\\s*[:=]\\s*[^\\s,;]+"), "$1=[REDACTED]")
        .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[EMAIL]")
        .replace(Regex("(?<![0-9])(?:\\+?86[- ]?)?1[3-9][0-9]{9}(?![0-9])"), "[PHONE]")
        .replace(Regex("(?i)(?:https?://|content://|file://)[^\\s]+"), "[URI]")
        .replace(Regex("(?:/storage/|/sdcard/|/data/user/)[^\\s]+"), "[PATH]")
        .replace(Regex("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}"), "[MAC]")
        .replace(Regex("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"), "[IP]")
}
