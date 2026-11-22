package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.R
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object QsTileCornerRules {
    const val SYSTEM_DEFAULT = -1
    const val MAX_PERCENT = 100
    fun normalize(value: Int) = value.coerceIn(SYSTEM_DEFAULT, MAX_PERCENT)
    fun customPercent(value: Int, enabled: Boolean = true): Int? =
        normalize(value).takeIf { enabled && it != SYSTEM_DEFAULT }
    /** 100% is half the background's short side; -1 leaves the native provider untouched. */
    fun pixels(value: Int, width: Int, height: Int): Float? {
        val percent = customPercent(value) ?: return null
        if (width <= 0 || height <= 0) return null
        return minOf(width, height) / 2f * (percent / MAX_PERCENT.toFloat())
    }
}

internal object QsTileCornerSettings {
    const val ENABLED = "systemui_qs_tile_corner_enabled"
    sealed interface Target { val key: String }
    enum class Size(val columns: Int, val rows: Int) : Target {
        OneXOne(1, 1), OneXTwo(1, 2), TwoXOne(2, 1),
        TwoXTwo(2, 2), FourXOne(4, 1), FourXTwo(4, 2);
        // Separate percentage keys so previously saved dp values are never reinterpreted.
        override val key = "systemui_qs_tile_corner_radius_percent_${columns}x$rows"
        val label = "${columns}x$rows"
        companion object {
            fun fromSpan(columns: Int, rows: Int): Size? =
                entries.firstOrNull { it.columns == columns && it.rows == rows }
        }
    }
    enum class DeviceCard(id: String, val viewName: String, val innerId: String, val size: Size, val title: Int) : Target {
        SquareDevice("square_device", "SquareDeviceCardView", "square_device_constraintlayout", Size.OneXOne, R.string.qs_corner_device_tile_radius),
        RectangleDevice("rectangle_device", "RectangleDeviceCardView", "rectangleCoLayout", Size.TwoXOne, R.string.qs_corner_device_tile_radius),
        SquareEntrance("square_entrance", "SquareEntranceCardView", "square_entrance_constraintLayout", Size.OneXOne, R.string.qs_corner_entrance_tile_radius),
        RectangleEntrance("rectangle_entrance", "RectangleEntranceCardView", "rectangle_entrance_constraintLayout", Size.TwoXOne, R.string.qs_corner_entrance_tile_radius),
        EmptyEntrance("empty_entrance", "NoDeviceEntranceCardView", "no_device_constraintLayout", Size.FourXOne, R.string.qs_corner_empty_tile_radius);

        override val key = "systemui_qs_device_corner_percent_$id"
    }
    private val targets: List<Target> = Size.entries + DeviceCard.entries
    val keys = setOf(ENABLED) + targets.map { it.key }
    data class State(
        val enabled: Boolean = false,
        val radii: Map<Target, Int> = emptyMap(),
    ) {
        fun radius(size: Target): Int = radii[size] ?: QsTileCornerRules.SYSTEM_DEFAULT
        fun withRadius(size: Target, value: Int) = copy(radii = radii + (size to QsTileCornerRules.normalize(value)))
    }
    fun state(context: Context): State = LspPreferenceStore.prefs(context).let {
        State(it.getBoolean(ENABLED, false), targets.associateWith { size ->
            QsTileCornerRules.normalize(it.getInt(size.key, QsTileCornerRules.SYSTEM_DEFAULT))
        })
    }
    fun enabledXposed() = HookConfigSnapshot.boolean(ENABLED, false) == true
    fun radiusXposed(size: Target): Int? = if (enabledXposed())
        QsTileCornerRules.customPercent(HookConfigSnapshot.int(size.key, QsTileCornerRules.SYSTEM_DEFAULT)
            ?: QsTileCornerRules.SYSTEM_DEFAULT) else null
    // Observer token changes when any independently stored radius changes.
    fun configurationTokenXposed(): Int? = if (enabledXposed())
        Size.entries.fold(1) { token, size -> 31 * token + (radiusXposed(size) ?: QsTileCornerRules.SYSTEM_DEFAULT) } else null
    fun setEnabled(context: Context, enabled: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(ENABLED, enabled).commit())
        LspConfig.syncReadableState(context)
    }
    fun setRadius(context: Context, size: Target, radius: Int) {
        check(LspPreferenceStore.prefs(context).edit().putInt(size.key, QsTileCornerRules.normalize(radius)).commit())
        LspConfig.syncReadableState(context)
    }
}
