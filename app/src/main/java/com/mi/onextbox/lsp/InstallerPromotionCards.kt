package com.mi.onextbox.lsp

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Field

internal object InstallerPromotionCards {
    fun matches(view: View): Boolean = view.id != View.NO_ID && runCatching {
        view.resources.getResourcePackageName(view.id) == InstallerCleanupSettings.APP_DETAIL &&
            view.resources.getResourceEntryName(view.id) in setOf("install_done_suggest_B", "layout_no_recommend")
    }.getOrDefault(false)

    fun hide(root: View): Int {
        var hidden = 0
        val pending = ArrayDeque<View>()
        pending.addLast(root)
        while (pending.isNotEmpty()) {
            val view = pending.removeFirst()
            if (matches(view)) {
                if (view.visibility != View.GONE) { view.visibility = View.GONE; hidden++ }
            } else if (view is ViewGroup) {
                for (index in 0 until view.childCount) view.getChildAt(index)?.let(pending::addLast)
            }
        }
        return hidden
    }

    fun refreshOwnedActivities(): Int {
        val type = Class.forName("android.app.ActivityThread")
        val thread = HiddenApiBypass.invoke(type, null, "currentActivityThread") ?: return 0
        val field = HiddenApiBypass.getInstanceFields(type).filterIsInstance<Field>()
            .single { it.name == "mActivities" }.apply { isAccessible = true }
        val records = field.get(thread) as? Map<*, *> ?: return 0
        return records.values.filterNotNull().sumOf { record ->
            val activityField = HiddenApiBypass.getInstanceFields(record.javaClass).filterIsInstance<Field>().single { it.name == "activity" }
                .apply { isAccessible = true }
            val activity = activityField.get(record) as? Activity
            if (activity?.packageName == InstallerCleanupSettings.APP_DETAIL) activity.window?.decorView?.let(::hide) ?: 0 else 0
        }
    }
}
