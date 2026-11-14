package com.mi.onextbox.lsp

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import kotlin.math.roundToInt

/** A private resource density keeps C16's dimensions, drawables, animation distances and hit areas together. */
internal object AospVolumeDensityContext {
    fun create(base: Context, percent: Int): Context {
        if (AospVolumeSize.percent(percent) == 100) return base
        val config = Configuration().apply {
            densityDpi = (base.resources.configuration.densityDpi * AospVolumeSize.percent(percent) / 100f).roundToInt()
        }
        val configured = base.createConfigurationContext(config)
        // TypedArray/drawables read density through their theme too, not only Context.resources.
        val theme = configured.resources.newTheme().apply { setTo(base.theme) }
        return ContextThemeWrapper(configured, theme)
    }
}
