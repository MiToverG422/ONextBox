package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import kotlin.math.roundToInt

internal object QsSliderPercentageRules {
    // ColorOS 17.99.02: 2x1 tile subtitle -> qs_two_x_one_second_label_desc_text_size (11dp).
    // The default draw reads that native resource; explicit user choices remain in sp.
    const val DEFAULT_SIZE_SP = 11
    const val MIN_SIZE_SP = 5
    const val MAX_SIZE_SP = DEFAULT_SIZE_SP * 2
    const val DEFAULT_OFFSET = 10

    fun size(value: Int) = value.coerceIn(MIN_SIZE_SP, MAX_SIZE_SP)
    fun offset(value: Int) = value.coerceIn(0, 100)

    /** Cancel only the detail panel's bounds transform, keeping the requested screen-space sp. */
    fun textTransform(scaleX: Float, scaleY: Float): Pair<Float, Float> {
        if (!scaleX.isFinite() || !scaleY.isFinite() || scaleX <= 0f || scaleY <= 0f) return 1f to 1f
        return (1f / scaleY) to (scaleY / scaleX)
    }

    fun percentage(progress: Int, min: Int, max: Int): Int? {
        val range = max.toLong() - min.toLong()
        if (range <= 0) return null
        return (((progress.toLong() - min.toLong()).toDouble() / range).coerceIn(0.0, 1.0) * 100).roundToInt()
    }

    /** Offset moves the text from top to bottom, keeping its font bounds inside the track. */
    fun baseline(top: Float, bottom: Float, ascent: Float, descent: Float, offset: Int): Float {
        val first = top - ascent
        val last = bottom - descent
        return if (last < first) (top + bottom - ascent - descent) / 2f
        else first + (last - first) * offset(offset) / 100f
    }
}

internal object QsSliderPercentageSettings {
    const val ENABLED = "systemui_qs_slider_percentage_enabled"
    const val SIZE = "systemui_qs_slider_percentage_size_sp"
    const val COLOR = "systemui_qs_slider_percentage_color"
    const val OFFSET = "systemui_qs_slider_percentage_offset_percent"
    val keys = setOf(ENABLED, SIZE, COLOR, OFFSET)

    data class State(
        val enabled: Boolean = false,
        val sizeSp: Int = QsSliderPercentageRules.DEFAULT_SIZE_SP,
        val color: Long? = null,
        val offset: Int = QsSliderPercentageRules.DEFAULT_OFFSET,
    )

    fun state(context: Context): State = LspPreferenceStore.prefs(context).let {
        State(it.getBoolean(ENABLED, false),
            QsSliderPercentageRules.size(it.getInt(SIZE, QsSliderPercentageRules.DEFAULT_SIZE_SP)),
            if (it.contains(COLOR)) it.getLong(COLOR, 0) else null,
            QsSliderPercentageRules.offset(it.getInt(OFFSET, QsSliderPercentageRules.DEFAULT_OFFSET)))
    }

    fun stateXposed() = State(
        HookConfigSnapshot.boolean(ENABLED, false) == true,
        QsSliderPercentageRules.size(HookConfigSnapshot.int(SIZE, QsSliderPercentageRules.DEFAULT_SIZE_SP)
            ?: QsSliderPercentageRules.DEFAULT_SIZE_SP),
        HookConfigSnapshot.long(COLOR),
        QsSliderPercentageRules.offset(HookConfigSnapshot.int(OFFSET, QsSliderPercentageRules.DEFAULT_OFFSET)
            ?: QsSliderPercentageRules.DEFAULT_OFFSET))

    fun configurationTokenXposed(): Int? = stateXposed().takeIf { it.enabled }?.hashCode()

    fun setEnabled(context: Context, enabled: Boolean) = save(context, ENABLED, State(enabled = enabled))

    fun save(context: Context, key: String, state: State) {
        val edit = LspPreferenceStore.prefs(context).edit()
        when (key) {
            ENABLED -> edit.putBoolean(key, state.enabled)
            SIZE -> edit.putInt(key, QsSliderPercentageRules.size(state.sizeSp))
            COLOR -> if (state.color == null) edit.remove(key) else edit.putLong(key, state.color)
            OFFSET -> edit.putInt(key, QsSliderPercentageRules.offset(state.offset))
            else -> error("Unknown slider percentage preference")
        }
        check(edit.commit())
        LspConfig.syncReadableState(context)
    }
}
