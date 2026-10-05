package com.mi.onextbox.lsp.compat

import java.lang.reflect.Executable

/**
 * Small before/after callback model used by ONextBox hook code.
 *
 * This is deliberately limited to the behavior used by this module. It is not a
 * reimplementation of a legacy Xposed API and has no dependency on legacy classes.
 */
internal abstract class ModernMethodHook {
    open fun beforeHookedMethod(param: MethodHookParam) = Unit

    open fun afterHookedMethod(param: MethodHookParam) = Unit

    internal class MethodHookParam(
        val method: Executable,
        val thisObject: Any?,
        val args: Array<Any?>,
    ) {
        private enum class Phase { BEFORE, AFTER }

        private var phase = Phase.BEFORE
        private var resultValue: Any? = null
        private var throwableValue: Throwable? = null

        internal var returnEarly: Boolean = false
            private set

        var result: Any?
            get() = resultValue
            set(value) {
                resultValue = value
                throwableValue = null
                if (phase == Phase.BEFORE) returnEarly = true
            }

        var throwable: Throwable?
            get() = throwableValue
            set(value) {
                throwableValue = value
                if (value != null && phase == Phase.BEFORE) returnEarly = true
            }

        internal fun beginAfter(result: Any?, throwable: Throwable?) {
            phase = Phase.AFTER
            resultValue = result
            throwableValue = throwable
        }

        internal fun resetAfterBeforeFailure() {
            resultValue = null
            throwableValue = null
            returnEarly = false
        }

        internal fun snapshotOutcome(): Outcome = Outcome(resultValue, throwableValue)

        internal fun restoreOutcome(outcome: Outcome) {
            resultValue = outcome.result
            throwableValue = outcome.throwable
        }

        internal fun resultOrThrow(): Any? {
            throwableValue?.let { throw it }
            return resultValue
        }

        internal data class Outcome(val result: Any?, val throwable: Throwable?)
    }
}
