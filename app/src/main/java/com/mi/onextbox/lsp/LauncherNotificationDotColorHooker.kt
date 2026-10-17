package com.mi.onextbox.lsp

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils
import com.mi.onextbox.lsp.LspConfig.LauncherFeature
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import java.lang.reflect.Modifier

// ColorOS paints every notification badge — the plain dot and the number pill — with one fixed
// red (#ffeb3b2f). This hooker tints the shared dot drawable and the number pill gradient with the
// host icon's dominant color for the duration of each draw, restoring the stock red afterwards.
//
// The dominant color is restored at the source. Workspace icons carry color=0 because
// OplusLauncherActivityCachingLogic.loadIcon() builds the BitmapInfo via BitmapInfo.fromBitmap()
// (hard-codes color=0), and IconCache later clones/repaints that BitmapInfo (copying color=0) before
// the drawable is made. So the only reliable restore point is BitmapInfo.newIcon(), the sole creator
// of the drawable: it reads this.color into the new FastBitmapDrawable. We fill this.color there
// (before the constructor consumes it) using ColorOS's own ColorExtractor over the icon bitmap.
internal object LauncherNotificationDotColorHooker {
    private const val TAG = "ONextBox-LauncherDotColor"
    private const val OPLUS_DOT_RENDERER = "com.android.launcher3.icons.OplusDotRenderer"
    private const val BITMAP_INFO = "com.android.launcher3.icons.BitmapInfo"
    private const val COLOR_EXTRACTOR = "com.android.launcher3.icons.ColorExtractor"
    private const val BIG_FOLDER_PREVIEW = "com.android.launcher3.folder.big.BigFolderPreviewItemManager"
    private const val BIG_FOLDER_NUM_BADGE = "com.android.launcher3.folder.big.BigFolderNumBadgeRender"
    private const val FOLDER_ICON = "com.android.launcher3.folder.FlexibleFolderIcon"

    // The red dot drawable is a process-wide singleton reached through an obfuscated lazy holder
    // (f4.k -> Lht/c -> launcher_unread_notification_dot). It inflates from an XML <shape> to a
    // GradientDrawable; the only other Lazy<Drawable> in f4 (ic_shadow_under_btv) is a PNG
    // BitmapDrawable, so matching the type selects the dot unambiguously.
    @Volatile
    private var dotDrawable: GradientDrawable? = null

    @Volatile
    private var extractor: Any? = null

    @Volatile
    private var launcherClassLoader: ClassLoader? = null

    // Carries the color from BigFolderPreviewItemManager.drawDotIfNecessary to BigFolderNumBadgeRender.draw.
    private val pendingFolderDotColor = ThreadLocal<Int>()

    fun hook(loader: ClassLoader) {
        launcherClassLoader = loader
        runCatching { installRestoreColor(loader) }.onSuccess {
            HookLog.i(TAG, "Icon color restoration installed")
        }.onFailure {
            HookLog.w(TAG, "Icon color restoration unavailable, stock color retained", it)
        }
        runCatching { installDot(loader) }.onSuccess {
            HookLog.i(TAG, "Dot color gate installed")
        }.onFailure {
            HookLog.w(TAG, "Dot color unavailable, stock color retained", it)
        }
        runCatching { installNumber(loader) }.onSuccess {
            HookLog.i(TAG, "Number badge color gate installed")
        }.onFailure {
            HookLog.w(TAG, "Number badge color unavailable, stock color retained", it)
        }
        runCatching { installBigFolder(loader) }.onSuccess {
            HookLog.i(TAG, "Big folder badge color gate installed")
        }.onFailure {
            HookLog.w(TAG, "Big folder badge color unavailable, stock color retained", it)
        }
        runCatching { installFolderIcon(loader) }.onSuccess {
            HookLog.i(TAG, "Folder icon badge color gate installed")
        }.onFailure {
            HookLog.w(TAG, "Folder icon badge color unavailable, stock color retained", it)
        }
    }

