package com.mi.onextbox.lsp

import android.graphics.Bitmap
import android.os.Bundle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import org.luckypray.dexkit.DexKitBridge

internal object CameraMediaGuardHooker {
    fun targets(dex: DexKitBridge, loader: ClassLoader): Map<String, Array<Method>> {
        val crop = dex.findMethod { matcher {
            usingStrings("cropCenterBitmapByScale, error, sw: ")
            returnType = Bitmap::class.java.name
            paramTypes(Bitmap::class.java.name, "float", "float")
        } }.single().getMethodInstance(loader)
        val bundle = dex.findMethod { matcher {
            usingStrings("WatermarkYuvBufferHelpe")
            returnType = Bundle::class.java.name
            paramTypes("int")
        } }.single().getMethodInstance(loader)
        return mapOf("crop" to arrayOf(crop), "bundle" to arrayOf(bundle))
    }

    fun hook(loader: ClassLoader, path: String) {
        val tag = "ONextBox-CameraMediaGuard"
        val prefix = "$tag@${System.identityHashCode(loader)}:"
        runCatching {
            val methods = HostDexMetadata.methods(loader, path, "camera-media-guard", "null-output-v1") {
                targets(it, loader)
            }
            val crop = methods.getValue("crop").single()
            ModernHookRegistry.installFast("${prefix}crop", crop, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                val source = chain.args.firstOrNull() as? Bitmap
                if (result == null && source != null && !source.isRecycled) source else result
            })
            ModernHookRegistry.installFast("${prefix}video", methods.getValue("bundle").single(), XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                if (result is Bundle && result.getByteArray("buffer") == null) null else result
            })
            HostDexMetadata.methods(loader, path, "camera-media-guard-callers", "null-output-v1") { dex ->
                val selected = methods.values.flatMap { it.toList() }.toSet()
                mapOf("callers" to selected.map { it.declaringClass.name }.distinct().flatMap { owner ->
                    dex.getClassData(owner)?.methods.orEmpty().filter { data ->
                        data.isMethod && runCatching { data.getMethodInstance(loader) in selected }.getOrDefault(false)
                    }.flatMap { it.callers }
                }.filter { it.isMethod }.mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }.distinct().toTypedArray())
            }.getValue("callers").forEach { runCatching { ModernHookRuntime.requireModule().deoptimize(it) } }
            HookLog.i(tag, "Preview crop and video watermark output guards installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(tag, "Native media guard targets unavailable", it)
        }
    }
}
