package com.mi.onextbox.ui.common

import android.content.SharedPreferences
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import io.github.suqi8.coui.kmp.basic.LocalCouiCardShape
import io.github.suqi8.coui.kmp.theme.COUITheme

val LocalAppUiStyle = compositionLocalOf { AppUiStyle.ColorOs }
internal val LocalSmootherCardCorners = compositionLocalOf { false }

@Composable
internal fun rememberAppUiStyleValues(style: AppUiStyle): Array<ProvidedValue<*>> {
    val context = LocalContext.current
    val preferences = remember(context) { AppUiStyle.preferences(context) }
    var smoother by remember(preferences) {
        mutableStateOf(preferences.getBoolean(AppUiStyle.SmootherCardCornersKey, false))
    }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == null || key == AppUiStyle.SmootherCardCornersKey) {
                smoother = prefs.getBoolean(AppUiStyle.SmootherCardCornersKey, false)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    return remember(style, smoother) {
        val useNativeShape = AppCardCornerStyle.resolve(style, smoother) == AppCardCornerStyle.ColorOs17
        arrayOf(
            LocalAppUiStyle provides style,
            LocalSmootherCardCorners provides smoother,
            LocalCouiCardShape provides if (useNativeShape) ::ColorOs17CardShape else null,
        )
    }
}

/** Keep shared Compose primitives in sync with the surrounding COUI palette. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun AppUiStyleAppTheme(
    style: AppUiStyle,
    mode: AppThemeMode,
    keyColor: Long?,
    paletteStyle: Int = 0,
    colorSpec: Int = 0,
    content: @Composable () -> Unit,
) {
    val couiIndication = LocalIndication.current
    val coui = COUITheme.colorScheme
    val base = if (coui.background.luminance() < .5f) darkColorScheme() else lightColorScheme()
    MaterialTheme(
        colorScheme = base.copy(
            primary = coui.primary,
            background = coui.background,
            onBackground = coui.onBackground,
            surface = coui.surface,
            onSurface = coui.onSurface,
            onSurfaceVariant = coui.onSurfaceVariantSummary,
        ),
    ) {
        CompositionLocalProvider(
            LocalIndication provides couiIndication,
            *rememberAppUiStyleValues(style),
            content = content,
        )
    }
}
