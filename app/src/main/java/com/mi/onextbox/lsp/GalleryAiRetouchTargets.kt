package com.mi.onextbox.lsp

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Method

internal class GalleryAiRetouchTargets(private val dex: DexKitBridge) {
    /** Export only host Methods, never native DexKit objects, across a hot reload. */
    fun resolve(loader: ClassLoader): Map<String, Array<Method>> {
        fun supportCandidates(data: Collection<MethodData>) = data.filter { it.isMethod }.mapNotNull { target ->
            runCatching { target.getMethodInstance(loader) }.getOrNull()
                ?.takeIf(GalleryAiRetouchRules::supportMethod)?.let { target to it }
        }.distinctBy { it.second }

        val primary = supportCandidates(dex.findMethod {
            matcher { usingStrings(GalleryAiRetouchRules.SYSTEM_FEATURE); paramTypes() }
        })
        val support = if (primary.isNotEmpty()) primary.single() else {
            val logMethods = dex.findMethod { matcher { usingStrings(GalleryAiRetouchRules.UNSUPPORTED_LOG) } }
            supportCandidates(logMethods.flatMap { log ->
                dex.getClassData(log.className)?.methods.orEmpty().filter { it.isConstructor }
                    .flatMap { it.callers }
            }).single()
        }
        val config = dex.findMethod {
            matcher { declaredClass = GalleryAiRetouchRules.STORAGE; usingStrings(GalleryAiRetouchRules.CONFIG_KEY) }
        }.filter { it.isMethod }.mapNotNull { target ->
            runCatching { target.getMethodInstance(loader) }.getOrNull()
                ?.takeIf(GalleryAiRetouchRules::configMethod)?.let { target to it }
        }.single()
        // Use only the String getter actually invoked by this dispatcher, not every getter on
        // the config object. Newer galleries add a second Boolean default-value parameter.
        val getter = config.first.invokes.filter { it.isMethod }.mapNotNull {
            runCatching { it.getMethodInstance(loader) }.getOrNull()
        }.filter { GalleryAiRetouchRules.keyGetter(it, config.second.parameterTypes[0]) }.distinct().single()
        getter.isAccessible = true
        val callers = (support.first.callers + config.first.callers).filter { it.isMethod }.mapNotNull {
            runCatching { it.getMethodInstance(loader) }.getOrNull()
        }.distinct().toTypedArray()
        return mapOf("support" to arrayOf(support.second), "config" to arrayOf(config.second),
            "key" to arrayOf(getter), "callers" to callers)
    }
}
