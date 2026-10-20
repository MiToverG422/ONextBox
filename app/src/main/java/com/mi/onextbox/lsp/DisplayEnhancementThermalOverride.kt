package com.mi.onextbox.lsp

import java.lang.reflect.Field

/** Only the native decision sees an unblocked flag; the genuine state is never discarded. */
internal class DisplayEnhancementThermalOverride(private val field: Field) {
    private class Frame(val owner: Any, var genuine: Boolean)
    private val current = ThreadLocal<Frame>()

    init {
        require(field.type == Boolean::class.javaPrimitiveType)
        field.isAccessible = true
    }

    fun observed(owner: Any?, genuine: Boolean): Boolean {
        val frame = current.get()
        if (frame == null || frame.owner !== owner) return genuine
        frame.genuine = genuine
        return false
    }

    fun <T> decide(owner: Any, enabled: Boolean, genuineAfter: (() -> Boolean)? = null, action: () -> T): T = synchronized(owner) {
        if (!enabled || current.get()?.owner === owner) return@synchronized action()
        val previous = current.get()
        val frame = Frame(owner, field.getBoolean(owner))
        current.set(frame)
        try {
            field.setBoolean(owner, false)
            action()
        } finally {
            try {
                if (genuineAfter != null) {
                    // A nested Hook can fall through while the lifecycle gate pauses. Read the
                    // genuine source outside this scope rather than losing its fresh update.
                    current.remove()
                    try { runCatching(genuineAfter).onSuccess { frame.genuine = it } }
                    finally { current.set(frame) }
                }
                field.setBoolean(owner, frame.genuine)
            } finally {
                if (previous == null) current.remove() else current.set(previous)
            }
        }
    }
}
