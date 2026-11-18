package com.mi.onextbox.lsp

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.content.res.AssetManager
import android.content.res.Resources
import android.view.LayoutInflater
import dalvik.system.DexClassLoader
import java.io.File
import java.security.MessageDigest

internal class IsolatedClockLoader(path: String, parent: ClassLoader) : DexClassLoader(path, null, null, parent) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(this) {
        findLoadedClass(name) ?: if (ClockClassLoaderPolicy.hostFirst(name)) super.loadClass(name, resolve)
        else runCatching { findClass(name) }.getOrElse { super.loadClass(name, resolve) }
    }

}

internal class IsolatedClockContext(
    base: Context,
    private val apk: File,
    private val pluginPackage: String,
    private val loader: ClassLoader,
) : ContextWrapper(base) {
    private val isolatedAssets = AssetManager::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance().also {
        val cookie = AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java).apply { isAccessible = true }
            .invoke(it, apk.absolutePath) as Int
        check(cookie != 0) { "Clock resource table could not be loaded" }
    }
    private val isolatedResources = Resources(isolatedAssets, base.resources.displayMetrics, base.resources.configuration)
    private val isolatedTheme = isolatedResources.newTheme().apply { applyStyle(android.R.style.Theme_DeviceDefault, true) }
    private val inflater by lazy { LayoutInflater.from(base).cloneInContext(this) }

    override fun getAssets() = isolatedAssets
    override fun getResources() = isolatedResources
    override fun getTheme() = isolatedTheme
    override fun setTheme(resid: Int) { isolatedTheme.applyStyle(resid, true) }
    override fun getClassLoader() = loader
    override fun getPackageName() = pluginPackage
    override fun getApplicationContext(): Context = this
    override fun getApplicationInfo() = ApplicationInfo(baseContext.applicationInfo).apply {
        packageName = pluginPackage
        sourceDir = apk.absolutePath
        publicSourceDir = apk.absolutePath
    }
    override fun getSystemService(name: String): Any? = if (name == LAYOUT_INFLATER_SERVICE) inflater else super.getSystemService(name)
}

internal class ClockStorageContext(base: Context, packageName: String) : ContextWrapper(base) {
    private val directory = File(base.cacheDir, "onextbox-clock-data/$packageName").apply { check(isDirectory || mkdirs()) }
    override fun getCacheDir() = directory
}

internal object ClockAssetStore {
    fun materialize(context: Context, name: String, expected: String): File {
        require(name.matches(Regex("[a-z0-9._-]+")))
        require(expected.matches(Regex("[a-f0-9]{64}")))
        val directory = File(context.codeCacheDir, "onextbox-clocks").apply { check(isDirectory || mkdirs()) }
        val file = File(directory, "${expected.take(16)}-$name")
        if (file.isFile && digest(file) == expected) return file
        val temporary = File(directory, "${file.name}.tmp")
        if (temporary.exists()) check(temporary.delete())
        val module = context.createPackageContext("com.mi.onextbox", 0)
        try {
            temporary.outputStream().use { output ->
                check(temporary.setReadOnly()) { "Clock bytecode must be read only" }
                module.assets.open("pixel-clocks/$name").use { it.copyTo(output) }
            }
            check(digest(temporary) == expected) { "Clock asset hash mismatch" }
            if (file.exists()) check(file.delete())
            check(temporary.renameTo(file))
        } finally { temporary.delete() }
        return file
    }

    private fun digest(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32768)
            while (true) { val size = input.read(buffer); if (size < 0) break; md.update(buffer, 0, size) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
