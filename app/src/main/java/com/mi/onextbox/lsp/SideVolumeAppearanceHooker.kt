package com.mi.onextbox.lsp

import android.view.View
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.QsCornerReflect as Reflect

/** Suppress the key-triggered thin animation when requested. */
internal object SideVolumeAppearanceHooker {
    private const val SEEK = "com.oplus.systemui.volume.OplusVolumeSeekBar"
    private const val TAG = "ONextBox-SideVolumeAppearance"
    private var reported = false

    private fun diagnose(error: Throwable) {
        if (!reported) { reported = true; HookLog.w(TAG, "Volume width adapter unavailable", error) }
    }

    fun hook(loader: ClassLoader) {
        val seekType = Reflect.findClassIfExists(SEEK, loader) ?: return
        val observers = QsCornerViewObservers("volume:appearance-views",
            configurationToken = SideVolumeAppearanceSettings::configurationTokenXposed,
        ) { view ->
            if (SideVolumeAppearanceSettings.stateXposed().keepWide &&
                ((Reflect.getObjectField(view, "mThickType") as Number).toInt() != 0 ||
                    Reflect.callMethod(view, "getHasEnlarge") == true)) {
                Reflect.callMethod(view, "resetSeekBar")
            }
            true
        }
        seekType.declaredConstructors.forEach { constructor ->
            ModernHookRegistry.installCompat("$TAG:observe:${constructor.toGenericString()}", constructor,
                object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable == null) (param.thisObject as? View)?.let(observers::watch)
                    }
                })
        }
        val dialog = Reflect.findClassIfExists("com.oplus.systemui.volume.OplusVolumeDialogImpl", loader)
        dialog?.declaredMethods?.filter { it.name in setOf("startThickAnim", "startKeyDownTransAnim") &&
            it.parameterCount == 1 }?.forEach { method ->
            ModernHookRegistry.installCompat("$TAG:keep-wide:${method.name}", method, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (SideVolumeAppearanceSettings.stateXposed().keepWide && param.args[0] == true)
                        param.result = null
                }
            })
            runCatching { ModernHookRuntime.requireModule().deoptimize(method) }.onFailure(::diagnose)
        }
    }
}
