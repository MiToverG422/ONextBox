package com.mi.onextbox.ui.common

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec

val LocalAppUiStyle = compositionLocalOf { AppUiStyle.ColorOs }
val LocalMaterialSwitchIconsEnabled = compositionLocalOf { false }

/** Material 3 Expressive motion and Tonal Spot 2025 palette for this app's theme choices. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Material3ExpressiveAppTheme(
    mode: AppThemeMode,
    keyColor: Long?,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = when (mode) {
        AppThemeMode.Dark, AppThemeMode.MonetDark -> true
        AppThemeMode.Light, AppThemeMode.MonetLight -> false
        AppThemeMode.System, AppThemeMode.MonetSystem -> isSystemInDarkTheme()
    }
    val seed = when {
        mode.isMonet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
        }
        mode.isMonet -> Color(0xFF2196F3)
        else -> keyColor?.let(::Color) ?: Color(0xFF2196F3)
    }
    val colors = remember(seed, dark) {
        dynamicColorScheme(
            seedColor = seed,
            isDark = dark,
            style = PaletteStyle.TonalSpot,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
        )
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
