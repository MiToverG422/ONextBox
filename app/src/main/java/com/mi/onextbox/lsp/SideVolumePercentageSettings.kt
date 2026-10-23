package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object SideVolumePercentageVisibility {
    // Check current width as well: native flags can change at different animation callbacks.
    fun shouldDraw(enabled: Boolean, thickType: Int, hasEnlarge: Boolean, widthRatio: Float = 1f) =
        enabled && thickType == 0 && !hasEnlarge && (!widthRatio.isFinite() || widthRatio >= 0.99f)
}

/** Independent values for the volume-key popup; shares percentage attribute rules with QS. */
internal object SideVolumePercentageSettings {
    const val ENABLED = "systemui_side_volume_percentage_enabled"
    const val SIZE = "systemui_side_volume_percentage_size_sp"
    const val COLOR = "systemui_side_volume_percentage_color"
    const val OFFSET = "systemui_side_volume_percentage_offset_percent"
    val keys = setOf(ENABLED, SIZE, COLOR, OFFSET)

    fun state(context: Context): QsSliderPercentageSettings.State = LspPreferenceStore.prefs(context).let {
        QsSliderPercentageSettings.State(it.getBoolean(ENABLED, false),
            QsSliderPercentageRules.size(it.getInt(SIZE, QsSliderPercentageRules.DEFAULT_SIZE_SP)),
            if (it.contains(COLOR)) it.getLong(COLOR, 0) else null,
            QsSliderPercentageRules.offset(it.getInt(OFFSET, QsSliderPercentageRules.DEFAULT_OFFSET)))
    }

    fun stateXposed() = QsSliderPercentageSettings.State(
        HookConfigSnapshot.boolean(ENABLED, false) == true,
        QsSliderPercentageRules.size(HookConfigSnapshot.int(SIZE, QsSliderPercentageRules.DEFAULT_SIZE_SP)
            ?: QsSliderPercentageRules.DEFAULT_SIZE_SP),
        HookConfigSnapshot.long(COLOR),
        QsSliderPercentageRules.offset(HookConfigSnapshot.int(OFFSET, QsSliderPercentageRules.DEFAULT_OFFSET)
            ?: QsSliderPercentageRules.DEFAULT_OFFSET))

    fun configurationTokenXposed(): Int? = stateXposed().takeIf { it.enabled }?.hashCode()
    fun setEnabled(context: Context, enabled: Boolean) =
        save(context, ENABLED, QsSliderPercentageSettings.State(enabled = enabled))

    fun save(context: Context, key: String, state: QsSliderPercentageSettings.State) {
        val edit = LspPreferenceStore.prefs(context).edit()
        when (key) {
            ENABLED -> edit.putBoolean(key, state.enabled)
            SIZE -> edit.putInt(key, QsSliderPercentageRules.size(state.sizeSp))
            COLOR -> if (state.color == null) edit.remove(key) else edit.putLong(key, state.color)
            OFFSET -> edit.putInt(key, QsSliderPercentageRules.offset(state.offset))
            else -> error("Unknown side volume percentage preference")
        }
        check(edit.commit())
        LspConfig.syncReadableState(context)
    }
}
