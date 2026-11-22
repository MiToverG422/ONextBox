package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GlassShaderVariantTest {
    @Test fun panelProgramsOnlyIncludeTheSelectedMaterialAndKeepTheirOwnCacheIdentity() {
        val vendor = """uniform shader uniBDFBitmap;
            uniform float u_commonArray[8];
            half4 main(float2 position) {
                half4 outputCol = half4(uniBDFBitmap.eval(mapCoords).rgb, 1.0);
                ShapeResult shapeResult = getShape(position);
                return outputCol*shape;
            }"""
        val variants = SoftLightGlassShader.PanelProgram.entries.map { mode ->
            val source = SoftLightGlassShader.extend(vendor, false, true, panel = mode)!!
            assertEquals(mode, SoftLightGlassShader.PanelProgram.inSource(source))
            assertEquals(mode.style == SoftLightGlassSettings.LIQUID, "float3 lgBackdrop(" in source)
            assertEquals(mode.style != SoftLightGlassSettings.LIQUID, "half3 onbMaterial(" in source)
            source
        }
        assertEquals(6, variants.distinct().size)
        val shared = SoftLightGlassShader.extend(vendor, false, true)!!
        assertEquals(null, SoftLightGlassShader.PanelProgram.inSource(shared))
        assertEquals(true, "lgBackdrop(" in shared && "onbMaterial(" in shared)
    }

    @Test fun firstFactoryCallCreatesOnlyTheShaderItReturns() {
        val variant = GlassShaderVariant("stock", "glass")
        val compiled = mutableListOf<String>()
        val shader = Any()
        assertSame(shader, variant.createCached({ compiled += it; shader }, { throw it }))
        assertEquals(listOf("glass"), compiled)
        assertEquals("glass", variant.constructorSource({ error("already verified") }, { throw it }))
    }

    @Test fun directConstructorValidatesEachVariantOnlyOnce() {
        val variant = GlassShaderVariant("stock", "glass")
        val probes = mutableListOf<String>()
        repeat(2) { assertEquals("glass", variant.constructorSource({ probes += it }, { throw it })) }
        assertEquals(listOf("glass"), probes)
    }

    @Test fun failedOrNullFactoryResultUsesStockIncludingOnSubsequentRequests() {
        for (failure in listOf(null, IllegalArgumentException(), IllegalStateException())) {
            val variant = GlassShaderVariant("stock", "glass")
            val calls = mutableListOf<String>()
            repeat(2) {
                assertEquals("stock", variant.createCached({ source ->
                    calls += source
                    if (source == "stock") source else if (failure != null) throw failure else null
                }, {}))
            }
            assertEquals(listOf("glass", "stock", "stock"), calls)
            assertEquals("stock", variant.constructorSource({ error("rejected") }, { throw it }))
        }
    }

    @Test fun failedLegacyValidationAlsoFallsBackInTheFactory() {
        val variant = GlassShaderVariant("stock", "glass")
        assertEquals("stock", variant.constructorSource({ throw IllegalArgumentException() }, {}))
        assertEquals("stock", variant.createCached({ it }, { throw it }))
    }

    @Test fun unifiedProfileReadsEachPreferenceOnlyOnce() {
        val reads = mutableListOf<String>()
        val params = LiquidGlassTuning.Parameters { key, default ->
            reads += key
            if (key == LiquidGlassTuning.UNIFIED) 1 else default
        }
        assertEquals(reads.distinct(), reads)
        for (surface in LiquidGlassTuning.surfaces) assertEquals(10f, params.blur(surface), 0f)
    }
}
