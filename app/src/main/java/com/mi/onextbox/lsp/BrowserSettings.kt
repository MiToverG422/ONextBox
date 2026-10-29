package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.R
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object BrowserSettings {
    const val PACKAGE_NAME = "com.heytap.browser"
    data class Option(val key: String, val title: Int, val summary: Int, val group: Int)
    val options = listOf(
        Option("browser_remove_ads", R.string.browser_remove_ads_title, R.string.browser_remove_ads_summary, R.string.browser_ads_group),
        Option("browser_web_adblock", R.string.browser_web_adblock_title, R.string.browser_web_adblock_summary, R.string.browser_ads_group),
        Option("browser_hide_push_guide", R.string.browser_hide_push_guide_title, R.string.browser_hide_push_guide_summary, R.string.browser_prompts_group),
        Option("browser_hide_adblock_toast", R.string.browser_hide_adblock_toast_title, R.string.browser_hide_adblock_toast_summary, R.string.browser_prompts_group),
        Option("browser_hide_video_guide", R.string.browser_hide_video_guide_title, R.string.browser_hide_video_guide_summary, R.string.browser_prompts_group),
        Option("browser_simple_mode", R.string.browser_simple_mode_title, R.string.browser_simple_mode_summary, R.string.browser_appearance_group),
        Option("browser_hide_home_tab", R.string.browser_hide_home_tab_title, R.string.browser_hide_home_tab_summary, R.string.browser_tabs_group),
        Option("browser_hide_video_tab", R.string.browser_hide_video_tab_title, R.string.browser_hide_video_tab_summary, R.string.browser_tabs_group),
        Option("browser_hide_novel_tab", R.string.browser_hide_novel_tab_title, R.string.browser_hide_novel_tab_summary, R.string.browser_tabs_group),
        Option("browser_hide_my_message", R.string.browser_hide_my_message_title, R.string.browser_hide_my_message_summary, R.string.browser_profile_group),
        Option("browser_hide_my_scan", R.string.browser_hide_my_scan_title, R.string.browser_hide_my_scan_summary, R.string.browser_profile_group),
        Option("browser_hide_my_badge", R.string.browser_hide_my_badge_title, R.string.browser_hide_my_badge_summary, R.string.browser_profile_group),
        Option("browser_hide_my_cash", R.string.browser_hide_my_cash_title, R.string.browser_hide_my_cash_summary, R.string.browser_profile_group),
        Option("browser_hide_my_functions", R.string.browser_hide_my_functions_title, R.string.browser_hide_my_functions_summary, R.string.browser_profile_group),
        Option("browser_hide_my_night", R.string.browser_hide_my_night_title, R.string.browser_hide_my_night_summary, R.string.browser_profile_group),
        Option("browser_hide_my_card", R.string.browser_hide_my_card_title, R.string.browser_hide_my_card_summary, R.string.browser_profile_group),
        Option("browser_hide_my_novel", R.string.browser_hide_my_novel_title, R.string.browser_hide_my_novel_summary, R.string.browser_profile_group),
        Option("browser_hide_my_tools", R.string.browser_hide_my_tools_title, R.string.browser_hide_my_tools_summary, R.string.browser_profile_group),
        Option("browser_hide_dl_clean", R.string.browser_hide_dl_clean_title, R.string.browser_hide_dl_clean_summary, R.string.browser_download_group),
        Option("browser_hide_ai_corner", R.string.browser_hide_ai_corner_title, R.string.browser_hide_ai_corner_summary, R.string.browser_ai_group),
        Option("browser_hide_ai_input", R.string.browser_hide_ai_input_title, R.string.browser_hide_ai_input_summary, R.string.browser_ai_group),
        Option("browser_hide_ai_logo", R.string.browser_hide_ai_logo_title, R.string.browser_hide_ai_logo_summary, R.string.browser_ai_group),
        Option("browser_pure_search", R.string.browser_pure_search_title, R.string.browser_pure_search_summary, R.string.browser_search_group),
        Option("browser_replace_top_channel", R.string.browser_replace_top_channel_title, R.string.browser_replace_top_channel_summary, R.string.browser_search_group),
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
