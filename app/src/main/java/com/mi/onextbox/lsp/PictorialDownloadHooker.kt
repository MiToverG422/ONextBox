package com.mi.onextbox.lsp

import android.graphics.Bitmap
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime

internal object PictorialDownloadHooker {
    private val saving = ThreadLocal<Boolean?>()
    fun hook(loader: ClassLoader) = runCatching {
        val save = loader.loadClass("com.heytap.pictorial.ui.media.SavePictureManager").declaredMethods.single {
            it.name == "saveBitmap" && it.parameterCount == 6 && it.parameterTypes[3] == Bitmap::class.java
        }
        val composer = loader.loadClass("com.heytap.pictorial.utils.BitmapUtils").declaredMethods.single {
            it.name == "k" && it.returnType == Bitmap::class.java && it.parameterTypes.contentEquals(arrayOf(
                android.content.Context::class.java, Bitmap::class.java, String::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType))
        }
        ModernHookRuntime.requireModule().deoptimize(save)
        ModernHookRegistry.installFast("PictorialDownload:save", save) { chain ->
            val previous = saving.get()
            saving.set(KeyboardPictorialSettings.active("pictorial_remove_download_watermark"))
            try { chain.proceed() } finally { if (previous == null) saving.remove() else saving.set(previous) }
        }
        ModernHookRegistry.installFast("PictorialDownload:compose", composer) { chain ->
            val bitmap = chain.args[1] as? Bitmap
            if (saving.get() == true && bitmap != null && !bitmap.isRecycled) {
                runCatching { bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: chain.proceed()
            } else chain.proceed()
        }
        HotReloadLifecycle.register("PictorialDownload:frame", detach = { saving.remove() })
        HookLog.i("PictorialDownload", "Wallpaper download watermark control installed")
    }.onFailure { HookLog.w("PictorialDownload", "Download watermark interface unavailable; native behavior retained", it) }
}
