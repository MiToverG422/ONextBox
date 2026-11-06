package com.mi.onextbox.lsp

import android.graphics.Rect
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import java.lang.ref.WeakReference
import java.lang.reflect.Method

/** Adds an owned occupied area to the native dispatcher, preserving all original capsule areas. */
internal class FaceAuthCapsuleAvoidance {
    private var dispatcher = WeakReference<Any>(null)
    private var type: Class<*>? = null
    private var setter: Method? = null
    private var locations = emptyList<Any>()
    private val nativeAreas = HashMap<Any, List<Rect>>()
    private var occupied: Rect? = null
    private var publishing = false
    private var loader: ClassLoader? = null

    fun hook(loader: ClassLoader) {
        this.loader = loader
        val locationType = Reflect.findClass("com.android.systemui.statusbar.phone.StatusBarLocation", loader)
        val target = Reflect.findClass("com.oplus.systemui.statusbar.seeding.HiddenIconDispatcherImpl", loader)
        val method = Reflect.findDeclaredMethodExact(target, "setHiddenIconAreas", locationType, List::class.java)
        locations = listOf("HOME", "KEYGUARD").map { locationType.getDeclaredField(it).get(null)!! }
        type = target; setter = method
        target.declaredConstructors.forEachIndexed { index, constructor ->
            ModernHookRegistry.installCompat("FaceCapsule:avoidance:init:$index", constructor, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!HotReloadLifecycle.isRetiring) runCatching { synchronized(this@FaceAuthCapsuleAvoidance) {
                        param.thisObject?.let(::remember)
                    } }
                }
            })
        }
        ModernHookRegistry.installCompat("FaceCapsule:avoidance:areas", method, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (HotReloadLifecycle.isRetiring) return
                runCatching { synchronized(this@FaceAuthCapsuleAvoidance) {
                    if (publishing) return@synchronized
                    val owner = param.thisObject ?: return@synchronized
                    val location = param.args[0] ?: return@synchronized
                    if (location !in locations) return@synchronized
                    remember(owner)
                    val original = (param.args[1] as? List<*>)?.filterIsInstance<Rect>().orEmpty()
                        .filter { it !== occupied }
                    nativeAreas[location] = original.map(::Rect)
                    param.args[1] = merged(location)
                } }.onFailure { HookLog.w("FaceCapsule", "Native occupied area update unavailable", it) }
            }
        })
        for (name in listOf("com.oplus.systemui.statusbar.seeding.CapsulePluginContainerController",
            "com.oplus.systemui.statusbar.seeding.CapsulePluginContainerController\$setHiddenIconAreas\$1")) {
            Reflect.findClassIfExists(name, loader)?.declaredMethods?.forEach {
                runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
            }
        }
    }

    @Synchronized fun reserve(bounds: FaceAuthCapsuleBounds): Boolean = runCatching {
        if (dispatcher.get() == null) bootstrap()
        if (dispatcher.get() == null || setter == null) return false
        val next = Rect(kotlin.math.floor(bounds.left).toInt() - 2, 0,
            kotlin.math.ceil(bounds.right).toInt() + 2, kotlin.math.ceil(bounds.bottom).toInt())
        if (occupied == next) return true
        occupied = next
        publish()
        true
    }.getOrElse {
        clear()
        HookLog.w("FaceCapsule", "Native icon avoidance unavailable; original indicators retained", it)
        false
    }

    private fun remember(owner: Any) {
        if (dispatcher.get() === owner) return
        dispatcher = WeakReference(owner)
        nativeAreas.clear()
        for (location in locations) {
            nativeAreas[location] = (Reflect.callMethod(owner, "getHiddenAreas", location) as? List<*>)
                .orEmpty().filterIsInstance<Rect>().filter { it !== occupied }.map(::Rect)
        }
    }

    private fun bootstrap() {
        val hostLoader = loader ?: return
        val application = Reflect.callStaticMethod(Reflect.findClass("android.app.ActivityThread", hostLoader),
            "currentApplication") ?: return
        val component = Reflect.getObjectField(application, "mSysUIComponent") ?: return
        val dump = Reflect.callMethod(component, "createDumpManager") ?: return
        val entries = Reflect.callMethod(dump, "getDumpables") as? Collection<*> ?: return
        for (entry in entries.toList()) {
            val owner = Reflect.callMethod(entry, "getDumpable") ?: continue
            if (type?.isInstance(owner) == true) { remember(owner); return }
        }
    }

    private fun merged(location: Any) = ArrayList<Rect>().apply {
        addAll(nativeAreas[location].orEmpty())
        occupied?.let(::add)
    }

    private fun publish() {
        val owner = dispatcher.get() ?: return
        publishing = true
        try { for (location in locations) setter?.invoke(owner, location, merged(location)) }
        finally { publishing = false }
    }

    @Synchronized fun clear() {
        if (occupied != null) {
            occupied = null
            runCatching(::publish).onFailure { HookLog.w("FaceCapsule", "Native icon areas could not be restored", it) }
        }
        dispatcher.clear(); nativeAreas.clear()
    }
}
