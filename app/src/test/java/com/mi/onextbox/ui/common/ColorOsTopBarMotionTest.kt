package com.mi.onextbox.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ColorOsTopBarMotionTest {
    @Test fun restingButtonKeepsPointerCoordinates() {
        val size = IntSize(120, 120)
        for (position in listOf(Offset.Zero, Offset(60f, 60f), Offset(120f, 120f), Offset(-10f, 130f))) {
            assertOffset(position, colorOsTopBarLocalPosition(position, size, 1f, 1f, 0f, 0f))
        }
    }

    @Test fun scaleLeavesTheCenterFixed() {
        val size = IntSize(120, 160)
        val center = Offset(60f, 80f)
        for (scale in listOf(.5f, .88858957f, 1f, 1.2f)) {
            assertOffset(center, colorOsTopBarLocalPosition(center, size, scale, scale, 0f, 0f))
        }
    }

    @Test fun inverseTransformIncludesPressDragDeformationAndTranslation() {
        val size = IntSize(120, 160)
        val center = Offset(60f, 80f)
        val press = .88858957f
        val scaleX = press * 1.15f
        val scaleY = press / 1.15f
        val translation = Offset(8f, -6f)
        for (local in listOf(Offset.Zero, center, Offset(120f, 160f), Offset(-10f, 190f))) {
            val screen = Offset(
                center.x + (local.x - center.x) * scaleX + translation.x,
                center.y + (local.y - center.y) * scaleY + translation.y,
            )
            assertOffset(local, colorOsTopBarLocalPosition(
                screen, size, scaleX, scaleY, translation.x, translation.y,
            ))
        }
    }

    @Test fun zeroAndTinyScaleNeverDivideByZero() {
        val size = IntSize(120, 120)
        for (scale in listOf(0f, .00001f, -1f)) {
            val local = colorOsTopBarLocalPosition(Offset(61f, 59f), size, scale, scale, 0f, 0f)
            assertTrue(local.x.isFinite())
            assertTrue(local.y.isFinite())
            assertEquals(1060f, local.x, .001f)
            assertEquals(-940f, local.y, .001f)
        }
    }

    @Test fun inputHitBoxDoesNotMoveWithTheVisualSubtree() {
        val button = source("ColorOsTopBarButton.kt")
        val pointer = button.indexOf(".pointerInput(motion, spotlight)")
        val click = button.indexOf(".clickable(")
        val transform = button.indexOf(".graphicsLayer {")
        val material = button.indexOf("ColorOsTopBarMaterialView(")
        assertTrue(pointer >= 0 && pointer < click)
        assertTrue(click < transform && transform < material)
        assertEquals(1, Regex("\\.graphicsLayer \\{").findAll(button).count())
        assertTrue(button.contains("scaleX = motion.scaleX"))
        assertTrue(button.contains("scaleY = motion.scaleY"))
        assertTrue(button.contains("translationX = motion.translationX"))
        assertTrue(button.contains("translationY = motion.translationY"))
        assertTrue(button.contains("role = Role.Button"))
        assertTrue(button.contains("onClick = onClick"))
    }

    @Test fun rawDownStartsFeedbackWithoutWaitingForClickInteraction() {
        val button = source("ColorOsTopBarButton.kt")
        val gesture = button.substringAfter("awaitEachGesture {").substringBefore("} finally {")
        assertTrue(gesture.contains("awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)"))
        assertTrue(gesture.indexOf("motion.begin()") < gesture.indexOf("while (true)"))
        assertTrue(gesture.contains("spotlight.onDown(it.x, it.y)"))
        assertFalse(button.contains("PressInteraction"))
        assertFalse(button.contains("interactions.collect"))
    }

    @Test fun dragAndMultiplePointersCannotTriggerTheToolbarAction() {
        val button = source("ColorOsTopBarButton.kt")
        val gesture = button.substringAfter("awaitEachGesture {").substringBefore("} finally {")
        assertTrue(gesture.contains("delta.getDistance() > viewConfiguration.touchSlop"))
        assertTrue(gesture.contains("motion.move(delta, size)"))
        assertTrue(gesture.contains("if (dragged) change.consume()"))
        val up = gesture.substringAfter("if (!change.pressed) {").substringBefore("val delta =")
        assertTrue(up.contains("if (dragged) change.consume()"))
        val multiPointer = gesture.substringAfter("if (!singlePointer) {").substringBefore("if (!change.pressed)")
        assertTrue(multiPointer.contains("event.changes.forEach { it.consume() }"))
        assertTrue(multiPointer.contains("break"))
    }

    @Test fun canceledGesturesStillReleaseAllFeedbackWithZeroVelocity() {
        val button = source("ColorOsTopBarButton.kt")
        val release = button.substringAfter("} finally {").substringBefore(".clickable(")
        assertTrue(release.contains("if (releasedNormally) tracker.calculateVelocity() else null"))
        assertTrue(release.contains("motion.release(size, velocity?.let { Offset(it.x, it.y) } ?: Offset.Zero)"))
        assertTrue(release.contains("spotlight.onUp(it.x, it.y)"))
        assertTrue(button.contains("motion.dispose()"))
        assertTrue(button.contains("spotlight.cancel()"))
    }

    @Test fun spotlightIsADrawOnlyOverlayAboveTheIcon() {
        val button = source("ColorOsTopBarButton.kt")
        assertFalse(button.contains("ColorOsTopBarSpotlightView("))
        assertEquals(1, Regex("AndroidView\\(").findAll(button).count())
        val overlay = button.substringAfter("content()")
        assertTrue(overlay.contains("Canvas(Modifier.fillMaxSize().clip(CircleShape).onSizeChanged"))
        assertTrue(overlay.contains("spotlight.onSizeChanged(it.width, it.height)"))
        assertTrue(overlay.contains("spotlight.draw(drawContext.canvas.nativeCanvas)"))
        assertFalse(overlay.contains("pointerInput"))
    }

    @Test fun geometryBouncesWhileTheHighlightFadesWithoutBounce() {
        val motion = source("ColorOsTopBarMotion.kt")
        assertTrue(motion.contains("val scaleX get() = press.value * drag.scaleX"))
        assertTrue(motion.contains("val scaleY get() = press.value * drag.scaleY"))
        assertTrue(motion.contains("press.to(0.88858957f, stiffness = 631.655f, damping = 1f)"))
        assertTrue(motion.contains("press.to(1f, stiffness = 157.914f, damping = 0.4f)"))
        assertTrue(motion.contains("mask.to(0f, stiffness = 438.649f, damping = 1f, resetVelocity = true)"))
        assertTrue(motion.contains("val maskProgress get() = mask.value.coerceIn(0f, 1f)"))
    }

    private fun assertOffset(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, .001f)
        assertEquals(expected.y, actual.y, .001f)
    }

    private fun source(name: String): String = listOf(
        File("src/main/java/com/mi/onextbox/ui/common/$name"),
        File("app/src/main/java/com/mi/onextbox/ui/common/$name"),
    ).first { it.isFile }.readText().replace("\r\n", "\n")
}
