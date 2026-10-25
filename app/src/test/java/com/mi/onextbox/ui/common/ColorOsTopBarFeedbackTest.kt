package com.mi.onextbox.ui.common

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorOsTopBarFeedbackTest {
    @Test fun releasingAtAnyPressProgressKeepsTheCurrentRadius() {
        for (step in 0..100) {
            val progress = step / 100f
            val pressedRadius = 100f + 400f * progress
            val releasedRadius = colorOsSpotlightRadius(100f, 500f, progress, false)
            assertEquals("Release at $progress", pressedRadius, releasedRadius, 0.0001f)
        }
    }

    @Test fun releaseCannotBrightenLightOrDarkButtonsAsProgressDecreases() {
        for (endIntensity in listOf(0.15f, 1f)) {
            for (distance in listOf(200f, 220f, 260f)) {
                var previousRadius = 500f
                var previousBrightness = Float.POSITIVE_INFINITY
                for (step in 100 downTo 0) {
                    val progress = step / 100f
                    val radius = colorOsSpotlightRadius(100f, 500f, progress, false)
                    val brightness = spotlightBrightness(radius, progress, endIntensity, distance)
                    assertTrue("Radius increased at $progress", radius <= previousRadius)
                    assertTrue(
                        "Brightness increased at $progress for intensity=$endIntensity distance=$distance",
                        brightness <= previousBrightness + 0.000001f,
                    )
                    previousRadius = radius
                    previousBrightness = brightness
                }
                assertEquals(0f, previousBrightness, 0f)
            }
        }
    }

    @Test fun shortTapDoesNotIntroduceALightThatWasAbsentBeforeRelease() {
        val progress = 0.2f
        val radius = colorOsSpotlightRadius(100f, 500f, progress, false)
        assertEquals(180f, radius, 0.0001f)
        for (endIntensity in listOf(0.15f, 1f)) {
            assertEquals(0f, spotlightBrightness(radius, progress, endIntensity), 0f)

            val expandingRadius = colorOsSpotlightRadius(100f, 500f, progress, true)
            assertEquals(980f, expandingRadius, 0.0001f)
            assertTrue(spotlightBrightness(expandingRadius, progress, endIntensity) > 0f)
        }
    }

    @Test fun otherSurfaceReleaseExpansionIsUnchanged() {
        for (endRadius in listOf(500f, 800f)) {
            for (progress in listOf(0f, 0.2f, 0.5f, 1f)) {
                val expected = 100f + (endRadius - 100f) * progress +
                    endRadius * 2f * (1f - progress)
                assertEquals(
                    expected,
                    colorOsSpotlightRadius(100f, endRadius, progress, true),
                    0.0001f,
                )
            }
        }
    }

    @Test fun radiusRemainsBoundedForSpringProgressOutsideTheVisibleRange() {
        assertEquals(100f, colorOsSpotlightRadius(100f, 500f, -0.2f, false), 0f)
        assertEquals(500f, colorOsSpotlightRadius(100f, 500f, 1.2f, false), 0f)
    }

    @Test fun rendererDisablesReleaseExpansionOnlyForToolbarButtons() {
        val renderer = source("ColorOsSpotlightRenderer.kt")
        assertTrue(renderer.contains("val radius = colorOsSpotlightRadius("))
        assertTrue(renderer.contains("expandOnRelease = extinguishing && style != Style.TopBarButton"))
    }

    @Test fun toolbarReleaseRestartsFromCurrentProgressWithoutInwardVelocity() {
        val release = source("ColorOsSpotlightRenderer.kt")
            .substringAfter("fun onUp(")
            .substringBefore("fun cancel()")
        assertTrue(
            Regex(
                "if \\(style == Style\\.TopBarButton\\) \\{.*?" +
                    "progressAnimation\\.cancel\\(\\).*?" +
                    "progressAnimation\\.setStartValue\\(progress\\).*?" +
                    "progressAnimation\\.setStartVelocity\\(0f\\).*?\\}.*?" +
                    "animateProgress\\(\\s*target = 0f",
                RegexOption.DOT_MATCHES_ALL,
            ).containsMatchIn(release),
        )
    }

    @Test fun releaseAndCancelFadeThePressMaskWithoutInheritedVelocity() {
        val motion = source("ColorOsTopBarMotion.kt")
        val release = motion.substringAfter("fun release(").substringBefore("fun localPosition(")
        assertTrue(release.contains("mask.to(0f, stiffness = 438.649f, damping = 1f, resetVelocity = true)"))
        val spring = motion.substringAfter("private class TopBarSpring")
        assertTrue(
            Regex("if \\(resetVelocity\\) \\{\\s*animation\\.cancel\\(\\)\\s*" +
                "animation\\.setStartValue\\(value\\)\\s*animation\\.setStartVelocity\\(0f\\)")
                .containsMatchIn(spring),
        )
        val input = source("ColorOsTopBarButton.kt")
        val finallyBlock = input.substringAfter("} finally {").substringBefore(".clickable(")
        assertTrue(finallyBlock.contains("motion.release(size,"))
        assertTrue(finallyBlock.contains("spotlight.onUp(it.x, it.y)"))
    }

    @Test fun cancellationResetsProgressAndRepressDoesNotResetTheCurrentRadius() {
        val renderer = source("ColorOsSpotlightRenderer.kt")
        val cancel = renderer.substringAfter("fun cancel()").substringBefore("fun draw(")
        assertTrue(cancel.contains("progress = 0f"))
        assertTrue(cancel.contains("progressHolder.value = 0f"))
        assertTrue(cancel.contains("extinguishing = false"))

        val down = renderer.substringAfter("fun onDown(").substringBefore("fun onMove(")
        assertTrue(down.contains("extinguishing = false"))
        assertTrue(down.contains("target = 1f"))
        assertTrue(!down.contains("progress = 0f"))
        assertTrue(!down.contains("setStartValue(0f)"))
    }

    private fun spotlightBrightness(
        radius: Float,
        progress: Float,
        endIntensity: Float,
        distance: Float = 200f,
    ): Float {
        if (distance >= radius || progress <= 0f) return 0f
        val t = (1f - distance / radius).coerceIn(0f, 1f)
        val attenuation = t * t * (3f - 2f * t)
        val illumination = 0.4f + 0.4f * 200f / distance
        return illumination * attenuation * 1.5f * endIntensity * progress
    }

    private fun source(name: String): String = listOf(
        File("src/main/java/com/mi/onextbox/ui/common/$name"),
        File("app/src/main/java/com/mi/onextbox/ui/common/$name"),
    ).first { it.isFile }.readText().replace("\r\n", "\n")
}
