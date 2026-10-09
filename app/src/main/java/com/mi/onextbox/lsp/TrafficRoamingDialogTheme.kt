package com.mi.onextbox.lsp

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView

/** Repairs the OEM roaming chooser without replacing its adapter, selection or animation. */
internal object TrafficRoamingDialogTheme {
    private val labelNames = listOf(
        "restrict_background_uss_never", "restrict_background_uss_always", "restrict_background_uss_roaming",
    )

    fun applyToRow(adapter: ArrayAdapter<*>, parent: ViewGroup?, row: View?): Boolean {
        if (parent !is ListView || row == null || adapter.count != 3) return false
        val context = row.context
        if (context.packageName != TrafficManagementRules.PACKAGE_NAME) return false
        val labels = labelNames.map { name ->
            val id = context.resources.getIdentifier(name, "string", context.packageName)
            if (id == 0) return false
            context.getString(id)
        }
        if (!TrafficManagementRules.isRoamingChoiceAdapter(
                adapter.javaClass.name, (0 until adapter.count).map { adapter.getItem(it)?.toString() }, labels,
            )) return false
        val text = row.findViewById<TextView>(android.R.id.text1) ?: return false
        val normal = nativeColor(context, "coui_color_primary_neutral") ?: return false
        val disabled = nativeColor(context, "coui_color_disabled_neutral") ?: return false
        // Use the host's day/night resources rather than its stale AppCompat list-text attribute.
        text.setTextColor(ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(disabled, normal),
        ))
        text.isForceDarkAllowed = false
        return true
    }

    private fun nativeColor(context: Context, name: String): Int? {
        val id = context.resources.getIdentifier(name, "color", context.packageName)
        return if (id == 0) null else context.resources.getColor(id, context.theme)
    }
}
