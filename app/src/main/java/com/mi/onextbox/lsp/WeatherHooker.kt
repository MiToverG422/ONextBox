package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import java.lang.reflect.Method

internal object WeatherHooker {
    private const val TAG = "Weather"
    fun hook(loader: ClassLoader) {
        fun install(type: String, name: String, key: String, value: (Method, List<Any?>) -> Any?) {
            runCatching {
                val methods = loader.loadClass("com.oplus.weather.$type").declaredMethods.filter { it.name == name && !it.isBridge }
                require(methods.isNotEmpty()) { "No $type.$name" }
                methods.forEach { method ->
                    ModernHookRegistry.installFast("$TAG:$method", method) { chain ->
                        if (SystemExtrasSettings.active(key)) value(method, chain.args) else chain.proceed()
                    }
                }
                HookLog.i(TAG, "$type.$name hooked (${methods.size})")
            }.onFailure { HookLog.w(TAG, "$type.$name unavailable", it) }
        }
        install("utils.AppFeatureUtils", "isSupportOplusAd", SystemExtrasSettings.WEATHER_ADS) { _, _ -> false }
        install("utils.AppFeatureUtils", "isShowNoticeAd", SystemExtrasSettings.WEATHER_ADS) { _, _ -> false }
        install("ad.OperationsCardAdManager", "canRequestAds", SystemExtrasSettings.WEATHER_ADS) { _, _ -> false }
        install("ad.OperationsCardAdManager", "getOperationsCardAd", SystemExtrasSettings.WEATHER_ADS) { _, _ -> emptyList<String>() }
        listOf("getHotRecommendSwitch", "getWonderfulRecommendSwitch", "getWeatherHotListSwitch").forEach { name ->
            install("uiconfig.UIConfigManager", name, SystemExtrasSettings.WEATHER_ADS) { _, _ -> false }
        }
        install("ad.splash.SplashAdManager", "createSplashView", SystemExtrasSettings.WEATHER_ADS) { _, args ->
            args.lastOrNull()?.let { ModernReflect.callMethod(it, "invoke") }
            null
        }
        // Keep the native size check: a short server response must never be indexed as 15 days.
        install("uiconfig.UIConfigManager", "getLocalDay15ExpandConfig", SystemExtrasSettings.WEATHER_DAYS) { _, _ -> true }
        install("main.view.itemview.CctvWeatherForecastItemCreator", "create", SystemExtrasSettings.WEATHER_FORECAST) { _, _ -> null }
        install("utils.AppFeatureUtils", "getNoOutLinkFeature", SystemExtrasSettings.WEATHER_BROWSER) { _, _ -> true }
        install("utils.LocalUtils", "jumpToBrowser", SystemExtrasSettings.WEATHER_BROWSER) { _, _ -> null }
    }
}
