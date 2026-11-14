package com.mi.onextbox.lsp

internal class PasswordEmergencyVisibility(var requested: Int) {
    private var hidden = false

    companion object {
        fun shouldHide(enabled: Boolean, passwordPage: Boolean, inCall: Boolean?): Boolean =
            enabled && passwordPage && inCall == false
    }

    fun nativeRequest(visibility: Int, hide: Boolean): Int {
        requested = visibility
        hidden = hide && visibility == 0
        return if (hidden) 4 else visibility
    }

    fun reconcile(hide: Boolean, current: Int): Int? {
        if (hide && requested == 0 && (current == 0 || hidden)) {
            hidden = true
            return 4
        }
        return if (!hide) restore() else null
    }

    fun restore(): Int? {
        if (!hidden) return null
        hidden = false
        return requested
    }
}
