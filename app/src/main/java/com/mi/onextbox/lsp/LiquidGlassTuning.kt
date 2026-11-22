package com.mi.onextbox.lsp

import android.graphics.RuntimeShader
import com.mi.onextbox.R

internal object LiquidGlassTuning {
    enum class Control(val title: Int, val range: IntRange, val divisor: Int) {
        BEZEL(R.string.lg_bezel, 0..500, 10),
        BLUR(R.string.glass_blur, 0..500, 10),
        THICKNESS(R.string.lg_thickness, 0..2000, 10),
        INDEX(R.string.lg_index, 100..500, 100),
        REFRACTION(R.string.glass_refraction, 50..500, 100),
        SPECULAR(R.string.lg_specular, 0..100, 100),
        LIGHT(R.string.lg_light_alpha, 0..100, 100),
        DARK(R.string.lg_dark_alpha, 0..100, 100),
        SAMPLING(R.string.lg_sampling, 10..100, 100);

        fun format(value: Int): String = if (divisor == 10) "%.1f".format(value / 10.0)
            else "%.2f".format(value / 100.0)
    }

    enum class Surface(val title: Int, private val sampling: Int, private val bezel: Int, private val blur: Int,
        private val thickness: Int, private val index: Int, private val refraction: Int, private val specular: Int,
        private val lightAlpha: Int, private val darkAlpha: Int) {
        GENERAL(R.string.lg_general, 10, 180, 100, 1000, 120, 180, 80, 80, 60),
        FOLDER(R.string.glass_scene_folder, 50, 120, 30, 900, 200, 200, 60, 10, 0),
        WIDGET(R.string.glass_scene_widget, 50, 180, 80, 1500, 120, 180, 60, 10, 30),
        SEARCH(R.string.glass_scene_bar, 10, 80, 30, 1200, 150, 150, 60, 10, 0),
        DOCK(R.string.glass_scene_dock, 25, 300, 100, 1500, 150, 150, 30, 10, 0),
        CONTROL(R.string.glass_scene_control, 25, 180, 100, 1200, 120, 135, 55, 10, 18),
        LOCK(R.string.glass_scene_lock, 50, 120, 80, 800, 100, 120, 60, 10, 0),
        ALL(R.string.lg_adjust_all, 25, 180, 100, 1200, 120, 135, 55, 10, 18);

        private val prefix = "coui_liquid_glass_${name.lowercase()}_"
        val modeKey = prefix + "tint_mode"
        val colorKey = prefix + "tint_argb"
        fun key(control: Control) = prefix + control.name.lowercase()
        fun default(control: Control) = when (control) {
            Control.SAMPLING -> sampling
            Control.BEZEL -> bezel
            Control.BLUR -> blur
            Control.THICKNESS -> thickness
            Control.INDEX -> index
            Control.REFRACTION -> refraction
            Control.SPECULAR -> specular
            Control.LIGHT -> lightAlpha
            Control.DARK -> darkAlpha
        }
    }

    const val CUSTOM_TINT = 3
    const val DEFAULT_COLOR = 0x40ffffff
    const val UNIFIED = "coui_liquid_glass_unified"
    val surfaces = Surface.entries.filterNot { it == Surface.ALL }
    val defaults = buildMap {
        put(UNIFIED, 0)
        for (surface in Surface.entries) {
            for (control in Control.entries) put(surface.key(control), surface.default(control))
            put(surface.modeKey, 0)
            put(surface.colorKey, DEFAULT_COLOR)
        }
    }

    class Parameters(read: (String, Int) -> Int) {
        val unified = read(UNIFIED, 0) == 1
        private val optics = FloatArray(surfaces.size * 4)
        private val finish = FloatArray(optics.size)
        private val tints = FloatArray(optics.size)
        private val sampling = FloatArray(surfaces.size)

        init {
            for (s in surfaces) {
                val i = s.ordinal * 4
                if (unified && s != Surface.GENERAL) {
                    optics.copyInto(optics, i, 0, 4)
                    finish.copyInto(finish, i, 0, 4)
                    tints.copyInto(tints, i, 0, 4)
                    sampling[s.ordinal] = sampling[Surface.GENERAL.ordinal]
                    continue
                }
                val source = if (unified) Surface.ALL else s
                fun value(c: Control) = read(source.key(c), source.default(c)).coerceIn(c.range).toFloat() / c.divisor
                optics[i] = value(Control.BEZEL)
                optics[i+1] = value(Control.THICKNESS)
                optics[i+2] = value(Control.INDEX)
                optics[i+3] = value(Control.REFRACTION)
                finish[i] = value(Control.BLUR)
                finish[i+1] = value(Control.SPECULAR)
                finish[i+2] = value(Control.LIGHT)
                finish[i+3] = value(Control.DARK)
                sampling[s.ordinal] = value(Control.SAMPLING)
                val mode = read(source.modeKey, 0).takeIf { it in 0..CUSTOM_TINT } ?: 0
                val color = read(source.colorKey, DEFAULT_COLOR)
                for (channel in 0..2) tints[i+channel] = (color ushr (16 - channel*8) and 255) / 255f
                tints[i+3] = if (mode == CUSTOM_TINT) (color ushr 24) / 255f else -(mode + 1f)
            }
        }

        fun blur(surface: Surface) = finish[surface.ordinal*4]

        fun nativeTint(surface: Surface, dark: Boolean): Int {
            val i = surface.ordinal*4
            val mode = tints[i+3]
            if (mode >= 0f) return android.graphics.Color.argb(mode, tints[i], tints[i+1], tints[i+2])
            val useDark = mode == -3f || mode == -1f && dark
            val gray = if (useDark) 0f else 1f
            return android.graphics.Color.argb(finish[i + if (useDark) 3 else 2], gray, gray, gray)
        }

        fun bind(shader: RuntimeShader) {
            shader.setFloatUniform("onbLgUnified", if (unified) 1f else 0f)
            shader.setFloatUniform("onbLgOptics", optics)
            shader.setFloatUniform("onbLgFinish", finish)
            shader.setFloatUniform("onbLgTint", tints)
            shader.setFloatUniform("onbLgSampling", sampling)
        }
    }
}
