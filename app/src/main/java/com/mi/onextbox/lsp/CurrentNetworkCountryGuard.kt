package com.mi.onextbox.lsp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Detects the country currently reported by registered mobile networks and connected Wi-Fi.
 * Device sales region and SIM home country are intentionally not used: they do not represent the
 * network the phone is using now and would keep a CN device blocked after travelling abroad.
 */
internal object CurrentNetworkCountryGuard {
    private const val CHINA_ISO = "CN"
    private const val WIFI_COUNTRY_SETTING = "wifi_country_code"
    private const val MOBILE_COUNTRY_PROPERTY = "gsm.operator.iso-country"

    fun isChina(context: Context?): Boolean {
        val countryCodes = linkedSetOf<String>()
        context?.applicationContext?.let { appContext ->
            collectMobileCountryCodes(appContext, countryCodes)
            collectConnectedWifiCountryCode(appContext, countryCodes)
        }
        addCountryCodes(countryCodes, readSystemProperty(MOBILE_COUNTRY_PROPERTY))
        return countryCodes.any { it.equals(CHINA_ISO, ignoreCase = true) }
    }

    fun isChinaInCurrentProcess(): Boolean = isChina(currentApplicationContext())

    private fun collectMobileCountryCodes(context: Context, output: MutableSet<String>) {
        val telephonyManager = runCatching {
            context.getSystemService(TelephonyManager::class.java)
        }.getOrNull() ?: return

        addCountryCodes(output, runCatching { telephonyManager.networkCountryIso }.getOrNull())

        // ColorOS exposes the per-slot overload. Reflection keeps this compatible with older APIs.
        val perSlotMethod = telephonyManager.javaClass.methods.firstOrNull { method ->
            method.name == "getNetworkCountryIso" &&
                method.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
        } ?: return
        val slotCount = runCatching {
            val activeModemCountMethod = telephonyManager.javaClass.methods.firstOrNull { method ->
                method.name == "getActiveModemCount" && method.parameterCount == 0
            }
            (activeModemCountMethod?.invoke(telephonyManager) as? Int)
                ?: telephonyManager.phoneCount
        }.getOrDefault(telephonyManager.phoneCount)
        repeat(slotCount.coerceIn(0, 4)) { slotIndex ->
            addCountryCodes(
                output,
                runCatching { perSlotMethod.invoke(telephonyManager, slotIndex) as? String }.getOrNull(),
            )
        }
    }

    private fun collectConnectedWifiCountryCode(context: Context, output: MutableSet<String>) {
        val connectivityManager = runCatching {
            context.getSystemService(ConnectivityManager::class.java)
        }.getOrNull() ?: return
        val hasConnectedWifi = runCatching {
            connectivityManager.allNetworks.any { network ->
                connectivityManager.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        }.getOrDefault(false)
        if (!hasConnectedWifi) return

        addCountryCodes(
            output,
            runCatching {
                Settings.Global.getString(context.contentResolver, WIFI_COUNTRY_SETTING)
            }.getOrNull(),
        )

        // Some ColorOS builds expose the current regulatory country only on WifiManager.
        val wifiManager = runCatching { context.getSystemService(Context.WIFI_SERVICE) }.getOrNull()
        val countryCode = wifiManager?.let { manager ->
            runCatching {
                manager.javaClass.methods.firstOrNull { method ->
                    method.name == "getCountryCode" && method.parameterCount == 0
                }?.invoke(manager) as? String
            }.getOrNull()
        }
        addCountryCodes(output, countryCode)
    }

    private fun addCountryCodes(output: MutableSet<String>, rawValue: String?) {
        rawValue
            ?.split(',', ';', ' ', '\t', '\n')
            ?.map { it.trim().uppercase(Locale.ROOT) }
            ?.filterTo(output) { it.length == 2 }
    }

    private fun readSystemProperty(key: String): String? {
        return runCatching {
            val systemProperties = Class.forName("android.os.SystemProperties")
            systemProperties.getMethod("get", String::class.java, String::class.java)
                .invoke(null, key, "") as? String
        }.getOrNull()
    }

    private fun currentApplicationContext(): Context? {
        return runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            activityThread.getMethod("currentApplication").invoke(null) as? Context
        }.getOrNull()?.applicationContext
    }
}
