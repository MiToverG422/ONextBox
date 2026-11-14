package com.mi.onextbox.lsp

import android.app.KeyguardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/** Tags the background that owns the sample, independently of progress fills, masks and glyphs. */
internal class SoftLightPanelPass(
    private val packageName: String,
    private val skipWhiteControl: () -> Boolean,
    private val skipWhiteVolume: () -> Boolean,
    private val report: (String) -> Unit,
) {
    private class Surface(val view: WeakReference<View>, val role: Int, val locked: Boolean)
    private data class Tint(val role: Int, val dark: Boolean)
    private val programs = WeakHashMap<RuntimeShader, Tint>()
    private val keyguardPrograms = WeakHashMap<RuntimeShader, Boolean>()
    private val aodPrograms = WeakHashMap<RuntimeShader, Boolean>()
    private val surfaces = WeakHashMap<Drawable, Surface>()
    private val kinds = Collections.synchronizedMap(WeakHashMap<Drawable, Int>())
    private val progressRadii = Collections.synchronizedMap(WeakHashMap<Drawable, Float>())
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val drawing = ThreadLocal<Int>()
    private val baseRole = when (packageName) {
        "com.coloros.smartsidebar" -> SIDEBAR
        "com.coloros.assistantscreen" -> ASSISTANT
        "com.heytap.quicksearchbox" -> GLOBAL_SEARCH
        "com.oplus.aod" -> AOD
        else -> UNSPECIFIED
    }
    private var lockedAt = -500L
    private var locked = false
    private var dozing = false

    private fun role(name: String): Int {
        val s = name.lowercase(java.util.Locale.ROOT)
        return when {
            ".qs." in s || "controlcenter" in s || "control_center" in s ||
                "quicksettings" in s || "quick_settings" in s || "qstile" in s || "qs_tile" in s -> QS
            ".volume." in s || "volumedialog" in s || "volume_dialog" in s ||
                "volumepanel" in s || "volume_panel" in s -> VOLUME
            ".keyguard." in s || ".lockscreen." in s || "keyguardstacking" in s -> LOCK
            else -> baseRole
        }
    }

    fun created(shader: RuntimeShader, context: Context, lockscreenPlugin: Boolean, supportsAod: Boolean) {
        synchronized(programs) {
            if (lockscreenPlugin) keyguardPrograms[shader] = true
            if (supportsAod) aodPrograms[shader] = true
        }
        val owner = drawing.get() ?: if (lockscreenPlugin) LOCK else if (baseRole != UNSPECIFIED) baseRole else
            Thread.currentThread().stackTrace.firstNotNullOfOrNull { frame -> role(frame.className).takeIf { it != UNSPECIFIED } } ?: UNSPECIFIED
        configure(shader, owner, context)
    }

    private fun configure(shader: RuntimeShader, owner: Int, context: Context) {
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        updateLockState(context)
        val surface: Int
        synchronized(programs) {
            surface = if (owner == LOCK && dozing && aodPrograms[shader] == true) AOD else owner
            if (programs[shader]?.let { it.role == surface && it.dark == dark } == true) return
            shader.setFloatUniform("onbPanelSurface", surface.toFloat(), if (dark) .30f else .63f)
            programs[shader] = Tint(surface, dark)
        }
        when (surface) {
            QS -> report("control center diffusion")
            VOLUME -> report("hardware volume dialog diffusion")
            VOLUME_MORE -> report("hardware volume expand button material")
            LOCK -> report("keyguard card material")
            AOD -> report("always-on display material")
            SIDEBAR -> report("sidebar material")
            ASSISTANT -> report("assistant material")
            GLOBAL_SEARCH -> report("global search material")
            FOREGROUND -> report("native progress fill preserved")
        }
    }

    private fun updateLockState(context: Context) {
        val now = SystemClock.uptimeMillis()
        if (now - lockedAt < 400L) return
        lockedAt = now
        locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        dozing = locked && context.getSystemService(PowerManager::class.java)?.isInteractive == false
    }

    private inline fun withRole(owner: Int, block: () -> Any?): Any? {
        val previous = drawing.get()
        drawing.set(owner)
        return try { block() } finally { if (previous == null) drawing.remove() else drawing.set(previous) }
    }

    fun installSources(loader: ClassLoader) {
        if (packageName != "com.android.systemui") return
        val qsProgressShape = QsSliderProgressShape()
        val volume = ModernReflect.findClassIfExists("com.oplus.systemui.volume.utils.material.VolumeSurfaceFactory", loader)
        val create = volume?.declaredMethods?.singleOrNull { it.name == "createContent" && it.parameterCount == 1 && Drawable::class.java.isAssignableFrom(it.returnType) }
        if (create != null) {
            val kind = create.parameterTypes[0].getMethod("getKind")
            val host = create.parameterTypes[0].methods.firstOrNull {
                it.name == "getHost" && it.parameterCount == 0 && View::class.java.isAssignableFrom(it.returnType)
            }
            ModernHookRegistry.installFast("soft-light:volume-source", create, XposedInterface.Hooker { chain ->
                val value = kind.invoke(chain.getArg(0)) as? Enum<*>
                val selected = when (value?.name) {
                    "BAR_PROGRESS" -> VOLUME_PROGRESS
                    "SIDE_ACCESSORY" -> if (isVolumeMore(host?.invoke(chain.getArg(0)) as? View)) VOLUME_MORE else VOLUME
                    "BAR_BACKGROUND", "PANEL_BACKGROUND", "SETTINGS_BUTTON" -> VOLUME
                    else -> return@Hooker chain.proceed()
                }
                withRole(selected) { chain.proceed() }.also { if (it is Drawable) kinds[it] = selected }
            })
        }
        val qs = ModernReflect.findClassIfExists("com.oplus.systemui.qs.base.util.QsSeekBarBlurManager", loader)
        qs?.declaredMethods?.filter { (it.name == "getSeekBarActiveDrawable" || it.name == "getSeekBarBackground") &&
            Drawable::class.java.isAssignableFrom(it.returnType) }?.forEach { method ->
            val selected = if (method.name == "getSeekBarActiveDrawable") FOREGROUND else QS
            ModernHookRegistry.installFast("soft-light:qs-source:${method.name}", method, XposedInterface.Hooker { chain ->
                withRole(selected) { chain.proceed() }.also { if (it is Drawable) kinds[it] = selected }
            })
        }
        qs?.declaredMethods?.filter { it.name == "applySeekBarActiveBlurConfig" || it.name == "applySeekBarBgBlurConfig" }
            ?.forEach { method ->
                val drawableIndex = method.parameterTypes.indexOfFirst { Drawable::class.java.isAssignableFrom(it) }
                if (drawableIndex < 0) return@forEach
                val selected = if (method.name == "applySeekBarActiveBlurConfig") FOREGROUND else QS
                ModernHookRegistry.installFast("soft-light:qs-update:${method.name}", method, XposedInterface.Hooker { chain ->
                    (chain.getArg(drawableIndex) as? Drawable)?.let { kinds[it] = selected }
                    withRole(selected) { chain.proceed() }
                })
            }
        val wrapper = ModernReflect.findClassIfExists("com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable", loader)
        val placeholder = wrapper?.methods?.firstOrNull {
            it.name == "getDefaultDrawable" && it.parameterCount == 0 && Drawable::class.java.isAssignableFrom(it.returnType)
        }
        val manager = ModernReflect.findClassIfExists("com.oplus.systemui.volume.utils.material.VolumeBlurManager", loader)
        manager?.declaredMethods?.firstOrNull {
            it.name == "syncVolumeBarProgressCorner" && it.parameterCount >= 2 &&
                it.parameterTypes[0] == wrapper && it.parameterTypes[1] == Float::class.javaPrimitiveType
        }?.let { method ->
            ModernHookRegistry.installFast("soft-light:volume-progress-corner", method, XposedInterface.Hooker { chain ->
                val progress = chain.getArg(0) as? Drawable
                if (progress != null && skipWhiteVolume()) progressRadii[progress] = chain.getArg(1) as Float
                chain.proceed()
            })
        }
        wrapper?.getDeclaredMethod("draw", Canvas::class.java)?.let { method ->
            ModernHookRegistry.installFast("soft-light:surface-draw", method, XposedInterface.Hooker { chain ->
                val drawable = chain.thisObject as Drawable
                val selected = kinds[drawable] ?: if (progressRadii.containsKey(drawable)) VOLUME_PROGRESS else return@Hooker chain.proceed()
                if (selected == FOREGROUND && skipWhiteControl()) {
                    val proxy = ModernReflect.callMethod(drawable, "getViewBlurProxy")
                    val config = proxy?.let { ModernReflect.callMethod(it, "getBlurConfig") }
                    val radius = config?.let { ModernReflect.callMethod(it, "getCornerRadius") as? Float }
                    if (radius != null) {
                        val path = runCatching {
                            val weight = ModernReflect.callMethod(config, "getRadiusWeight") as? Float
                            qsProgressShape.path(drawable, radius, weight)
                        }.onFailure { report("native control progress shape unavailable: ${it.javaClass.simpleName}") }.getOrNull()
                        if (path != null) {
                            progressPaint.color = android.graphics.Color.WHITE
                            progressPaint.alpha = drawable.alpha
                            (chain.getArg(0) as Canvas).drawPath(path, progressPaint)
                            report("solid white control progress")
                            return@Hooker null
                        }
                    }
                }
                if (selected == VOLUME_PROGRESS && skipWhiteVolume() && placeholder != null) {
                    val solid = runCatching { placeholder.invoke(drawable) as? GradientDrawable }.getOrNull()
                    val color = solid?.color?.let { it.getColorForState(drawable.state, it.defaultColor) }
                    if (solid != null && color != null && SoftLightSelection.white(color)) {
                        // drawActiveTrack already clipped the canvas to the current (animated) level.
                        val radius = progressRadii[drawable] ?: solid.cornerRadius
                        val b = drawable.bounds
                        progressPaint.color = color
                        (chain.getArg(0) as Canvas).drawRoundRect(b.left.toFloat(), b.top.toFloat(),
                            b.right.toFloat(), b.bottom.toFloat(), radius, radius, progressPaint)
                        report("solid white volume progress")
                        return@Hooker null
                    }
                }
                withRole(selected) { chain.proceed() }
            })
        }
    }

    fun drawableAdapter(type: Class<*>, owns: (RuntimeShader) -> Boolean, app: () -> Context?): ((Drawable) -> Unit)? {
        val field = type.declaredFields.singleOrNull { it.type.name == "com.oplus.posteffect.agsl.DrawableShader" } ?: return null
        val current = field.type.declaredFields.singleOrNull { it.type == RuntimeShader::class.java } ?: return null
        field.isAccessible = true
        current.isAccessible = true
        return update@{ drawable ->
            if (Looper.myLooper() != Looper.getMainLooper()) return@update
            val material = field.get(drawable) ?: return@update
            val shader = current.get(material) as? RuntimeShader ?: return@update
            if (!owns(shader)) return@update
            var selected = drawing.get() ?: kinds[drawable]
            var callback = drawable.callback
            var depth = 0
            while (callback is Drawable && depth++ < 12) {
                if (selected == null) selected = kinds[callback]
                callback = callback.callback
            }
            val view = callback as? View
            val context = view?.context ?: app() ?: return@update
            if (selected == null && synchronized(programs) { keyguardPrograms[shader] == true }) selected = LOCK
            if (selected != null || baseRole != UNSPECIFIED) {
                configure(shader, selected ?: baseRole, context)
                return@update
            }
            if (view == null || !view.isAttachedToWindow) return@update
            updateLockState(context)
            val previous = surfaces[drawable]
            val surface = if (previous?.view?.get() === view && previous.locked == locked) previous else {
                Surface(WeakReference(view), ownerRole(view, locked), locked).also { surfaces[drawable] = it }
            }
            if (surface.role != UNSPECIFIED || previous != null && previous !== surface && previous.role != UNSPECIFIED)
                configure(shader, surface.role, view.context)
        }
    }

    private fun isVolumeMore(view: View?): Boolean = view != null && view.id > 0 &&
        runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull() == "more_row_stream_ll"

    private fun ownerRole(view: View, onLockscreen: Boolean): Int {
        val title = (view.rootView.layoutParams as? WindowManager.LayoutParams)?.title?.toString().orEmpty()
        if (role(title) == VOLUME) return if (isVolumeMore(view)) VOLUME_MORE else VOLUME
        var current: View? = view
        while (current != null) {
            val v = current
            val name = v.javaClass.name
            if (onLockscreen && (name.endsWith("NotificationBackgroundView") || name.endsWith("ExpandableNotificationRow"))) return LOCK
            val kind = role(name)
            if (kind != UNSPECIFIED) return kind
            if (v.id > 0) {
                val id = runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull()
                if (id != null && role(id) != UNSPECIFIED) return role(id)
            }
            current = v.parent as? View
        }
        return role(title)
    }

    fun capture(): Any = arrayOf(
        synchronized(kinds) { kinds.map { (drawable, kind) -> arrayOf(drawable, kind) }.toTypedArray() },
        synchronized(programs) { programs.map { (shader, tint) -> arrayOf(shader, tint.role, tint.dark) }.toTypedArray() },
        synchronized(programs) { keyguardPrograms.keys.toTypedArray() },
        synchronized(programs) { aodPrograms.keys.toTypedArray() },
    )

    fun restore(saved: Any?) {
        val state = saved as? Array<*> ?: return
        (state[0] as? Array<*>)?.filterIsInstance<Array<*>>()?.forEach { kinds[it[0] as Drawable] = it[1] as Int }
        synchronized(programs) {
            (state.getOrNull(2) as? Array<*>)?.filterIsInstance<RuntimeShader>()?.forEach { keyguardPrograms[it] = true }
            (state.getOrNull(3) as? Array<*>)?.filterIsInstance<RuntimeShader>()?.forEach { aodPrograms[it] = true }
            (state[1] as? Array<*>)?.filterIsInstance<Array<*>>()?.forEach {
                programs[it[0] as RuntimeShader] = Tint(it[1] as Int, it[2] as Boolean)
            }
        }
    }

    private companion object {
        const val VOLUME_PROGRESS = -2
        const val FOREGROUND = -1
        const val UNSPECIFIED = 0
        const val QS = 1
        const val VOLUME = 2
        const val LOCK = 3
        const val ASSISTANT = 4
        const val SIDEBAR = 5
        const val VOLUME_MORE = 6
        const val GLOBAL_SEARCH = 7
        const val AOD = 8
    }
}
