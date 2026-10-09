package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class FluidCloudExpandedMaterialRulesTest {
    @Test fun matchesOverlayExpandedColorsExactly() {
        assertEquals(0x2a585858, FluidCloudExpandedMaterialRules.BLEND_COLOR)
        assertEquals(0x20282828, FluidCloudExpandedMaterialRules.MIX_COLOR)
        assertEquals(0x40000000, FluidCloudExpandedMaterialRules.GRADIENT_COLOR)
        assertEquals(0x50282828, FluidCloudExpandedMaterialRules.NO_BLUR_COLOR)
    }

    @Test fun preservesNativeRgbaOrderAndAlpha() {
        assertArrayEquals(floatArrayOf(0x58 / 255f, 0x58 / 255f, 0x58 / 255f, 0x2a / 255f),
            FluidCloudExpandedMaterialRules.rgba(FluidCloudExpandedMaterialRules.BLEND_COLOR), 0f)
        assertArrayEquals(floatArrayOf(0x28 / 255f, 0x28 / 255f, 0x28 / 255f, 0x20 / 255f),
            FluidCloudExpandedMaterialRules.rgba(FluidCloudExpandedMaterialRules.MIX_COLOR), 0f)
    }

    @Test fun producesIndependentArraysForEveryNativeMaterial() {
        val first = FluidCloudExpandedMaterialRules.rgba(FluidCloudExpandedMaterialRules.MIX_COLOR)
        first[3] = 0f
        assertEquals(0x20 / 255f,
            FluidCloudExpandedMaterialRules.rgba(FluidCloudExpandedMaterialRules.MIX_COLOR)[3], 0f)
    }

    @Test fun onlyExpandedBackgroundAndVerifiedInstantContentAreTargets() {
        assertTrue(FluidCloudExpandedMaterialRules.isBackgroundHost(
            "com.oplus.systemui.plugins.seedling.card.ui.view.CardBackgroundView", false))
        assertTrue(FluidCloudExpandedMaterialRules.isBackgroundHost("android.widget.FrameLayout", true))
        for (name in listOf(null, "android.view.View", "com.oplus.systemui.plugins.seedling.capsule.ui.view.CapsuleView",
            "com.oplus.systemui.statusbar.notification.customcard.OplusCustomRow")) {
            assertFalse(FluidCloudExpandedMaterialRules.isBackgroundHost(name, false))
        }
    }

    @Test fun neverChangesOtherBlurModesOrUnexpectedVectorFormats() {
        assertTrue(FluidCloudExpandedMaterialRules.shouldReplaceParams(true, 4, 4, 4))
        assertFalse(FluidCloudExpandedMaterialRules.shouldReplaceParams(false, 4, 4, 4))
        assertFalse(FluidCloudExpandedMaterialRules.shouldReplaceParams(true, 0, 4, 4))
        assertFalse(FluidCloudExpandedMaterialRules.shouldReplaceParams(true, 4, 3, 4))
        assertFalse(FluidCloudExpandedMaterialRules.shouldReplaceParams(true, 4, 4, null))
    }

    @Test fun nestedNonTargetBuildDoesNotInheritOuterMaterial() {
        val scope = FluidCloudExpandedBuildScope()
        val outer = FluidCloudExpandedBuildScope.Style(true)
        assertNull(scope.current)
        scope.duringBuild(outer) {
            assertSame(outer, scope.current)
            scope.duringBuild(null) { assertNull(scope.current) }
            assertSame(outer, scope.current)
        }
        assertNull(scope.current)
    }

    @Test fun legacyNestedBuildRestoresModernBranch() {
        val scope = FluidCloudExpandedBuildScope()
        scope.duringBuild(FluidCloudExpandedBuildScope.Style(true)) {
            scope.duringBuild(FluidCloudExpandedBuildScope.Style(false)) { assertFalse(scope.current!!.modernBlur) }
            assertTrue(scope.current!!.modernBlur)
        }
    }

    @Test fun nativeFailureCannotLeakChangesToAnotherBackground() {
        val scope = FluidCloudExpandedBuildScope()
        runCatching { scope.duringBuild(FluidCloudExpandedBuildScope.Style(true)) { error("native failure") } }
        assertNull(scope.current)
    }

    @Test fun buildScopeCannotCrossThreads() {
        val scope = FluidCloudExpandedBuildScope()
        scope.duringBuild(FluidCloudExpandedBuildScope.Style(true)) {
            var other: FluidCloudExpandedBuildScope.Style? = scope.current
            Thread { other = scope.current }.apply { start(); join() }
            assertNull(other)
            assertTrue(scope.current!!.modernBlur)
        }
    }
}
