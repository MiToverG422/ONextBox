package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference

/** C16 exposes an OEM provider factory and the legacy AOSP dialog, not C17's plugin factory. */
internal object ColorOs16AospVolumeHooker {
    internal const val NATIVE = "com.android.systemui.volume.VolumeDialogImpl"
    internal const val CALLBACK = "$NATIVE\$7"
    private var component = WeakReference<Any>(null)
    private val bypass = ThreadLocal.withInitial { false }

    fun hook(loader: ClassLoader) = runCatching {
        val host = Class.forName("com.oplus.systemui.dagger.DaggerOplusReferenceGlobalRootComponent\$OplusReferenceSysUIComponentImpl", false, loader)
        val factory = Class.forName("com.oplus.systemui.volume.dagger.OplusVolumeModule_ProvideVolumeDialogFactory", false, loader)
        val module = Class.forName("com.oplus.systemui.volume.dagger.OplusVolumeModule", false, loader)
        val provide = module.declaredMethods.single { it.name == "provideVolumeDialog" && it.parameterCount == 11 }
        ColorOs16AospVolumeOptions.hook(loader)
        AospVolumeCallbackBridge.registerHotReload(loader, CALLBACK)
        HotReloadLifecycle.registerReplay("aosp-volume:c16-component", capture = { component.get() },
            replay = { component = WeakReference(it) })
        host.declaredConstructors.forEachIndexed { index, constructor ->
            ModernHookRegistry.installFast("aosp-volume:c16-component:$index", constructor, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                component = WeakReference(chain.thisObject)
                result
            })
        }
        ModernHookRegistry.installFast("aosp-volume:c16-factory", provide, XposedInterface.Hooker { chain ->
            if (bypass.get() == true || !AospVolumeSettings.enabledXposed()) return@Hooker chain.proceed()
            val owner = component.get() ?: return@Hooker chain.proceed()
            val arguments = chain.args.toTypedArray()
            try {
                AospVolumeCallbackBridge.install(loader, CALLBACK)
                AospVolumeHooker.wrapColorOs16(AospVolumeGraph.createColorOs16(owner)) {
                    bypass.set(true)
                    try { requireNotNull(provide.invoke(null, *arguments)) } finally { bypass.remove() }
                }
            } catch (error: Throwable) {
                HookLog.i("ONextBox-AospVolume", "C16 native volume unavailable; retaining stock: $error")
                chain.proceed()
            }
        })
        factory.declaredMethods.filter { it.name == "get" }.forEach {
            ModernHookRuntime.requireModule().deoptimize(it)
        }
    }.onFailure { HookLog.i("ONextBox-AospVolume", "C16 native volume adapter unavailable: $it") }
}
