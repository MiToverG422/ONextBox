package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import org.luckypray.dexkit.DexKitBridge

internal object GalleryWatermarkDeviceHooker {
    val hasselKeys = setOf("gallery_unlock_watermark_hasselblad", "gallery_unlock_watermark_hasselblad_xpan",
        "gallery_unlock_watermark_hasselblad_master")
    val lumoKeys = setOf("gallery_unlock_watermark_lumo", "gallery_unlock_watermark_lumo_master")
    val keys = hasselKeys + lumoKeys + MediaWatermarkSettings.ENABLED

    fun targets(dex: DexKitBridge, loader: ClassLoader): Map<String, List<Member>> {
        val owner = dex.findMethod { matcher {
            usingStrings("WatermarkDevice(isHasselDevice=", ", subWatermarkType=")
        } }.single().className
        val constructors = checkNotNull(dex.getClassData(owner)).methods.filter { it.isConstructor }
            .mapNotNull { data -> runCatching { data.getConstructorInstance(loader) }.getOrNull()?.takeIf {
                it.parameterTypes.toList() == listOf(String::class.java, Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            }?.let { data to it } }
        check(constructors.size == 1)
        val plugin = loader.loadClass("com.oplus.tbluniformeditor.plugins.watermark.data.WatermarkDevice")
            .getDeclaredConstructor(Boolean::class.javaPrimitiveType, String::class.java, Int::class.javaPrimitiveType)
        val callers = constructors.flatMap { it.first.callers }.filter { it.isMethod }.mapNotNull {
            runCatching { it.getMethodInstance(loader) }.getOrNull()
        }.distinct()
        return mapOf("host" to constructors.map { it.second }, "plugin" to listOf(plugin), "callers" to callers)
    }

    fun hook(loader: ClassLoader, path: String) {
        if (keys.none(MediaUnlockSettings::enabledXposed)) return
        val tag = "ONextBox-WatermarkDevice"
        val prefix = "$tag@${System.identityHashCode(loader)}:"
        runCatching {
            val metadata = HostDexMetadata.methods(loader, path, "watermark-device", "device-capabilities-v2") { dex ->
                val resolved = targets(dex, loader)
                mapOf("host" to arrayOf(resolved.getValue("host").single().declaringClass.getDeclaredMethod("toString")),
                    "plugin" to arrayOf(resolved.getValue("plugin").single().declaringClass.getDeclaredMethod("toString")),
                    "callers" to resolved.getValue("callers").filterIsInstance<Method>().toTypedArray())
            }
            val constructors = mapOf("host" to metadata.getValue("host").single().declaringClass.getDeclaredConstructor(
                String::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType),
                "plugin" to metadata.getValue("plugin").single().declaringClass.getDeclaredConstructor(
                    Boolean::class.javaPrimitiveType, String::class.java, Int::class.javaPrimitiveType))
            val reached = AtomicBoolean()
            listOf("host", "plugin").forEach { group ->
                val constructor = constructors.getValue(group)
                ModernHookRegistry.installFast("$prefix$group", constructor, XposedInterface.Hooker { chain ->
                    val hassel = hasselKeys.any(MediaUnlockSettings::enabledXposed)
                    val lumo = group == "host" && lumoKeys.any(MediaUnlockSettings::enabledXposed)
                    val name = MediaWatermarkSettings.textXposed(MediaWatermarkSettings.MODEL)
                    if (!hassel && !lumo && name.isEmpty()) return@Hooker chain.proceed()
                    val args = chain.args.toTypedArray()
                    val hasselIndex = if (group == "host") 2 else 0
                    var changed = false
                    if (name.isNotEmpty()) { args[if (group == "host") 0 else 1] = name; changed = true }
                    if (hassel && args[hasselIndex] != true) { args[hasselIndex] = true; changed = true }
                    if (lumo && args[3] != true) { args[3] = true; changed = true }
                    if (changed && reached.compareAndSet(false, true))
                        HookLog.i(tag, "Selected native watermark capabilities reached")
                    if (changed) chain.proceed(args) else chain.proceed()
                })
            }
            metadata.getValue("callers").forEach {
                runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
                    .onFailure { error -> HookLog.w(tag, "Watermark constructor caller deoptimization unavailable", error) }
            }
            HotReloadLifecycle.register("${prefix}targets", capture = { metadata })
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(tag, "Unsupported watermark device descriptors; native behavior retained", it)
        }
    }
}
