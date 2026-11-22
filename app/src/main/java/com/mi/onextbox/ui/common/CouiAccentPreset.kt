package com.mi.onextbox.ui.common

import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import com.mi.onextbox.R
import io.github.suqi8.coui.kmp.theme.Colors
import org.json.JSONObject

internal data class CouiAccent(val primary: Color, val text: Color = primary)

internal enum class CouiAccentPreset(
    @StringRes val label: Int,
    private val light: CouiAccent,
    private val dark: CouiAccent,
) {
    System(R.string.theme_mode_system, CouiAccent(Color.Unspecified), CouiAccent(Color.Unspecified)),
    Blue(R.string.theme_key_color_blue, CouiAccent(Color(0xFF0080FF)), CouiAccent(Color(0xFF1A8CFF), Color(0xFF5C9DFF))),
    Green(R.string.theme_key_color_green, CouiAccent(Color(0xFF00BD13), Color(0xFF00990F)), CouiAccent(Color(0xFF24B232))),
    Purple(R.string.theme_key_color_purple, CouiAccent(Color(0xFF952AF4)), CouiAccent(Color(0xFFA539FF))),
    Red(R.string.theme_key_color_red, CouiAccent(Color(0xFFEB3B2F), Color(0xFFDB382C)), CouiAccent(Color(0xFFEB493D), Color(0xFFFF6C61))),
    Orange(R.string.theme_key_color_orange, CouiAccent(Color(0xFFFF7700), Color(0xFFEB6E00)), CouiAccent(Color(0xFFF08222))),
    Yellow(R.string.theme_key_color_yellow, CouiAccent(Color(0xFFFFB200), Color(0xFFDB9A00)), CouiAccent(Color(0xFFE5A100)));

    fun accent(darkTheme: Boolean): CouiAccent = if (darkTheme) dark else light

    companion object {
        private const val PreferenceName = "onextbox_prefs"
        internal const val Key = "app_coui_accent"

        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: System

        fun preferences(context: Context): SharedPreferences =
            context.getSharedPreferences(PreferenceName, Context.MODE_PRIVATE)

        fun save(context: Context, preset: CouiAccentPreset) {
            preferences(context).edit().putString(Key, preset.name).apply()
        }
    }
}

internal val LocalCouiAccentPreset = staticCompositionLocalOf { CouiAccentPreset.System }

@Composable
internal fun rememberCouiAccentPreset(): CouiAccentPreset {
    val context = LocalContext.current
    val prefs = remember(context) { CouiAccentPreset.preferences(context) }
    fun read() = CouiAccentPreset.fromName(runCatching { prefs.getString(CouiAccentPreset.Key, null) }.getOrNull())
    val choice = remember(prefs) { mutableStateOf(read()) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == CouiAccentPreset.Key) choice.value = read()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        choice.value = read()
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return choice.value
}

private const val ColorThemeSetting = "color_theme_setting"
private const val OverlaySetting = "theme_customization_overlay_packages"
private data class SystemAccentSettings(val colorTheme: Long?, val overlayAccent: Color?)

@Composable
internal fun rememberSystemCouiAccent(darkTheme: Boolean): CouiAccent {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val configuration = LocalConfiguration.current
    val fallback = colorResource(if (darkTheme) android.R.color.system_accent1_400 else android.R.color.system_accent1_600)

    fun read(): SystemAccentSettings {
        val colorTheme = runCatching { Settings.System.getString(resolver, ColorThemeSetting)?.toLongOrNull() }.getOrNull()
        val overlayAccent = runCatching {
            val raw = Settings.Secure.getString(resolver, OverlaySetting) ?: return@runCatching null
            val json = JSONObject(raw)
            if (colorTheme == null && !json.has("material_you_overlay_enable")) return@runCatching null
            if (json.optInt("material_you_overlay_enable", 1) == 0) return@runCatching null
            val value = parseMonetSeed(json.optString("android.theme.customization.accent_color"))
                ?: parseMonetSeed(json.optString("android.theme.customization.system_palette"))
            value?.let { Color(it) }
        }.getOrNull()
        return SystemAccentSettings(colorTheme, overlayAccent)
    }

    val settings = remember(resolver, configuration) { mutableStateOf(read()) }
    DisposableEffect(resolver, settings) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { settings.value = read() }
        }
        val registered = listOf(Settings.System.getUriFor(ColorThemeSetting), Settings.Secure.getUriFor(OverlaySetting))
            .map { uri -> runCatching { resolver.registerContentObserver(uri, false, observer) }.isSuccess }.any { it }
        settings.value = read()
        onDispose { if (registered) resolver.unregisterContentObserver(observer) }
    }
    val (colorTheme, overlayAccent) = settings.value
    return resolveSystemCouiAccent(colorTheme, overlayAccent, darkTheme, fallback)
}

private val singleColorPresets = listOf(
    Color(0xFFFF6436), Color(0xFFFFCB1B), Color(0xFF38D7D5), Color(0xFF66B92C), Color(0xFF4696B4),
    Color(0xFFCD2638), Color(0xFF007AFF), Color(0xFF7E70FF), Color(0xFFC35570), Color(0xFF63E5A7),
)

internal fun resolveSystemCouiAccent(colorTheme: Long?, overlayAccent: Color?, darkTheme: Boolean, androidAccent: Color): CouiAccent {
    if (colorTheme != null && (colorTheme and 0xFF0000L) == 0x10000L) {
        singleColorPresets.getOrNull((colorTheme and 0xFFFFL).toInt())?.let { return CouiAccent(it) }
    }
    if (overlayAccent != null) {
        val preset = CouiAccentPreset.entries.firstOrNull {
            it != CouiAccentPreset.System && (it.accent(false).primary == overlayAccent || it.accent(true).primary == overlayAccent)
        }
        return preset?.accent(darkTheme) ?: CouiAccent(overlayAccent)
    }
    return if (colorTheme == 0L) CouiAccentPreset.Blue.accent(darkTheme) else CouiAccent(androidAccent)
}

internal fun Colors.withCouiAccent(accent: CouiAccent, darkTheme: Boolean): Colors {
    val fill = accent.primary
    val filledText = if (fill.luminance() > .30f) Color.Black else Color.White
    return copy(
        primary = fill,
        onPrimary = filledText,
        primaryVariant = fill,
        onPrimaryVariant = lerp(fill, Color.White, if (darkTheme) .58f else .72f),
        primaryContainer = fill,
        onPrimaryContainer = filledText,
        disabledPrimary = fill.copy(alpha = .30f),
        disabledPrimaryButton = fill.copy(alpha = .30f),
        tertiaryContainer = fill.copy(alpha = if (darkTheme) .25f else .15f),
        tertiaryContainerVariant = fill.copy(alpha = if (darkTheme) .25f else .15f),
        onTertiaryContainer = accent.text,
        sliderKeyPointForeground = lerp(fill, Color.White, .25f),
    )
}
