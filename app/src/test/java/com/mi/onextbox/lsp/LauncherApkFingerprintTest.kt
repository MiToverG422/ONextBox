package com.mi.onextbox.lsp

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class LauncherApkFingerprintTest {
    @Test fun repackagingDoesNotInvalidateIdenticalCodeButChangedDexAlwaysDoes() {
        val directory = kotlin.io.path.createTempDirectory("launcher-fingerprint").toFile()
        try {
            fun apk(name: String, entries: List<Pair<String, String>>): File = File(directory, name).also { file ->
                ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (path, value) ->
                    zip.putNextEntry(ZipEntry(path)); zip.write(value.toByteArray()); zip.closeEntry()
                } }
            }
            val original = apk("1754.apk", listOf("classes.dex" to "old-code", "AndroidManifest.xml" to "launcher"))
            val repacked = apk("same-code.apk", listOf("AndroidManifest.xml" to "launcher", "classes.dex" to "old-code"))
            val changed = apk("1755.apk", listOf("classes.dex" to "new-code", "AndroidManifest.xml" to "launcher"))
            assertEquals(LauncherDexResolver.fingerprint(original), LauncherDexResolver.fingerprint(repacked))
            assertNotEquals(LauncherDexResolver.fingerprint(original), LauncherDexResolver.fingerprint(changed))
        } finally { directory.deleteRecursively() }
    }
}
