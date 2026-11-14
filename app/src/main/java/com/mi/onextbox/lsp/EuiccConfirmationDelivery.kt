package com.mi.onextbox.lsp

import java.lang.reflect.Modifier
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch

internal object EuiccConfirmationDelivery {
    /** Both named and obfuscated responses retain a two-hop reference to the OEM service. */
    fun deliver(callback: Any, serviceType: Class<*>, code: String?) {
        fun fields(type: Class<*>) = generateSequence(type) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }.filter { !Modifier.isStatic(it.modifiers) }.toList()
        fun references(value: Any) = fields(value.javaClass).filter { !it.type.isPrimitive }
            .mapNotNull { runCatching { it.isAccessible = true; it.get(value) }.getOrNull() }
        val services = IdentityHashMap<Any, Boolean>()
        val outer = references(callback)
        (outer + outer.flatMap(::references)).filter(serviceType::isInstance).forEach { services[it] = true }
        val service = services.keys.single()
        // IntentService also owns a String and must never be used as the carrier response slot.
        val serviceFields = serviceType.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
        val codeField = serviceFields.single { it.type == String::class.java }.apply { isAccessible = true }
        val latchField = serviceFields.single { it.type == CountDownLatch::class.java }.apply { isAccessible = true }
        val latch = latchField.get(service) as? CountDownLatch ?: error("LPA confirmation latch unavailable")
        codeField.set(service, code)
        latch.countDown()
    }
}
