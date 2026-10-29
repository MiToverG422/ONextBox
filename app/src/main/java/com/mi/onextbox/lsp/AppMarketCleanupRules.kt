package com.mi.onextbox.lsp

internal object AppMarketCleanupRules {
    enum class Page { UPDATE, DOWNLOAD }

    fun enabled(override: Boolean?, legacy: Boolean): Boolean = override ?: legacy

    fun page(classNames: Sequence<String>): Page? {
        for (name in classNames) {
            if (name.startsWith("com.heytap.market.appmanage.core.upgrade.") &&
                name.substringAfterLast('.') in setOf("AppUpdateFragment", "AppUpdateActivity", "UpdateListAdapter", "ExpUpdateListAdapter")) return Page.UPDATE
            if (name.startsWith("com.heytap.market.appmanage.core.download.") &&
                name.substringAfterLast('.') in setOf("DownloadManageFragment", "DownloadManageActivity", "DownloadListAdapter")) return Page.DOWNLOAD
        }
        return null
    }
}
