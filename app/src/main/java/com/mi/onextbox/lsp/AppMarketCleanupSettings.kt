package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.R
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object AppMarketCleanupSettings {
    const val UPDATE = "app_market_remove_update_recommendations"
    const val DOWNLOAD = "app_market_remove_download_recommendations"
    const val LEGACY_UPDATE_DOWNLOAD = "app_market_remove_update_download_recommendations"

    data class Option(val key: String, val title: Int, val summary: Int, val group: Int)
    val options = listOf(
        Option("app_market_remove_splash_recommendations", R.string.market_remove_splash_title, R.string.market_remove_splash_summary, R.string.market_ads_group),
        Option(UPDATE, R.string.market_remove_update_title, R.string.market_remove_update_summary, R.string.feature_group_app_market_recommendations),
        Option(DOWNLOAD, R.string.market_remove_download_title, R.string.market_remove_download_summary, R.string.feature_group_app_market_recommendations),
        Option("app_market_remove_mine_recommendations", R.string.market_remove_mine_title, R.string.market_remove_mine_summary, R.string.feature_group_app_market_recommendations),
        Option("app_market_hide_search_home_recommendations", R.string.market_remove_search_home_title, R.string.market_remove_search_home_summary, R.string.market_search_detail_group),
        Option("app_market_hide_search_result_recommendations", R.string.market_remove_search_result_title, R.string.market_remove_search_result_summary, R.string.market_search_detail_group),
        Option("app_market_hide_detail_recommendations", R.string.market_remove_detail_title, R.string.market_remove_detail_summary, R.string.market_search_detail_group),
    )
    val keys = options.map { it.key }.toSet()

    fun enabled(context: Context, key: String): Boolean {
        val prefs = LspPreferenceStore.prefs(context)
        val inherited = (key == UPDATE || key == DOWNLOAD) &&
            LspConfig.isAppMarketRemoveUpdateDownloadRecommendationsEnabled(context)
        return AppMarketCleanupRules.enabled(
            if (prefs.contains(key)) prefs.getBoolean(key, false) else null,
            inherited,
        )
    }

    fun state(context: Context) = keys.associateWith { enabled(context, it) }

    fun set(context: Context, key: String, enabled: Boolean) {
        require(key in keys)
        when (key) {
            UPDATE, DOWNLOAD -> ModernXposedPreferenceSync.setBooleanAndSync(context, key, enabled)
            "app_market_remove_splash_recommendations" -> LspConfig.setAppMarketRemoveSplashRecommendationsEnabled(context, enabled)
            "app_market_remove_mine_recommendations" -> LspConfig.setAppMarketRemoveMineRecommendationsEnabled(context, enabled)
            "app_market_hide_search_home_recommendations" -> LspConfig.setAppMarketHideSearchHomeRecommendationsEnabled(context, enabled)
            "app_market_hide_search_result_recommendations" -> LspConfig.setAppMarketHideSearchResultRecommendationsEnabled(context, enabled)
            "app_market_hide_detail_recommendations" -> LspConfig.setAppMarketHideDetailRecommendationsEnabled(context, enabled)
        }
    }

    fun active(page: AppMarketCleanupRules.Page): Boolean {
        val legacy = LspConfig.isAppMarketRemoveUpdateDownloadRecommendationsEnabledXposed()
        return HookConfigSnapshot.boolean(if (page == AppMarketCleanupRules.Page.UPDATE) UPDATE else DOWNLOAD, legacy) ?: legacy
    }
}
