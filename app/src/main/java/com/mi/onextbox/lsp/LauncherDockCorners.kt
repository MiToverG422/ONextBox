package com.mi.onextbox.lsp

import android.content.Context
import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings.System
import android.view.View
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import java.lang.reflect.Modifier
import java.util.WeakHashMap

internal object LauncherDockCorners {
    const val MODE = "launcher_dock_corner_mode"
    const val RADIUS = "launcher_dock_corner_radius_dp"
    const val SYSTEM = 0
    const val ICON = 1
    const val CUSTOM = 2
    const val DEFAULT_DP = 24
    const val MAX_DP = 64
    const val FOLDER_COLUMNS = 2
    const val FOLDER_ROWS = 1

    data class Settings(val mode: Int, val radiusDp: Int)

    fun read(context: Context): Settings = LspPreferenceStore.prefs(context).let {
        Settings(it.getInt(MODE, SYSTEM).takeIf { mode -> mode in SYSTEM..CUSTOM } ?: SYSTEM,
            it.getInt(RADIUS, DEFAULT_DP).coerceIn(0, MAX_DP))
    }

    fun mode() = HookConfigSnapshot.int(MODE, SYSTEM) ?: SYSTEM

    fun iconCornerFraction(config: Long): Float? {
        val theme = ((config ushr 4) and 15).toInt()
        val shape = (config ushr 12) and 15
        if (shape != 0L || theme != 2 && theme != 3 && theme !in 6..9) return null
        val radius = ((config ushr 48) and 4095).toInt()
        return when {
            radius in 0..75 -> radius / 150f
            radius and 0xf00 == 0x100 -> .31f // ColorOS oval preset
            else -> null
        }
    }

    class IconChanges {
        private val docks = WeakHashMap<View, Unit>()
        private var resolver: ContentResolver? = null
        private var config = -1L
        private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val active = resolver ?: return
                if (HotReloadLifecycle.isRetiring) return
                config = readConfig(active)
                if (mode() == ICON) attached().forEach { it.requestLayout() }
            }
        }

        private fun readConfig(active: ContentResolver) =
            runCatching { System.getLong(active, ICON_CONFIG, -1L) }.getOrDefault(-1L)

        fun fraction(dock: View): Float? {
            docks[dock] = Unit
            if (resolver == null) {
                val active = dock.context.contentResolver
                active.registerContentObserver(System.getUriFor(ICON_CONFIG), false, observer)
                resolver = active
                config = readConfig(active)
            }
            return iconCornerFraction(config)
        }

        fun attached() = docks.keys.filter { it.isAttachedToWindow }

        fun close() {
            resolver?.unregisterContentObserver(observer)
            resolver = null
            docks.clear()
        }

        private companion object { const val ICON_CONFIG = "key_ux_icon_config" }
    }

    inline fun folderFraction(iconFraction: Float?, readFolder: () -> Float?): Float? {
        if (iconFraction == .5f) return .5f
        return readFolder()?.takeIf { it.isFinite() && it >= 0f } ?: iconFraction
    }

    fun radius(view: View, height: Int, changes: IconChanges, folderReader: ((View) -> Float)?): Float? {
        if (height <= 0) return null
        val px = when (mode()) {
            ICON -> {
                val icon = runCatching { changes.fraction(view) }.getOrNull()
                val fraction = folderFraction(icon) { runCatching { folderReader?.invoke(view) }.getOrNull() }
                (fraction ?: return null) * height
            }
            CUSTOM -> (HookConfigSnapshot.int(RADIUS, DEFAULT_DP) ?: DEFAULT_DP).coerceIn(0, MAX_DP) *
                view.resources.displayMetrics.density
            else -> return null
        }
        return px.takeIf { it.isFinite() && it >= 0f }?.coerceAtMost(height * .5f)
    }

    fun nativeFolderFractionReader(hotseat: Class<*>, loader: ClassLoader): (View) -> Float {
        val launcher = hotseat.getDeclaredField("mLauncher").apply { isAccessible = true }
        val getProfile = launcher.type.getMethod("getDeviceProfile")
        val icon = getProfile.returnType.getDeclaredField("mIconParam").apply { isAccessible = true }
        val sizeGetter = icon.type.methods.firstOrNull { it.name == "getIconSizePx" && it.parameterCount == 0 }
        val sizeField = if (sizeGetter == null) icon.type.getDeclaredField("d").apply { isAccessible = true } else null
        val signature = arrayOf(Context::class.java, java.lang.Float.TYPE, Integer.TYPE, Integer.TYPE, java.lang.Boolean.TYPE)
        val radius = sequenceOf("com.android.common.util.IconUtils", "com.android.common.util.i6")
            .mapNotNull { runCatching { loader.loadClass(it) }.getOrNull() }
            .flatMap { it.declaredMethods.asSequence() }
            .first { it.name in setOf("getFolderIconRadius", "w") && Modifier.isStatic(it.modifiers) &&
                it.returnType == Float::class.javaPrimitiveType && it.parameterTypes.contentEquals(signature) }
        return { dock ->
            val profile = getProfile.invoke(launcher.get(dock))
            val metrics = icon.get(profile)
            val size = ((sizeGetter?.invoke(metrics) ?: sizeField!!.get(metrics)) as Number).toFloat()
            if (size <= 0f) Float.NaN
            else (radius.invoke(null, dock.context, size, FOLDER_COLUMNS, FOLDER_ROWS, true) as Float) / size
        }
    }
}
