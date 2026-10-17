package com.mi.onextbox.lsp

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.graphics.withTranslation
import com.mi.onextbox.lsp.LspConfig.LauncherFeature
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import kotlin.math.max
import kotlin.math.min

// ColorOS removed the drop shadow that AOSP's DotRenderer draws under every notification dot. AOSP
// renders it via ShadowGenerator: a key shadow (blurred black, offset downward) plus an ambient
// shadow (blurred black, centered), behind the colored dot. We redraw those two layers before the
// dot drawable paints, using AOSP's exact constants and the same dot center ColorOS computes.
//
// Two render paths exist: the regular icon dot (OplusDotRenderer.draw) and the big-folder preview
// dot (BigFolderNumBadgeRender.draw). Each computes its dot center differently, so both are hooked.
internal object LauncherNotificationDotShadowHooker {
    private const val TAG = "ONextBox-LauncherDotShadow"
    private const val OPLUS_DOT_RENDERER = "com.android.launcher3.icons.OplusDotRenderer"
    private const val BIG_FOLDER_NUM_BADGE = "com.android.launcher3.folder.big.BigFolderNumBadgeRender"

    // AOSP ShadowGenerator.Builder defaults; DotRenderer sets ambientShadowAlpha = 88.
    private const val AMBIENT_SHADOW_ALPHA = 88
    private const val KEY_SHADOW_ALPHA = 61

    @Volatile
    private var launcherClassLoader: ClassLoader? = null

    fun hook(loader: ClassLoader) {
        launcherClassLoader = loader
        runCatching { installDotShadow(loader) }.onSuccess {
            HookLog.i(TAG, "Dot shadow gate installed")
        }.onFailure {
            HookLog.w(TAG, "Dot shadow unavailable, stock rendering retained", it)
        }
        runCatching { installBigFolderShadow(loader) }.onSuccess {
            HookLog.i(TAG, "Big folder dot shadow gate installed")
        }.onFailure {
            HookLog.w(TAG, "Big folder dot shadow unavailable, stock rendering retained", it)
        }
    }

