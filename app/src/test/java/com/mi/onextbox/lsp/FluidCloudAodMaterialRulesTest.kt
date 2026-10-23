package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class FluidCloudAodMaterialRulesTest {
    private val rules = FluidCloudAodMaterialRules

    @Test fun progressClampsNativeAnimationWithoutAcceptingInvalidValues() {
        assertEquals(0f, rules.progress(null), 0f)
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f)) {
            assertEquals(0f, rules.progress(value), 0f)
        }
        assertEquals(0.4f, rules.progress(0.4f), 0f)
        assertEquals(1f, rules.progress(2f), 0f)
    }

    @Test fun nativeMasksAreHiddenOnlyForAnActiveUnifiedCustomCardRenderer() {
        for (enabled in listOf(false, true)) for (custom in listOf(false, true)) {
            for (renderer in listOf(false, true)) for (fraction in listOf(0f, .5f, 1f)) {
                assertEquals(enabled && custom && renderer && fraction > 0f,
                    rules.use(enabled, custom, fraction, renderer))
            }
        }
        assertFalse(rules.use(true, true, Float.NaN, true))
    }

    @Test fun wallpaperAndUnknownMixesKeepTheirRendererWithoutShaderWrites() {
        for (name in listOf("BlurMixSingle", "BlurMixMulti", "BlurMixMultiWithShader")) {
            assertTrue(rules.supportsShaderMix(name))
        }
        for (name in listOf(null, "None", "OverlayColor", "BlurMixSingleWithShader", "UNKNOWN")) {
            assertFalse(rules.supportsShaderMix(name))
            assertTrue(rules.use(true, true, 1f, true))
        }
    }

    @Test fun awakeFramesDoNotOverwriteNativeReferenceEffectAlpha() {
        assertFalse(rules.needsEffectUpdate(true, null, 0f))
        assertFalse(rules.needsEffectUpdate(true, 0f, 0f))
        assertFalse(rules.needsEffectUpdate(false, 0f, 0f))
    }

    @Test fun effectsFadeOnEntryAndRestoreOnExit() {
        assertTrue(rules.needsEffectUpdate(false, 0f, 0.5f))
        assertTrue(rules.needsEffectUpdate(false, 0.5f, 1f))
        assertTrue(rules.needsEffectUpdate(false, 1f, 0.5f))
        assertTrue(rules.needsEffectUpdate(false, 0.5f, 0f))
        assertFalse(rules.needsEffectUpdate(false, 1f, 1f))
    }

    @Test fun newlyAddedAodCardAndStyleChangeApplyEffectsImmediately() {
        assertTrue(rules.needsEffectUpdate(false, null, 1f))
        assertTrue(rules.needsEffectUpdate(true, 1f, 1f))
    }

    @Test fun nativeMaterialTintRemainsVisibleThroughoutUnifiedAodTransition() {
        for (fraction in listOf(0f, .01f, .5f, 1f)) {
            assertTrue(rules.drawMaterialTint(fraction, true))
        }
        for (fraction in listOf(.01f, .5f, 1f)) {
            assertFalse(rules.drawMaterialTint(fraction, false))
        }
        assertTrue(rules.drawMaterialTint(0f, false))
    }

    @Test fun normalColorScopeIsIdentityBoundAndRestoresAcrossNestedUpdates() {
        val scope = FluidCloudAodColorScope()
        val normal = Any()
        val other = Any()
        assertFalse(scope.preservesBackground())
        scope.duringUpdate(normal) {
            assertTrue(scope.preservesColor(normal))
            assertFalse(scope.preservesColor(other))
            assertTrue(scope.preservesBackground())
            scope.duringUpdate(null) {
                assertFalse(scope.preservesColor(normal))
                assertFalse(scope.preservesBackground())
            }
            assertTrue(scope.preservesColor(normal))
            scope.duringUpdate(other) {
                assertFalse(scope.preservesColor(normal))
                assertTrue(scope.preservesColor(other))
            }
            assertTrue(scope.preservesColor(normal))
        }
        assertFalse(scope.preservesBackground())
    }

    @Test fun normalColorScopeDoesNotLeakAfterFailureOrAcrossThreads() {
        val scope = FluidCloudAodColorScope()
        val normal = Any()
        scope.duringUpdate(normal) {
            var visibleOnOtherThread = true
            val thread = Thread { visibleOnOtherThread = scope.preservesBackground() }
            thread.start()
            thread.join()
            assertFalse(visibleOnOtherThread)
        }
        runCatching { scope.duringUpdate(normal) { error("native update failure") } }
        assertFalse(scope.preservesColor(normal))
        assertFalse(scope.preservesBackground())
    }
}
