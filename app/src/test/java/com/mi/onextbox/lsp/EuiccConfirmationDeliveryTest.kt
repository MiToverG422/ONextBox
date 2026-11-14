package com.mi.onextbox.lsp

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class EuiccConfirmationDeliveryTest {
    private open class FrameworkService(val name: String = "framework", val wait: CountDownLatch = CountDownLatch(1))
    private class Service(var code: String? = "previous", val latch: CountDownLatch? = CountDownLatch(1)) : FrameworkService()
    private class ProgressionCallback(val service: Service)
    private class NamedResponse(val progression: ProgressionCallback)
    private class ObfuscatedOuter(val a: Service)
    private class ObfuscatedResponse(val b: ObfuscatedOuter)
    private class DirectResponse(val a: Service)
    private class AmbiguousResponse(val a: Service, val b: Service)

    @Test fun bothCallbackLayoutsPublishTheCodeBeforeWakingTheCarrierTransaction() {
        for (obfuscated in listOf(false, true)) {
            val service = Service()
            val response = if (obfuscated) ObfuscatedResponse(ObfuscatedOuter(service))
                else NamedResponse(ProgressionCallback(service))
            val received = AtomicReference<String?>()
            val waiter = Thread {
                if (requireNotNull(service.latch).await(1, TimeUnit.SECONDS)) received.set(service.code)
            }
            waiter.start()
            EuiccConfirmationDelivery.deliver(response, Service::class.java, "test-code")
            waiter.join(1500)
            assertFalse(waiter.isAlive)
            assertEquals("test-code", received.get())
            assertEquals("framework", service.name)
            assertEquals(1L, service.wait.count)
        }
    }

    @Test fun cancellationClearsThePreviousCodeAndReleasesTheNativeWait() {
        val service = Service()
        EuiccConfirmationDelivery.deliver(DirectResponse(service), Service::class.java, null)
        assertNull(service.code)
        assertEquals(0L, requireNotNull(service.latch).count)
    }

    @Test fun ambiguousServicesDoNotWakeOrChangeEitherTransaction() {
        val a = Service()
        val b = Service()
        assertTrue(runCatching {
            EuiccConfirmationDelivery.deliver(AmbiguousResponse(a, b), Service::class.java, "test-code")
        }.isFailure)
        for (service in listOf(a, b)) {
            assertEquals("previous", service.code)
            assertEquals(1L, requireNotNull(service.latch).count)
        }
    }

    @Test fun missingLatchLeavesThePreviousCodeUntouched() {
        val service = Service(latch = null)
        assertTrue(runCatching {
            EuiccConfirmationDelivery.deliver(DirectResponse(service), Service::class.java, "test-code")
        }.isFailure)
        assertEquals("previous", service.code)
    }
}
