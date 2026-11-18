package com.mi.onextbox.ui.home

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

internal object BatteryHardwareRules {
    fun text(raw: String?): String? = raw?.trim()?.trim('\u0000')?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("null", true) && !it.equals("unknown", true) &&
            !it.equals("unsupported", true) && !it.equals("unsupport", true) && !it.equals("N/A", true) }

    fun number(raw: String?): Double? = text(raw)?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it != Long.MIN_VALUE.toDouble() && it != Long.MAX_VALUE.toDouble() &&
            it != Int.MIN_VALUE.toDouble() && it != Int.MAX_VALUE.toDouble() }

    fun capacityMah(raw: String?, micro: Boolean = false): Double? = number(raw)
        ?.let { if (micro) it / 1000.0 else it }
        ?.takeIf { it > 0 && it <= 100_000 }

    fun cycles(raw: String?): Long? = text(raw)?.toLongOrNull()
        ?.takeIf { it in 0..100_000 && it != 65535L }

    fun percent(raw: String?): Double? = number(raw)?.takeIf { it > 0 && it <= 100 }

    fun ratio(full: Double?, design: Double?): Double? =
        if (full != null && design != null && full > 0 && design > 0) full / design * 100 else null

    fun format(value: Double, decimals: Int = 2): String {
        val formatted = String.format(Locale.ROOT, "%.${decimals}f", value)
        return (if (decimals > 0) formatted.trimEnd('0').trimEnd('.') else formatted)
            .takeUnless { it == "-0" } ?: "0"
    }

    fun date(raw: String?): String? {
        val value = text(raw) ?: return null
        if (value == "0" || value == "-1") return null
        val calendar = when {
            Regex("\\d{8}").matches(value) -> runCatching {
                LocalDate.parse(value, DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT))
            }.getOrNull()
            Regex("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}").matches(value) -> runCatching {
                val parts = value.split('-', '/').map(String::toInt)
                LocalDate.of(parts[0], parts[1], parts[2])
            }.getOrNull()
            else -> value.toLongOrNull()?.let { timestamp ->
                val seconds = if (timestamp >= 1_000_000_000_000L) timestamp / 1000 else timestamp
                seconds.takeIf { it in 315_532_800L..4_133_980_799L }?.let {
                    runCatching { Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).toLocalDate() }.getOrNull()
                }
            }
        }
        return calendar?.takeIf { it.year in 1980..2100 }?.toString()
    }

    fun selectedUsbType(raw: String?): String? = text(raw)?.let { value ->
        Regex("\\[([^]]+)]").find(value)?.groupValues?.get(1) ?: value
    }

    fun chemistryId(raw: String?): String? {
        val value = text(raw) ?: return null
        val bytes = value.substringAfterLast(']').substringBefore(',').trim().split(Regex("\\s+"))
            .mapNotNull { it.toIntOrNull(16)?.takeIf { code -> code in 0..255 } }
        val decoded = bytes.takeWhile { it != 0 }.takeIf { it.isNotEmpty() && it.all { code -> code in 32..126 } }
            ?.map(Int::toChar)?.joinToString("")
        return decoded ?: value
    }

    fun parseFields(lines: List<String>): Map<String, String> {
        val result = linkedMapOf<String, StringBuilder>()
        var key: String? = null
        lines.forEach { line ->
            if (line.startsWith("ONB_BAT=")) {
                key = line.removePrefix("ONB_BAT=")
                result[key!!] = StringBuilder()
            } else key?.let { result[it]?.appendLine(line) }
        }
        return result.mapValues { it.value.toString().trim() }
    }
}
