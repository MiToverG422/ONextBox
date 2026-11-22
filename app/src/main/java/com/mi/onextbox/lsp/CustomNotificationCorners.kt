package com.mi.onextbox.lsp

import android.view.View
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.abs

/** The OEM row has its own base roundness source, blur background and lockscreen scene. */
internal class CustomNotificationCorners(val type: Class<*>) {
    private val blurManager = type.getDeclaredField("viewBlurManager").apply { isAccessible = true }
    private val controller = blurManager.type.getDeclaredField("statusBarStateController").apply { isAccessible = true }
    private val status = controller.type.getMethod("getState")
    private val defaultRadius = type.getDeclaredField("defaultCornerRadius").apply { isAccessible = true }
    private val baseSource = type.getDeclaredField("BASE_VALUE").apply { isAccessible = true }
    private val request = type.getMethod("requestRoundness", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
        baseSource.type, Boolean::class.javaPrimitiveType)
    private val updateBase = type.getMethod("updateBaseRoundness")
    private val expanding = type.getMethod("isExpandAnimationRunning")
    private val top = type.getMethod("getTopCornerRadius")
    private val bottom = type.getMethod("getBottomCornerRadius")
    private val background = type.getMethod("getMBackgroundNormal")
    private val extension = background.returnType.getMethod("getExt")
    private val setRadius = background.returnType.getMethod("setRadius", Float::class.javaPrimitiveType,
        Float::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)

    private class Radius(val view: WeakReference<View>, var native: Float, var selected: Float, var resting: Boolean)
    private val owned = WeakHashMap<View, Radius>()
    private val materials = WeakHashMap<Any, Radius>()

    fun install() {
        val ext = type.classLoader!!.loadClass("com.oplus.systemui.statusbar.notification.row.NotificationBackgroundViewExtImp")
        val updateRadius = ext.getDeclaredMethod("updateRadius", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
        for (method in listOf(setRadius, updateRadius)) {
            ModernHookRegistry.installCompat("appearance:custom-material:${method.toGenericString()}", method, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val radius = materials[param.thisObject] ?: return
                    val view = radius.view.get() ?: return
                    if (!radius.resting || expanding.invoke(view) == true) return
                    for (i in 0..1) {
                        if (abs((param.args[i] as Float) - radius.native) < .5f) param.args[i] = radius.selected
                    }
                }
            })
        }
    }

    // Mirrors OplusCustomRow.syncClipCanvasToBgView, which treats KEYGUARD (1) specially.
    fun scope(view: View) = if (status.invoke(controller.get(blurManager.get(view))) == 1)
        SystemUiAppearance.Lock else SystemUiAppearance.Shade

    fun apply(view: View, selected: Float, resting: Boolean) {
        val native = defaultRadius.getFloat(view)
        val radius = owned.getOrPut(view) { Radius(WeakReference(view), native, selected, resting) }
        radius.native = native
        radius.selected = selected
        radius.resting = resting
        background.invoke(view)?.let { layer ->
            materials[layer] = radius
            extension.invoke(layer)?.let { materials[it] = radius }
        }
        if (resting && expanding.invoke(view) != true) {
            request.invoke(view, 1f, 1f, baseSource.get(null), false)
            syncBackground(view)
        }
    }

    fun restore(view: View) {
        val previous = owned.remove(view) ?: return
        previous.resting = false
        background.invoke(view)?.let { layer ->
            materials.remove(layer)
            materials.remove(extension.invoke(layer))
        }
        updateBase.invoke(view)
        if (expanding.invoke(view) != true) syncBackground(view)
    }

    fun nativeWrite(view: View, resting: Boolean) {
        owned[view]?.resting = resting
    }

    private fun syncBackground(view: View) {
        background.invoke(view)?.let { setRadius.invoke(it, top.invoke(view), bottom.invoke(view), true) }
    }
}
