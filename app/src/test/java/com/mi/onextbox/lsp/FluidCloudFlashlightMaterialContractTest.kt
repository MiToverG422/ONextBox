package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class FluidCloudFlashlightMaterialContractTest {
    private val hooks = File("src/main/java/com/mi/onextbox/lsp/FluidCloudMaterialHooker.kt").readText()
        .substringAfter("private fun installFlashlightMaterial").substringBefore("private fun installPlugin")
    private val adapter = File("src/main/java/com/mi/onextbox/lsp/FluidCloudFlashlightMaterial.kt").readText()

    @Test fun flashlightFactoryKeepsNativeDrawableIdentity() {
        assertTrue(hooks.contains("flashlight.apply(original)"))
        assertFalse(hooks.contains("material.background("))
        assertFalse(hooks.contains("qs_detail_bg_corner_radius"))
        assertTrue(hooks.lineSequence().any { it.trim() == "original" })
    }

    @Test fun materialDoesNotOverrideNativeGeometryOrAnimationAlpha() {
        val code = adapter.lineSequence().map { it.substringBefore("//") }.joinToString("\n")
        for (forbidden in listOf("setCornerRadius", "setPathProvider", "setBlockPathProvider",
            "setAlpha(", "setDrawable(", "view.background =")) assertFalse(forbidden, code.contains(forbidden))
        assertTrue(adapter.contains("mask.setColor(Color.TRANSPARENT)"))
        assertTrue(adapter.contains("Reflect.callMethod(value, \"getThird\")"))
    }

    @Test fun onlyVerifiedNativeFlashlightLayersAreChangedAndStateIsRestorable() {
        assertTrue(adapter.contains("layers.numberOfLayers != 2"))
        assertTrue(adapter.contains("MixColorTileDrawable"))
        assertTrue(adapter.contains("states.putIfAbsent(tile, original)"))
        assertTrue(adapter.contains("mask.setColor(savedMask)"))
        assertTrue(adapter.contains("val shader = mix() ?: return false"))
    }
}
