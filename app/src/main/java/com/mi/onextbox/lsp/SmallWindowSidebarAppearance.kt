package com.mi.onextbox.lsp

import android.content.Context
import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.mi.onextbox.lsp.compat.HotReloadLifecycle

internal class SmallWindowSidebarAppearance(context: Context, private val changed: () -> Unit) : AutoCloseable {
    data class Style(val color: Int, val opacity: Float)

    private val context = context.applicationContext ?: context
    private val resolver = this.context.contentResolver
    private var configuration = Configuration(this.context.resources.configuration)
    private var registered = false
    private var closed = false
    private var appearance = read()
    val value: Style
        get() {
            if (configuration != context.resources.configuration) {
                configuration = Configuration(context.resources.configuration)
                appearance = read()
            }
            return appearance
        }
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (closed || HotReloadLifecycle.isRetiring) return
            appearance = read()
            changed()
        }
    }

    init {
        runCatching {
            KEYS.forEach { resolver.registerContentObserver(Settings.Secure.getUriFor(it), false, observer) }
            registered = true
        }.onFailure {
            runCatching { resolver.unregisterContentObserver(observer) }
            HookLog.w("ONextBox-SmallWindow", "Sidebar appearance observer unavailable; current appearance retained", it)
        }
    }

    private fun read(): Style {
        val color = runCatching {
            val resources = context.packageManager.getResourcesForApplication(PACKAGE_NAME)
            val id = resources.getIdentifier("coloros_ep_float_bar_fill", "color", PACKAGE_NAME)
            if (id != 0) resources.getColor(id, null) else DEFAULT_COLOR
        }.getOrDefault(DEFAULT_COLOR)
        fun setting(key: String, default: Int) = runCatching { Settings.Secure.getInt(resolver, key, default) }.getOrDefault(default)
        val modern = setting(ALPHA_KEY, -1).takeUnless { it == -1 }
        val legacy = setting(LEGACY_ALPHA_KEY, 40)
        val permanent = setting(PERMANENT_KEY, 1) == 1
        return Style(color, SmallWindowRules.sidebarOpacity(modern, legacy, permanent))
    }

    override fun close() {
        if (closed) return
        closed = true
        if (registered) runCatching { resolver.unregisterContentObserver(observer) }
        registered = false
    }

    companion object {
        const val PACKAGE_NAME = "com.coloros.smartsidebar"
        const val ALPHA_KEY = "edge_panel_os_float_bar_alpha"
        const val LEGACY_ALPHA_KEY = "edge_panel_float_bar_alpha"
        const val PERMANENT_KEY = "edge_panel_float_bar_permanent"
        const val DEFAULT_COLOR = -3487030 // 0xffcacaca
        private val KEYS = listOf(ALPHA_KEY, LEGACY_ALPHA_KEY, PERMANENT_KEY)
    }
}
