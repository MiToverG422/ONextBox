package com.mi.onextbox.lsp

import java.lang.reflect.Modifier
import java.util.concurrent.locks.ReentrantReadWriteLock

internal class ScreenTranslationBrowserVersions(owner: Class<*>) {
    private val maps = owner.declaredFields.filter {
        Modifier.isStatic(it.modifiers) && Map::class.java.isAssignableFrom(it.type)
    }.onEach { it.isAccessible = true }
    private val lock = owner.declaredFields.singleOrNull {
        Modifier.isStatic(it.modifiers) && it.type == ReentrantReadWriteLock::class.java
    }?.apply { isAccessible = true }

    fun read(packageName: String, version: Long = 0, nativeClass: String? = null): List<Long>? = runCatching {
        val readLock = (lock?.get(null) as? ReentrantReadWriteLock)?.readLock() ?: return@runCatching null
        readLock.lock()
        try {
            val current = maps.mapNotNull { it.get(null) as? Map<*, *> }
            // Preserve explicit version matches and version-independent native configurations.
            if (!nativeClass.isNullOrBlank() && current.any {
                it[packageName] == nativeClass || it["$packageName:$version"] == nativeClass
            }) return@runCatching null
            val versions = current.mapNotNull { map ->
                val list = map[packageName] as? List<*> ?: return@mapNotNull null
                if (list.isEmpty() || list.any { it !is Number }) null
                else list.map { (it as Number).toLong() }
            }
            versions.singleOrNull()
        } finally { readLock.unlock() }
    }.getOrNull()
}
