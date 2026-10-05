package com.mi.onextbox.lsp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.widget.ImageView
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.LinkedHashMap

/** Native notification-icon policy hooks and grayscale analysis for SystemUI. */
internal object SystemUiNotificationIconHooker {
    private const val TAG = "ONextBox-LSP"
    private const val SMALL_ICON_MAX_DP = 64
    private const val GRAYSCALE_PROBE_MAX_PX = 96
    private const val GRAYSCALE_CACHE_MAX_ENTRIES = 128

    private val grayscaleDrawableCache = Collections.synchronizedMap(
        object : LinkedHashMap<DrawableCacheKey, Boolean>(
            GRAYSCALE_CACHE_MAX_ENTRIES,
            0.75f,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<DrawableCacheKey, Boolean>?,
            ): Boolean = size > GRAYSCALE_CACHE_MAX_ENTRIES
        },
    )
    private val grayscaleResolverLock = Any()
    @Volatile private var systemGrayscaleResolvers: List<SystemGrayscaleResolver>? = null

    private val contrastClassNames = listOf(
        "com.android.internal.util.ContrastColorUtil",
        "com.android.internal.util.NotificationColorUtil",
        "com.oplusos.util.OplusContrastColorUtil",
        "com.oplus.util.OplusContrastColorUtil",
        "com.oplusos.util.OplusNotificationColorUtil",
        "com.oplus.util.OplusNotificationColorUtil",
    )
    private val notificationUtilsClassNames = listOf(
        "com.android.systemui.statusbar.notification.NotificationUtils",
        "com.oplus.systemui.statusbar.notification.NotificationUtils",
        "com.oplusos.systemui.statusbar.notification.NotificationUtils",
    )
    private val iconUtilClassNames = listOf(
        "com.oplus.systemui.statusbar.notification.util.OplusNotificationSmallIconUtil",
        "com.oplusos.systemui.statusbar.notification.util.OplusNotificationSmallIconUtil",
    )
    private val iconManagerClassNames = listOf(
        "com.android.systemui.statusbar.notification.icon.IconManager",
        "com.oplus.systemui.statusbar.notification.icon.IconManager",
        "com.oplusos.systemui.statusbar.notification.icon.IconManager",
    )

    private data class DrawableCacheKey(
        val constantStateIdentity: Int,
        val className: String,
        val width: Int,
        val height: Int,
    )

    private data class SystemGrayscaleResolver(
        val method: Method,
        val target: Any?,
    )

    fun hook(classLoader: ClassLoader?, packageName: String): Int {
        var decisionMethods = 0
        iconUtilClassNames.forEach { className ->
            val hookClass = ModernReflect.findClassIfExists(className, classLoader)
                ?: return@forEach
            hookClass.declaredMethods
                .filter { method ->
                    method.returnType == java.lang.Boolean.TYPE &&
                        method.name.equals("useAppIconForSmallIcon", ignoreCase = true)
                }
                .forEach { method ->
                    hookReturnFalse(method)
                    decisionMethods++
                }
        }

        var grayscaleMethods = 0
        notificationUtilsClassNames.forEach { className ->
            val hookClass = ModernReflect.findClassIfExists(className, classLoader)
                ?: return@forEach
            hookClass.declaredMethods
                .filter { method ->
                    method.returnType == java.lang.Boolean.TYPE &&
                        method.parameterTypes.firstOrNull()?.let(ImageView::class.java::isAssignableFrom) == true &&
                        (method.name.equals("isGrayscale", ignoreCase = true) ||
                            method.name.equals("isGrayscaleOplus", ignoreCase = true))
                }
                .forEach { method ->
                    hookGrayscaleDetector(method, classLoader)
                    grayscaleMethods++
                }
        }

        var descriptorMethods = 0
        if (NativeNotifyIconRules.hasSupplementRules) {
            iconManagerClassNames.forEach { className ->
                val hookClass = ModernReflect.findClassIfExists(className, classLoader)
                    ?: return@forEach
                hookClass.declaredMethods
                    .filter { method ->
                        !method.returnType.isPrimitive &&
                            method.name.equals("getIconDescriptor", ignoreCase = true) &&
                            method.parameterTypes.any { type ->
                                type.name.contains("Notification") || type.name.contains("Entry")
                            }
                    }
                    .forEach { method ->
                        hookReplaceIconDescriptor(method)
                        descriptorMethods++
                    }
            }
        }

        val installed = decisionMethods + grayscaleMethods + descriptorMethods
        HookLog.i(
            TAG,
            if (installed > 0) {
                "SystemUI native-icon API102 hooks installed in $packageName: " +
                    "decision=$decisionMethods, grayscale=$grayscaleMethods, descriptor=$descriptorMethods"
            } else {
                "SystemUI native-icon hooks not matched in $packageName"
            },
        )
        return installed
    }

    private fun hookReturnFalse(method: Method) {
        installFastHook(
            key = "false:${method.toGenericString()}",
            method = method,
            hooker = XposedInterface.Hooker { false },
        )
    }

    private fun hookGrayscaleDetector(method: Method, classLoader: ClassLoader?) {
        installFastHook(
            key = "grayscale:${method.toGenericString()}",
            method = method,
            hooker = XposedInterface.Hooker { chain ->
                val imageView = chain.args.firstOrNull() as? ImageView
                    ?: return@Hooker chain.proceed()
                val drawable = imageView.drawable ?: return@Hooker chain.proceed()
                if (isLargeIconCandidate(imageView, drawable)) {
                    false
                } else {
                    resolveIsGrayscaleFromSystem(classLoader, imageView.context, drawable)
                        ?: isGrayscaleDrawable(drawable, imageView.context)
                }
            },
        )
    }

    private fun hookReplaceIconDescriptor(method: Method) {
        installFastHook(
            key = "descriptor:${method.toGenericString()}",
            method = method,
            hooker = XposedInterface.Hooker { chain ->
                val originalResult = chain.proceed()
                val supplementIcon = NativeNotifyIconRules.buildSupplementResultForArgsXposed(
                    args = chain.args,
                    returnType = Icon::class.java,
                ) as? Icon ?: return@Hooker originalResult

                val descriptor = originalResult ?: return@Hooker originalResult
                when {
                    setIconField(descriptor, supplementIcon) -> descriptor
                    method.returnType.isAssignableFrom(Icon::class.java) -> supplementIcon
                    else -> originalResult
                }
            },
        )
    }

    private fun installFastHook(
        key: String,
        method: Method,
        hooker: XposedInterface.Hooker,
    ) {
        ModernHookRegistry.installFast("systemui:native-icon:$key", method, hooker)
    }

    private fun setIconField(target: Any, icon: Icon): Boolean {
        var current: Class<*>? = target.javaClass
        while (current != null) {
            val field = current.declaredFields.firstOrNull { candidate ->
                Icon::class.java.isAssignableFrom(candidate.type) &&
                    (candidate.name == "icon" || candidate.name == "mIcon")
            }
            if (field != null) {
                return runCatching {
                    field.isAccessible = true
                    field.set(target, icon)
                    true
                }.getOrDefault(false)
            }
            current = current.superclass
        }
        return false
    }

    private fun resolveIsGrayscaleFromSystem(
        classLoader: ClassLoader?,
        context: Context,
        drawable: Drawable,
    ): Boolean? {
        resolveSystemGrayscaleResolvers(classLoader, context).forEach { resolver ->
            val value = runCatching {
                resolver.method.invoke(resolver.target, drawable) as? Boolean
            }.getOrNull()
            if (value != null) return value
        }
        return null
    }

    private fun resolveSystemGrayscaleResolvers(
        classLoader: ClassLoader?,
        context: Context,
    ): List<SystemGrayscaleResolver> {
        systemGrayscaleResolvers?.let { return it }
        return synchronized(grayscaleResolverLock) {
            systemGrayscaleResolvers?.let { return@synchronized it }
            contrastClassNames.mapNotNull { className ->
                val hookClass = ModernReflect.findClassIfExists(className, classLoader)
                    ?: return@mapNotNull null
                val method = hookClass.declaredMethods.firstOrNull { candidate ->
                    candidate.returnType == java.lang.Boolean.TYPE &&
                        (candidate.name.equals("isGrayscaleIcon", ignoreCase = true) ||
                            candidate.name.equals("isGrayscale", ignoreCase = true)) &&
                        candidate.parameterTypes.size == 1 &&
                        Drawable::class.java.isAssignableFrom(candidate.parameterTypes[0])
                } ?: return@mapNotNull null
                method.isAccessible = true

                val target = if (Modifier.isStatic(method.modifiers)) {
                    null
                } else {
                    val factory = hookClass.declaredMethods.firstOrNull { candidate ->
                        candidate.name == "getInstance" &&
                            candidate.parameterTypes.size == 1 &&
                            Context::class.java.isAssignableFrom(candidate.parameterTypes[0]) &&
                            Modifier.isStatic(candidate.modifiers)
                    } ?: return@mapNotNull null
                    runCatching {
                        factory.isAccessible = true
                        factory.invoke(null, context)
                    }.getOrNull() ?: return@mapNotNull null
                }
                SystemGrayscaleResolver(method, target)
            }.also { resolved -> systemGrayscaleResolvers = resolved }
        }
    }

    private fun isGrayscaleDrawable(drawable: Drawable, context: Context): Boolean {
        if (isDrawableTooLargeForSmallIcon(drawable, context)) return false
        val constantState = drawable.constantState
        val cacheKey = constantState?.let {
            DrawableCacheKey(
                constantStateIdentity = System.identityHashCode(it),
                className = drawable.javaClass.name,
                width = drawable.intrinsicWidth,
                height = drawable.intrinsicHeight,
            )
        }
        cacheKey?.let { grayscaleDrawableCache[it] }?.let { return it }

        val probe = drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
        val safeWidth = (probe.intrinsicWidth.takeIf { it > 0 } ?: 64)
            .coerceIn(16, GRAYSCALE_PROBE_MAX_PX)
        val safeHeight = (probe.intrinsicHeight.takeIf { it > 0 } ?: 64)
            .coerceIn(16, GRAYSCALE_PROBE_MAX_PX)
        val bitmap = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
        val result = try {
            probe.setBounds(0, 0, safeWidth, safeHeight)
            probe.draw(Canvas(bitmap))
            isGrayscaleBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
        cacheKey?.let { grayscaleDrawableCache[it] = result }
        return result
    }

    private fun isGrayscaleBitmap(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return true

        val xStep = (width / 32).coerceAtLeast(1)
        val yStep = (height / 32).coerceAtLeast(1)
        for (y in 0 until height step yStep) {
            for (x in 0 until width step xStep) {
                val color = bitmap.getPixel(x, y)
                if (Color.alpha(color) == 0) continue
                val red = Color.red(color)
                val green = Color.green(color)
                val blue = Color.blue(color)
                if (
                    kotlin.math.abs(red - green) > 10 ||
                    kotlin.math.abs(red - blue) > 10 ||
                    kotlin.math.abs(green - blue) > 10
                ) return false
            }
        }
        return true
    }

    private fun isLargeIconCandidate(imageView: ImageView, drawable: Drawable): Boolean {
        val maxSizePx = resolveSmallIconMaxPx(imageView.context)
        val viewWidth = imageView.width.takeIf { it > 0 }
            ?: imageView.measuredWidth.takeIf { it > 0 }
            ?: 0
        val viewHeight = imageView.height.takeIf { it > 0 }
            ?: imageView.measuredHeight.takeIf { it > 0 }
            ?: 0
        return viewWidth > maxSizePx || viewHeight > maxSizePx ||
            isDrawableTooLargeForSmallIcon(drawable, imageView.context)
    }

    private fun isDrawableTooLargeForSmallIcon(drawable: Drawable, context: Context): Boolean {
        val maxSizePx = resolveSmallIconMaxPx(context)
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: return false
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: return false
        return width > maxSizePx || height > maxSizePx
    }

    private fun resolveSmallIconMaxPx(context: Context): Int =
        (SMALL_ICON_MAX_DP * context.resources.displayMetrics.density)
            .toInt()
            .coerceAtLeast(64)
}
