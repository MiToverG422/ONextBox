package com.mi.onextbox.lsp

import android.view.View
import com.mi.onextbox.lsp.QsCornerReflect as Reflect

/** Prefer grid metadata; only use concrete component names for hosts without a span API. */
internal object QsCornerSizeResolver {
    fun resolve(view: View): QsTileCornerSettings.Size? {
        for (getter in listOf("getTileViewSize", "getSpanSize")) {
            val span = runCatching { Reflect.callMethod(view, getter) }.getOrNull() ?: continue
            val columns = runCatching { (Reflect.callMethod(span, "getColSpan") as Number).toInt() }.getOrNull()
            val rows = runCatching { (Reflect.callMethod(span, "getRowSpan") as Number).toInt() }.getOrNull()
            if (columns != null && rows != null) return QsTileCornerSettings.Size.fromSpan(columns, rows)
        }
        return fromClassNames(generateSequence(view.javaClass as Class<*>?) { it.superclass }.map { it.name }.toList())
    }

    internal fun fromClassNames(names: List<String>): QsTileCornerSettings.Size? {
        val size = QsTileCornerSettings.Size.entries.firstOrNull { candidate ->
            names.any { name ->
                name == "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView${candidate.name}" ||
                    name == "com.oplus.systemui.qs.scenario.ui.template.ScenarioTileView${candidate.name}"
            }
        }
        if (size != null) return size
        return when {
            names.any { QsControlCenterCornerTargets.isMainSlider(it) } -> QsTileCornerSettings.Size.OneXTwo
            "com.oplus.systemui.qs.media.SepQsLargeMediaPanelView" in names -> QsTileCornerSettings.Size.FourXTwo
            "com.oplus.systemui.qs.media.OplusQsNormalMediaPanelView" in names -> QsTileCornerSettings.Size.TwoXTwo
            "com.oplus.systemui.qs.base.tile.OplusQSHighlightTileView" in names -> QsTileCornerSettings.Size.TwoXOne
            "com.oplus.systemui.qs.base.tile.OplusQSTileBaseView" in names -> QsTileCornerSettings.Size.OneXOne
            else -> null
        }
    }
}