    // Recompute the icon's dominant color at drawable creation when newIcon() is about to read a
    // color that is still unset (0). BitmapInfo.color is final, but we mutate it before the
    // constructor consumes it; this is the one point where the value reliably reaches the drawable.
    private fun installRestoreColor(loader: ClassLoader) {
        val info = ModernReflect.findClass(BITMAP_INFO, loader)
        val newIcon = info.declaredMethods.single {
            it.name == "newIcon" && it.parameterTypes.size == 2 && it.returnType.name == "com.android.launcher3.icons.FastBitmapDrawable"
        }
        runCatching { ModernHookRuntime.requireModule().deoptimize(newIcon) }
        ModernHookRegistry.installCompat("$TAG:restore-color", newIcon, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!enabled()) return
                restoreColor(param.thisObject ?: return)
            }
        })
    }

    private fun installDot(loader: ClassLoader) {
        val renderer = ModernReflect.findClass(OPLUS_DOT_RENDERER, loader)
        val draw = renderer.declaredMethods.single { it.name == "draw" && it.parameterTypes.size == 3 }
        runCatching { ModernHookRuntime.requireModule().deoptimize(draw) }
        ModernHookRegistry.installCompat("$TAG:dot", draw, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!enabled()) return
                // The renderer pipeline calls setDrawDotView(hostView) right before draw(), so the
                // host BubbleTextView is available here even though draw() has no host parameter.
                val host = ModernReflect.getObjectField(param.thisObject ?: return, "drawDotView") ?: return
                val color = iconColor(host) ?: return
                val drawable = resolveDotDrawable(loader) ?: return
                drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                resolveDotDrawable(loader)?.clearColorFilter()
            }
        })
    }

    private fun installNumber(loader: ClassLoader) {
        val renderer = ModernReflect.findClass(OPLUS_DOT_RENDERER, loader)
        val drawNumber = renderer.declaredMethods.single { it.name == "drawNumber" && it.parameterTypes.size == 5 }
        runCatching { ModernHookRuntime.requireModule().deoptimize(drawNumber) }
        ModernHookRegistry.installCompat("$TAG:number", drawNumber, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!enabled()) return
                // drawNumber(Canvas, int count, DrawParams, Boolean, View host) — the last arg is
                // the host BubbleTextView.
                val color = iconColor(param.args[4]) ?: return
                val pill = numberPill(param.thisObject) ?: return
                pill.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                setTextColorForBg(param.thisObject, color)
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                numberPill(param.thisObject)?.clearColorFilter()
            }
        })
    }

    // Big folders paint a notification dot per previewed app via BigFolderNumBadgeRender, which    // builds its own DotRenderer$DrawParams (no host view) and never sees the app's icon color. We
    // compute the color in drawDotIfNecessary (which has the WorkspaceItemInfo) and hand it to the
    // badge renderer through a thread-local; both the plain dot and the number pill read it.
    private fun installBigFolder(loader: ClassLoader) {
        val manager = ModernReflect.findClass(BIG_FOLDER_PREVIEW, loader)
        val drawDot = manager.declaredMethods.single {
            it.name == "drawDotIfNecessary" && it.parameterTypes.size == 2 &&
                it.parameterTypes[1].name.endsWith("PreviewItemDrawingParams")
        }
        runCatching { ModernHookRuntime.requireModule().deoptimize(drawDot) }
        ModernHookRegistry.installCompat("$TAG:big-folder-dot-color", drawDot, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!enabled()) return
                val params = param.args[1] ?: return
                val item = ModernReflect.getObjectField(params, "item") ?: return
                val info = ModernReflect.getObjectField(item, "bitmap") ?: return
                val color = ModernReflect.getIntField(info, "color")
                if (color == 0 || color == -0x1000000) return
                pendingFolderDotColor.set(scrim(color))
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                pendingFolderDotColor.remove()
            }
        })

        val render = ModernReflect.findClass(BIG_FOLDER_NUM_BADGE, loader)
        val draw = render.declaredMethods.single { it.name == "draw" && it.parameterTypes.size == 4 }
        val drawNumber = render.declaredMethods.single {
            it.name == "drawNumberForBigFolderItem" && it.parameterTypes.size == 6
        }
        runCatching { ModernHookRuntime.requireModule().deoptimize(draw) }
        runCatching { ModernHookRuntime.requireModule().deoptimize(drawNumber) }
        ModernHookRegistry.installCompat("$TAG:big-folder-dot", draw, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!enabled()) return
                val color = pendingFolderDotColor.get() ?: return
                val drawable = resolveDotDrawable(loader) ?: return
                drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                resolveDotDrawable(loader)?.clearColorFilter()
            }
        })
        ModernHookRegistry.installCompat("$TAG:big-folder-number", drawNumber, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!enabled()) return
                val color = pendingFolderDotColor.get() ?: return
                val pill = numberPill(param.thisObject) ?: return
                pill.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                setTextColorForBg(param.thisObject, color)
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                numberPill(param.thisObject)?.clearColorFilter()
            }
        })
    }

    // A folder icon aggregates several apps; its notification dot belongs to whichever app inside
    // actually has a notification. FlexibleFolderIcon.drawDot() (the desktop folder icon) draws via
    // mDotRenderer without setting drawDotView first, so the regular installDot hook never sees a
    // host. Hook drawDot directly, resolve the first notified item in the folder's contents, and
    // tint the shared dot drawable with that app's dominant color for the duration of the draw.
    private fun installFolderIcon(loader: ClassLoader) {
        val folderIcon = ModernReflect.findClass(FOLDER_ICON, loader)
        val drawDot = folderIcon.declaredMethods.single { it.name == "drawDot" && it.parameterTypes.size == 1 }
        runCatching { ModernHookRuntime.requireModule().deoptimize(drawDot) }
        ModernHookRegistry.installCompat("$TAG:folder-icon-dot", drawDot, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!enabled()) return
                val host = param.thisObject ?: return
                val color = folderDotColor(host) ?: return
                // Both the plain dot (draw) and the number pill (drawNumber) read from the renderer,
                // so tint both for the duration of the draw.
                val drawable = resolveDotDrawable(loader)
                drawable?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                val pill = numberPill(ModernReflect.getObjectField(host, "mDotRenderer"))
                pill?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                val renderer = ModernReflect.getObjectField(host, "mDotRenderer")
                setTextColorForBg(renderer, color)
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                resolveDotDrawable(loader)?.clearColorFilter()
                numberPill(ModernReflect.getObjectField(param.thisObject ?: return, "mDotRenderer"))?.clearColorFilter()
            }
        })
    }

    // Resolve the first notified app inside the folder, then return its (scrimmed) dominant color.
    private fun folderDotColor(folderIcon: Any): Int? {
        val folderInfo = ModernReflect.getObjectField(folderIcon, "mInfo") ?: return null
        val contents = ModernReflect.getObjectField(folderInfo, "contents") as? List<*> ?: return null
        val activity = ModernReflect.getObjectField(folderIcon, "mActivity") ?: return null
        for (item in contents) {
            if (item == null) continue
            val dotInfo = runCatching {
                ModernReflect.callMethod(activity, "getDotInfoForItem", item)
            }.getOrNull() ?: continue
            // A non-null DotInfo is not enough; it must actually show a badge (number type 1 or dot
            // type 2), matching OplusFolderDotInfo.extractDotInfoFromItems().
            val hasNumber = runCatching { ModernReflect.callMethod(dotInfo, "getBadgeType") as? Number }
                .getOrNull()?.toInt() == 1
            val hasDot = runCatching { ModernReflect.callMethod(dotInfo, "canShowDot") as? Boolean }
                .getOrNull() == true
            if (!hasNumber && !hasDot) continue
            val info = ModernReflect.getObjectField(item, "bitmap") ?: continue
            val color = ModernReflect.getIntField(info, "color")
            if (color != 0 && color != -0x1000000) return scrim(color)
            val bitmap = ModernReflect.getObjectField(info, "icon") as? Bitmap ?: continue
            val extracted = dominantColor(bitmap) ?: continue
            return scrim(extracted)
        }
        return null
    }

    // Runs ColorOS's own ColorExtractor over the icon bitmap when color is unset (0 or opaque
    // black, the extractor's "no dominant color" fallback). Workspace icons are HARDWARE-config
    // bitmaps whose getPixel() throws, so copy to ARGB_8888 first.
    private fun restoreColor(info: Any) {
        val before = ModernReflect.getIntField(info, "color")
        val bitmap = ModernReflect.getObjectField(info, "icon") as? Bitmap
        // 0 (unset) and 0xFF000000 (extractor's black fallback) both mean "no real color".
        if (before != 0 && before != -0x1000000) return
        if (bitmap == null) return
        val value = dominantColor(bitmap) ?: return
        runCatching { ModernReflect.setIntField(info, "color", value) }
    }

    // Extract the dominant color via ColorOS's own ColorExtractor. Returns null when the icon has
    // no usable color. Workspace icons are HARDWARE-config bitmaps whose getPixel() throws, so copy
    // to ARGB_8888 first.
    private fun dominantColor(bitmap: Bitmap): Int? {
        val ex = extractor() ?: return null
        val software = if (bitmap.config == Bitmap.Config.HARDWARE) {
            runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: return null
        } else {
            bitmap
        }
        val value = runCatching { ModernReflect.callMethod(ex, "findDominantColorByHue", software) as? Number }
            .getOrNull()?.toInt() ?: 0
        return if (value == 0 || value == -0x1000000) null else value
    }

    // AOSP IconPalette.getMutedColor(color, 0.54f): composite white at alpha 138 over the color.
    private fun scrim(color: Int): Int {
        val r = (Color.red(color) * (255 - 138) + 255 * 138) / 255
        val g = (Color.green(color) * (255 - 138) + 255 * 138) / 255
        val b = (Color.blue(color) * (255 - 138) + 255 * 138) / 255
        return Color.rgb(r, g, b)
    }

    private fun extractor(): Any? {
        extractor?.let { return it }
        val loader = launcherClassLoader ?: return null
        extractor = runCatching {
            ModernReflect.newInstance(ModernReflect.findClass(COLOR_EXTRACTOR, loader))
        }.getOrNull()
        return extractor
    }

    // host -> icon drawable -> getIconColor(), which already applies the 54% white scrim. Stacked
    // category preview icons (OplusStackedBubbleTextView) carry a translucent placeholder white and
    // their tag is a list of AppInfo, so resolve the color from the first notified app instead.
    // During the category-folder open transition the icon is a temporary LayerDrawable whose
    // getIconColor() returns null, so fall back to the host's tag bitmap color.
    private fun iconColor(host: Any?): Int? {
        if (host == null) return null
        val tag = runCatching { ModernReflect.callMethod(host, "getTag") }.getOrNull()
        if (tag is List<*>) {
            // Stacked category icon: pick the first app that actually has a notification.
            val activity = ModernReflect.getObjectField(host, "mActivity")
            for (app in tag) {
                if (app == null) continue
                val dotInfo = runCatching {
                    ModernReflect.callMethod(activity, "getDotInfoForItem", app)
                }.getOrNull() ?: continue
                val hasNumber = runCatching { ModernReflect.callMethod(dotInfo, "getBadgeType") as? Number }
                    .getOrNull()?.toInt() == 1
                val hasDot = runCatching { ModernReflect.callMethod(dotInfo, "canShowDot") as? Boolean }
                    .getOrNull() == true
                if (!hasNumber && !hasDot) continue
                val info = ModernReflect.getObjectField(app, "bitmap") ?: continue
                val color = ModernReflect.getIntField(info, "color")
                if (color != 0 && color != -0x1000000 && Color.alpha(color) == 0xFF) return scrim(color)
                val bitmap = ModernReflect.getObjectField(info, "icon") as? Bitmap ?: continue
                val extracted = dominantColor(bitmap) ?: continue
                return scrim(extracted)
            }
            return null
        }
        val icon = runCatching { ModernReflect.callMethod(host, "getIcon") }.getOrNull() ?: return null
        val value = runCatching { ModernReflect.callMethod(icon, "getIconColor") as? Number }.getOrNull()?.toInt()
        if (value != null && value != 0 && Color.alpha(value) == 0xFF) return value
        // Fallback: the icon is a transient LayerDrawable (category-folder open animation) or its
        // color is unset; read the dominant color from the item's bitmap.
        return itemBitmapColor(tag)
    }

    // Read the (scrimmed) dominant color from a WorkspaceItemInfo's bitmap, extracting it if the
    // stored color is still unset.
    private fun itemBitmapColor(item: Any?): Int? {
        if (item == null) return null
        val info = ModernReflect.getObjectField(item, "bitmap") ?: return null
        val color = ModernReflect.getIntField(info, "color")
        if (color != 0 && color != -0x1000000 && Color.alpha(color) == 0xFF) return scrim(color)
        val bitmap = ModernReflect.getObjectField(info, "icon") as? Bitmap ?: return null
        val extracted = dominantColor(bitmap) ?: return null
        return scrim(extracted)
    }

    private fun numberPill(host: Any?): GradientDrawable? =
        ModernReflect.getObjectField(host ?: return null, "mGradientNumBg") as? GradientDrawable

    // Pick black or white text for the number pill based on the (scrimmed) background luminance,
    // using Android's WCAG relative luminance so the count stays readable over any icon color.
    private fun setTextColorForBg(renderer: Any?, bg: Int) {
        if (renderer == null) return
        val textColor = if (ColorUtils.calculateLuminance(bg) > 0.5) Color.BLACK else Color.WHITE
        runCatching { ModernReflect.setIntField(renderer, "mBadgeNumTextColor", textColor) }
    }

    private fun resolveDotDrawable(loader: ClassLoader): GradientDrawable? {
        dotDrawable?.let { return it }
        dotDrawable = runCatching {
            val holder = ModernReflect.findClass("com.android.common.util.f4", loader)
            holder.declaredFields.asSequence()
                .filter { Modifier.isStatic(it.modifiers) && it.type.name == "ht.c" }
                .firstNotNullOfOrNull { field ->
                    runCatching {
                        field.isAccessible = true
                        ModernReflect.callMethod(field.get(null), "getValue") as? GradientDrawable
                    }.getOrNull()
                }
        }.getOrNull()
        return dotDrawable
    }

    private fun enabled() = LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.ColorfulNotificationDot)
}
