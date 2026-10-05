package com.mi.onextbox.lsp

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import java.util.concurrent.ConcurrentHashMap

private data class IconPackEntry(
    val packageName: String
)

object NativeNotifyIconRules {
    private val builtInEntries: Map<String, IconPackEntry> = listOf(
        IconPackEntry(packageName = "")
    ).filter { it.packageName.isNotBlank() }
        .associateBy { it.packageName }

    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()

    val hasSupplementRules: Boolean
        get() = builtInEntries.isNotEmpty()

    fun buildSupplementResultForArgsXposed(
        args: Array<Any?>,
        returnType: Class<*>
    ): Any? = buildSupplementResultForArgsXposed(args.asIterable(), returnType)

    fun buildSupplementResultForArgsXposed(
        args: Iterable<Any?>,
        returnType: Class<*>
    ): Any? {
        if (!hasSupplementRules) return null
        val packageName = extractPackageName(args) ?: return null
        if (!builtInEntries.containsKey(packageName)) return null
        val bitmap = getOrBuildBitmap(packageName, Color.WHITE) ?: return null

        return when {
            returnType.isAssignableFrom(Icon::class.java) -> Icon.createWithBitmap(bitmap)
            Drawable::class.java.isAssignableFrom(returnType) -> BitmapDrawable(Resources.getSystem(), bitmap)
            else -> null
        }
    }

    private fun extractPackageName(args: Iterable<Any?>): String? {
        args.forEach { arg ->
            extractFromAny(arg, depth = 0)?.let { return it }
        }
        return null
    }

    private fun extractFromAny(value: Any?, depth: Int): String? {
        if (value == null || depth > 3) return null

        if (value is String) {
            normalizePackageName(value)?.let { return it }
        }

        runCatching {
            val packageNameMethod = value.javaClass.methods.firstOrNull {
                it.name == "getPackageName" && it.parameterCount == 0
            }
            normalizePackageName((packageNameMethod?.invoke(value) as? String).orEmpty())
        }.getOrNull()?.let { return it }

        val nestedMethodNames = arrayOf(
            "getSbn",
            "getStatusBarNotification",
            "getNotificationRecord",
            "getNotification"
        )
        nestedMethodNames.forEach { methodName ->
            runCatching {
                val method = value.javaClass.methods.firstOrNull {
                    it.name == methodName && it.parameterCount == 0
                } ?: return@runCatching null
                extractFromAny(method.invoke(value), depth + 1)
            }.getOrNull()?.let { return it }
        }

        val nestedFieldNames = arrayOf("sbn", "mSbn", "pkg", "packageName", "mPkg")
        nestedFieldNames.forEach { fieldName ->
            runCatching {
                val field = value.javaClass.declaredFields.firstOrNull { it.name == fieldName }
                    ?: return@runCatching null
                field.isAccessible = true
                extractFromAny(field.get(value), depth + 1)
            }.getOrNull()?.let { return it }
        }

        return null
    }

    private fun normalizePackageName(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        val regex = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")
        return if (regex.matches(value)) value else null
    }

    private fun getOrBuildBitmap(packageName: String, color: Int): Bitmap? {
        val cacheKey = "$packageName|$color"
        bitmapCache[cacheKey]?.let { return it }

        val context = resolveXposedContext() ?: return null
        val generated = buildLocalMonochromeBitmap(context, packageName, color) ?: return null
        bitmapCache[cacheKey] = generated
        return generated
    }

    private fun resolveXposedContext(): Context? {
        return runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            val currentApplication = activityThread.getDeclaredMethod("currentApplication")
            currentApplication.invoke(null) as? Context
        }.getOrNull()
    }

    private fun buildLocalMonochromeBitmap(context: Context, packageName: String, tintColor: Int): Bitmap? {
        val appIcon = runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull() ?: return null

        val source = selectDrawableForMonochrome(appIcon) ?: return null
        val size = resolveBitmapSize(source)
        val sourceBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sourceBitmap)
        source.setBounds(0, 0, size, size)
        source.draw(canvas)
        return tintBitmapWithAlphaMask(sourceBitmap, tintColor)
    }

    private fun selectDrawableForMonochrome(drawable: Drawable): Drawable? {
        if (drawable is AdaptiveIconDrawable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                drawable.monochrome?.mutate()?.let { return it }
            }
            drawable.foreground?.mutate()?.let { return it }
            return drawable.mutate()
        }
        return drawable.mutate()
    }

    private fun resolveBitmapSize(drawable: Drawable): Int {
        val maxDefault = 108
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxDefault
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: maxDefault
        return maxOf(48, minOf(192, maxOf(width, height)))
    }

    private fun tintBitmapWithAlphaMask(source: Bitmap, tintColor: Int): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val outR = Color.red(tintColor)
        val outG = Color.green(tintColor)
        val outB = Color.blue(tintColor)

        for (index in pixels.indices) {
            val src = pixels[index]
            val alpha = Color.alpha(src)
            if (alpha == 0) {
                pixels[index] = Color.TRANSPARENT
                continue
            }

            val r = Color.red(src)
            val g = Color.green(src)
            val b = Color.blue(src)
            val luminance = (r * 299 + g * 587 + b * 114) / 1000
            val maskAlpha = maxOf(alpha, luminance)
            pixels[index] = Color.argb(maskAlpha, outR, outG, outB)
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
