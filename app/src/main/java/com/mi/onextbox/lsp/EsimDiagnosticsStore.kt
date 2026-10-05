package com.mi.onextbox.lsp

import android.content.Context
import android.content.Intent
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

internal data class EsimProfileDiagnostic(
    val displayName: String = "",
    val serviceProviderName: String = "",
    val iccidMasked: String = "",
    val profileClass: Int? = null,
    val enabled: Boolean = false,
    val portIndex: Int? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("displayName", displayName)
        put("serviceProviderName", serviceProviderName)
        put("iccidMasked", iccidMasked)
        putNullable("profileClass", profileClass)
        put("enabled", enabled)
        putNullable("portIndex", portIndex)
    }

    companion object {
        fun fromJson(json: JSONObject): EsimProfileDiagnostic = EsimProfileDiagnostic(
            displayName = json.optText("displayName"),
            serviceProviderName = json.optText("serviceProviderName"),
            iccidMasked = json.optText("iccidMasked"),
            profileClass = json.optIntOrNull("profileClass"),
            enabled = json.optBoolean("enabled", false),
            portIndex = json.optIntOrNull("portIndex"),
        )
    }
}

internal data class EsimDiagnosticsSnapshot(
    val capturedAtMillis: Long = 0L,
    val hookReady: Boolean = false,
    val eidMasked: String = "",
    val powerState: String = "unknown",
    val bindingResult: String = "",
    val mepSupported: Boolean? = null,
    val networkAvailable: Boolean? = null,
    val networkCountries: String = "",
    val locationEnabled: Boolean? = null,
    val profileCount: Int? = null,
    val operationalProfileCount: Int? = null,
    val enabledProfileCount: Int? = null,
    val profiles: List<EsimProfileDiagnostic> = emptyList(),
    val profileLimit: Int? = null,
    val ppVersion: String = "",
    val svn: String = "",
    val firmwareVersion: String = "",
    val freeNonVolatileMemory: Long? = null,
    val freeVolatileMemory: Long? = null,
    val smdpAddress: String = "",
    val smdsAddress: String = "",
    val lastDownloadResult: Int? = null,
    val collectionMessage: String = "",
) {
    fun toJson(): String = JSONObject().apply {
        put("capturedAtMillis", capturedAtMillis)
        put("hookReady", hookReady)
        put("eidMasked", eidMasked)
        put("powerState", powerState)
        put("bindingResult", bindingResult)
        putNullable("mepSupported", mepSupported)
        putNullable("networkAvailable", networkAvailable)
        put("networkCountries", networkCountries)
        putNullable("locationEnabled", locationEnabled)
        putNullable("profileCount", profileCount)
        putNullable("operationalProfileCount", operationalProfileCount)
        putNullable("enabledProfileCount", enabledProfileCount)
        put("profiles", JSONArray().apply { profiles.forEach { put(it.toJson()) } })
        putNullable("profileLimit", profileLimit)
        put("ppVersion", ppVersion)
        put("svn", svn)
        put("firmwareVersion", firmwareVersion)
        putNullable("freeNonVolatileMemory", freeNonVolatileMemory)
        putNullable("freeVolatileMemory", freeVolatileMemory)
        put("smdpAddress", smdpAddress)
        put("smdsAddress", smdsAddress)
        putNullable("lastDownloadResult", lastDownloadResult)
        put("collectionMessage", collectionMessage)
    }.toString()

    companion object {
        fun fromJson(raw: String?): EsimDiagnosticsSnapshot? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val json = JSONObject(raw)
                EsimDiagnosticsSnapshot(
                    capturedAtMillis = json.optLong("capturedAtMillis", 0L),
                    hookReady = json.optBoolean("hookReady", false),
                    eidMasked = json.optText("eidMasked"),
                    powerState = json.optText("powerState").ifBlank { "unknown" },
                    bindingResult = json.optText("bindingResult"),
                    mepSupported = json.optBooleanOrNull("mepSupported"),
                    networkAvailable = json.optBooleanOrNull("networkAvailable"),
                    networkCountries = json.optText("networkCountries"),
                    locationEnabled = json.optBooleanOrNull("locationEnabled"),
                    profileCount = json.optIntOrNull("profileCount"),
                    operationalProfileCount = json.optIntOrNull("operationalProfileCount"),
                    enabledProfileCount = json.optIntOrNull("enabledProfileCount"),
                    profiles = json.optProfileDiagnostics("profiles"),
                    profileLimit = json.optIntOrNull("profileLimit"),
                    ppVersion = json.optText("ppVersion"),
                    svn = json.optText("svn"),
                    firmwareVersion = json.optText("firmwareVersion"),
                    freeNonVolatileMemory = json.optLongOrNull("freeNonVolatileMemory"),
                    freeVolatileMemory = json.optLongOrNull("freeVolatileMemory"),
                    smdpAddress = json.optText("smdpAddress"),
                    smdsAddress = json.optText("smdsAddress"),
                    lastDownloadResult = json.optIntOrNull("lastDownloadResult"),
                    collectionMessage = json.optText("collectionMessage"),
                )
            }.getOrNull()
        }
    }
}

internal object EsimDiagnosticsStore {
    const val ACTION_REFRESH = "com.mi.onextbox.action.REFRESH_ESIM_DIAGNOSTICS"
    const val TARGET_PACKAGE = "com.oplus.euicc"
    const val SETTINGS_KEY_SNAPSHOT = "oost_esim_diagnostics_snapshot"

    fun read(context: Context): EsimDiagnosticsSnapshot? = runCatching {
        EsimDiagnosticsSnapshot.fromJson(
            Settings.Global.getString(context.contentResolver, SETTINGS_KEY_SNAPSHOT),
        )
    }.getOrNull()

    fun publish(context: Context, snapshot: EsimDiagnosticsSnapshot): Boolean = runCatching {
        Settings.Global.putString(
            context.contentResolver,
            SETTINGS_KEY_SNAPSHOT,
            snapshot.toJson(),
        )
    }.getOrDefault(false)

    fun requestRefresh(context: Context): Boolean = runCatching {
        context.sendBroadcast(
            Intent(ACTION_REFRESH).setPackage(TARGET_PACKAGE),
        )
        true
    }.getOrDefault(false)
}

private fun JSONObject.putNullable(key: String, value: Any?) {
    put(key, value ?: JSONObject.NULL)
}

private fun JSONObject.optText(key: String): String =
    if (!has(key) || isNull(key)) "" else optString(key, "")

private fun JSONObject.optBooleanOrNull(key: String): Boolean? =
    if (!has(key) || isNull(key)) null else optBoolean(key)

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (!has(key) || isNull(key)) null else optInt(key)

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (!has(key) || isNull(key)) null else optLong(key)

private fun JSONObject.optProfileDiagnostics(key: String): List<EsimProfileDiagnostic> {
    val array = optJSONArray(key) ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let { add(EsimProfileDiagnostic.fromJson(it)) }
        }
    }
}
