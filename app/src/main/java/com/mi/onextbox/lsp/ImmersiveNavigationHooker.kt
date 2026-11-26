package com.mi.onextbox.lsp

import android.content.res.AssetManager
import android.content.res.ColorStateList
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.View
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import io.github.libxposed.api.XposedInterface
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

// Gesture navigation insets, handle dimensions, and system colors.
internal object ImmersiveNavigationHooker {
    private data class ResourceName(val packageName: String, val type: String, val name: String)
    private val resourceNames = WeakHashMap<AssetManager, ConcurrentHashMap<Int, ResourceName>>()
    private val dimensionMethods = setOf("getDimension", "getDimensionPixelSize", "getDimensionPixelOffset")
    private val colorMethods = setOf("getColor", "getColorStateList")
    private val resourcePackages = setOf("android", "com.android.systemui", "oplus")

    fun hook(classLoader: ClassLoader? = null) {
        if (!LspConfig.isImmersiveNavigationEnabledXposed() &&
            !LspConfig.isNavigationHandleCustomLengthEnabledXposed() &&
            !LspConfig.isNavigationHandleAutoHideEnabledXposed()
        ) return
        runCatching { installHooks() }
            .onFailure { HookLog.w("ImmersiveNavigation", "Navigation resource hooks unavailable", it) }
        if (classLoader != null && LspConfig.isNavigationHandleCustomLengthEnabledXposed()) {
            installOpacityHooks(classLoader)
        }
    }

    private fun installOpacityHooks(classLoader: ClassLoader) {
        for (className in listOf(
            "com.oplus.systemui.navigationbar.gesture.sidegesture.OplusNavigationHandle",
            "com.android.systemui.navigationbar.gestural.NavigationHandle",
        )) {
            runCatching {
                val handleClass = HostClassLookup.load(className, false, classLoader)
                val paintField = handleClass.getDeclaredField("mPaint").apply { isAccessible = true }
                val drawMethod = handleClass.getDeclaredMethod("onDraw", Canvas::class.java)
                ModernHookRegistry.installFast(
                    key = "navigation-handle:opacity:${drawMethod.toGenericString()}",
                    executable = drawMethod,
                    hooker = XposedInterface.Hooker { chain ->
                        if (!LspConfig.isNavigationHandleCustomLengthEnabledXposed()) return@Hooker chain.proceed()
                        val opacity = LspConfig.getNavigationHandleOpacityXposed()
                        if (opacity == ImmersiveNavigationRules.SYSTEM_DEFAULT) return@Hooker chain.proceed()
                        val view = chain.thisObject as? View ?: return@Hooker chain.proceed()
                        if (!gestureNavigation(view.resources)) return@Hooker chain.proceed()
                        val paint = paintField.get(view) as? Paint ?: return@Hooker chain.proceed()
                        val originalAlpha = paint.alpha
                        val alpha = ImmersiveNavigationRules.handleAlpha(originalAlpha, opacity)
                        if (alpha == originalAlpha) return@Hooker chain.proceed()
                        paint.alpha = alpha
                        try {
                            chain.proceed()
                        } finally {
                            // Restore the paint after drawing to preserve system colors and fading.
                            paint.alpha = originalAlpha
                        }
                    },
                )
            }.onFailure { HookLog.w("ImmersiveNavigation", "Handle opacity hook unavailable: $className", it) }
        }
    }

