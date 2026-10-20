package com.mi.onextbox.ui.common

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class LiquidGlassUpstreamSyncContractTest {
    @Test fun upstreamVersionAndShapeDependencyArePinnedWithoutChangingTheProjectToMultiplatform() {
        val upstream = File("../third_party/android-liquid-glass/README.upstream.txt").readText()
        assertTrue(upstream.contains("Version: 2.0.1"))
        assertTrue(upstream.contains("Commit: 65ab177e90e5c1d8c62e70cf7755841982da65f6"))
        val versions = File("../gradle/libs.versions.toml").readText()
        assertTrue(versions.contains("kyantShapes = \"1.2.1\""))
        assertTrue(versions.contains("io.github.kyant0:shapes"))
        assertFalse(versions.contains("com.github.Kyant0:Capsule"))
        val sources = File("src/main/java/com/kyant/backdrop").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty())
        for (file in sources) {
            assertFalse(file.path, Regex("\\b(?:expect|actual)\\s+(?:suspend\\s+)?fun\\b").containsMatchIn(file.readText()))
        }
    }

    @Test fun newRenderingKeepsLocalCanvasAndInvalidationFixes() {
        assertTrue(source("com/kyant/backdrop/DrawBackdropModifier.kt").contains("override val shouldAutoInvalidate: Boolean = false"))
        val capture = source("com/kyant/backdrop/backdrops/LayerBackdropModifier.kt")
        assertTrue(capture.contains("override val shouldAutoInvalidate: Boolean = false"))
        assertTrue(capture.contains("node.backdrop.layerCoordinates = null"))
        val record = source("com/kyant/backdrop/internal/LayerRecorder.kt")
        assertTrue(record.contains("density = node.requireDensity()"))
        assertTrue(record.contains("this@recordLayer.draw("))
        assertTrue(source("com/kyant/backdrop/effects/Lens.kt").contains("is RoundedRectangularShape"))
        assertTrue(source("com/kyant/backdrop/highlight/HighlightStyle.kt").contains("setColorUniform(\"color\", color.copy(alpha = 1f))"))
    }

    @Test fun navigationKeepsThemeColorsAndRebuildsGestureGeometryWhenLayoutChanges() {
        val bar = source("com/mi/onextbox/ui/layout/LiquidGlassBottomNavigationBar.kt")
        assertTrue(bar.contains("COUITheme.colorScheme"))
        assertTrue(bar.contains("com.kyant.shapes.Capsule"))
        assertTrue(bar.contains("if (tabs.isEmpty()) return"))
        assertTrue(bar.contains("remember(animationScope, tabs.size, tabWidth, isLtr)"))
        assertTrue(bar.contains("remember(density, constraints.maxWidth)"))
        assertTrue(bar.contains("latestOnTabSelected(index)"))
        assertTrue(bar.contains("clearAndSetSemantics {}"))
    }

    @Test fun highlightsUseGuardedShadersAndDragTrackingUsesMonotonicTime() {
        val highlight = source("com/mi/onextbox/ui/common/InteractiveHighlight.kt")
        assertTrue(highlight.contains("if (isRuntimeShaderSupported()) RuntimeShader("))
        assertTrue(highlight.contains("Color.White.copy(0.25f * progress)"))
        assertTrue(highlight.contains("shader.asComposeShader()"))
        val drag = source("com/mi/onextbox/ui/common/DampedDragAnimation.kt")
        assertTrue(drag.contains("SystemClock.uptimeMillis()"))
        assertFalse(drag.contains("System.currentTimeMillis()"))
        assertTrue(drag.contains("pointerInput(this@DampedDragAnimation)"))
    }

    private fun source(path: String) = File("src/main/java/$path").readText()
}
