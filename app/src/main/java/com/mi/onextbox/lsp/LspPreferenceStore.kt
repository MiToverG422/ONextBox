package com.mi.onextbox.lsp

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Local preferences and cached reads of legacy system mirrors. */
internal object LspPreferenceStore {
    // Cache legacy reads outside frequent Hook callbacks.
    private const val XPOSED_READ_CACHE_NANOS = 500_000_000L
    private data class TimedStringValue(val value: String?, val readAtNanos: Long)
    private val systemPropertyReadCache = ConcurrentHashMap<String, TimedStringValue>()
    private val settingsGlobalReadCache = ConcurrentHashMap<String, TimedStringValue>()
    private val systemPropertiesGetMethod: Method? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        runCatching {
            Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java, String::class.java)
        }.getOrNull()
    }

    private const val PREFS_NAME = "lsp_features"

    fun readSystemPropertyValue(propertyKey: String): String? {
        return readTimedString(systemPropertyReadCache, propertyKey) {
            runCatching {
                (systemPropertiesGetMethod?.invoke(null, propertyKey, "") as? String)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }.getOrNull()
        }
    }

    fun readFlagFile(filePath: String): Boolean? {
        return runCatching {
            val file = File(filePath)
            if (!file.exists()) return@runCatching null
            when (file.readText().trim()) {
                "1", "true", "on", "enabled" -> true
                "0", "false", "off", "disabled" -> false
                else -> null
            }
        }.getOrNull()
    }

    fun readSettingsGlobalToggle(settingsKey: String): Boolean? {
        return parseToggleValue(readSettingsGlobalValue(settingsKey))
    }

    fun readSettingsGlobalValue(settingsKey: String): String? {
        return readTimedString(settingsGlobalReadCache, settingsKey) {
            readSettingsGlobalViaFramework(settingsKey)
                ?: readSettingsGlobalViaXml(settingsKey)
        }
    }

    private inline fun readTimedString(
        cache: ConcurrentHashMap<String, TimedStringValue>,
        key: String,
        reader: () -> String?,
    ): String? {
        val now = System.nanoTime()
        cache[key]?.let { cached ->
            if (now - cached.readAtNanos < XPOSED_READ_CACHE_NANOS) return cached.value
        }
        return reader().also { value ->
            cache[key] = TimedStringValue(value = value, readAtNanos = now)
        }
    }

    private fun readSettingsGlobalViaFramework(settingsKey: String): String? {
        return runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentThread = activityThreadClass
                .getMethod("currentActivityThread")
                .invoke(null)
                ?: return@runCatching null
            val systemContext = activityThreadClass
                .getMethod("getSystemContext")
                .invoke(currentThread)
                ?: return@runCatching null

            val contentResolver = systemContext.javaClass
                .getMethod("getContentResolver")
                .invoke(systemContext)
                ?: return@runCatching null

            val settingsGlobalClass = Class.forName("android.provider.Settings\$Global")
            val getStringMethod = settingsGlobalClass.getMethod(
                "getString",
                Class.forName("android.content.ContentResolver"),
                String::class.java
            )
            getStringMethod.invoke(null, contentResolver, settingsKey) as? String
        }.getOrNull()
    }

    private fun readSettingsGlobalViaXml(settingsKey: String): String? {
        return runCatching {
            val file = File("/data/system/users/0/settings_global.xml")
            if (!file.exists()) return@runCatching null
            val text = file.readText()
            val escaped = Regex.escape(settingsKey)
            val directOrder = Regex("<setting[^>]*name=\"$escaped\"[^>]*value=\"([^\"]*)\"[^>]*/?>")
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
            if (directOrder != null) return@runCatching directOrder

            val reversedOrder = Regex("<setting[^>]*value=\"([^\"]*)\"[^>]*name=\"$escaped\"[^>]*/?>")
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
            reversedOrder
        }.getOrNull()
    }

    private fun parseToggleValue(raw: String?): Boolean? {
        val value = raw?.trim()?.lowercase() ?: return null
        return when (value) {
            "1", "true", "on", "enabled" -> true
            "0", "false", "off", "disabled" -> false
            else -> null
        }
    }

    fun prefs(context: Context): SharedPreferences {
        val storageContext = prefsContext(context)
        return storageContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun prefsContext(context: Context): Context {
        val deviceContext = context.createDeviceProtectedStorageContext()
        runCatching {
            val devicePrefsFile = File(
                File(deviceContext.dataDir, "shared_prefs").path,
                "$PREFS_NAME.xml"
            )
            if (!devicePrefsFile.exists()) {
                deviceContext.moveSharedPreferencesFrom(context, PREFS_NAME)
            }
        }
        return deviceContext
    }

    fun readSystemPropertyToggle(propertyKey: String): Boolean? {
        return parseToggleValue(readSystemPropertyValue(propertyKey))
    }

    fun readBoolean(context: Context, key: String, defaultValue: Boolean): Boolean =
        prefs(context).getBoolean(key, defaultValue)

    fun readInt(context: Context, key: String, defaultValue: Int): Int =
        prefs(context).getInt(key, defaultValue)

    fun readString(context: Context, key: String, defaultValue: String): String =
        prefs(context).getString(key, defaultValue) ?: defaultValue

    fun invalidateMirrors() {
        systemPropertyReadCache.clear()
        settingsGlobalReadCache.clear()
    }
}

internal fun SharedPreferences.Editor.commitOrReport(): Boolean =
    runCatching { commit() }.getOrDefault(false).also {
        if (!it) ConfigSyncStatus.failed("preferences")
    }

internal fun launcherSearchBarModeValue(value: Any?): Int = when (value) {
    true -> LspConfig.LAUNCHER_SEARCH_BAR_MODE_INTERNATIONAL
    false -> LspConfig.LAUNCHER_SEARCH_BAR_MODE_OFF
    is Int -> value.takeIf { it in 0..2 } ?: LspConfig.LAUNCHER_SEARCH_BAR_MODE_OFF
    else -> LspConfig.LAUNCHER_SEARCH_BAR_MODE_OFF
}
