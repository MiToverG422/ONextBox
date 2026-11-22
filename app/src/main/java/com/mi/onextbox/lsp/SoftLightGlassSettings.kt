package com.mi.onextbox.lsp

import android.content.Context
import android.graphics.Color
import com.mi.onextbox.R
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import kotlin.math.abs
import kotlin.math.roundToInt

internal object SoftLightGlassSettings {
    const val ENABLED = "coui_soft_light_glass"
    const val STYLE = "${ENABLED}_style"
    const val SOFT = 0
    const val REFRACTION = 1
    const val LIQUID = 2
    const val CUSTOM = "${ENABLED}_custom_scope"
    const val SELECTION = "${ENABLED}_scope_mask"
    const val KEEP_WHITE = "${ENABLED}_keep_white_selected"
    const val GYRO = "${ENABLED}_gyro_light"
    const val LOW_POWER = "${ENABLED}_low_power"
    private const val INDEPENDENT_SEARCH = 1 shl 30
    private const val INDEPENDENT_LOCK = 1 shl 29
    private const val INDEPENDENT_DOCK = 1 shl 28

    enum class Scene(val bit: Int, val title: Int) {
        DESKTOP(1, R.string.glass_scene_desktop), FOLDER(2, R.string.glass_scene_folder),
        WIDGET(4, R.string.glass_scene_widget), BAR(8, R.string.glass_scene_bar),
        DOCK(16384, R.string.glass_scene_dock),
        SHADE(16, R.string.glass_scene_shade), CONTROL(32, R.string.glass_scene_control),
        VOLUME(64, R.string.glass_scene_volume), LOCK(128, R.string.glass_scene_lock),
        AOD(8192, R.string.glass_scene_aod),
        ASSISTANT(256, R.string.glass_scene_assistant), SIDEBAR(512, R.string.glass_scene_sidebar),
        GLOBAL_SEARCH(4096, R.string.glass_scene_global_search),
        SETTINGS(1024, R.string.glass_scene_settings), OTHER(2048, R.string.glass_scene_other)
    }

    enum class Parameter(id: String, val title: Int, val min: Int = 0, val max: Int = 300, val default: Int = 100) {
        STRENGTH("strength", R.string.glass_strength, max = 100),
        BLUR("blur", R.string.glass_blur), WIDTH("rim_width", R.string.glass_rim_width),
        SHINE("shine", R.string.glass_shine), REFRACTION("refraction", R.string.glass_refraction),
        SATURATION("saturation", R.string.glass_saturation), EXPOSURE("exposure", R.string.glass_exposure, -100, 100, 0),
        DIMMING("dimming", R.string.glass_dimming, -200, 200, 0),
        WARMTH("warmth", R.string.glass_warmth, -200, 200, 0),
        CONTRAST("contrast", R.string.glass_contrast, -150, 150, 0),
        ANGLE("light_angle", R.string.glass_light_angle, -180, 180, 0),
        RESPONSE("tilt_response", R.string.glass_tilt_response, max = 400);

        val key = "${ENABLED}_$id"
    }

    fun style(value: Int?) = when (value) { REFRACTION -> REFRACTION; LIQUID -> LIQUID; else -> SOFT }

    private val rimLightKeys = listOf("${ENABLED}_soft_rim_light", "${ENABLED}_refraction_rim_light", "${ENABLED}_liquid_rim_light")
    fun rimLightKey(materialStyle: Int?) = rimLightKeys[style(materialStyle)]

    val keys = Parameter.entries.mapTo(mutableSetOf(STYLE, CUSTOM, SELECTION, KEEP_WHITE, GYRO, LOW_POWER)) { it.key } +
        rimLightKeys + LiquidGlassTuning.defaults.keys
    fun read(context: Context): Map<String, Int> {
        val prefs = LspPreferenceStore.prefs(context)
        return buildMap {
            put(STYLE, style(prefs.getInt(STYLE, SOFT)))
            put(CUSTOM, prefs.getInt(CUSTOM, 0))
            put(SELECTION, selectionMask(prefs.getInt(SELECTION, 0)))
            put(KEEP_WHITE, prefs.getInt(KEEP_WHITE, 0))
            put(GYRO, prefs.getInt(GYRO, 0))
            put(LOW_POWER, prefs.getInt(LOW_POWER, 0))
            for (key in rimLightKeys) put(key, prefs.getInt(key, 1))
            for (p in Parameter.entries) put(p.key, prefs.getInt(p.key, p.default).coerceIn(p.min, p.max))
        }
    }
    internal fun selectionMask(raw: Int): Int {
        var selection = raw
        if (raw and INDEPENDENT_SEARCH == 0 && raw and Scene.BAR.bit != 0) selection = selection or Scene.GLOBAL_SEARCH.bit
        if (raw and INDEPENDENT_LOCK == 0 && raw and Scene.LOCK.bit != 0) selection = selection or Scene.AOD.bit
        if (raw and INDEPENDENT_DOCK == 0 && raw and Scene.BAR.bit != 0) selection = selection or Scene.DOCK.bit
        return selection and (INDEPENDENT_SEARCH or INDEPENDENT_LOCK or INDEPENDENT_DOCK).inv()
    }