    private fun installHooks() {
        Resources::class.java.declaredMethods.filter { method ->
            method.parameterTypes.firstOrNull() == Int::class.javaPrimitiveType && when {
                method.name in colorMethods -> method.parameterTypes.size == 1 ||
                    method.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, Resources.Theme::class.java))
                else -> method.parameterTypes.size == 1 && (method.name in dimensionMethods || method.name == "getBoolean")
            }
        }.forEach { method ->
            val type = when (method.name) {
                in colorMethods -> "color"
                "getBoolean" -> "bool"
                else -> "dimen"
            }
            ModernHookRegistry.installFast(
                key = "immersive-navigation:resources:${method.toGenericString()}",
                executable = method,
                hooker = XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    if (!needsLookup(type)) return@Hooker result
                    val resources = chain.thisObject as? Resources ?: return@Hooker result
                    val id = chain.getArg(0) as? Int ?: return@Hooker result
                    replacement(type, method.name, resources, id) ?: result
                },
            )
        }
        // Handle dimensions and colors from the system theme.
        TypedArray::class.java.declaredMethods.filter { method ->
            method.name in dimensionMethods || method.name == "getLayoutDimension" || method.name in colorMethods
        }.filter { method ->
            method.parameterTypes.firstOrNull() == Int::class.javaPrimitiveType &&
                (method.name in colorMethods || method.parameterTypes.size == 2)
        }.forEach { method ->
            val type = if (method.name in colorMethods) "color" else "dimen"
            ModernHookRegistry.installFast(
                key = "immersive-navigation:typed-array:${method.toGenericString()}",
                executable = method,
                hooker = XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    if (!needsLookup(type)) return@Hooker result
                    val values = chain.thisObject as? TypedArray ?: return@Hooker result
                    val index = chain.getArg(0) as? Int ?: return@Hooker result
                    val id = values.getResourceId(index, 0)
                    replacement(type, method.name, values.resources, id) ?: result
                },
            )
        }
        HookLog.i("ImmersiveNavigation", "Navigation resource hooks installed")
    }

    private fun needsLookup(type: String): Boolean = when (type) {
        "color" -> LspConfig.isNavigationHandleAutoHideEnabledXposed()
        "bool" -> LspConfig.isImmersiveNavigationEnabledXposed()
        else -> true
    }

    private fun replacement(type: String, method: String, resources: Resources, id: Int): Any? {
        val name = resourceName(resources, id) ?: return null
        if (name.type != type || name.packageName !in resourcePackages) return null
        return when (type) {
            "color" -> replacementColor(method, resources, name)
            "bool" -> if (ImmersiveNavigationRules.isBoolean(name.packageName, name.name)) {
                ImmersiveNavigationRules.boolean(name.packageName, name.name, gestureNavigation(resources))
            } else null
            else -> replacementDimension(method, resources, name)
        }
    }

    private fun replacementColor(method: String, resources: Resources, name: ResourceName): Any? {
        if (!ImmersiveNavigationRules.isFadedHandleColor(name.packageName, name.name)) return null
        val color = ImmersiveNavigationRules.fadedHandleColor(
            name.packageName, name.name,
            gestureNavigation = gestureNavigation(resources),
            autoHideEnabled = LspConfig.isNavigationHandleAutoHideEnabledXposed(),
        ) ?: return null
        return if (method == "getColorStateList") ColorStateList.valueOf(color) else color
    }

    private fun resourceName(resources: Resources, id: Int): ResourceName? {
        if (id == 0) return null
        val names = synchronized(resourceNames) {
            resourceNames.getOrPut(resources.assets) { ConcurrentHashMap() }
        }
        return names[id] ?: runCatching {
            // Android returns the canonical "package:type/entry" in one native lookup.
            val qualified = resources.getResourceName(id)
            val packageEnd = qualified.indexOf(':')
            val typeEnd = qualified.indexOf('/', packageEnd + 1)
            if (packageEnd <= 0 || typeEnd <= packageEnd + 1 || typeEnd == qualified.lastIndex) return@runCatching null
            ResourceName(qualified.substring(0, packageEnd), qualified.substring(packageEnd + 1, typeEnd),
                qualified.substring(typeEnd + 1))
        }.getOrNull()?.also { names[id] = it }
    }

    private fun gestureNavigation(resources: Resources): Boolean {
        val id = resources.getIdentifier("config_navBarInteractionMode", "integer", "android")
        return id != 0 && resources.getInteger(id) == 2
    }

    private fun replacementDimension(method: String, resources: Resources, name: ResourceName): Any? {
        if (!ImmersiveNavigationRules.isDimension(name.packageName, name.name)) return null
        if (!gestureNavigation(resources)) return null
        val immersiveEnabled = LspConfig.isImmersiveNavigationEnabledXposed()
        val customLengthEnabled = LspConfig.isNavigationHandleCustomLengthEnabledXposed()
        if (name.packageName == "android" && name.name == "navigation_bar_frame_height") {
            if (!immersiveEnabled) return null
            val id = resources.getIdentifier("navigation_bar_frame_height_gestural", "dimen", "android")
            if (id == 0) return null
            return when (method) {
                "getDimension" -> resources.getDimension(id)
                "getDimensionPixelOffset" -> resources.getDimensionPixelOffset(id)
                else -> resources.getDimensionPixelSize(id)
            }
        }
        val dimension = ImmersiveNavigationRules.dimension(
            name.packageName,
            name.name,
            resources.configuration.smallestScreenWidthDp,
            gestureNavigation = true,
            immersiveEnabled = immersiveEnabled,
            customLengthEnabled = customLengthEnabled,
            lengthDp = if (customLengthEnabled) LspConfig.getNavigationHandleLengthDpXposed()
                else ImmersiveNavigationRules.DEFAULT_LENGTH_DP,
            geometryDp = { HookConfigSnapshot.int(it.key, ImmersiveNavigationRules.SYSTEM_DEFAULT)
                ?: ImmersiveNavigationRules.SYSTEM_DEFAULT },
        ) ?: return null
        return dimensionResult(method, dimension, resources)
    }

    private fun dimensionResult(
        method: String,
        dimension: ImmersiveNavigationRules.Dimension,
        resources: Resources,
    ): Any {
        val pixels = TypedValue.applyDimension(
            if (dimension.inPixels) TypedValue.COMPLEX_UNIT_PX else TypedValue.COMPLEX_UNIT_DIP,
            dimension.value,
            resources.displayMetrics,
        )
        return when (method) {
            "getDimension" -> pixels
            "getDimensionPixelOffset" -> pixels.toInt()
            else -> if (pixels == 0f) 0 else (pixels + 0.5f).toInt().coerceAtLeast(1)
        }
    }
}
