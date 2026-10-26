package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class LauncherGridCompletionTest {
    open class NativeTask<Result> {
        open fun onPostExecute(result: Result) = Unit
    }

    class LegacyTask : NativeTask<Boolean?>() {
        var calls = 0
        var received: Boolean? = null
        override fun onPostExecute(result: Boolean?) {
            calls++
            received = result
        }
    }

    class OptimizedTask : NativeTask<Any?>() {
        var calls = 0
        var received: Any? = null
        override fun onPostExecute(result: Any?) {
            calls++
            received = result
        }
    }

    class InheritedTask : NativeTask<Any?>()

    class StaticCallback {
        companion object {
            @JvmStatic fun onPostExecute(result: Any?) = Unit
        }
    }

    abstract class AbstractCallback {
        abstract fun onPostExecute(result: Any?)
    }

    class WrongReturnCallback {
        fun onPostExecute(result: Any?) = true
    }

    @Test fun legacyBridgeStillInvokesTheTypedNativeCallbackExactlyOnce() {
        val task = LegacyTask()
        val method = LauncherGridApplyTargets.completionMethod(task.javaClass)
        assertTrue(method.isBridge)
        assertNotNull(task.javaClass.getDeclaredMethod("onPostExecute", Boolean::class.javaObjectType))
        method.invoke(task, true)
        assertEquals(1, task.calls)
        assertEquals(true, task.received)
    }

    @Test fun optimizedObjectCallbackPreservesSuccessFailureAndNullResults() {
        val task = OptimizedTask()
        assertThrows(NoSuchMethodException::class.java) {
            task.javaClass.getDeclaredMethod("onPostExecute", Boolean::class.javaObjectType)
        }
        val method = LauncherGridApplyTargets.completionMethod(task.javaClass)
        assertFalse(method.isBridge)
        for ((index, result) in listOf(true, false, null).withIndex()) {
            method.invoke(task, result)
            assertEquals(index + 1, task.calls)
            assertEquals(result, task.received)
        }
    }

    @Test fun inheritedDefaultCallbackCannotHideAMissingTaskOverride() {
        assertThrows(NoSuchMethodException::class.java) {
            LauncherGridApplyTargets.completionMethod(InheritedTask::class.java)
        }
    }

    @Test fun staticCallbacksCannotBeInstalledAsTaskCompletion() {
        assertThrows(IllegalArgumentException::class.java) {
            LauncherGridApplyTargets.completionMethod(StaticCallback::class.java)
        }
    }

    @Test fun abstractCallbacksCannotBeInstalledAsTaskCompletion() {
        assertThrows(IllegalArgumentException::class.java) {
            LauncherGridApplyTargets.completionMethod(AbstractCallback::class.java)
        }
    }

    @Test fun callbacksMustRetainTheNativeVoidReturnType() {
        assertThrows(IllegalArgumentException::class.java) {
            LauncherGridApplyTargets.completionMethod(WrongReturnCallback::class.java)
        }
    }
}
