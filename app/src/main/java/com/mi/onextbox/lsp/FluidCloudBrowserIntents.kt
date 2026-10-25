package com.mi.onextbox.lsp

import android.app.SearchManager
import android.content.Intent
import android.net.Uri

internal object FluidCloudBrowserIntents {
    fun redirect(original: Intent, url: String, target: String): Intent =
        Intent(original).apply {
            action = Intent.ACTION_VIEW
            component = null
            data = Uri.parse(url)
            setPackage(target)
            addCategory(Intent.CATEGORY_BROWSABLE)
            if (original.action == Intent.ACTION_WEB_SEARCH) removeExtra(SearchManager.QUERY)
        }
}
