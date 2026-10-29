package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.R
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object SystemExtrasSettings {
    const val WEATHER = "com.coloros.weather2"
    const val PICKER = "com.android.photopicker"
    const val ENGINEER = "com.oplus.engineermode"
    const val WEATHER_ADS = "weather_remove_ads"
    const val WEATHER_BROWSER = "weather_block_browser"
    const val WEATHER_DAYS = "weather_fifteen_days"
    const val WEATHER_FORECAST = "weather_hide_forecast_card"
    const val NATIVE_PICKER = "photopicker_native"
    const val ENGINEER_HIDDEN = "engineer_show_hidden"
    const val ENGINEER_KEYS = "engineer_spoof_keys"
    const val AI_BATTERY = "battery_ai_assistant"

    data class Option(val key: String, val title: Int, val summary: Int, val group: Int, val pkg: String)
    val options = listOf(
        Option(WEATHER_ADS, R.string.weather_remove_ads, R.string.weather_remove_ads_summary, R.string.weather_cleanup_group, WEATHER),
        Option(WEATHER_BROWSER, R.string.weather_block_browser, R.string.weather_block_browser_summary, R.string.weather_cleanup_group, WEATHER),
        Option(WEATHER_FORECAST, R.string.weather_hide_forecast, R.string.weather_hide_forecast_summary, R.string.weather_cleanup_group, WEATHER),
        Option(WEATHER_DAYS, R.string.weather_fifteen_days, R.string.weather_fifteen_days_summary, R.string.weather_forecast_group, WEATHER),
        Option(NATIVE_PICKER, R.string.photopicker_native, R.string.photopicker_native_summary, R.string.photopicker_group, PICKER),
        Option(ENGINEER_HIDDEN, R.string.engineer_show_hidden, R.string.engineer_show_hidden_summary, R.string.engineer_options_group, ENGINEER),
        Option(ENGINEER_KEYS, R.string.engineer_spoof_keys, R.string.engineer_spoof_keys_summary, R.string.engineer_keys_group, ENGINEER),
        Option(AI_BATTERY, R.string.battery_ai_assistant, R.string.battery_ai_assistant_summary, R.string.battery_optimization_group, "com.oplus.battery"),
    )
    val keys = options.map { it.key }.toSet()
    fun active(key: String) = HookConfigSnapshot.boolean(key, false) == true
    fun enabled(context: Context, key: String) = LspPreferenceStore.prefs(context).getBoolean(key, false)
    fun state(context: Context) = keys.associateWith { enabled(context, it) }
    fun set(context: Context, key: String, enabled: Boolean) {
        require(key in keys)
        ModernXposedPreferenceSync.setBooleanAndSync(context, key, enabled)
    }
}

internal object EngineerKeySettings {
    data class Key(val id: String, val nativeClass: String, val title: String)
    val entries = listOf(
        Key("rpmb", "RpmbKey", "Rpmb key"), Key("soter", "SOTERKey", "SOTER key"),
        Key("ifaa", "IFAAKey", "IFAA key"), Key("crypto", "CryptoKey", "Crypto key"),
        Key("widevine", "WidevineL1Key", "WidevineL1 key"), Key("hdcp", "HdcpKey", "HDCP key"),
        Key("google", "GoogleKey", "Attestation key"), Key("fido", "FidoKey", "FIDO key"),
        Key("pki", "PKICert", "PKI cert"), Key("pki_group", "PKIGroupCert", "PKI Group cert"),
        Key("fido2", "Fido2Key", "FIDO2 key"), Key("rkp", "RkpDefaultKey", "RKP default"),
        Key("rkp_widevine", "RkpWidevineKey", "RKP widevine"), Key("strongbox", "StrongBoxKey", "StrongBox key"),
        Key("dp_hdcp", "DPHDCPKey", "DP HDCP V2 key"), Key("rkp_avf", "RkpAvfKey", "RKP AVF"),
    )
    fun pref(key: Key) = "engineer_key_${key.id}"
    val keys = entries.map(::pref).toSet()
    fun mode(context: Context, key: Key) = LspPreferenceStore.prefs(context).getInt(pref(key), 0).coerceIn(0, 3)
    fun state(context: Context) = entries.associate { pref(it) to mode(context, it) }
    fun mode(nativeClass: String): Int = if (!SystemExtrasSettings.active(SystemExtrasSettings.ENGINEER_KEYS)) 0 else entries.firstOrNull { nativeClass == "com.oplus.engineermode.security.KeyInstanceImpl\$${it.nativeClass}" }
        ?.let { HookConfigSnapshot.int(pref(it), 0)?.coerceIn(0, 3) } ?: 0
    fun set(context: Context, key: Key, mode: Int) = ModernXposedPreferenceSync.setIntAndSync(context, pref(key), mode.coerceIn(0, 3))
}
