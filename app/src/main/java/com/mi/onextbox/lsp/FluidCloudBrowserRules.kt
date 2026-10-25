package com.mi.onextbox.lsp

import java.net.URI
import java.net.URLDecoder

internal object FluidCloudBrowserRules {
    const val STOCK_BROWSER = "com.heytap.browser"

    fun firstAvailableBrowser(vararg lookups: () -> String?): String? {
        for (lookup in lookups) {
            val candidate = runCatching { lookup() }.getOrNull()
            if (!candidate.isNullOrBlank() && candidate != "android") return candidate
        }
        return null
    }

    fun accepts(
        enabled: Boolean,
        textType: Int?,
        intentType: Int,
        packageName: String?,
        action: String?,
        scheme: String?,
        host: String?,
        explicitPackage: String?,
        componentPackage: String?,
        hasSelector: Boolean,
    ): Boolean = enabled && intentType == 1 && packageName == STOCK_BROWSER &&
        (action == "android.intent.action.VIEW" ||
            (action == "android.intent.action.WEB_SEARCH" && textType == 29)) &&
        (scheme.equals("http", true) || scheme.equals("https", true)) && !host.isNullOrBlank() &&
        (explicitPackage == null || explicitPackage == STOCK_BROWSER) &&
        (componentPackage == null || componentPackage == STOCK_BROWSER) && !hasSelector

    fun launchUrl(action: String?, data: String?, query: String?, textType: Int?): String? =
        when (action) {
            "android.intent.action.VIEW" -> webUrl(data)
            "android.intent.action.WEB_SEARCH" ->
                if (textType == 29 && data == null) webUrl(query) else null
            else -> null
        }

    /** Only unwrap the browser's webpage route, never arbitrary app schemes or search queries. */
    fun webUrl(data: String?): String? {
        val value = data?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        fun isWeb(target: URI) = (target.scheme.equals("http", true) || target.scheme.equals("https", true)) &&
            !target.host.isNullOrBlank()
        if (isWeb(uri)) return value
        if (!uri.scheme.equals("heytapbrowser", true) || !uri.host.equals("webpage", true) ||
            uri.rawUserInfo != null || uri.port != -1 || uri.rawPath !in listOf("", "/")) return null
        fun decode(part: String) = URLDecoder.decode(part, "UTF-8")
        return runCatching {
            val urls = uri.rawQuery.orEmpty().split('&').mapNotNull { entry ->
                val key = entry.substringBefore('=')
                if (decode(key) == "url") decode(entry.substringAfter('=', "")) else null
            }
            // Decode exactly once; repeated decoding would corrupt encoded parameters in the target URL.
            urls.singleOrNull()?.takeIf { isWeb(URI(it)) }
        }.getOrNull()
    }

    fun <T> withTarget(
        originalPackage: String?,
        targetPackage: String,
        setPackage: (String?) -> Unit,
        launch: () -> T,
    ): T {
        try {
            setPackage(targetPackage)
            return launch()
        } finally {
            setPackage(originalPackage)
        }
    }
}
