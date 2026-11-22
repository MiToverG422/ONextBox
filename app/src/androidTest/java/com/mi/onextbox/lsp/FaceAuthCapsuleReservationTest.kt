package com.mi.onextbox.lsp

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.lang.ref.WeakReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaceAuthCapsuleReservationTest {
    class Dispatcher {
        var areas: List<Rect> = emptyList()
        fun setHiddenIconAreas(location: String, next: List<Rect>) {
            require(location == "KEYGUARD")
            areas = next
        }
    }

    @Test fun nativeAreaDiscoveredBeforeFirstDrawWinsAndCanLaterBeReleased() {
        val avoidance = FaceAuthCapsuleAvoidance()
        val dispatcher = Dispatcher()
        fun field(name: String) = avoidance.javaClass.getDeclaredField(name).apply { isAccessible = true }
        field("dispatcher").set(avoidance, WeakReference(dispatcher))
        field("setter").set(avoidance, Dispatcher::class.java.getMethod("setHiddenIconAreas", String::class.java, List::class.java))
        field("locations").set(avoidance, listOf("KEYGUARD"))
        @Suppress("UNCHECKED_CAST")
        val native = field("nativeAreas").get(avoidance) as MutableMap<Any, List<Rect>>
        val cloud = Rect(75, 0, 205, 88)
        native["KEYGUARD"] = listOf(cloud)
        dispatcher.areas = listOf(cloud)
        val bounds = FaceAuthCapsuleBounds(100f, 0f, 180f, 88f)
        assertFalse(avoidance.reserve(bounds))
        assertFalse(avoidance.ownsArea())
        assertEquals(listOf(cloud), dispatcher.areas)
        native["KEYGUARD"] = emptyList()
        assertTrue(avoidance.reserve(bounds))
        assertTrue(avoidance.ownsArea())
        avoidance.release()
        assertTrue(dispatcher.areas.isEmpty())
    }
}
