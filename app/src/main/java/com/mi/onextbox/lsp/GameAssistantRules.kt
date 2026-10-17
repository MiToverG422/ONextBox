package com.mi.onextbox.lsp

internal object GameAssistantRules {
    const val PACKAGE_NAME = "com.oplus.games"
    const val ENV_PREFERENCES = "com.oplus.games_environment_switch"
    const val DEFAULT_SKIN = "default"
    val skins = listOf(DEFAULT_SKIN, "gungod-comp", "gungod-snipe", "genshin-hutao",
        "genshin-xiangling", "genshin-keqing", "genshin-shenlilinghua", "naruto")

    fun skin(value: String?): String = value?.takeIf { it in skins } ?: DEFAULT_SKIN
    fun skinName(value: String?): String = skin(value).takeUnless { it == DEFAULT_SKIN }.orEmpty()
    // Do not accidentally remove TRACE, crash-recovery or refresh-rate protections.
    fun safetyStatus(blockRoot: Boolean, status: Int): Int = if (blockRoot && status == 1) 0 else status
    fun maxItems(unlimited: Boolean, stock: Int): Int = if (unlimited) Int.MAX_VALUE else stock
    fun musicMembership(unlocked: Boolean, candidate: String?, known: Boolean): Boolean =
        if (unlocked) !candidate.isNullOrEmpty() else known
    val developerCategories = listOf("env", "perf", "display", "audio", "control", "business",
        "desktop", "debug", "game_setting")
    fun developerIntKey(key: String): Boolean = key == "feel_adjust_v2_idle_fade_delay_sec_key"
}
