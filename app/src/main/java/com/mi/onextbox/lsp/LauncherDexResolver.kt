package com.mi.onextbox.lsp

import android.content.pm.ApplicationInfo
import android.util.AtomicFile
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import org.json.JSONObject
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.security.MessageDigest
import java.util.WeakHashMap
import java.util.zip.ZipFile

/** Metadata belongs to the host APK, not to its displayed version or obfuscated names. */
internal object LauncherDexResolver {
    private const val SCHEMA = 1
    private data class Binding(val path: String, val fingerprint: String, val generation: Int,
        val file: File?, val targets: JSONObject, val resolved: MutableMap<String, Any> = mutableMapOf())
    private val bindings = WeakHashMap<ClassLoader, Binding>()

    @Synchronized fun bind(loader: ClassLoader, path: String, cacheDirectory: File? = hostCacheDirectory()) {
        val generation = HookConfigSnapshot.int("developer_dex_cache_generation", 0) ?: 0
        val fingerprint = fingerprint(File(path))
        val previous = bindings[loader]
        if (previous?.fingerprint == fingerprint && previous.generation == generation) return
        val file = cacheDirectory?.let { File(it, "onextbox-launcher-targets.json") }
        val saved = file?.takeIf(File::isFile)?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
        val targets = if (saved?.optInt("schema") == SCHEMA && saved.optString("apk") == fingerprint &&
            saved.optInt("generation") == generation) saved.optJSONObject("targets") ?: JSONObject() else JSONObject()
        bindings[loader] = Binding(path, fingerprint, generation, file, targets)
    }

    internal fun fingerprint(apk: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        ZipFile(apk).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory }.sortedBy { it.name }.forEach {
                digest.update("${it.name}:${it.size}:${it.crc}\n".toByteArray())
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun hostCacheDirectory(): File? = runCatching {
        val thread = Class.forName("android.app.ActivityThread").getMethod("currentActivityThread").invoke(null)
            ?: return null
        val bound = com.mi.onextbox.lsp.compat.ModernReflect.getObjectField(thread, "mBoundApplication") ?: return null
        val info = com.mi.onextbox.lsp.compat.ModernReflect.getObjectField(bound, "appInfo") as? ApplicationInfo
            ?: return null
        if (info.packageName != "com.android.launcher") return null
        File(info.dataDir, "code_cache")
    }.getOrNull()

    @Synchronized fun <T> scan(loader: ClassLoader, action: (DexKitBridge) -> T): T {
        val binding = requireNotNull(bindings[loader]) { "Launcher APK was not bound" }
        System.loadLibrary("dexkit")
        return DexKitBridge.create(binding.path).use { dex -> dex.setThreadNum(1); action(dex) }
    }

    @Synchronized private fun <T : Any> resolve(loader: ClassLoader, key: String,
        query: (DexKitBridge) -> String, load: (String) -> T): T {
        val binding = requireNotNull(bindings[loader]) { "Launcher APK was not bound" }
        @Suppress("UNCHECKED_CAST")
        binding.resolved[key]?.let { return it as T }
        binding.targets.optString(key).takeIf(String::isNotEmpty)?.let { descriptor ->
            runCatching { load(descriptor) }.getOrNull()?.let { result ->
                binding.resolved[key] = result
                return result
            }
            binding.targets.remove(key)
        }
        val descriptor = scan(loader, query)
        val result = load(descriptor)
        binding.resolved[key] = result
        binding.targets.put(key, descriptor)
        binding.file?.let { file -> runCatching {
            file.parentFile?.mkdirs()
            val atomic = AtomicFile(file)
            val stream = atomic.startWrite()
            try {
                stream.write(JSONObject().put("schema", SCHEMA).put("apk", binding.fingerprint)
                    .put("generation", binding.generation).put("targets", binding.targets).toString().toByteArray())
                atomic.finishWrite(stream)
            } catch (error: Throwable) { atomic.failWrite(stream); throw error }
        }.onFailure { HookLog.w("LauncherTargets", "Unable to persist launcher metadata", it) } }
        HookLog.i("LauncherTargets", "$key resolved: $descriptor")
        return result
    }

    fun type(loader: ClassLoader, key: String, query: (DexKitBridge) -> String): Class<*> =
        resolve(loader, "type:$key", query) { Class.forName(it, false, loader) }

    fun method(loader: ClassLoader, key: String, query: (DexKitBridge) -> MethodData): Method =
        resolve(loader, "method:$key", { query(it).descriptor }) {
            DexMethod(it).getMethodInstance(loader).apply { isAccessible = true }
        }

    fun field(loader: ClassLoader, key: String, query: (DexKitBridge) -> FieldData): Field =
        resolve(loader, "field:$key", { query(it).descriptor }) {
            DexField(it).getFieldInstance(loader).apply { isAccessible = true }
        }
}
