package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernHookRegistry
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

internal object CameraAiCompositionHooker {
    const val INSPIRATION = "camera_unlock_ai_composition_inspiration"
    const val SCENE = "camera_unlock_ai_composition_scene"
    private const val FEATURE = "com.oplus.camera.feature.ai_composition"
    private const val KEY = "key_ai_composition"
    private const val BASE = "com.oplus.ocs.camera.configure."

    fun target(loader: ClassLoader): Method = loader.loadClass(BASE + "CameraConfigure").getDeclaredMethod(
        "initCameraTypeFeatureTable", loader.loadClass(BASE + "ProtobufFeatureConfig\$CameraTypeFeatureTable"),
        List::class.java, String::class.java, String::class.java)

    fun hook(loader: ClassLoader) {
        if (!MediaUnlockSettings.enabledXposed(INSPIRATION) && !MediaUnlockSettings.enabledXposed(SCENE)) return
        val tag = "ONextBox-CameraComposition"
        val prefix = "$tag@${System.identityHashCode(loader)}:"
        runCatching {
            val method = target(loader)
            val keyFactory = loader.loadClass(BASE + "CameraFeatureKeyContainer").getDeclaredMethod("initFeatureKey", String::class.java, String::class.java).apply { isAccessible = true }
            val keyType = loader.loadClass(BASE + "CameraFeatureKey")
            val constructor = loader.loadClass(BASE + "ProtobufFeatureInfoImpl").getDeclaredConstructor(
                String::class.java, String::class.java, String::class.java, String::class.java, String::class.java,
                Boolean::class.javaPrimitiveType, keyType).apply { isAccessible = true }
            ModernHookRegistry.installFast(prefix + "native-feature", method, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                if ((!MediaUnlockSettings.enabledXposed(INSPIRATION) && !MediaUnlockSettings.enabledXposed(SCENE)) ||
                    chain.getArg(2) != "photo_mode" || result !is Map<*, *> ||
                    !(chain.getArg(3) as? String).orEmpty().startsWith("rear_")) return@Hooker result
                val key = keyFactory.invoke(null, KEY, "string") ?: return@Hooker result
                if (key in result) return@Hooker result
                val feature = constructor.newInstance(FEATURE, "[on,off]", "off", "string",
                    "main_menu,other_app,video_other_app,quick_launch,watch,gimbal", false, key)
                HookLog.i(tag, "Native composition feature added for ${chain.getArg(3)}")
                HashMap<Any?, Any?>(result).apply { put(key, feature) }
            })
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(tag, "Native composition registration unavailable", it)
        }
    }
}
