package com.mi.onextbox.lsp

import android.content.res.Configuration
import android.content.res.Resources
import android.view.View
import org.xmlpull.v1.XmlPullParser
import java.util.WeakHashMap

internal object C15AboutLayoutProfile {
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private data class Entry(val configuration: Configuration, val modern: Boolean)
    private val profiles = WeakHashMap<Resources, Entry>()

    fun isModern(resources: Resources): Boolean = synchronized(profiles) {
        val configuration = resources.configuration
        profiles[resources]?.takeIf { it.configuration == configuration }?.let { return it.modern }
        val modern = runCatching {
            hasStorageArrowRow(resources) && hasNameDivider(resources)
        }.getOrDefault(false)
        profiles[resources] = Entry(Configuration(configuration), modern)
        modern
    }

    private fun hasStorageArrowRow(resources: Resources): Boolean {
        val layout = resources.getIdentifier(
            "device_info_square_item_grid_preference", "layout", "com.android.settings",
        )
        val arrow = resources.getIdentifier("device_storage_jump", "id", "com.android.settings")
        if (layout == 0 || arrow == 0) return false
        resources.getLayout(layout).use { parser ->
            var rowDepth = -1
            val rowIds = mutableSetOf<Int>()
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    if (parser.depth == 2 && parser.name == "LinearLayout" &&
                        parser.getAttributeIntValue(ANDROID_NS, "orientation", 0) == 0
                    ) {
                        rowDepth = parser.depth
                        rowIds.clear()
                    } else if (rowDepth > 0 && parser.depth == rowDepth + 1) {
                        rowIds += parser.getAttributeResourceValue(ANDROID_NS, "id", View.NO_ID)
                    }
                } else if (parser.eventType == XmlPullParser.END_TAG && parser.depth == rowDepth) {
                    if (rowIds.containsAll(listOf(android.R.id.title, android.R.id.summary, arrow))) return true
                    rowDepth = -1
                }
                parser.next()
            }
        }
        return false
    }

    private fun hasNameDivider(resources: Resources): Boolean {
        val layout = resources.getIdentifier(
            "device_name_square_item_grid_preference", "layout", "com.android.settings",
        )
        if (layout == 0) return false
        resources.getLayout(layout).use { parser ->
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.depth == 2 &&
                    parser.name == "View" &&
                    parser.getAttributeResourceValue(ANDROID_NS, "id", View.NO_ID) == View.NO_ID &&
                    parser.getAttributeIntValue(ANDROID_NS, "layout_width", 0) == -1
                ) return true
                parser.next()
            }
        }
        return false
    }
}
