package com.mi.onextbox.lsp

/** Only expanded/notification templates participate, never capsules or generic QS tiles. */
internal object FluidCloudMaterialRules {
    const val PLUGIN_PACKAGE = "com.oplus.systemui.plugins"
    const val CUSTOM_CARD = "CUSTOM"
    const val NOTIFICATION_CARD = "NOTIFICATION"
    const val PAGE_PACKAGE = "$PLUGIN_PACKAGE.shared.template.page.entity"
    const val SINGLE_BACKGROUND = "$PAGE_PACKAGE.SingleCardPage\$bind\$1\$4\$4"
    const val MINI_BACKGROUND = "$PAGE_PACKAGE.j"

    fun notificationMode(night: Boolean): String = if (night) "SHADE_DARK" else "SHADE_LIGHT"

    fun notificationMode(night: Boolean, nativeMode: String?): String =
        nativeMode?.takeIf(::isNotificationMode)
            ?: notificationMode(night)

    fun isNotificationMode(mode: String?): Boolean = when (mode) {
        "SHADE_LIGHT", "SHADE_DARK", "WALLPAPER_A", "WALLPAPER_B" -> true
        else -> false
    }

    fun isExpandedTemplate(size: String?): Boolean =
        size == "VIEW_SIZE_MD" || size == "VIEW_SIZE_LG" || size == "VIEW_SIZE_IMMERSIVE"

    fun shouldUseNotificationMaterial(enabled: Boolean, cardType: String?): Boolean =
        enabled && cardType == CUSTOM_CARD

    fun shouldClearMiniBackground(enabled: Boolean, branch: Int): Boolean = enabled && branch != 0

    fun blurAmount(alpha: Int): Float = alpha.coerceIn(0, 255) / 255f

    fun canClearTemplate(enabled: Boolean, nativeRendererReady: Boolean, blurDisabled: Boolean): Boolean =
        enabled && nativeRendererReady && !blurDisabled

    fun canUseReference(mode: String?, referenceMode: String?, colorized: Boolean, stacked: Boolean): Boolean =
        isNotificationMode(mode) && mode == referenceMode && !colorized && !stacked

    data class StyleKey(
        val mode: String,
        val night: Boolean,
        val motion: Boolean,
        val width: Int,
        val height: Int,
        val resources: Int,
        val revision: Int,
    )
}
