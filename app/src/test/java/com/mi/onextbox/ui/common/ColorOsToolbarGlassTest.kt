package com.mi.onextbox.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ColorOsToolbarGlassTest {
    @Test fun sharedMaterialRestoresOriginalBottomBarPresets() {
        assertEquals(0x80666666.toInt(), ColorOsToolbarGlass.firstTint(true))
        assertEquals(0xB3262626.toInt(), ColorOsToolbarGlass.luminosityTint(true))
        assertEquals(0xB3666666.toInt(), ColorOsToolbarGlass.firstTint(false))
        assertEquals(0x99F5F5F5.toInt(), ColorOsToolbarGlass.luminosityTint(false))
        assertEquals(25f, ColorOsToolbarGlass.BlurRadiusPx, 0f)
    }

    @Test fun fallbackMatchesOriginalBottomBar() {
        assertEquals(255, ColorOsToolbarGlass.FallbackAlpha)
    }

    @Test fun darkCouiTranslucentContainersBecomeDarkMaterialInsteadOfWhite() {
        val colors = darkColorScheme()
        assertEquals(0x1AFFFFFF, colors.surfaceContainer.toArgb())
        assertEquals(0x33FFFFFF, colors.surfaceContainerHigh.toArgb())
        assertEquals(0xFF1A1A1A.toInt(), ColorOsToolbarGlass.fallbackColor(
            colors.surfaceContainer, colors.surface,
        ).toArgb())
        assertEquals(0xFF333333.toInt(), ColorOsToolbarGlass.fallbackColor(
            colors.surfaceContainerHigh, colors.surface,
        ).toArgb())
    }

    @Test fun lightCouiOpaqueContainersKeepTheirExistingColors() {
        val colors = lightColorScheme()
        assertEquals(Color.White, ColorOsToolbarGlass.fallbackColor(colors.surfaceContainer, colors.surface))
        assertEquals(Color(0xFFE6E6E6), ColorOsToolbarGlass.fallbackColor(
            colors.surfaceContainerHigh, colors.surface,
        ))
    }

    @Test fun opaqueMonetContainersAreNotTintedAgain() {
        val dark = darkColorScheme(
            surface = Color(0xFF101418), surfaceContainer = Color(0xFF1D2024),
        )
        val light = lightColorScheme(
            surface = Color(0xFFF7F9FF), surfaceContainer = Color(0xFFEBEDF4),
        )
        for (colors in listOf(dark, light)) {
            assertEquals(colors.surfaceContainer, ColorOsToolbarGlass.fallbackColor(
                colors.surfaceContainer, colors.surface,
            ))
        }
    }

    @Test fun fullyTransparentContainersRevealTheActualPageSurface() {
        for (surface in listOf(Color.Black, Color.White, Color(0xFF121820))) {
            assertEquals(surface, ColorOsToolbarGlass.fallbackColor(Color.Transparent, surface))
        }
    }

    @Test fun onlyNativeBottomBarSharesSearchTints() {
        val ui = listOf(File("src/main/java/com/mi/onextbox/ui"),
            File("app/src/main/java/com/mi/onextbox/ui")).first { it.isDirectory }
        val native = File(ui, "common/ColorOsBottomBarMaterialView.kt").readText()
        assertTrue(native.contains("ColorOsToolbarGlass.firstTint(darkTheme)"))
        assertTrue(native.contains("ColorOsToolbarGlass.luminosityTint(darkTheme)"))
        assertTrue(native.contains("if (darkTheme) BlendMode.OVERLAY else BlendMode.COLOR_DODGE"))
        assertTrue(native.contains("ColorOsToolbarGlass.BlurRadiusPx"))
        assertTrue(native.contains("ColorOsToolbarGlass.FallbackAlpha"))
        assertFalse(native.contains("ColorDrawable(fallbackColor)"))
        val liquid = File(ui, "layout/LiquidGlassBottomNavigationBar.kt").readText()
        assertFalse(liquid.contains("ColorOsToolbarGlass"))
        assertTrue(liquid.contains("copy(0.4f)"))
        assertTrue(liquid.contains("lens(24.dp.toPx(), 24.dp.toPx())"))
    }

    @Test fun materialCannotPutAnOpaqueBackgroundBehindTheFallback() {
        val dir = listOf(File("src/main/java/com/mi/onextbox/ui/common"),
            File("app/src/main/java/com/mi/onextbox/ui/common")).first { it.isDirectory }
        val material = File(dir, "ColorOsTopBarMaterialView.kt").readText()
        assertFalse(material.contains("ColorDrawable(fallbackColor)"))
        assertFalse(material.contains("else fallbackColor"))
        assertFalse(material.contains("#CC262626"))
        assertTrue(Regex("""RenderEffect\.createBlurEffect\(\s*ColorOsToolbarGlass\.BlurRadiusPx,\s*ColorOsToolbarGlass\.BlurRadiusPx,""")
            .containsMatchIn(material))
        assertTrue(material.contains("ColorOsToolbarGlass.FallbackAlpha"))
        assertTrue(material.contains("ColorOsToolbarGlass.firstTint(darkTheme)"))
        assertTrue(material.contains("ColorOsToolbarGlass.luminosityTint(darkTheme)"))
        assertTrue(material.contains("if (darkTheme) BlendMode.OVERLAY else BlendMode.COLOR_DODGE"))
        val button = File(dir, "ColorOsTopBarButton.kt").readText()
        assertTrue(button.contains("content()"))
        assertTrue(button.contains("ColorOsSpotlightRenderer.Style.TopBarButton"))
        assertTrue(button.contains("spotlight.draw(drawContext.canvas.nativeCanvas)"))
        assertTrue(button.contains("motion.begin()"))
        assertTrue(button.contains("motion.release(size,"))
    }

    @Test fun detailToolbarMaterialStaysPageLocalAcrossAllNavigationStates() {
        val page = uiSource("settings/SettingsPageSurface.kt")
        val toolbar = page.substringAfter("topBar = {").substringBefore("bottomBar = {")
        val nativePolicy =
            "LocalChromeNativeBlurEnabled provides (LocalChromeNativeBlurEnabled.current && !showBack)"
        assertTrue(toolbar.contains(nativePolicy))
        assertTrue(toolbar.indexOf("BlurredChromeBar(") < toolbar.indexOf(nativePolicy))
        assertTrue(toolbar.indexOf(nativePolicy) < toolbar.indexOf("val navigationIcon:"))
        assertTrue(toolbar.indexOf(nativePolicy) < toolbar.indexOf("ColorOs17DetailTopBar("))
        assertTrue(toolbar.indexOf(nativePolicy) < toolbar.indexOf("TopAppBar("))
        assertFalse(toolbar.contains("isRunning"))
        assertFalse(toolbar.contains("Lifecycle.State"))
    }

    @Test fun toolbarAndSearchMaterialsHonorInheritedNativeBlurPolicy() {
        for ((relativePath, updateCall) in listOf(
            "common/ColorOsTopBarButton.kt" to
                "view.updateAppearance(darkTheme, fallbackColor.toArgb(), nativeBlurEnabled)",
            "common/ColorOsSearchSurface.kt" to
                "it.updateAppearance(dark, fallbackColor.toArgb(), nativeBlurEnabled)",
        )) {
            val source = uiSource(relativePath)
            assertTrue(relativePath, source.contains("val nativeBlurEnabled = LocalChromeNativeBlurEnabled.current"))
            assertTrue(relativePath, source.contains("nativeBlurEnabled = nativeBlurEnabled,"))
            assertTrue(relativePath, source.contains(updateCall))
        }
        assertTrue(uiSource("FeatureWindowMotion.kt").contains(
            "CompositionLocalProvider(LocalChromeNativeBlurEnabled provides false)",
        ))
    }

    @Test fun toolbarAndSearchPrecomposeTheSameFallbackForCreationAndUpdates() {
        for (relativePath in listOf("common/ColorOsTopBarButton.kt", "common/ColorOsSearchSurface.kt")) {
            val source = uiSource(relativePath)
            assertTrue(relativePath, source.contains("val fallbackColor = ColorOsToolbarGlass.fallbackColor("))
            val material = source.substringAfter("ColorOsTopBarMaterialView(")
                .substringBefore("modifier = Modifier.fillMaxSize()")
            assertEquals(relativePath, 2, Regex("fallbackColor\\.toArgb\\(\\)").findAll(material).count())
            assertFalse(relativePath, material.contains("background.toArgb()"))
            assertFalse(relativePath, material.contains("surfaceContainer.toArgb()"))
        }
    }

    @Test fun pageLocalMaterialClearsTheWindowEffectBeforeDrawingThemeFallback() {
        val material = uiSource("common/ColorOsTopBarMaterialView.kt")
        val update = material.substringAfter("fun updateAppearance(")
            .substringBefore("fun updateSearchProgress(")
        assertTrue(update.contains("this.nativeBlurEnabled == nativeBlurEnabled"))
        assertTrue(update.contains("this.nativeBlurEnabled = nativeBlurEnabled"))
        assertTrue(update.contains("applyColorOsMaterial()"))
        val apply = material.substringAfter("private fun applyColorOsMaterial()")
            .substringBefore("private fun applyBackgroundRenderEffect()")
        assertTrue(Regex(
            "if \\(nativeBlurEnabled\\) \\{\\s*" +
                "nativeBackgroundApplied = applyBackgroundRenderEffect\\(\\)\\s*" +
                "} else \\{\\s*clearBackgroundRenderEffect\\(\\)",
        ).containsMatchIn(apply))
        assertTrue(Regex(
            "if \\(!nativeBackgroundApplied\\) \\{\\s*drawFallbackMaterial\\(canvas\\)",
        ).containsMatchIn(material))
        assertTrue(material.contains("nativeBlurEnabled: Boolean = true"))
    }

    @Test fun materialIsPreparedBeforeDrawingWithoutPostedBackgroundReplacement() {
        val material = uiSource("common/ColorOsTopBarMaterialView.kt")
            .substringBefore("internal class ColorOsTopBarSpotlightView")
        for (callback in listOf("onAttachedToWindow()", "onSizeChanged(")) {
            val body = material.substringAfter("override fun $callback")
                .substringBefore("override fun ")
            assertTrue(callback, body.contains("applyColorOsMaterial()"))
            assertFalse(callback, body.contains("post"))
        }
        assertEquals(1, Regex("background = ColorDrawable\\(Color.TRANSPARENT\\)")
            .findAll(material).count())
        assertTrue(material.contains("isForceDarkAllowed = false"))
    }

    private fun uiSource(relativePath: String): String = File(
        listOf(File("src/main/java/com/mi/onextbox/ui"),
            File("app/src/main/java/com/mi/onextbox/ui")).first { it.isDirectory },
        relativePath,
    ).readText()
}
