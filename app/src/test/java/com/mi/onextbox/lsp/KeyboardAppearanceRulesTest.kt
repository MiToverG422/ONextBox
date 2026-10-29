package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class KeyboardAppearanceRulesTest {
    @Test fun emojiPreviewAndAlbumTilesUseContainersWithSeparateForegrounds() {
        listOf("colorEmojiPop", "colorEmojiSearchDragBar", "colorAiEmoticonItem", "colorAiEmoticonCreateItem", "colorAiEmoticonCreateAlbumItemNormal").forEach {
            assertEquals("surface_container_high", KeyboardAppearanceRules.monetRole(it))
        }
        assertEquals("primary_container", KeyboardAppearanceRules.monetRole("colorAiEmoticonCreateAlbumItemSelected"))
        assertEquals("on_surface", KeyboardAppearanceRules.monetRole("colorAiEmoticonCreateAlbumItemTitle"))
        assertEquals("on_primary_container", KeyboardAppearanceRules.monetRole("colorAiEmoticonCreateGenerateButtonTextNormal"))
    }
    @Test fun selectedSegmentAndTipBackgroundsAreNotMisclassifiedAsText() {
        listOf("colorClipSegmentSelected", "colorCandidateSegmentSelectedOut", "colorToolbarConfigSegmentSelectedBG", "colorKaomojiCategorySelected").forEach {
            assertEquals("primary_container", KeyboardAppearanceRules.monetRole(it))
        }
        listOf("colorCandidateSegmentBgOut", "colorClipSegmentContainer", "colorToolbarConfigSegmentContainerBG", "colorFloatDragBar").forEach {
            assertEquals("surface_container_high", KeyboardAppearanceRules.monetRole(it))
        }
        assertEquals("on_primary_container", KeyboardAppearanceRules.monetRole("colorCandidateSegmentTextSelectedOut"))
        assertEquals("secondary_container", KeyboardAppearanceRules.monetRole("bgContainer"))
        assertFalse(KeyboardAppearanceRules.rectangularKeyboardBackground("bgContainer"))
    }
    @Test fun materialOutlineUsesTheSamePillGeometryAsTheMonetEnterKey() {
        assertEquals(1f, KeyboardAppearanceRules.keyCornerRatio(true, true, null), 0f)
        assertEquals(.45f, KeyboardAppearanceRules.keyCornerRatio(false, true, null), 0f)
        assertEquals(.2f, KeyboardAppearanceRules.keyCornerRatio(true, true, 20), 0f)
    }
    @Test fun voiceWaveAndItsMaterialVariantUseMatchingContainerColors() {
        listOf("colorWaveView", "bgVoiceWhite", "bgColorVoiceBottomBlur", "colorBottomDefaultBg", "colorVoiceHoverBG").forEach {
            assertEquals("primary_container", KeyboardAppearanceRules.monetRole(it))
        }
        assertEquals("on_primary_container", KeyboardAppearanceRules.monetRole("colorVoiceBarMicTint"))
        assertEquals("on_surface", KeyboardAppearanceRules.monetRole("colorTips"))
    }
    @Test fun popupBackgroundNeverUsesTheForegroundTextColor() {
        assertEquals("surface_container_high", KeyboardAppearanceRules.monetRole("colorKeyPopBackground"))
        assertEquals("on_surface", KeyboardAppearanceRules.monetRole("colorKeyPopTextNormal"))
        assertEquals("on_primary_container", KeyboardAppearanceRules.monetRole("colorKeyPopTextSelected"))
        assertEquals("outline_variant", KeyboardAppearanceRules.monetRole("colorKeyPopStroke"))
        assertNull(KeyboardAppearanceRules.monetRole("colorKeyPopShadow"))
    }
    @Test fun coversToolbarToolboxAndLongPressSurfaces() {
        assertEquals("secondary_container", KeyboardAppearanceRules.monetRole("iconHeaderToolItemNormalBlur"))
        assertEquals("primary_container", KeyboardAppearanceRules.monetRole("bgToolboxItemSelected"))
        assertEquals("secondary_container", KeyboardAppearanceRules.monetRole("colorCouiContainerTheme"))
        assertEquals("surface_container_high", KeyboardAppearanceRules.monetRole("bgKeyPopPackLong"))
    }
    @Test fun continuousKeyboardBackgroundDoesNotIntroduceRoundedBottomGaps() {
        assertTrue(KeyboardAppearanceRules.rectangularKeyboardBackground("bgKeyboardMain"))
        assertTrue(KeyboardAppearanceRules.rectangularKeyboardBackground("bgKeyboardInner"))
        assertFalse(KeyboardAppearanceRules.rectangularKeyboardBackground("bgKeyPopPackLong"))
        assertFalse(KeyboardAppearanceRules.rectangularKeyboardBackground("bgToolboxItemSelected"))
    }
    private class CanvasStub
    private class KeyStub
    private class RendererStub {
        fun main(canvas: CanvasStub, key: KeyStub) {}
        fun compound(canvas: CanvasStub, key: KeyStub) {}
        fun background(key: KeyStub, pressed: Boolean) = Any()
        fun other(canvas: CanvasStub, key: String) {}
    }
    @Test fun hooksBothNativeDrawingEntriesWithoutTreatingTheSharedSignatureAsAnError() {
        assertEquals(setOf("main", "compound"), KeyboardAppearanceRules.drawEntries(RendererStub::class.java, CanvasStub::class.java, KeyStub::class.java).map { it.name }.toSet())
    }
    @Test fun secondaryLabelVisibilityIsIndependentForT9AndQwerty() {
        assertTrue(KeyboardAppearanceRules.hideMark("T9_PINYIN", true, false))
        assertFalse(KeyboardAppearanceRules.hideMark("QWERTY_EN", true, false))
        assertTrue(KeyboardAppearanceRules.hideMark("QWERTY_PINYIN", false, true))
        assertFalse(KeyboardAppearanceRules.hideMark("T9_NUMBER", false, true))
    }
    @Test fun doesNotTreatHandwritingOrUnknownLayoutsAsQwerty() {
        listOf("HANDWRITING", "STROKE", "", "FUTURE_LAYOUT").forEach {
            assertFalse(KeyboardAppearanceRules.hideMark(it, true, true))
        }
    }
    @Test fun doublePinyinUsesTheFullKeyboardOption() {
        assertTrue(KeyboardAppearanceRules.hideMark("DOUBLE_PY_MSPY", false, true))
        assertFalse(KeyboardAppearanceRules.hideMark("DOUBLE_PY_MSPY", true, false))
    }
}
