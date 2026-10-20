package com.mi.onextbox.lsp

import org.luckypray.dexkit.DexKitBridge
import java.util.zip.ZipFile

/** Gallery APKs bundle many large algorithms; index only DEX files containing these gates. */
internal object GalleryAiRetouchDex {
    private val feature = GalleryAiRetouchRules.SYSTEM_FEATURE.toByteArray(Charsets.US_ASCII)
    private val log = GalleryAiRetouchRules.UNSUPPORTED_LOG.toByteArray(Charsets.US_ASCII)
    private val config = GalleryAiRetouchRules.CONFIG_KEY.toByteArray(Charsets.US_ASCII)
    private val storage = ("L" + GalleryAiRetouchRules.STORAGE.replace('.', '/') + ";").toByteArray(Charsets.US_ASCII)

    fun open(apkPath: String): DexKitBridge {
        val selected = arrayListOf<ByteArray>()
        var totalBytes = 0
        ZipFile(apkPath).use { apk ->
            val entries = apk.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.name.matches(Regex("classes[0-9]*\\.dex"))) continue
                require(entry.size in 1..(32L * 1024 * 1024)) { "Unsupported Gallery DEX size" }
                val bytes = apk.getInputStream(entry).use { it.readBytes() }
                if (containsTarget(bytes)) {
                    totalBytes += bytes.size
                    require(totalBytes <= 64 * 1024 * 1024) { "Gallery target scan exceeds memory budget" }
                    selected += bytes
                }
            }
        }
        check(selected.isNotEmpty()) { "Gallery has no AI Retouch capability anchors" }
        return DexKitBridge.create(selected.toTypedArray()).also { it.setThreadNum(2) }
    }

    internal fun containsTarget(bytes: ByteArray): Boolean =
        contains(bytes, feature) || contains(bytes, log) || (contains(bytes, storage) && contains(bytes, config))

    private fun contains(bytes: ByteArray, needle: ByteArray): Boolean {
        for (start in 0..(bytes.size - needle.size)) {
            if (bytes[start] != needle[0]) continue
            var offset = 1
            while (offset < needle.size && bytes[start + offset] == needle[offset]) offset++
            if (offset == needle.size) return true
        }
        return false
    }
}
