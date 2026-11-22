package com.mi.onextbox.lsp

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Run before HWUI opens its disk cache. The OEM's precompiled shaders live in separate files. */
internal object QsPipelineCache {
    const val NAME = "com.android.skia.shaders_cache"
    private const val RETRY_INTERVAL_MS = 24L * 60 * 60 * 1000
    private const val MIB = 1024L * 1024

    fun prepare(directory: File, maxValueBytes: Long, now: Long = System.currentTimeMillis()): Boolean {
        if (maxValueBytes !in MIB..256 * MIB) return false
        // A single new control material can produce several driver pipelines before the next save.
        val watermark = maxValueBytes - maxValueBytes / 8
        val cache = File(directory, NAME)
        if (cache.length() < watermark) return false
        val backup = File(directory, "$NAME.onextbox-previous")
        if (backup.exists() && now - backup.lastModified() in 0 until RETRY_INTERVAL_MS) return false
        if (!nearLimit(cache, watermark)) return false
        Files.move(cache.toPath(), backup.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        // Renaming keeps the original mtime. Use the recovery time for the retry window.
        backup.setLastModified(now)
        return true
    }

    private fun nearLimit(file: File, watermark: Long): Boolean = RandomAccessFile(file, "r").use { input ->
        fun u32() = Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
        fun i64() = java.lang.Long.reverseBytes(input.readLong())
        val length = input.length()
        if (length < 56 || input.readInt() != 0x45474c24) return false // EGL$
        input.skipBytes(4) // checksum
        if (u32() != 0x5f426224L || u32() != 3L || u32() != 1L) return false
        input.skipBytes(4) // native alignment
        val entries = i64()
        val buildIdBytes = u32()
        if (entries !in 1..65536 || buildIdBytes !in 1..256) return false
        var offset = (40 + buildIdBytes + 3) and -4L
        // BlobCache orders keys by length. Only the short keys can be the Vulkan pipeline cache.
        repeat(minOf(entries, 32).toInt()) {
            if (offset > length - 16) return false
            input.seek(offset)
            val keyBytes = i64()
            val valueBytes = i64()
            if (keyBytes !in 1..4 || valueBytes !in 0..(length - offset - 16 - keyBytes)) return false
            if (keyBytes == 4L && u32() == 1L && valueBytes >= watermark) {
                return u32() == 32L && u32() == 1L // VkPipelineCacheHeaderVersionOne
            }
            offset += (16 + keyBytes + valueBytes + 3) and -4L
        }
        false
    }
}
