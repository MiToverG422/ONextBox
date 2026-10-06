package com.mi.onextbox.lsp

/** Synchronous request scope, cannot leak into another request or an asynchronous callback. */
internal class CaptureRequestScope<T : Any> {
    private val current = ThreadLocal<T>()

    fun get(): T? = current.get()

    fun <R> withValue(value: T?, block: () -> R): R {
        val previous = current.get()
        if (value == null) current.remove() else current.set(value)
        return try {
            block()
        } finally {
            if (previous == null) current.remove() else current.set(previous)
        }
    }
}
