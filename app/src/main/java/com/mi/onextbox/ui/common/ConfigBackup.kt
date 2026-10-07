package com.mi.onextbox.ui.common

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.mi.onextbox.BuildConfig
import com.mi.onextbox.lsp.GoogleMessagesConfig
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.launcherSearchBarModeValue
import com.mi.onextbox.refresh.RefreshRatePreferences
import com.mi.onextbox.refresh.RefreshRateControllerClient
import com.mi.onextbox.touch.TouchSamplingController
import com.mi.onextbox.touch.TouchSamplingPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ConfigBackup {
    private const val BACKUP_VERSION = 1
    const val MIME_TYPE = "application/json"

    private const val APP_PREFS_NAME = "onextbox_prefs"
    private const val LSP_PREFS_NAME = "lsp_features"
    private const val MAX_BACKUP_BYTES = 1_048_576
    private const val TYPE_BOOLEAN = "boolean"
    private const val TYPE_INT = "int"
    private const val TYPE_LONG = "long"
    private const val TYPE_FLOAT = "float"
    private const val TYPE_STRING = "string"
    private const val TYPE_STRING_SET = "string_set"

    private val supportedPrefs = listOf(
        APP_PREFS_NAME, LSP_PREFS_NAME,
        RefreshRatePreferences.PREFS_NAME, TouchSamplingPreferences.PREFS_NAME,
    )

    data class ImportResult(val systemStateSynced: Boolean)

    data class ResetResult(
        val systemStateReset: Boolean,
    )

    fun defaultFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "onextbox_config_$timestamp.json"
    }

    fun exportToUri(context: Context, uri: Uri) {
        val bytes = exportToJson(context).toByteArray(StandardCharsets.UTF_8)
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(bytes)
        } ?: error("Cannot open output file")
    }

    fun importFromUri(context: Context, uri: Uri): ImportResult {
        val jsonText = context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(bytes.size() + count <= MAX_BACKUP_BYTES) { "Config file is too large" }
                bytes.write(buffer, 0, count)
            }
            bytes.toString(StandardCharsets.UTF_8.name())
        } ?: error("Cannot open input file")
        return importFromJson(context, jsonText)
    }

    fun exportToJson(context: Context): String {
        val prefsJson = JSONObject()
        supportedPrefs.forEach { prefsName ->
            prefsJson.put(prefsName, prefsToJson(prefs(context, prefsName)))
        }

        return JSONObject()
            .put("version", BACKUP_VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("packageName", context.packageName)
            .put("appVersionName", BuildConfig.VERSION_NAME)
            .put("appVersionCode", BuildConfig.VERSION_CODE)
            .put("appVersionCodeLabel", BuildConfig.APP_VERSION_CODE_LABEL)
            .put("preferences", prefsJson)
            .toString(2)
    }

    fun importFromJson(context: Context, jsonText: String): ImportResult {
        require(jsonText.toByteArray(StandardCharsets.UTF_8).size <= MAX_BACKUP_BYTES) {
            "Config file is too large"
        }
        val root = JSONObject(jsonText)
        require(root.get("version") == BACKUP_VERSION) { "Unsupported config version" }
        val packageName = root.optString("packageName")
        require(packageName == context.packageName || packageName == "com.mi.fluidbox") {
            "Config belongs to another app"
        }
        val prefsRoot = root.getJSONObject("preferences")
        val groups = supportedPrefs.filter { prefsRoot.has(it) }.associateWith { name ->
            val values = decodePreferences(prefsRoot.getJSONObject(name))
            if (name == LSP_PREFS_NAME) {
                values.mapValues { (key, value) ->
                    if (key == "launcher_taskbar_search_box" && value is Boolean) {
                        launcherSearchBarModeValue(value)
                    } else value
                }
            } else values
        }
        require(groups.isNotEmpty()) { "Config contains no supported preferences" }
        val stores = groups.keys.associateWith { name -> sharedPreferenceStore(prefs(context, name)) }
        for ((name, values) in groups) {
            val existing = stores.getValue(name).read()
            for ((key, value) in values) {
                val previous = existing[key] ?: continue
                require(preferenceTypeMatches(previous, value)) { "Preference type changed: $key" }
            }
        }
        restorePreferences(groups, stores)

        val synced = if (LSP_PREFS_NAME in groups) {
            GoogleMessagesConfig.removeRetiredPreferences(prefs(context, LSP_PREFS_NAME))
            LspConfig.syncTogglesForBoot(context)
        } else {
            true
        }
        return ImportResult(systemStateSynced = synced)
    }

    suspend fun resetToDefaults(context: Context): ResetResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext

        require(prefs(appContext, APP_PREFS_NAME).edit().clear().commit()) {
            "Failed to clear $APP_PREFS_NAME"
        }

        val lspStateReset = runCatching {
            LspConfig.resetToDefaults(appContext)
        }.getOrDefault(false)
        val refreshRateStateReset = runCatching {
            RefreshRateControllerClient.resetRefreshRateMode(appContext) == true
        }.getOrDefault(false)
        require(prefs(appContext, RefreshRatePreferences.PREFS_NAME).edit().clear().commit()) {
            "Failed to clear refresh rate preferences"
        }
        val touchStateReset = runCatching {
            TouchSamplingController.resetConfiguration()
        }.getOrDefault(false)
        require(prefs(appContext, TouchSamplingPreferences.PREFS_NAME).edit().clear().commit()) {
            "Failed to clear touch sampling preferences"
        }

        val permissionMonitorReset = runCatching {
            applyPermissionMonitorVisibility(false).success
        }.getOrDefault(false)
        val launcherLayoutReset = runCatching {
            applyLauncherLayoutUnlocked(false).success
        }.getOrDefault(false)
        val assistantScreenReset = runCatching {
            applyAssistantScreenOption(AssistantScreenOption.Default).success
        }.getOrDefault(false)

        ResetResult(
            systemStateReset = lspStateReset &&
                refreshRateStateReset &&
                touchStateReset &&
                permissionMonitorReset &&
                launcherLayoutReset &&
                assistantScreenReset,
        )
    }

    private fun prefs(context: Context, name: String): SharedPreferences {
        return if (name == LSP_PREFS_NAME) {
            LspPreferenceStore.prefs(context)
        } else {
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
        }
    }

    private fun prefsToJson(prefs: SharedPreferences): JSONObject {
        val json = JSONObject()
        prefs.all.forEach { (key, value) ->
            json.put(key, prefValueToJson(value))
        }
        return json
    }

    private fun prefValueToJson(value: Any?): JSONObject {
        return when (value) {
            is Boolean -> JSONObject().put("type", TYPE_BOOLEAN).put("value", value)
            is Int -> JSONObject().put("type", TYPE_INT).put("value", value)
            is Long -> JSONObject().put("type", TYPE_LONG).put("value", value)
            is Float -> JSONObject().put("type", TYPE_FLOAT).put("value", value.toDouble())
            is String -> JSONObject().put("type", TYPE_STRING).put("value", value)
            is Set<*> -> JSONObject()
                .put("type", TYPE_STRING_SET)
                .put("value", JSONArray(value.filterIsInstance<String>()))
            else -> JSONObject().put("type", TYPE_STRING).put("value", value?.toString().orEmpty())
        }
    }

    private fun decodePreferences(json: JSONObject): Map<String, Any> {
        require(json.length() <= 1024) { "Too many preferences" }
        return buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                require(key.isNotBlank() && key.length <= 256) { "Invalid preference key" }
                val item = json.getJSONObject(key)
                val value = item.get("value")
                val decoded: Any = when (item.getString("type")) {
                    TYPE_BOOLEAN -> value.also { require(it is Boolean) { "Invalid boolean: $key" } }
                    TYPE_INT -> integerValue(value, key).also {
                        require(it in Int.MIN_VALUE..Int.MAX_VALUE) { "Integer out of range: $key" }
                    }.toInt()
                    TYPE_LONG -> integerValue(value, key)
                    TYPE_FLOAT -> {
                        require(value is Number && value.toDouble().isFinite()) { "Invalid float: $key" }
                        value.toFloat().also { require(it.isFinite()) { "Float out of range: $key" } }
                    }
                    TYPE_STRING -> value.also { require(it is String) { "Invalid string: $key" } }
                    TYPE_STRING_SET -> {
                        require(value is JSONArray && value.length() <= 4096) { "Invalid string set: $key" }
                        buildSet {
                            for (index in 0 until value.length()) {
                                val entry = value.get(index)
                                require(entry is String) { "Invalid string set entry: $key" }
                                add(entry)
                            }
                        }
                    }
                    else -> error("Unsupported preference type: $key")
                }
                put(key, decoded)
            }
        }
    }

    private fun integerValue(value: Any, key: String): Long {
        require(value is Int || value is Long) { "Invalid integer: $key" }
        return (value as Number).toLong()
    }

    private fun preferenceTypeMatches(previous: Any, value: Any): Boolean = when (previous) {
        is Boolean -> value is Boolean
        is Int -> value is Int
        is Long -> value is Long
        is Float -> value is Float
        is String -> value is String
        is Set<*> -> value is Set<*>
        else -> false
    }

    private fun sharedPreferenceStore(preferences: SharedPreferences) = object : PreferenceStore {
        override fun read(): Map<String, *> = preferences.all
        override fun write(values: Map<String, *>): Boolean {
            val editor = preferences.edit().clear()
            values.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    else -> error("Unsupported preference value: $key")
                }
            }
            return editor.commit()
        }
    }
}
