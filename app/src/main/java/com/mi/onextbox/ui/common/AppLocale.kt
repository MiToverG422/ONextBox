package com.mi.onextbox.ui.common

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale
import com.mi.onextbox.R

object AppLocale {
    const val LANGUAGE_SYSTEM = ""
    const val LANGUAGE_EN = "en"
    const val LANGUAGE_ZH_CN = "zh-CN"
    const val LANGUAGE_ZH_TW = "zh-TW"
    const val LANGUAGE_YUE = "yue-Hant"
    const val LANGUAGE_PSEUDO_HAN = "ja-Pseudo-CN"
    const val LANGUAGE_RU = "ru"
    const val LANGUAGE_VI = "vi"
    const val LANGUAGE_JA = "ja"
    const val LANGUAGE_KO = "ko"

    data class LanguageOption(val tag: String, val nativeName: String)

    val languages = listOf(
        LanguageOption(LANGUAGE_ZH_CN, "简体中文"),
        LanguageOption(LANGUAGE_ZH_TW, "正體中文"),
        LanguageOption(LANGUAGE_EN, "English"),
        LanguageOption(LANGUAGE_YUE, "繁體廣東話"),
        LanguageOption(LANGUAGE_PSEUDO_HAN, "偽中国語"),
        LanguageOption(LANGUAGE_RU, "Русский"),
        LanguageOption(LANGUAGE_VI, "Tiếng Việt"),
        LanguageOption(LANGUAGE_JA, "日本語"),
        LanguageOption(LANGUAGE_KO, "한국어"),
    )

    fun options(context: Context): List<LanguageOption> =
        listOf(LanguageOption(LANGUAGE_SYSTEM, context.getString(R.string.language_system))) + languages

    fun displayName(context: Context, tag: String): String =
        options(context).firstOrNull { it.tag == tag.normalizeLanguageTag() }?.nativeName
            ?: context.getString(R.string.language_system)

    // The public identifier keeps the requested spelling; Android resource qualifiers require
    // the region before the variant. Keep the novelty locale separate from ordinary Japanese.
    fun resourceLanguageTag(languageTag: String): String =
        when (val tag = languageTag.normalizeLanguageTag()) {
            LANGUAGE_PSEUDO_HAN -> "ja-CN-pseudo"
            else -> tag
        }

    private const val PREFS_NAME = "onextbox_prefs"
    private const val KEY_APP_LANGUAGE = "app_language"

    fun getSelectedLanguageTag(context: Context): String {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_APP_LANGUAGE, LANGUAGE_SYSTEM)
            .orEmpty()
            .normalizeLanguageTag()
            .takeIf { it in supportedLanguageTags }
            ?: LANGUAGE_SYSTEM
    }

    fun setSelectedLanguageTag(context: Context, languageTag: String) {
        val normalizedTag = languageTag
            .normalizeLanguageTag()
            .takeIf { it in supportedLanguageTags }
            ?: LANGUAGE_SYSTEM
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_APP_LANGUAGE, normalizedTag) }
    }

    fun wrapContext(context: Context): Context {
        return wrapContext(context, getSelectedLanguageTag(context))
    }

    fun wrapContext(context: Context, languageTag: String): Context {
        if (languageTag.isBlank()) return context

        val locale = Locale.forLanguageTag(resourceLanguageTag(languageTag))
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList(locale))
        return context.createConfigurationContext(configuration)
    }

    private val supportedLanguageTags = languages.mapTo(mutableSetOf(LANGUAGE_SYSTEM)) { it.tag }

    private fun String.normalizeLanguageTag(): String {
        return when (this) {
            "zh-HK", "zh-MO" -> LANGUAGE_ZH_TW
            "yue", "yue-HK" -> LANGUAGE_YUE
            "zh-CN-fakehan", "ja-CN-pseudo" -> LANGUAGE_PSEUDO_HAN
            else -> this
        }
    }
}
