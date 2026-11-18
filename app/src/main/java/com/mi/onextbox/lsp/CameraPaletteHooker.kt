package com.mi.onextbox.lsp

import android.opengl.EGL14
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

internal object CameraPaletteHooker {
    private const val ROOT = "com.oplus.ocs.camera."
    fun cameraTarget(loader: ClassLoader): Method = loader.loadClass(ROOT + "consumer.ApsProcessor").getDeclaredMethod(
        "createMetaItemInfo", loader.loadClass(ROOT + "common.util.CameraRequestTag"))
    fun galleryTargets(loader: ClassLoader): List<Method> = loader.loadClass(
        ROOT + "ipusdk.processunit.palette.PaletteProcessUnitSDK").let {
            listOf(it.getDeclaredMethod("isTextureRequest"), it.getDeclaredMethod("processImage"))
        }

    fun camera(loader: ClassLoader) {
        if (!MediaUnlockSettings.enabledXposed(CameraPaletteSupport.OPTION)) return
        val tag = "ONextBox-CameraPalette"
        val prefix = "$tag@${System.identityHashCode(loader)}:"
        runCatching {
            val method = cameraTarget(loader)
            val request = method.parameterTypes.single()
            val palette = request.getDeclaredField("mbPaletteEnable")
            val id = request.getDeclaredField("mRearFrontCameraId")
            val aps = request.getDeclaredField("mApsRequestTag")
            val mode = aps.type.getDeclaredField("mModeName")
            val convert = loader.loadClass(ROOT + "consumer.ApsDataConvert").getDeclaredMethod("getApsModeName", String::class.java, Int::class.javaPrimitiveType)
            val support = loader.loadClass(ROOT + "consumer.apsAdapter.config.AlgoSwitchConfig").getDeclaredMethod(
                "getSupportCaptureAlgo", String::class.java, Int::class.javaPrimitiveType, String::class.java)
            val key = loader.loadClass(ROOT + "common.util.ParameterKeys").getField("KEY_CAPTURE_ALGO_LIST")
            val get = method.returnType.methods.single { it.name == "get" && it.parameterTypes.contentEquals(arrayOf(key.type)) }
            val set = method.returnType.methods.single { it.name == "setParameter" && it.parameterCount == 2 && it.parameterTypes[0] == key.type }
            ModernHookRegistry.installFast(prefix + "capture", method, XposedInterface.Hooker { chain ->
                val result = chain.proceed() ?: return@Hooker null
                if (!MediaUnlockSettings.enabledXposed(CameraPaletteSupport.OPTION)) return@Hooker result
                val input = chain.getArg(0) ?: return@Hooker result
                if (!palette.getBoolean(input)) return@Hooker result
                val camera = id.getInt(input)
                val cameraMode = aps.get(input)?.let { mode.get(it) } ?: return@Hooker result
                val nativeMode = convert.invoke(null, cameraMode, camera) as? String ?: return@Hooker result
                if (support.invoke(null, nativeMode, camera, CameraPaletteAlgorithms.ID) != true) return@Hooker result
                val algorithmKey = key.get(null)
                val previous = (get.invoke(result, algorithmKey) as? Array<*>)?.map { it as String }?.toTypedArray() ?: return@Hooker result
                val complete = CameraPaletteAlgorithms.include(previous)
                if (complete !== previous) {
                    set.invoke(result, algorithmKey, complete)
                    HookLog.i(tag, "Palette capture node completed for mode=$nativeMode camera=$camera")
                }
                result
            })
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(tag, "Palette capture integration unavailable", it)
        }
    }

    fun gallery(loader: ClassLoader) {
        if (CameraPaletteSupport.options.none { it.startsWith("gallery_") && MediaUnlockSettings.enabledXposed(it) }) return
        val tag = "ONextBox-GalleryPalette"
        val prefix = "$tag@${System.identityHashCode(loader)}:"
        runCatching {
            val targets = galleryTargets(loader)
            val type = targets.first().declaringClass
            val context = type.getDeclaredField("mShareGlContext").apply { isAccessible = true }
            val share = type.getDeclaredMethod("setShareGlContext", EGLDisplay::class.java, EGLContext::class.java)
            ModernHookRegistry.installFast(prefix + "texture", targets[0], XposedInterface.Hooker { chain ->
                if (CameraPaletteSupport.available()) false else chain.proceed()
            })
            ModernHookRegistry.installFast(prefix + "context", targets[1], XposedInterface.Hooker { chain ->
                if (context.get(chain.thisObject) == null) {
                    val current = EGL14.eglGetCurrentContext()
                    if (current != null && current != EGL14.EGL_NO_CONTEXT)
                        share.invoke(chain.thisObject, EGL14.eglGetCurrentDisplay(), current)
                }
                chain.proceed()
            })
            HookLog.i(tag, "Native palette P010 path installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(tag, "Palette editor integration unavailable", it)
        }
    }
}
