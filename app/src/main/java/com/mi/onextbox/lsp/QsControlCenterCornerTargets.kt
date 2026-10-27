package com.mi.onextbox.lsp

internal object QsControlCenterCornerTargets {
    const val BASE_SLIDER = "com.oplus.systemui.qs.base.seek.OplusQsBaseToggleSliderLayout"
    const val SEEK_BAR = "com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar"
    const val DETAIL_SLIDER = "com.oplus.systemui.qs.slider.OplusQsDetailToggleSliderLayout"
    const val DETAIL_ANIMATION = "com.oplus.systemui.qs.slider.OplusQsSliderDetailAnimationManager"
    val mainSliders = setOf(
        "com.oplus.systemui.plugins.qs.widget.OplusQsToggleSliderLayout",
        "com.oplus.systemui.qs.widget.OplusQsToggleSliderLayout",
    )
    val sliders = mainSliders + DETAIL_SLIDER
    // Grid size resolution still only recognizes main tiles.
    fun isMainSlider(className: String) = className in mainSliders
    fun isSlider(className: String) = className in sliders
}
