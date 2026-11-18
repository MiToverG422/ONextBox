package com.mi.onextbox.lsp

import android.content.pm.ApplicationInfo
import com.mi.onextbox.BuildConfig
import com.mi.onextbox.diagnostics.RuntimeDiagnostics
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.ModernReflect
import org.luckypray.dexkit.DexKitBridge
import java.io.File
import java.lang.reflect.Member
import java.lang.reflect.Method

internal object HostDexMetadata {
    private val queryWarnings = ThreadLocal<Int?>()
    fun noteResolutionWarning() { queryWarnings.get()?.let { queryWarnings.set(it + 1) } }

    fun methods(loader: ClassLoader, path: String, namespace: String, schema: String,
        query: (DexKitBridge) -> Map<String, Array<Method>>): Map<String, Array<Method>> =
        members(loader, path, namespace, schema, accepts = { groups -> groups.values.flatten().all { it is Method } }) {
            dex -> query(dex).mapValues { it.value.toList() }
        }
            .mapValues { (_, members) -> members.map { it as Method }.toTypedArray() }

    fun members(loader: ClassLoader, path: String, namespace: String, schema: String,
        accepts: (Map<String, List<Member>>) -> Boolean = { true },
        query: (DexKitBridge) -> Map<String, List<Member>>): Map<String, List<Member>> =
        ApkDexSession.withSession(path) { open ->
            val started = System.nanoTime()
            val file = cacheDirectory(path)?.let { directory -> File(directory, "$namespace.bin") }
            val identity = file?.let {
                runCatching { DexCacheIdentity(ApkDexSession.fingerprint(path), BuildConfig.APP_BUILD_TIMESTAMP,
                    HookConfigSnapshot.adaptationVersion().name,
                    HookConfigSnapshot.int("developer_dex_cache_generation", 0) ?: 0, schema) }.getOrNull()
            }
            if (file != null && identity != null) {
                DexDescriptorStore.read(file, identity)?.let { descriptors ->
                    // A single invalid signature rejects the entire group before any Hook is installed.
                    runCatching { descriptors.mapValues { (_, rows) -> rows.map { it.resolve(loader) } } }.getOrNull()?.takeIf(accepts)
                        ?.let { resolved ->
                            RuntimeDiagnostics.event("dex", namespace, "descriptor cache hit; no scan", (System.nanoTime() - started) / 1_000_000)
                            return@withSession resolved
                        }
                }
            }
            val parentWarnings = queryWarnings.get()
            queryWarnings.set(0)
            var warnings = 0
            val result = try { query(open()) } finally {
                warnings = queryWarnings.get() ?: 0
                if (parentWarnings == null) queryWarnings.remove() else queryWarnings.set(parentWarnings + warnings)
            }
            // Never persist partial/empty resolution results: a temporary miss must be retried.
            if (file != null && identity != null && warnings == 0 && accepts(result) && result.values.any { it.isNotEmpty() }) runCatching {
                DexDescriptorStore.write(file, identity, result.mapValues { (_, rows) -> rows.map(DexMemberDescriptor::of) })
            }.onFailure { HookLog.w("DexMetadata", "Unable to persist $namespace targets", it) }
            RuntimeDiagnostics.event("dex", namespace, "descriptor cache miss; targets resolved", (System.nanoTime() - started) / 1_000_000)
            result
        }

    private fun cacheDirectory(path: String): File? = runCatching {
        val thread = HostClassLookup.load("android.app.ActivityThread").getMethod("currentActivityThread").invoke(null) ?: return null
        val bound = ModernReflect.getObjectField(thread, "mBoundApplication") ?: return null
        val info = ModernReflect.getObjectField(bound, "appInfo") as? ApplicationInfo ?: return null
        if (info.sourceDir != path || info.dataDir.isNullOrEmpty()) return null
        File(info.dataDir, "code_cache/onextbox-targets")
    }.getOrNull()
}
