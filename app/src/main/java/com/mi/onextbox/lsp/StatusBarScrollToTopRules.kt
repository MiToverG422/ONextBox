package com.mi.onextbox.lsp

internal object StatusBarScrollToTopRules {
    fun isAllowedPackage(removeWhitelist: Boolean, packageName: String?, original: Boolean): Boolean =
        original || removeWhitelist && !packageName.isNullOrBlank()
}
