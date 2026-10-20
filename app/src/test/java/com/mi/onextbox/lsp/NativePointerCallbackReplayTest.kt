package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class NativePointerCallbackReplayTest {
    @Test fun nativePointerPairRecoversItsOwnerOnlyOnce() {
        val methods = SensorCallback::class.java.declaredMethods.toList()
        val owners = NativePointerCallbackReplay.owners(methods + methods, NativeCallback::class.java)
        assertEquals(listOf(SensorCallback::class.java), owners)
    }

    @Test fun unrelatedClassesWithMatchingNamesCannotBecomeAuthenticationCallbacks() {
        assertTrue(NativePointerCallbackReplay.owners(
            Lookalike::class.java.declaredMethods.toList(), NativeCallback::class.java).isEmpty())
    }

    @Test fun constructorOtherEventsAndWrongSignaturesAreIgnored() {
        val targets = MalformedCallback::class.java.declaredMethods.toList() +
            MalformedCallback::class.java.getDeclaredConstructor()
        assertTrue(NativePointerCallbackReplay.owners(targets, NativeCallback::class.java).isEmpty())
    }

    @Test fun inheritedCallbackIsTrackedByItsActualDeclaringOwner() {
        val method = ChildCallback::class.java.getMethod("onUdfpsPointerUp", Int::class.javaPrimitiveType!!)
        assertEquals(listOf(SensorCallback::class.java),
            NativePointerCallbackReplay.owners(listOf(method), NativeCallback::class.java))
    }

    @Test fun discoveryNeverInvokesAnAuthenticationOrPressEvent() {
        val methods = ThrowingCallback::class.java.declaredMethods.toList()
        assertEquals(listOf(ThrowingCallback::class.java),
            NativePointerCallbackReplay.owners(methods, NativeCallback::class.java))
    }

    open class NativeCallback
    open class SensorCallback : NativeCallback() {
        fun onUdfpsPointerDown(sensorId: Int) = Unit
        fun onUdfpsPointerUp(sensorId: Int) = Unit
    }
    class ChildCallback : SensorCallback()
    class Lookalike {
        fun onUdfpsPointerUp(sensorId: Int) = Unit
    }
    class MalformedCallback : NativeCallback() {
        fun onUdfpsPointerUp(sensorId: Long) = Unit
        fun onUdfpsPointerDown(sensorId: Int): Boolean = false
        fun onAuthenticationSucceeded(sensorId: Int) = Unit
    }
    class ThrowingCallback : NativeCallback() {
        fun onUdfpsPointerDown(sensorId: Int): Unit = error("Must not invoke DOWN")
        fun onUdfpsPointerUp(sensorId: Int): Unit = error("Must not invoke UP")
    }
}
