package com.mi.onextbox.lsp

import android.app.Dialog
import android.os.Handler
import android.os.Looper
import android.view.View
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal object AospVolumeOptionsHooker {
    private const val TAG = "ONextBox-AospVolume"
    private const val PRODUCER = "com.android.systemui.volume.dialog.domain.interactor.VolumeDialogCallbacksInteractor\$VolumeDialogEventModelProducer"
    private val subscribers = WeakHashMap<Any, Boolean>()
    private var shadeState = WeakReference<Any>(null)

    fun hook(loader: ClassLoader) {
        install("size") { hookSize(loader) }
        install("control-center visibility") { hookQsVisibility(loader) }
    }

    private fun install(name: String, action: () -> Unit) {
        runCatching(action).onFailure { HookLog.i(TAG, "Native volume $name adapter unavailable: $it") }
    }

    private fun hookSize(loader: ClassLoader) {
        val sizer = AospVolumeViewSize()
        HotReloadLifecycle.register("aosp-volume:size", detach = sizer::restore, restore = { sizer.reapply() },
            canPrepare = { !sizer.isShowing() }, canReload = { !sizer.isShowing() })
        val binder = Class.forName("com.android.systemui.volume.dialog.ui.binder.VolumeDialogViewBinder", false, loader)
        val bind = binder.declaredMethods.single { it.name == "bind" && Dialog::class.java in it.parameterTypes }
        ModernHookRegistry.installFast("aosp-volume:size:dialog", bind, XposedInterface.Hooker { chain ->
            val result = chain.proceed()
            runCatching {
                val dialog = chain.args.filterIsInstance<Dialog>().single()
                val id = dialog.context.resources.getIdentifier("volume_dialog", "id", "com.android.systemui")
                dialog.findViewById<View>(id)?.let { sizer.apply(it, AospVolumeSettings.scaleXposed()) }
            }.onFailure { HookLog.i(TAG, "Native volume sizing failed: $it") }
            result
        })
        // Floating stream sliders are inflated after the main dialog is bound.
        val slider = Class.forName("com.android.systemui.volume.dialog.sliders.ui.VolumeDialogSliderViewBinder", false, loader)
        ModernHookRegistry.installFast("aosp-volume:size:slider", slider.getDeclaredMethod("bind", View::class.java),
            XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                runCatching { sizer.apply(chain.getArg(0) as View, AospVolumeSettings.scaleXposed()) }
                result
            })
        val dimensions = Class.forName("com.android.systemui.volume.dialog.sliders.ui.VolumeSliderDimensions", false, loader)
        val constructor = dimensions.getDeclaredConstructor(*Array(4) { Float::class.javaPrimitiveType!! }).apply { isAccessible = true }
        val fields = listOf("iconSize", "thumbHeight", "thumbWidth", "trackSize").map { dimensions.getDeclaredField(it).apply { isAccessible = true } }
        val companion = Class.forName("${dimensions.name}\$Companion", false, loader)
        listOf("getVertical", "getHorizontal").forEach { name ->
            ModernHookRegistry.installFast("aosp-volume:size:$name", companion.getDeclaredMethod(name), XposedInterface.Hooker { chain ->
                val original = chain.proceed() ?: return@Hooker null
                val percent = AospVolumeSettings.scaleXposed()
                if (percent == 100) original else runCatching {
                    constructor.newInstance(*fields.map { it.getFloat(original) * percent / 100f }.toTypedArray())
                }.getOrDefault(original)
            })
        }
        // The small dimension getters can be inlined into the native Compose call site.
        Class.forName("com.android.systemui.volume.dialog.sliders.ui.VolumeDialogSliderViewBinderKt", false, loader)
            .declaredMethods.filter { it.name == "VolumeDialogSlider" }.forEach { method ->
                runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
            }
    }

    private fun hookQsVisibility(loader: ClassLoader) {
        val producer = Class.forName(PRODUCER, false, loader)
        val dismiss = producer.getMethod("onDismissRequested", Int::class.javaPrimitiveType)
        val main = Handler(Looper.getMainLooper())
        var wasQsVisible = false
        fun dismissIfNeeded() {
            if (!AospVolumeSettings.hideInQsXposed() || !qsVisible()) return
            val targets = synchronized(subscribers) { subscribers.keys.toList() }
            targets.forEach { runCatching { dismiss.invoke(it, 2) } }
        }
        HotReloadLifecycle.registerReplay("aosp-volume:qs-state", capture = {
            arrayOf(shadeState.get(), synchronized(subscribers) { subscribers.keys.toTypedArray() })
        }, replay = { saved ->
            val values = saved as? Array<*>
            shadeState = WeakReference(values?.getOrNull(0))
            wasQsVisible = qsVisible()
            (values?.getOrNull(1) as? Array<*>)?.filterNotNull()?.forEach { synchronized(subscribers) { subscribers[it] = true } }
        })
        producer.declaredConstructors.forEachIndexed { index, ctor ->
            ModernHookRegistry.installFast("aosp-volume:subscriber:$index", ctor, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                chain.thisObject?.let { synchronized(subscribers) { subscribers[it] = true } }
                result
            })
        }
        val show = producer.getMethod("onShowRequested", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        ModernHookRegistry.installFast("aosp-volume:qs:show", show, XposedInterface.Hooker { chain ->
            if (AospVolumeSettings.hideInQsXposed() && qsVisible()) {
                chain.thisObject?.let { runCatching { dismiss.invoke(it, 2) } }
                null
            } else chain.proceed()
        })
        val controller = Class.forName("com.android.systemui.shade.NotificationShadeWindowControllerImpl", false, loader)
        val apply = controller.declaredMethods.single { it.name == "apply" && it.parameterTypes.singleOrNull()?.simpleName == "NotificationShadeWindowState" }
        ModernHookRegistry.installFast("aosp-volume:qs:state", apply, XposedInterface.Hooker { chain ->
            shadeState = WeakReference(chain.getArg(0))
            val result = chain.proceed()
            val visible = qsVisible()
            val justOpened = visible && !wasQsVisible
            wasQsVisible = visible
            if (justOpened) {
                if (Looper.myLooper() == Looper.getMainLooper()) dismissIfNeeded()
                else main.post { HotReloadLifecycle.withHook(fallback = {}, action = ::dismissIfNeeded) }
            }
            result
        })
    }

    private fun qsVisible() = AospVolumeQsState.visible(shadeState.get())
}

internal object AospVolumeQsState {
    fun visible(state: Any?): Boolean = state?.let {
        listOf("qsExpanded", "qsPanelVisible").any { name ->
            runCatching { state.javaClass.getDeclaredField(name).apply { isAccessible = true }.getBoolean(state) }.getOrDefault(false)
        }
    } ?: false
}
