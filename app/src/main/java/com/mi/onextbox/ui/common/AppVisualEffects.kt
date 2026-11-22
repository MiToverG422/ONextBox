package com.mi.onextbox.ui.common

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

internal enum class AppVisualEffect(val systemKey: String, val preferenceKey: String) {
    BackgroundBlur("system_material_blur_enable", "app_visual_blur_mode"),
    Outline("system_material_stroke_enable", "app_visual_outline_mode"),
    AnimationBlur("animationBlurrySwitch", "app_animation_blur_mode"),
}

internal enum class VisualEffectMode {
    FollowSystem, Off, On;

    fun resolve(systemEnabled: Boolean): Boolean = when (this) {
        FollowSystem -> systemEnabled
        Off -> false
        On -> true
    }
}

internal data class AppVisualEffects(
    val backgroundBlur: Boolean = true,
    val outline: Boolean = true,
    val animationBlur: Boolean = true,
)

internal val LocalAppVisualEffects = compositionLocalOf { AppVisualEffects() }
internal val LocalVisualEffectSettings = compositionLocalOf<VisualEffectSettings?> { null }

private const val LegacyTopBarBlur = "blur_effect_enabled"
private const val LowPower = "low_power"
private const val SuperPowerSave = "super_powersave_mode_state"

internal fun readVisualEffectMode(effect: AppVisualEffect, value: String?, legacyBlur: Boolean): VisualEffectMode =
    VisualEffectMode.entries.firstOrNull { it.name == value } ?: when {
        value == null && effect == AppVisualEffect.BackgroundBlur && !legacyBlur -> VisualEffectMode.Off
        else -> VisualEffectMode.FollowSystem
    }

internal class VisualEffectSettings(private val prefs: SharedPreferences, private val resolver: ContentResolver) {
    private var modes by mutableStateOf(readModes())
    private var system by mutableStateOf(readSystem())

    fun mode(effect: AppVisualEffect) = modes.getValue(effect)

    fun set(effect: AppVisualEffect, selected: VisualEffectMode) {
        if (selected == mode(effect)) return
        prefs.edit().putString(effect.preferenceKey, selected.name).apply()
    }

    fun effects() = AppVisualEffects(
        backgroundBlur = mode(AppVisualEffect.BackgroundBlur).resolve(system.backgroundBlur),
        outline = mode(AppVisualEffect.Outline).resolve(system.outline),
        animationBlur = mode(AppVisualEffect.AnimationBlur).resolve(system.animationBlur),
    )

    fun register(): () -> Unit {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { system = readSystem() }
        }
        val uris = AppVisualEffect.entries.map { Settings.System.getUriFor(it.systemKey) } +
            listOf(Settings.Global.getUriFor(LowPower), Settings.System.getUriFor(SuperPowerSave))
        uris.forEach { uri ->
            try { resolver.registerContentObserver(uri, false, observer) } catch (_: SecurityException) {}
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == LegacyTopBarBlur || AppVisualEffect.entries.any { it.preferenceKey == key }) {
                modes = readModes()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        modes = readModes()
        system = readSystem()
        return {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            resolver.unregisterContentObserver(observer)
        }
    }

    private fun readModes(): Map<AppVisualEffect, VisualEffectMode> {
        val snapshot = prefs.all
        val legacyBlur = snapshot[LegacyTopBarBlur] as? Boolean ?: true
        return AppVisualEffect.entries.associateWith { effect ->
            readVisualEffectMode(effect, snapshot[effect.preferenceKey] as? String, legacyBlur)
        }
    }

    private fun readSystem(): AppVisualEffects {
        val powerSave = runCatching { Settings.Global.getInt(resolver, LowPower, 0) == 1 }.getOrDefault(false) ||
            systemFlag(SuperPowerSave, default = false)
        return AppVisualEffects(
            backgroundBlur = !powerSave && systemFlag(AppVisualEffect.BackgroundBlur.systemKey),
            outline = !powerSave && systemFlag(AppVisualEffect.Outline.systemKey),
            animationBlur = systemFlag(AppVisualEffect.AnimationBlur.systemKey),
        )
    }

    private fun systemFlag(key: String, default: Boolean = true): Boolean =
        runCatching { Settings.System.getInt(resolver, key, if (default) 1 else 0) == 1 }.getOrDefault(default)
}

@Composable
internal fun rememberVisualEffectSettings(): VisualEffectSettings {
    val context = LocalContext.current.applicationContext
    val settings = remember(context) {
        VisualEffectSettings(context.getSharedPreferences("onextbox_prefs", Context.MODE_PRIVATE), context.contentResolver)
    }
    DisposableEffect(settings) { onDispose(settings.register()) }
    return settings
}

@Composable
internal fun AppVisualEffectsHost(content: @Composable () -> Unit) {
    val settings = rememberVisualEffectSettings()
    CompositionLocalProvider(
        LocalVisualEffectSettings provides settings,
        LocalAppVisualEffects provides settings.effects(),
        content = content,
    )
}
