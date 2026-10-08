package com.mi.onextbox.lsp

import android.content.res.Resources
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.View
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import io.github.libxposed.api.XposedInterface
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

// Gesture navigation insets, handle dimensions, and system colors.
internal object ImmersiveNavigationHooker {
    private data class ResourceName(val packageName: String, val type: String, val name: String)
    private val resourceNames = Collections.synchronizedMap(
        WeakHashMap<Resources, ConcurrentHashMap<Int, ResourceName>>(),
    )
    private val dimensionMethods = setOf("getDimension", "getDimensionPixelSize", "getDimensionPixelOffset")
    private val colorMethods = setOf("getColor", "getColorStateList")
    private val resourcePackages = setOf("android", "com.android.systemui", "oplus")

    fun hook(classLoader: ClassLoader? = null) {
        if (!LspConfig.isImmersiveNavigationEnabledXposed() &&
            !LspConfig.isNavigationHandleCustomLengthEnabledXposed()
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
                val handleClass = Class.forName(className, false, classLoader)
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
            ModernHookRegistry.installCompat(
                key = "immersive-navigation:resources:${method.toGenericString()}",
                executable = method,
                callback = object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable != null) return
                        val resources = param.thisObject as? Resources ?: return
                        val id = param.args.firstOrNull() as? Int ?: return
                        val name = resourceName(resources, id) ?: return
                        if (name.packageName !in resourcePackages) return
                        if (name.type == "color" && method.name in colorMethods) {
                            replacementColor(method.name, resources, name)?.let { param.result = it }
                        } else if (name.type == "bool" && method.name == "getBoolean" &&
                            ImmersiveNavigationRules.isBoolean(name.packageName, name.name) &&
                            LspConfig.isImmersiveNavigationEnabledXposed()
                        ) {
                            ImmersiveNavigationRules.boolean(
                                name.packageName, name.name, gestureNavigation(resources),
                            )?.let { param.result = it }
                        } else if (name.type == "dimen" && method.name in dimensionMethods) {
                            replacementDimension(method.name, resources, name)?.let { param.result = it }
                        }
                    }
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
            ModernHookRegistry.installCompat(
                key = "immersive-navigation:typed-array:${method.toGenericString()}",
                executable = method,
                callback = object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable != null) return
                        val values = param.thisObject as? TypedArray ?: return
                        val index = param.args.firstOrNull() as? Int ?: return
                        val id = values.getResourceId(index, 0)
                        val name = resourceName(values.resources, id) ?: return
                        if (name.type == "color" && method.name in colorMethods) {
                            replacementColor(method.name, values.resources, name)?.let { param.result = it }
                        } else if (name.type == "dimen" && method.name !in colorMethods) {
                            replacementDimension(method.name, values.resources, name)?.let { param.result = it }
                        }
                    }
                },
            )
        }
        HookLog.i("ImmersiveNavigation", "Navigation resource hooks installed")
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
            resourceNames.getOrPut(resources) { ConcurrentHashMap() }
        }
        return names[id] ?: runCatching {
            ResourceName(
                resources.getResourcePackageName(id),
                resources.getResourceTypeName(id),
                resources.getResourceEntryName(id),
            )
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
            else -> (pixels + 0.5f).toInt().coerceAtLeast(1)
        }
    }
}
