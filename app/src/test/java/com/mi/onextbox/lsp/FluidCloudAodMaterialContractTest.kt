package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Reflection contracts cannot be executed with the host JVM's Android stubs. */
class FluidCloudAodMaterialContractTest {
    @Test fun customAodCallbackPreservesNativeContentsThenSynchronizesMaterial() {
        val source = source("FluidCloudMaterialHooker.kt")
            .substringAfter("\"\$TAG:panel:aodProgress\"").substringBefore("changer.declaredMethods")
        assertTrue(source.indexOf("chain.proceed()") < source.indexOf("material.updatePanelAod(view, fraction)"))
        assertTrue(source.contains("chain.getArg(1)"))
        assertFalse(source.contains("changeAodViewColor"))
    }

    @Test fun aodPreservesLiveShaderRecipeWithoutDarkTargetsOrFallbackLayers() {
        val source = source("FluidCloudAodMaterial.kt")
        assertTrue(source.contains("\"getCurrentBlurMixConfig\""))
        assertTrue(source.contains("\"changeBlurColor\", mix, false"))
        for (removed in listOf("createBgAodColor", "aod_notification_card_", "notification_panoramic_aod_bg",
            "setPanoramicAodDrawable", "ColorDrawable", "Color.parseColor", "0xff")) {
            assertFalse(removed, source.contains(removed))
        }
    }

    @Test fun newMixIsAppliedBeforeAodPreservationReadsIt() {
        val source = source("FluidCloudNotificationMaterial.kt")
            .substringAfter("fun preparePanel(").substringBefore("fun updatePanelAod(")
        assertTrue(source.indexOf("\"applyBlurConfig\"") < source.indexOf("aod.prepare("))
        assertTrue(source.contains("if (fraction == 0f) native.ensureNotificationMetaBall"))
        assertTrue(source.contains("native.applyAod(config, key.mode, fraction)"))
    }

    @Test fun nativeMaskSuppressionIsScopedAndAddsNoDarkFinalDraw() {
        val hook = source("FluidCloudMaterialHooker.kt")
        assertTrue(hook.contains("aodScope.hidesMask(chain.thisObject)"))
        assertTrue(hook.contains("aodScope.duringDraw("))
        assertTrue(hook.contains("material.hasPanelAodMaterial"))
        assertTrue(hook.contains("material.drawPanelColor("))
        assertFalse(hook.contains("material.drawPanelAod("))
    }

    @Test fun unavailableRendererReleasesStateAndKeepsNativeFallback() {
        val source = source("FluidCloudAodMaterial.kt")
        assertTrue(source.contains("fraction, rendererActive"))
        assertTrue(source.indexOf("release(view)") < source.indexOf("states[view] = state"))
        val material = source("FluidCloudNotificationMaterial.kt")
        assertTrue(material.contains("rendererActive = false"))
    }

    @Test fun exitDisableAndReloadRestoreOwnedState() {
        val source = source("FluidCloudAodMaterial.kt")
        assertTrue(source.contains("\"setAodState\", false"))
        assertTrue(source.contains("\"changeBlurColor\", origin, true"))
        assertTrue(source.indexOf("states[view] = state") < source.indexOf("\"setAodState\", true"))
        val material = source("FluidCloudNotificationMaterial.kt")
        assertTrue(material.contains("aod.capture()"))
        assertTrue(material.contains("aod.releaseAll()"))
        assertTrue(material.contains("panels.keys.toList().forEach(::releasePanelAod)"))
        assertTrue(material.contains("api().applyAod(config, mode, 0f)"))
        assertTrue(material.contains("HotReloadLifecycle.isRetiring || !LspConfig.isFluidCloudMaterialEnabledXposed()"))
    }

    @Test fun noGlobalDozePolicyOrRealNotificationTypeIsRewritten() {
        for (file in listOf("FluidCloudAodMaterial.kt", "FluidCloudMaterialHooker.kt", "FluidCloudNotificationMaterial.kt")) {
            // Expanded views own a separate proxy; this contract concerns real native rows.
            val source = source(file).substringBefore("private inner class MaterialDrawable")
            for (method in listOf("\"setCardType\"", "\"setDozing\"", "\"setPanoramicAodProgress\"", "\"setForeground\"")) {
                assertFalse("$file must not call $method", source.contains(method))
            }
        }
    }

    @Test fun normalAodKeepsNativeProgressAndCachesWhileOnlyReplacingBackgroundInputs() {
        val hook = source("FluidCloudMaterialHooker.kt")
            .substringAfter("private fun installNormalNotificationAod(")
            .substringBefore("private fun installPanelMaterial(")
        assertTrue(hook.contains("if (enabled() && getStart.invoke(nativeColor) !== empty) nativeColor else null"))
        assertTrue(hook.contains("scope.duringUpdate(activeColor) { chain.proceed() }"))
        assertTrue(hook.contains("scope.preservesColor(chain.thisObject)"))
        assertTrue(hook.contains("getStart.invoke(chain.thisObject)"))
        assertTrue(hook.contains("if (!scope.preservesBackground()) return@Hooker chain.proceed()"))
        assertTrue(hook.contains("args[1] = 0f"))
        assertTrue(hook.contains("deoptimize(getColor)"))
        assertTrue(hook.contains("deoptimize(changeBackground)"))
        for (removed in listOf("preparePanel", "setAodState", "setAodColor", "changeAodEffectAlpha", "changeAodViewColorInternal")) {
            assertFalse(removed, hook.contains("\"$removed\""))
        }
    }

    private fun source(name: String): String = listOf(
        File("src/main/java/com/mi/onextbox/lsp/$name"),
        File("app/src/main/java/com/mi/onextbox/lsp/$name"),
    ).first { it.isFile }.readText()
}
