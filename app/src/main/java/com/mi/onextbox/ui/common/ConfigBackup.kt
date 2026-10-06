package com.mi.onextbox.ui.common

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.mi.onextbox.BuildConfig
import com.mi.onextbox.lsp.GoogleMessagesConfig
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.refresh.RefreshRatePreferences
import com.mi.onextbox.refresh.RefreshRateControllerClient
import com.mi.onextbox.touch.TouchSamplingPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ConfigBackup {
    private const val BACKUP_VERSION = 1
    const val MIME_TYPE = "application/json"

    private const val APP_PREFS_NAME = "onextbox_prefs"
    private const val LSP_PREFS_NAME = "lsp_features"
    private const val SETTINGS_C15_ABOUT_LAYOUT_PREF_KEY = "settings_c15_about_layout"
    private const val SETTINGS_SKIP_SPECIAL_PERMISSION_RISK_CONFIRM_PREF_KEY = "settings_skip_special_permission_risk_confirm"
    private const val SETTINGS_RESTORE_APP_OPEN_BUTTON_PREF_KEY = "settings_restore_app_open_button"
    private const val SETTINGS_RESTORE_DOMESTIC_AUXILIARY_FUNCTIONS_PREF_KEY =
        "settings_restore_domestic_auxiliary_functions"
    private const val SETTINGS_UNLOCK_REFRESH_RATE_PREF_KEY = "settings_unlock_refresh_rate"
    private const val SETTINGS_FORCE_GLOBAL_EXTREME_REFRESH_RATE_PREF_KEY =
        "settings_force_global_extreme_refresh_rate"
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

    fun importFromUri(context: Context, uri: Uri) {
        val jsonText = context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(StandardCharsets.UTF_8).readText()
        } ?: error("Cannot open input file")
        importFromJson(context, jsonText)
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

    fun importFromJson(context: Context, jsonText: String) {
        val root = JSONObject(jsonText)
        val version = root.optInt("version", -1)
        require(version == BACKUP_VERSION) { "Unsupported config version: $version" }

        val prefsRoot = root.getJSONObject("preferences")
        supportedPrefs.forEach { prefsName ->
            if (prefsRoot.has(prefsName)) {
                restorePrefsFromJson(prefs(context, prefsName), prefsRoot.getJSONObject(prefsName))
            }
        }
        if (prefsRoot.has(LSP_PREFS_NAME)) {
            // Do not revive removed features when importing an older configuration.
            GoogleMessagesConfig.removeRetiredPreferences(prefs(context, LSP_PREFS_NAME))
            LspConfig.syncPermissionFeatures(context)
            // Restore the cross-process mirror as well as the exported preference value.
            val enabled = prefs(context, LSP_PREFS_NAME)
                .getBoolean(SETTINGS_C15_ABOUT_LAYOUT_PREF_KEY, false)
            LspConfig.setSettingsC15AboutLayoutEnabled(context, enabled)
            val skipSpecialPermissionRiskConfirm = prefs(context, LSP_PREFS_NAME)
                .getBoolean(SETTINGS_SKIP_SPECIAL_PERMISSION_RISK_CONFIRM_PREF_KEY, false)
            LspConfig.setSettingsSkipSpecialPermissionRiskConfirmEnabled(context, skipSpecialPermissionRiskConfirm)
            val restoreAppOpenButton = prefs(context, LSP_PREFS_NAME)
                .getBoolean(SETTINGS_RESTORE_APP_OPEN_BUTTON_PREF_KEY, false)
            LspConfig.setSettingsRestoreAppOpenButtonEnabled(context, restoreAppOpenButton)
            val restoreDomesticAuxiliaryFunctions = prefs(context, LSP_PREFS_NAME)
                .getBoolean(SETTINGS_RESTORE_DOMESTIC_AUXILIARY_FUNCTIONS_PREF_KEY, false)
            LspConfig.setSettingsRestoreDomesticAuxiliaryFunctionsEnabled(context, restoreDomesticAuxiliaryFunctions)
            val unlockRefreshRate = prefs(context, LSP_PREFS_NAME)
                .getBoolean(SETTINGS_UNLOCK_REFRESH_RATE_PREF_KEY, false)
            LspConfig.setSettingsRefreshRateUnlocked(context, unlockRefreshRate)
            val forceGlobalExtremeRefreshRate = prefs(context, LSP_PREFS_NAME)
                .getBoolean(SETTINGS_FORCE_GLOBAL_EXTREME_REFRESH_RATE_PREF_KEY, false)
            LspConfig.setSettingsForceGlobalExtremeRefreshRateEnabled(
                context,
                forceGlobalExtremeRefreshRate,
            )
        }
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
                permissionMonitorReset &&
                launcherLayoutReset &&
                assistantScreenReset,
        )
    }

    private fun prefs(context: Context, name: String): SharedPreferences {
        return if (name == LSP_PREFS_NAME) {
            context.createDeviceProtectedStorageContext()
                .getSharedPreferences(name, Context.MODE_PRIVATE)
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

    private fun restorePrefsFromJson(prefs: SharedPreferences, json: JSONObject) {
        val editor = prefs.edit().clear()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = json.optJSONObject(key) ?: continue
            when (item.optString("type")) {
                TYPE_BOOLEAN -> editor.putBoolean(key, item.getBoolean("value"))
                TYPE_INT -> editor.putInt(key, item.getInt("value"))
                TYPE_LONG -> editor.putLong(key, item.getLong("value"))
                TYPE_FLOAT -> editor.putFloat(key, item.getDouble("value").toFloat())
                TYPE_STRING -> editor.putString(key, item.optString("value", ""))
                TYPE_STRING_SET -> {
                    val values = item.optJSONArray("value") ?: JSONArray()
                    editor.putStringSet(
                        key,
                        buildSet {
                            for (index in 0 until values.length()) {
                                values.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                            }
                        }
                    )
                }
            }
        }
        require(editor.commit()) { "Failed to write preferences" }
    }
}
