package com.mi.onextbox.lsp

/** Expanded-card color parameters from PuiThemePluginColor.apk, not an installed overlay. */
internal object FluidCloudExpandedMaterialRules {
    const val BLEND_COLOR = 0x2a585858
    const val MIX_COLOR = 0x20282828
    const val GRADIENT_COLOR = 0x40000000
    const val NO_BLUR_COLOR = 0x50282828
    const val MATERIAL_MODE = 4

    fun isBackgroundHost(className: String?, instantContent: Boolean): Boolean =
        className == "${FluidCloudMaterialRules.PLUGIN_PACKAGE}.seedling.card.ui.view.CardBackgroundView" || instantContent

    fun shouldReplaceParams(scoped: Boolean, mode: Int, blendSize: Int?, mixSize: Int?): Boolean =
        scoped && mode == MATERIAL_MODE && blendSize == 4 && mixSize == 4

    fun rgba(color: Int): FloatArray = floatArrayOf(
        ((color ushr 16) and 255) / 255f,
        ((color ushr 8) and 255) / 255f,
        (color and 255) / 255f,
        ((color ushr 24) and 255) / 255f,
    )
}

/** Restricts shared framework hooks to one native expanded-card background initialization. */
internal class FluidCloudExpandedBuildScope {
    data class Style(val modernBlur: Boolean)

    private val active = ThreadLocal<Style>()
    val current: Style? get() = active.get()

    fun <T> duringBuild(style: Style?, build: () -> T): T {
        val previous = active.get()
        if (style == null) active.remove() else active.set(style)
        return try {
            build()
        } finally {
            if (previous == null) active.remove() else active.set(previous)
        }
    }
}
