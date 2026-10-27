@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.mi.onextbox.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mi.onextbox.color2026.hct.Hct
import com.mi.onextbox.color2026.scheme.SchemeCmf
import io.github.suqi8.coui.kmp.theme.Colors
import io.github.suqi8.coui.kmp.theme.MonetRoles
import io.github.suqi8.coui.kmp.theme.mapMd3RolesToCOUIColorsCommon

internal fun coui2026Colors(seed: Color, dark: Boolean): Colors {
    val scheme = SchemeCmf(Hct.fromInt(seed.toArgb()), dark, 0.0)
    // Reuse the pinned COUI 1.1.0 role mapping, including disabled and container colors.
    return mapMd3RolesToCOUIColorsCommon(MonetRoles(
        primary = Color(scheme.primary),
        onPrimary = Color(scheme.onPrimary),
        primaryFixed = Color(scheme.primaryFixed),
        onPrimaryFixed = Color(scheme.onPrimaryFixed),
        error = Color(scheme.error),
        onError = Color(scheme.onError),
        errorContainer = Color(scheme.errorContainer),
        onErrorContainer = Color(scheme.onErrorContainer),
        primaryContainer = Color(scheme.primaryContainer),
        onPrimaryContainer = Color(scheme.onPrimaryContainer),
        secondary = Color(scheme.secondary),
        onSecondary = Color(scheme.onSecondary),
        secondaryContainer = Color(scheme.secondaryContainer),
        onSecondaryContainer = Color(scheme.onSecondaryContainer),
        tertiaryContainer = Color(scheme.tertiaryContainer),
        onTertiaryContainer = Color(scheme.onTertiaryContainer),
        background = Color(scheme.background),
        onBackground = Color(scheme.onBackground),
        surface = Color(scheme.surface),
        onSurface = Color(scheme.onSurface),
        surfaceVariant = Color(scheme.surfaceVariant),
        surfaceContainer = Color(scheme.surfaceContainer),
        surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
        surfaceContainerHighest = Color(scheme.surfaceContainerHighest),
        outline = Color(scheme.outline),
        outlineVariant = Color(scheme.outlineVariant),
        onSurfaceVariant = Color(scheme.onSurfaceVariant),
    ), dark)
}
