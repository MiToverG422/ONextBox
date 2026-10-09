package com.mi.onextbox.lsp

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object SettingsAppInfoRules {
    enum class Field { PackageName, Version, Sdk, FirstInstalled, LastUpdated, Installer }

    data class Snapshot(
        val packageName: String,
        val versionName: String?,
        val versionCode: Long,
        val minSdk: Int,
        val targetSdk: Int,
        val firstInstalled: Long,
        val lastUpdated: Long,
        val uid: Int,
    )

    fun version(name: String?, code: Long, unknown: String): String {
        val value = name?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            code < 0 -> value ?: unknown
            value == null -> code.toString()
            else -> "$value ($code)"
        }
    }

    fun installer(packageName: String?, label: String?, unknown: String): String {
        val name = packageName?.trim()?.takeIf { it.isNotEmpty() } ?: return unknown
        val display = label?.trim()?.takeIf { it.isNotEmpty() && it != name } ?: return name
        return "$display\n$name"
    }

    fun timestamp(time: Long, pattern: String, locale: Locale, zone: TimeZone, unknown: String): String =
        if (time <= 0) unknown else SimpleDateFormat(pattern, locale).apply { timeZone = zone }.format(Date(time))

    // Reserve a slot before the notification category without tying order to translated titles.
    fun shiftedOrder(order: Int, anchor: Int): Int = if (order >= anchor && order < Int.MAX_VALUE) order + 1 else order

    // Follow the visible native body height, including interrupted expansion and collapse.
    fun titleDividerOpacity(animating: Boolean, expanded: Boolean, expanding: Boolean, height: Int, totalHeight: Int): Float = when {
        !animating -> if (expanded) 1f else 0f
        height < 0 || totalHeight <= 0 -> if (expanding) 0f else 1f
        else -> (height.toFloat() / totalHeight).coerceIn(0f, 1f)
    }
}
