package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import java.lang.reflect.Modifier
import com.mi.onextbox.lsp.compat.ModernReflect

internal object Wallpaper3DForceHooker {
    private const val TAG = "ONextBox-3D"
    private const val FEATURE_UTIL = "com.oplus.wallpapers.rely.utils.AppFeatureUtil"

    fun hook(classLoader: ClassLoader?) {
        val featureUtil = ModernReflect.findClassIfExists(FEATURE_UTIL, classLoader)
        if (featureUtil == null) {
            HookLog.w(TAG, "Wallpapers AppFeatureUtil unavailable")
            return
        }
        runCatching {
            val target = featureUtil.getDeclaredMethod("q0")
            require(target.returnType == Boolean::class.javaPrimitiveType && Modifier.isStatic(target.modifiers))
            ModernHookRegistry.installCompat("$TAG:force-enable", target, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (Wallpaper3DForceSettings.enabledXposed()) param.result = true
                }
            })
        }.onSuccess {
            HookLog.i(TAG, "3D wallpaper feature gate hooked")
        }.onFailure {
            HookLog.w(TAG, "3D wallpaper feature gate unavailable", it)
        }
    }
}
