package com.mi.onextbox.lsp

internal object KeyboardAppearanceRules {
    fun monetRole(name: String): String? = when {
        name.contains("Shadow", true) -> null
        name in setOf("colorWaveView", "bgVoiceWhite", "bgColorVoiceBottomBlur", "colorBottomDefaultBg", "colorVoiceHoverBG", "colorKaomojiCategorySelected") -> "primary_container"
        name == "colorVoiceBarMicTint" -> "on_primary_container"
        name in setOf("colorAiEmoticonCreateAlbumItemSelected", "colorAiEmoticonCreateGenerateButton") -> "primary_container"
        name.startsWith("colorAiEmoticonCreateGenerateButtonText") -> "on_primary_container"
        name.contains("Segment") && !name.contains("Text") && !name.contains("Title") ->
            if (name.contains("Selected") || name.contains("Indicator")) "primary_container" else "surface_container_high"
        name in setOf("colorFloatDragBar", "colorHandwriteKeyPressed", "colorHoverBgReset", "colorClipItemFuncSelector",
            "colorEmojiPop", "colorEmojiSearchDragBar", "colorAiEmoticonItem", "colorAiEmoticonCreateItem",
            "colorAiEmoticonCreateAlbumItemNormal", "colorAiEmoticonCreateGenerateButtonDisable") -> "surface_container_high"
        name == "bgContainer" -> "secondary_container"
        name == "colorKeyPopStroke" || name.contains("Divider") || name.endsWith("Line") || name.endsWith("SplitBar") -> "outline_variant"
        name == "colorKeyPopBackground" -> "surface_container_high"
        name.startsWith("bgKeyPopTextSelected") || name == "colorSegmentIndicator" || name.endsWith("SelectedBG") -> "primary_container"
        name.startsWith("bgKeyPop") -> "surface_container_high"
        name == "colorKeyPopTextSelected" -> "on_primary_container"
        name == "colorCouiContainerTheme" || name.startsWith("iconHeaderToolItem") || name.startsWith("bgHeaderIcon") ->
            if (name.contains("Pressed")) "primary_container" else "secondary_container"
        name == "colorCouiLabelTheme" || name == "colorCouiLabelPrimaryTheme" -> "primary"
        name.startsWith("bg") -> when {
            name.contains("Pressed") || name.contains("Selected") || name.contains("Focused") || name.contains("Confirm") -> "primary_container"
            name.contains("Item") || name.contains("Button") || name.contains("Search") || name.contains("Input") -> "surface_container_high"
            else -> "surface_container_low"
        }
        name == "colorNavigationBar" || name == "colorInputTopBg" || name.endsWith("DefaultBg") -> "surface_container_low"
        (name.contains("Bg", true) || name.contains("Background")) && !name.contains("Text") -> "surface_container_high"
        name.endsWith("Container") || name.endsWith("Card") || name == "colorAiEmoticonCreateInput" -> "surface_container_high"
        name.contains("Track") -> if (name.contains("UnChecked")) "surface_container_highest" else "primary"
        name.startsWith("colorEnter") -> "on_primary_container"
        name.startsWith("color") && name.contains("TextSelected") -> "on_primary_container"
        name.startsWith("colorFuncKey") -> "on_secondary_container"
        name.startsWith("color") || name.startsWith("icon") -> "on_surface"
        else -> null
    }

    fun rectangularKeyboardBackground(name: String) = name in setOf(
        "bgKeyboard", "bgKeyboardMain", "bgKeyboardInner", "bgKeyboardSecondary", "bgKeyboardHeader", "bgKeyboardGrooves", "bgBottom",
    )

    fun keyCornerRatio(enter: Boolean, monet: Boolean, custom: Int?): Float =
        custom?.coerceIn(0, 100)?.div(100f) ?: if (monet) { if (enter) 1f else .45f } else .3f

    fun drawEntries(renderer: Class<*>, canvas: Class<*>, key: Class<*>) = renderer.declaredMethods.filter {
        it.parameterTypes.contentEquals(arrayOf(canvas, key)) && it.returnType == Void.TYPE
    }.also { require(it.isNotEmpty()) { "No native key draw entry" } }

    fun hideMark(type: String, t9: Boolean, qwerty: Boolean): Boolean = when {
        type.startsWith("T9") -> t9
        type.startsWith("QWERTY") || type.startsWith("DOUBLE_PY") -> qwerty
        else -> false
    }
}
