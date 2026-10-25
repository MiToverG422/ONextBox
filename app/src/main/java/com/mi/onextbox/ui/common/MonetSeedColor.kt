package com.mi.onextbox.ui.common

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import org.json.JSONObject

private const val SystemPaletteSetting = "theme_customization_overlay_packages"

@Composable
internal fun rememberMonetSeedColor(enabled: Boolean, selectedColor: Long?): Color? {
    if (!enabled) return null
    if (selectedColor != null) return Color(selectedColor)

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val fallback = colorResource(android.R.color.system_accent1_500)
    val resolver = context.contentResolver
    fun readSeed(): Int? = runCatching {
        val json = Settings.Secure.getString(resolver, SystemPaletteSetting) ?: return@runCatching null
        parseMonetSeed(JSONObject(json).optString("android.theme.customization.system_palette"))
    }.getOrNull()
    var systemSeed by remember(context, configuration) { mutableStateOf(readSeed()) }
    DisposableEffect(resolver, configuration) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { systemSeed = readSeed() }
        }
        val registered = runCatching {
            resolver.registerContentObserver(Settings.Secure.getUriFor(SystemPaletteSetting), false, observer)
        }.isSuccess
        systemSeed = readSeed()
        onDispose { if (registered) resolver.unregisterContentObserver(observer) }
    }
    // Explicit seed generation lets COUI apply the app's palette style and color spec.
    return systemSeed?.let { Color(it) } ?: fallback
}

internal fun parseMonetSeed(value: String): Int? {
    val hex = value.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    if (hex.any { it.digitToIntOrNull(16) == null }) return null
    val argb = hex.toLongOrNull(16) ?: return null
    return (argb or 0xFF000000L).toInt()
}