    private fun installDotShadow(loader: ClassLoader) {
        val renderer = ModernReflect.findClass(OPLUS_DOT_RENDERER, loader)
        val draw = renderer.declaredMethods.single { it.name == "draw" && it.parameterTypes.size == 3 }
        runCatching { ModernHookRuntime.requireModule().deoptimize(draw) }
        ModernHookRegistry.installCompat("$TAG:dot-shadow", draw, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!enabled()) return
                val canvas = param.args[0] as? Canvas ?: return
                val drawParams = param.args[1] ?: return
                val centeredScale = param.args[2] as? Boolean ?: false
                drawDotShadow(canvas, param.thisObject ?: return, drawParams, centeredScale)
            }
        })
    }

    private fun installBigFolderShadow(loader: ClassLoader) {
        val render = ModernReflect.findClass(BIG_FOLDER_NUM_BADGE, loader)
        val draw = render.declaredMethods.single { it.name == "draw" && it.parameterTypes.size == 4 }
        runCatching { ModernHookRuntime.requireModule().deoptimize(draw) }
        ModernHookRegistry.installCompat("$TAG:big-folder-dot-shadow", draw, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!enabled()) return
                val canvas = param.args[0] as? Canvas ?: return
                val drawParams = param.args[1] ?: return
                val rectF = param.args[2] as? RectF
                drawBigFolderShadow(canvas, param.thisObject ?: return, drawParams, rectF)
            }
        })
    }

    private fun drawDotShadow(canvas: Canvas, renderer: Any, drawParams: Any, centeredScale: Boolean) {
        val bounds = ModernReflect.getObjectField(drawParams, "iconBounds") as? Rect ?: return
        val leftAlign = ModernReflect.getObjectField(drawParams, "leftAlign") as? Boolean ?: false
        val dotSize = (ModernReflect.getObjectField(renderer, "mDotSize") as? Number)?.toInt() ?: return
        if (dotSize <= 0) return
        val offset = (ModernReflect.getObjectField(renderer, "mNumBadgeBGOffsetByDesign") as? Number)?.toInt() ?: 0

        // ColorOS nudges the dot inward based on the icon radius type: radiusType==2 subtracts 2dp
        // from x and adds 2dp to y, radiusType==1 does 1dp. offset is always 3dp, so derive the
        // dp-to-px scale from it (Canvas.getDensity() is unreliable here).
        val radiusAdj = when {
            isRadiusType(2) -> offset * 2 / 3
            isRadiusType(1) -> offset / 3
            else -> 0
        }

        // OplusDotRenderer.draw() also grows the corner offset when the dot would overflow the
        // canvas clip (drawer-category icons sit flush against a container edge). Replicate it.
        val effOffset = clipAdjustedOffset(canvas, bounds, leftAlign, dotSize, offset)

        // Replicate OplusDotRenderer.draw()'s dot center: the icon corner inset by the effective
        // offset, then shifted inward by the radius-type adjustment.
        val cx = if (leftAlign) bounds.left + effOffset + radiusAdj else bounds.right - effOffset - radiusAdj
        val cy = bounds.top + effOffset + radiusAdj
        val scale = (ModernReflect.getObjectField(drawParams, "scale") as? Number)?.toFloat() ?: 1f
        paintShadow(canvas, cx.toFloat(), cy.toFloat(), dotSize, scale, centeredScale)
    }

    // Replicates OplusDotRenderer.draw()'s clip guard. When the dot would overflow the canvas, the
    // corner offset grows so the dot stays inside. Right-aligned dots use max(offset, min(sideGap,
    // topGap)); left-aligned dots use dotSize - min(sideGap, topGap).
    private fun clipAdjustedOffset(canvas: Canvas, bounds: Rect, leftAlign: Boolean, dotSize: Int, offset: Int): Int {
        val clip = canvas.clipBounds
        val half = dotSize / 2
        val sideGap = if (leftAlign) bounds.left - clip.left else clip.right - bounds.right
        val topGap = bounds.top - clip.top
        if (sideGap >= half && topGap >= half - 2) return offset
        val m = min(sideGap, topGap)
        return if (leftAlign) dotSize - m else max(offset, m)
    }

    private fun drawBigFolderShadow(canvas: Canvas, renderer: Any, drawParams: Any, rectF: RectF?) {
        val bounds = ModernReflect.getObjectField(drawParams, "iconBounds") as? Rect ?: return
        val leftAlign = ModernReflect.getObjectField(drawParams, "leftAlign") as? Boolean ?: false
        val dotSize = (ModernReflect.getObjectField(renderer, "mDotSize") as? Number)?.toInt() ?: return
        if (dotSize <= 0) return
        val endOffset = (ModernReflect.getObjectField(renderer, "mDotEndOffset") as? Number)?.toFloat() ?: 0f
        val topOffset = (ModernReflect.getObjectField(renderer, "mDotTopOffset") as? Number)?.toFloat() ?: 0f
        val radius = dotSize / 2f

        // Replicate BigFolderNumBadgeRender.draw()'s dot center.
        val baseX = if (leftAlign) {
            max(0f, bounds.left.toFloat() - endOffset)
        } else {
            val right = rectF?.right ?: bounds.right.toFloat()
            min(right - dotSize.toFloat(), bounds.right.toFloat() - dotSize.toFloat() + endOffset)
        }
        val baseY = max(0f, bounds.top.toFloat() - topOffset)
        val scale = (ModernReflect.getObjectField(drawParams, "scale") as? Number)?.toFloat() ?: 1f
        // BigFolderNumBadgeRender translates to the dot center and scales with no pivot, i.e. it
        // scales about the dot center — so mirror that centered pivot, not the top-left origin.
        paintShadow(canvas, baseX + radius, baseY + radius, dotSize, scale, centeredScale = true)
    }

    // Draws AOSP's two shadow layers behind the colored dot the original method paints. ColorOS
    // translates to the dot's top-left (cx-radius, cy-radius) and scales from there, so the shadow
    // must use the same origin to stay glued to the dot through the appear/disappear animation.
    // centeredScale mirrors the z10 branch: centered (radius,radius) pivot vs. top-left origin.
    private fun paintShadow(canvas: Canvas, cx: Float, cy: Float, dotSize: Int, scale: Float, centeredScale: Boolean = false) {
        val radius = dotSize / 2f
        // AOSP ShadowGenerator: shadowBlur = size/24, keyShadowDistance = size/16.
        val blur = dotSize / 24f
        val keyDistance = dotSize / 16f

        canvas.withTranslation(cx - radius, cy - radius) {
            if (scale > 0f && scale != 1f) {
                if (centeredScale) scale(scale, scale, radius, radius) else scale(scale, scale)
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            paint.color = Color.TRANSPARENT
            paint.setShadowLayer(blur, 0f, keyDistance, Color.argb(KEY_SHADOW_ALPHA, 0, 0, 0))
            drawCircle(radius, radius, radius, paint)
            paint.setShadowLayer(blur, 0f, 0f, Color.argb(AMBIENT_SHADOW_ALPHA, 0, 0, 0))
            drawCircle(radius, radius, radius, paint)
        }
    }

    private fun isRadiusType(type: Int): Boolean {
        val loader = launcherClassLoader ?: return false
        return runCatching {
            val i6 = ModernReflect.findClass("com.android.common.util.i6", loader)
            val method = when (type) {
                2 -> "N"
                1 -> "L"
                else -> return@runCatching false
            }
            ModernReflect.callStaticMethod(i6, method) as? Boolean ?: false
        }.getOrDefault(false)
    }

    private fun enabled() = LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.NotificationDotShadow)
}
