package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import kotlin.math.roundToInt

internal object SettingsAppearanceSettings {
    const val ACCOUNT_SIZE = "settings_account_card_resize"
    const val ACCOUNT_PERCENT = "settings_account_card_scale_percent"
    const val TITLE_LOCK = "settings_title_collapse_locked"
    const val TITLE_COLLAPSED = "settings_title_collapsed"
    const val MIN_PERCENT = 60
    const val MAX_PERCENT = 150
    const val DEFAULT_PERCENT = 100
    val keys = setOf(ACCOUNT_SIZE, ACCOUNT_PERCENT, TITLE_LOCK, TITLE_COLLAPSED)

    data class State(
        val accountSize: Boolean = false,
        val accountPercent: Int = DEFAULT_PERCENT,
        val titleCollapsed: Boolean = false,
        val titleLocked: Boolean = false,
    )

    fun state(context: Context): State {
        val values = LspPreferenceStore.prefs(context).all
        return State(values[ACCOUNT_SIZE] == true,
            normalize((values[ACCOUNT_PERCENT] as? Number)?.toInt() ?: DEFAULT_PERCENT),
            LspConfig.isSettingsTitleCollapsedEnabled(context), values[TITLE_LOCK] == true)
    }

    fun stateXposed() = State(
        HookConfigSnapshot.boolean(ACCOUNT_SIZE, false) == true,
        normalize(HookConfigSnapshot.int(ACCOUNT_PERCENT, DEFAULT_PERCENT) ?: DEFAULT_PERCENT),
        LspConfig.isSettingsTitleCollapsedEnabledXposed(),
        HookConfigSnapshot.boolean(TITLE_LOCK, false) == true,
    )

    fun setEnabled(context: Context, key: String, enabled: Boolean) {
        require(key == ACCOUNT_SIZE || key == TITLE_LOCK)
        ModernXposedPreferenceSync.setBooleanAndSync(context, key, enabled)
    }

    fun setPercent(context: Context, percent: Int) =
        ModernXposedPreferenceSync.setIntAndSync(context, ACCOUNT_PERCENT, normalize(percent))

    fun normalize(percent: Int) = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
}

internal object SettingsAppearanceRules {
    fun scaled(value: Int, percent: Int): Int =
        if (value < 0) value else (value * (SettingsAppearanceSettings.normalize(percent) / 100.0)).roundToInt()

    fun titleMode(nativeMode: Int, locked: Boolean): Int = if (locked && nativeMode != 2) 1 else nativeMode
}

/** A native write made after our override becomes the new value to restore. */
internal class SettingsOwnedDimension(initial: Int) {
    private var native = initial
    private var applied: Int? = null

    fun apply(current: Int, percent: Int, minimum: Int = 0): Int {
        if (applied == null || current != applied) native = current
        val scaled = SettingsAppearanceRules.scaled(native, percent)
        return (if (scaled < 0) scaled else scaled.coerceAtLeast(minimum)).also { applied = it }
    }

    fun restore(current: Int): Int {
        if (applied == null || current != applied) native = current
        applied = null
        return native
    }
}

internal class SettingsTitleModeState(var nativeMode: Int, var nativeExpanded: Boolean) {
    var overriding = false
}
