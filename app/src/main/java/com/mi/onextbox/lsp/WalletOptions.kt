package com.mi.onextbox.lsp

import com.mi.onextbox.R
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal enum class WalletOptions(val key: String, val title: Int, val summary: Int = 0) {
    MARKETING("wallet_marketing", R.string.wallet_marketing, R.string.wallet_marketing_summary),
    HOME("wallet_home", R.string.wallet_home, R.string.wallet_home_summary),
    SURVEY("wallet_survey", R.string.wallet_survey, R.string.wallet_survey_summary),
    SWIPE_ADS("wallet_swipe_ads", R.string.wallet_swipe_ads, R.string.wallet_swipe_ads_summary),
    SHORTCUTS("wallet_swipe_shortcuts", R.string.wallet_swipe_shortcuts, R.string.wallet_swipe_shortcuts_summary),
    TOOLBAR("wallet_toolbar", R.string.wallet_toolbar, R.string.wallet_toolbar_summary),
    FEEDBACK("wallet_feedback", R.string.wallet_feedback, R.string.wallet_feedback_summary),
    FEED("wallet_feed", R.string.wallet_feed, R.string.wallet_feed_summary),
    NFC("wallet_nfc", R.string.wallet_nfc, R.string.wallet_nfc_summary),
    SPLASH("wallet_splash", R.string.wallet_splash, R.string.wallet_splash_summary);

    fun active() = HookConfigSnapshot.boolean(key, false) == true

    fun set(context: android.content.Context, enabled: Boolean) {
        ModernXposedPreferenceSync.setBooleanAndSync(context, key, enabled)
        if (enabled) LspFrameworkService.requestScopes(setOf(PACKAGE))
    }

    companion object {
        const val PACKAGE = "com.finshell.wallet"
    }
}
