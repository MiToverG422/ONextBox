package com.mi.onextbox.lsp

internal object StatusBarKeepRules {
    const val VISIBLE = 0

    fun visible(
        enabled: Boolean,
        statusBarVisible: Boolean,
        allowed: Boolean,
    ): Boolean = statusBarVisible && (allowed || enabled)

    fun notificationIconsVisible(
        enabled: Boolean,
        anyChipVisible: Boolean,
        statusBarVisible: Boolean,
        allowed: Boolean,
    ): Boolean = !anyChipVisible && visible(enabled, statusBarVisible, allowed)
}