    fun save(context: Context, key: String, value: Int) = ModernXposedPreferenceSync.setIntAndSync(
        context, key, if (key == SELECTION) value or INDEPENDENT_SEARCH or INDEPENDENT_LOCK or INDEPENDENT_DOCK else value
    )

    class Profile(packageName: String) {
        val materialStyle = style(HookConfigSnapshot.int(STYLE, SOFT))
        private val rimLight = HookConfigSnapshot.int(rimLightKey(materialStyle), 1) != 0
        val liquid = materialStyle != SOFT
        val plugin = if (materialStyle == LIQUID) LiquidGlassTuning.Parameters { key, default ->
            HookConfigSnapshot.int(key, default) ?: default
        } else null
        val fallbackSurface = if (packageName == "com.oplus.aod") LiquidGlassTuning.Surface.LOCK else LiquidGlassTuning.Surface.GENERAL
        val keepWhite = HookConfigSnapshot.int(KEEP_WHITE, 0) == 1
        val lowPower = HookConfigSnapshot.int(LOW_POWER, 0) == 1
        val panelProgram = SoftLightGlassShader.PanelProgram.forStyle(materialStyle, lowPower)
        val needsCurrentShader = liquid || lowPower
        val gyro = !lowPower && plugin == null && HookConfigSnapshot.int(GYRO, 0) == 1
        private val selection = if (HookConfigSnapshot.int(CUSTOM, 0) == 1) selectionMask(HookConfigSnapshot.int(SELECTION, 0) ?: 0) else -1
        private val optics = Parameter.entries.map { p ->
            (if (plugin == null) HookConfigSnapshot.int(p.key, p.default) ?: p.default else p.default).coerceIn(p.min, p.max) / 100f
        }
        operator fun get(parameter: Parameter) = if (parameter == Parameter.SHINE && !rimLight) 0f else optics[parameter.ordinal]
        fun blurRadius(pixels: Float, density: Float) = if (lowPower) minOf(pixels * .5f, 6f * density) else pixels
        // Native tint-only backdrops can express the same adjustment by compositing it into their mix color.
        val dimmingOverlay: Float get() {
            val offset = this[Parameter.DIMMING]
            return offset * this[Parameter.STRENGTH] * (if (offset < 0f) .24f else .50f)
        }
        fun adjustMixColor(color: Int): Int {
            val overlay = dimmingOverlay
            if (overlay == 0f) return color
            val amount = abs(overlay)
            val retainedAlpha = Color.alpha(color) / 255f * (1f - amount)
            val alpha = retainedAlpha + amount
            val light = if (overlay < 0f) 255f * amount else 0f
            fun channel(value: Int) = ((value * retainedAlpha + light) / alpha).roundToInt()
            return Color.argb((alpha * 255f).roundToInt(), channel(Color.red(color)),
                channel(Color.green(color)), channel(Color.blue(color)))
        }
        fun selected(scene: Scene) = selection and scene.bit != 0
        // Each component maps to a material owner in the shader. Untagged native blur
        // can only be adjusted when all the possible owners in this process are selected.
        private val owners = when (packageName) {
            "com.android.launcher" -> listOf(Scene.DESKTOP, Scene.FOLDER, Scene.WIDGET, Scene.BAR, Scene.DOCK)
            "com.android.systemui" -> listOf(Scene.SHADE, Scene.CONTROL, Scene.VOLUME, Scene.LOCK)
            "com.android.settings" -> listOf(Scene.SETTINGS)
            "com.coloros.assistantscreen" -> listOf(Scene.ASSISTANT)
            "com.coloros.smartsidebar" -> listOf(Scene.SIDEBAR)
            "com.oplus.aod" -> listOf(Scene.AOD)
            "com.heytap.quicksearchbox" -> listOf(Scene.GLOBAL_SEARCH)
            else -> listOf(Scene.OTHER)
        }
        val channels = FloatArray(4) { if (selected(owners.getOrElse(it) { owners.first() })) 1f else 0f }
        val dockChannels = channels.copyOf().apply { this[3] = if (selected(Scene.DOCK)) 1f else 0f }
        val anyScene = owners.any(::selected)
        val wholeProcess = owners.all(::selected)
    }
    val mainPackages = setOf(
        "com.android.settings", "com.android.systemui", "com.android.launcher",
        "com.coloros.assistantscreen", "com.coloros.smartsidebar", "com.oplus.aod", "com.heytap.quicksearchbox",
    )

    fun enabled(context: Context) = LspPreferenceStore.prefs(context).getBoolean(ENABLED, false)
    fun enabledXposed() = HookConfigSnapshot.boolean(ENABLED, false) == true
    fun setEnabled(context: Context, value: Boolean) {
        ModernXposedPreferenceSync.setBooleanAndSync(context, ENABLED, value)
        if (value) requestInstalledScopes(context)
    }
    fun requestInstalledScopes(context: Context) {
        LspFrameworkService.requestScopes(mainPackages.filterTo(mutableSetOf()) { pkg ->
            runCatching { context.packageManager.getApplicationInfo(pkg,0) }.isSuccess
        })
    }
}
