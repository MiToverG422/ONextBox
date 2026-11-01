package com.mi.onextbox.lsp

internal object FluidCloudRecentsRules {
    const val VISIBLE = 0
    const val HIDDEN = 4

    fun keepCapsuleVisible(
        enabled: Boolean,
        visibility: Int,
        canShowOngoingActivityChips: Boolean,
        miniCapsuleShowing: Boolean,
        fullscreenPrivacyIcon: Boolean,
        notificationIconsAllowed: Boolean,
    ): Int {
        if (!enabled || visibility == VISIBLE) return visibility
        if (!canShowOngoingActivityChips || miniCapsuleShowing || fullscreenPrivacyIcon) return visibility
        if (notificationIconsAllowed) return visibility
        return VISIBLE
    }
}
