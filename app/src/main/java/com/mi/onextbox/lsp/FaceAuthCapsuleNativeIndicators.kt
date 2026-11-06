package com.mi.onextbox.lsp

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import java.lang.ref.WeakReference

/** Owns only the native lock glyph's visibility; charging and other tips keep their layout. */
internal class FaceAuthCapsuleNativeIndicators(
    private val enabled: () -> Boolean,
    private val active: () -> Boolean,
    private val observeMonitor: (Any) -> Unit,
) {
    private var controller = WeakReference<Any>(null)
    private var root = WeakReference<View>(null)
    private var icon = WeakReference<View>(null)
    private var capsule = WeakReference<View>(null)
    private var iconVisibility: Int? = null
    private var nextCapsuleSearch = 0L
    private var replacingGlyph = false

    fun hook(loader: ClassLoader) {
        val type = Reflect.findClass("com.oplus.keyguard.OplusLockIconViewControllerExImpl", loader)
        listOf("lockIconView", "keyguardUpdateMonitor").forEach(type::getDeclaredField)
        val methods = type.declaredMethods.filter { it.name == "init" || it.name == "checkUpdateStateAndVisibility" }
        require(methods.any { it.name == "checkUpdateStateAndVisibility" })
        methods.forEach { method ->
            ModernHookRegistry.installCompat("FaceCapsule:native-tips:${method.name}", method, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.thisObject === controller.get()) restoreIcon()
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    runCatching {
                        val owner = param.thisObject ?: return
                        val host = Reflect.getObjectField(owner, "lockIconView") as? View ?: return
                        if (host.display?.displayId != 0) return
                        if (controller.get() !== owner) {
                            restoreIcon(); capsule.clear(); nextCapsuleSearch = 0L
                        }
                        controller = WeakReference(owner); root = WeakReference(host)
                        icon = WeakReference(Reflect.callMethod(host, "getLockIcon") as? View)
                        Reflect.getObjectField(owner, "keyguardUpdateMonitor")?.let(observeMonitor)
                        reconcile()
                    }.onFailure { HookLog.w("FaceCapsule", "Native indicator update unavailable", it) }
                }
            })
        }
    }

    fun reconcile() {
        if (controller.get() == null) return
        val image = icon.get()
        if (!enabled()) replacingGlyph = false
        else if (active()) replacingGlyph = true
        val hide = enabled() && replacingGlyph
        if (hide && image != null) {
            if (iconVisibility == null) iconVisibility = image.visibility
            if (image.visibility == View.VISIBLE) image.visibility = View.INVISIBLE
        } else restoreIcon()
    }

    fun protectedContentTop(): Float? {
        val host = root.get() ?: return null
        val slots = mutableListOf<View>()
        icon.get()?.let(slots::add)
        for (name in listOf("lockicon_tips_view", "keyguard_date", "keyguard_style_clock")) {
            val id = host.resources.getIdentifier(name, "id", "com.android.systemui")
            if (id != 0) host.rootView.findViewById<View>(id)?.let(slots::add)
        }
        // An empty or invisible tips slot can receive charging text during the success animation.
        return slots.mapNotNull { view ->
            if (!view.isAttachedToWindow || !view.isLaidOut || view.height <= 0) null
            else IntArray(2).also(view::getLocationOnScreen)[1].toFloat()
        }.minOrNull()
    }

    fun capsuleLayout(): FaceAuthCapsuleNativeLayout? = runCatching {
        var source = capsule.get()?.takeIf { it.isAttachedToWindow }
        val now = SystemClock.uptimeMillis()
        if (source == null && now >= nextCapsuleSearch) {
            nextCapsuleSearch = now + 1000L
            source = root.get()?.rootView?.let(::findCapsule)
            capsule = WeakReference(source)
        }
        val view = source ?: return null
        val height = (Reflect.callMethod(view, "getMaxHeight") as? Int)?.toFloat() ?: return null
        if (height <= 0f || view.height < height || !view.isLaidOut) return null
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val top = location[1] + (view.height - height) / 2f
        val res = view.resources
        val pkg = "com.oplus.systemui.plugins"
        val statusHeight = res.getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it != 0 }?.let(res::getDimension) ?: return null
        if (top < 0 || top + height > statusHeight + 1f) return null
        val minWidth = res.getDimension(res.getIdentifier("capsule_view_min_width", "dimen", pkg))
        val radius = res.getDimension(res.getIdentifier("capsule_corner_radius", "dimen", pkg))
        val centerX = location[0] + view.width / 2f
        val hole = runCatching {
            val blankWidth = (Reflect.callMethod(view, "getBlankWidth") as? Int)?.toFloat() ?: return@runCatching null
            val pair = Reflect.callMethod(view, "g", height.toInt()) ?: return@runCatching null
            val holeTop = (Reflect.callMethod(pair, "getFirst") as? Int)?.toFloat() ?: return@runCatching null
            val holeBottom = (Reflect.callMethod(pair, "getSecond") as? Int)?.toFloat() ?: return@runCatching null
            if (blankWidth <= 0 || holeBottom <= holeTop || blankWidth > minWidth) return@runCatching null
            FaceAuthCapsuleBounds(centerX - blankWidth / 2, location[1] + holeTop,
                centerX + blankWidth / 2, location[1] + holeBottom)
        }.getOrNull()
        FaceAuthCapsuleNativeLayout(top, height, minWidth, radius, centerX, hole)
    }.getOrNull()

    private fun findCapsule(view: View): View? {
        if (view.javaClass.name == "com.oplus.systemui.plugins.seedling.capsule.ui.view.CapsuleContainer") return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findCapsule(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun restoreIcon() {
        iconVisibility?.let { visibility -> icon.get()?.visibility = visibility }
        iconVisibility = null
    }

    fun clear() {
        restoreIcon()
        controller.clear(); root.clear(); icon.clear(); capsule.clear()
        nextCapsuleSearch = 0L; replacingGlyph = false
    }
}
