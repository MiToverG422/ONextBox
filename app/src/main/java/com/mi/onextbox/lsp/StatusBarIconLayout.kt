package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.SystemUiAppearance as Option
import com.mi.onextbox.lsp.StatusBarLayoutSettings as Legacy
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import kotlin.math.roundToInt

internal object StatusBarIconLayout {
    val sliders = listOf(Option.LeftX, Option.LeftY, Option.LeftScale, Option.RightX, Option.RightY, Option.RightScale)
    val options = listOf(Option.Sides) + sliders
    const val NATIVE_AXES = "statusbar_icon_native_inset_axes"
    val keys = options.mapTo(mutableSetOf()) { it.key } +
        setOf(NATIVE_AXES, Legacy.MARGINS_ENABLED, Legacy.VERTICAL_MARGINS_ENABLED, Legacy.LEFT, Legacy.RIGHT, Legacy.TOP, Legacy.BOTTOM)
    private const val X = 1
    private const val Y = 2

    data class State(val enabled: Boolean, val nativeAxes: Int, val values: Map<Option, Int>) {
        val nativeX get() = enabled && nativeAxes and X != 0
        val nativeY get() = enabled && nativeAxes and Y != 0
        fun value(option: Option) = if (option == Option.Sides) (if (enabled) 1 else 0) else values.getValue(option)

        fun edit(option: Option, value: Int): State {
            if (option == Option.Sides) return copy(enabled = value != 0)
            val axis = when (option) {
                Option.LeftX, Option.RightX -> X
                Option.LeftY, Option.RightY -> Y
                else -> 0
            }
            return copy(nativeAxes = nativeAxes and axis.inv(), values = values + (option to value.coerceIn(option.range)))
        }
    }

    fun resolve(contains: (String) -> Boolean, boolean: (String, Boolean) -> Boolean, int: (String, Int) -> Int): State {
        val enabled = boolean(Option.Sides.key, false)
        val savedAxes = int(NATIVE_AXES, -1)
        val axes = when {
            savedAxes >= 0 -> savedAxes and (X or Y)
            enabled -> 0
            else -> (if (boolean(Legacy.MARGINS_ENABLED, false)) X else 0) or
                (if (boolean(Legacy.VERTICAL_MARGINS_ENABLED, false)) Y else 0)
        }
        val inherited = savedAxes < 0 && axes != 0
        fun old(key: String) = StatusBarLayoutRules.offset(int(key, 0))
        val dormant = savedAxes < 0 && !inherited && !contains(Option.Sides.key)
        val inheritedY = if (dormant || inherited && axes and Y != 0) ((old(Legacy.TOP) - old(Legacy.BOTTOM)) / 2f).roundToInt() else 0
        val values = sliders.associateWith { option ->
            val legacyValue = when (option) {
                Option.LeftX -> if (dormant || axes and X != 0) old(Legacy.LEFT) else 0
                Option.RightX -> if (dormant || axes and X != 0) -old(Legacy.RIGHT) else 0
                Option.LeftY, Option.RightY -> inheritedY
                else -> option.default
            }
            if (inherited) legacyValue else int(option.key, if (dormant) legacyValue else option.default).coerceIn(option.range)
        }
        return State(enabled || inherited, axes, values)
    }

    fun read(context: Context) = LspPreferenceStore.prefs(context).let { resolve(it::contains, it::getBoolean, it::getInt) }

    fun save(context: Context, option: Option, value: Int) {
        val prefs = LspPreferenceStore.prefs(context)
        val state = resolve(prefs::contains, prefs::getBoolean, prefs::getInt).edit(option, value)
        val editor = prefs.edit().putBoolean(Option.Sides.key, state.enabled).putInt(NATIVE_AXES, state.nativeAxes)
        state.values.forEach { (key, amount) -> editor.putInt(key.key, amount) }
        check(editor.commit())
        LspConfig.syncReadableState(context)
    }

    @Volatile private var cached: Pair<Any?, State>? = null
    fun current(): State {
        val generation = HookConfigSnapshot.identity
        cached?.let { if (it.first === generation) return it.second }
        return resolve(HookConfigSnapshot::contains, { key, fallback -> HookConfigSnapshot.boolean(key, fallback) ?: fallback },
            { key, fallback -> HookConfigSnapshot.int(key, fallback) ?: fallback }).also { cached = generation to it }
    }
}
