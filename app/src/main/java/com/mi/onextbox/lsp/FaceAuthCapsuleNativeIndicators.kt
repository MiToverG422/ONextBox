package com.mi.onextbox.lsp

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
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
    private var pluginHost = WeakReference<ViewGroup>(null)
    private var iconVisibility: Int? = null
    private var nextCapsuleSearch = 0L

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
                            restoreIcon(); capsule.clear(); pluginHost.clear(); nextCapsuleSearch = 0L
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
        val hide = enabled() && active() && !pluginShowing()
        if (hide && image != null) {
            if (iconVisibility == null) iconVisibility = image.visibility
            if (image.visibility == View.VISIBLE) image.visibility = View.INVISIBLE
        } else restoreIcon()
    }

    fun keyguardHost(): ViewGroup? {
        pluginHost.get()?.takeIf { it.isAttachedToWindow }?.let { return it }
        val host = root.get()?.rootView ?: return null
        val id = host.resources.getIdentifier("keyguard_seeding_card_container", "id", "com.android.systemui")
        return if (id == 0) null else host.findViewById<ViewGroup>(id)?.also { pluginHost = WeakReference(it) }
    }

    private fun pluginContainer(): View? = capsule.get()?.takeIf { it.isAttachedToWindow }
        ?: keyguardHost()?.let(::findCapsule)?.also { capsule = WeakReference(it) }

    fun inflateShell(): View? {
        val plugin = pluginContainer() as? ViewGroup ?: return null
        val context = plugin.context
        val id = context.resources.getIdentifier("seedling_capsule_view", "layout", "com.oplus.systemui.plugins")
        if (id == 0) return null
        return LayoutInflater.from(context).inflate(id, plugin, false)
            .takeIf { it.javaClass.name == "com.oplus.systemui.plugins.seedling.capsule.ui.view.CapsuleView" }
    }

    fun pluginShowing(): Boolean {
        val content = pluginContainer() as? ViewGroup ?: return false
        if (!content.isShown) return false
        // On the C17 plugin these are the backing fields of setIsAnimatorRunning and
        // setIsAnimatorContinuity. The departing item may already have left the list.
        return runCatching {
            val running = Reflect.getObjectField(content, "s") as Boolean
            val continuing = Reflect.getObjectField(content, "t") as Boolean
            if (running || continuing) return@runCatching true
            if ((Reflect.callMethod(content, "getCapsuleViewNum") as Int) > 0) return@runCatching true
            (0 until content.childCount).any { index ->
                val child = content.getChildAt(index)
                child.visibility == View.VISIBLE && child.alpha > 0f && child.width > 0 && child.height > 0
            }
        }.getOrDefault(true)
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
            nextCapsuleSearch = now + 250L
            source = keyguardHost()?.let(::findCapsule)
            capsule = WeakReference(source)
        }
        source?.takeIf { it.isAttachedToWindow }?.let(FaceAuthCapsuleNativePosition::read)
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
        controller.clear(); root.clear(); icon.clear(); capsule.clear(); pluginHost.clear()
        nextCapsuleSearch = 0L
    }
}
