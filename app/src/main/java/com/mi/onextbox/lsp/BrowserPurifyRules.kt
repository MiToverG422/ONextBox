package com.mi.onextbox.lsp

internal object BrowserPurifyRules {
    val engineKeys = listOf("bing_ov", "google", "baidu")
    fun hideTab(nativeId: Int?, home: Boolean, video: Boolean, novel: Boolean): Boolean =
        when (nativeId) {
            1 -> home
            2 -> video
            5, 8 -> novel
            else -> false
        }

    fun <T> filterTabs(tabs: List<T>, id: (T) -> Int?, home: Boolean, video: Boolean, novel: Boolean): List<T> {
        val canRedirectHome = tabs.any { id(it) in setOf(4, 9) }
        val visible = tabs.filterNot { hideTab(id(it), home && canRedirectHome, video, novel) }
        return if (visible.isEmpty()) tabs else visible
    }

    fun currentEngineKey(key: String?): String = key?.takeIf { it in engineKeys } ?: engineKeys.first()
}
