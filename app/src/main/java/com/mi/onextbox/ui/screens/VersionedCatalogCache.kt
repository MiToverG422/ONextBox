package com.mi.onextbox.ui.screens

/** Invalidation never waits for PackageManager work, including on a broadcast/main thread. */
internal class VersionedCatalogCache<K, V> {
    private data class Entry<K, V>(val key: K, val revision: Long, val value: V)
    private val lock = Any()
    private val loadLock = Any()
    private var revision = 0L
    private var entry: Entry<K, V>? = null

    fun invalidate(): Long = synchronized(lock) { entry = null; ++revision }

    fun get(key: K, load: () -> V): V = synchronized(loadLock) {
        while (true) {
            val expected = synchronized(lock) {
                entry?.takeIf { it.key == key && it.revision == revision }?.let { return it.value }
                revision
            }
            val value = load()
            synchronized(lock) {
                if (revision == expected) {
                    entry = Entry(key, expected, value)
                    return value
                }
            }
        }
        @Suppress("UNREACHABLE_CODE") error("Unreachable")
    }
}
