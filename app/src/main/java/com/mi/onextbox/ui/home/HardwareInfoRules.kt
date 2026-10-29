package com.mi.onextbox.ui.home

import java.util.Locale
import kotlin.math.roundToInt

internal object HardwareInfoRules {
    fun panelSeries(name: String?): String? = Regex("(?i)(?:^|[^a-z0-9])(?:boe|京东方)[ _-]+(x[1-9][0-9]*)(?:$|[^a-z0-9])")
        .find(name.orEmpty())?.groupValues?.get(1)?.uppercase(Locale.ROOT)?.let { "BOE $it" }

    fun panelVendor(raw: String?): String? = when (raw?.trim()?.lowercase(Locale.ROOT)) {
        "boe", "京东方" -> "BOE 京东方"
        "tianma", "tm", "天马" -> "Tianma 天马"
        "samsung", "sdc", "三星" -> "Samsung 三星"
        "csot", "tcl", "华星", "华星光电" -> "CSOT 华星光电"
        "visionox", "维信诺" -> "Visionox 维信诺"
        "lg", "lgd", "lg display" -> "LG Display"
        "sharp", "夏普" -> "Sharp 夏普"
        "auo", "友达" -> "AUO 友达"
        "jdi" -> "JDI"
        else -> null
    }

    fun touchPanelVendor(raw: String?): String? {
        val entry = raw?.trim()?.trimEnd('\u0000') ?: return null
        if (!entry.startsWith("touch_panel0=")) return null
        return panelVendor(entry.substringAfter('=').split(':').getOrNull(2))
    }

    fun field(text: String?, name: String): String? = text?.lineSequence()?.map(String::trim)
        ?.firstOrNull { it.substringBefore(':').trim().equals(name, true) }
        ?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }

    fun specification(raw: String?): String? {
        val text = raw?.trim() ?: return null
        val value = if (text.startsWith("0x", true)) text.drop(2).toIntOrNull(16) else text.toIntOrNull()
        return when (value) {
            0x100 -> "1.0"; 0x110 -> "1.1"; 0x200 -> "2.0"; 0x210 -> "2.1"
            0x220 -> "2.2"; 0x300 -> "3.0"; 0x310 -> "3.1"; 0x400 -> "4.0"; 0x410 -> "4.1"
            else -> null
        }
    }

    fun rates(values: List<Float>): String = values.filter { it.isFinite() && it > 0 }
        .map { (it * 100).roundToInt() / 100.0 }.distinct().sorted()
        .joinToString(" / ") { String.format(Locale.ROOT, "%.2f", it).trimEnd('0').trimEnd('.') }

    fun colorModes(line: String?): String? {
        val values = Regex("supportedColorModes[ =]+\\[([^]]*)]").find(line.orEmpty())?.groupValues?.get(1) ?: return null
        return values.split(',').mapNotNull { it.trim().toIntOrNull() }.distinct().joinToString(" / ") {
            when (it) {
                0 -> "原生模式"; 1 -> "BT.601 625"; 2 -> "BT.601 625 Unadjusted"
                3 -> "BT.601 525"; 4 -> "BT.601 525 Unadjusted"; 5 -> "BT.709"
                6 -> "DCI-P3"; 7 -> "sRGB"; 8 -> "Adobe RGB"; 9 -> "Display P3"
                10 -> "BT.2020"; 11 -> "BT.2100 PQ"; 12 -> "BT.2100 HLG"; else -> "模式 $it"
            }
        }.takeIf { it.isNotBlank() }
    }
}
