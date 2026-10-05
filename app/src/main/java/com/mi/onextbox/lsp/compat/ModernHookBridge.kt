package com.mi.onextbox.lsp.compat

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable

/**
 * ONextBox's independently designed adapter from its before/after callbacks to
 * the API 102 interceptor chain.
 */
internal object ModernHookBridge {
    /**
     * Direct API 102 path for methods called at frame/policy frequency. Unlike [hookMethod], this
     * does not allocate a compatibility MethodHookParam or copy the immutable argument list.
     */
    fun hookMethodFast(
        executable: Executable,
        hooker: XposedInterface.Hooker,
    ): XposedInterface.HookHandle {
        executable.isAccessible = true
        return ModernHookRuntime.requireModule()
            .hook(executable)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(hooker)
    }

    fun hookMethod(
        executable: Executable,
        callback: ModernMethodHook,
    ): XposedInterface.HookHandle {
        executable.isAccessible = true
        val module = ModernHookRuntime.requireModule()
        return module
            .hook(executable)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain -> intercept(chain, callback) }
    }

    fun hookAllMethods(
        hookClass: Class<*>,
        methodName: String,
        callback: ModernMethodHook,
    ): Set<XposedInterface.HookHandle> = hookClass.declaredMethods
        .asSequence()
        .filter { it.name == methodName }
        .map { hookMethod(it, callback) }
        .toSet()

    fun hookAllConstructors(
        hookClass: Class<*>,
        callback: ModernMethodHook,
    ): Set<XposedInterface.HookHandle> = hookClass.declaredConstructors
        .asSequence()
        .map { hookMethod(it, callback) }
        .toSet()

    private fun intercept(
        chain: XposedInterface.Chain,
        callback: ModernMethodHook,
    ): Any? {
        val param = ModernMethodHook.MethodHookParam(
            method = chain.executable,
            thisObject = chain.thisObject,
            args = chain.args.toTypedArray(),
        )

        val beforeSucceeded = runCallback("before", chain.executable) {
            callback.beforeHookedMethod(param)
        }
        if (!beforeSucceeded) param.resetAfterBeforeFailure()

        var result: Any? = null
        var throwable: Throwable? = null
        if (param.returnEarly) {
            result = param.result
            throwable = param.throwable
        } else {
            try {
                result = chain.proceed(param.args)
            } catch (error: Throwable) {
                throwable = error
            }
        }

        param.beginAfter(result, throwable)
        val originalOutcome = param.snapshotOutcome()
        val afterSucceeded = runCallback("after", chain.executable) {
            callback.afterHookedMethod(param)
        }
        if (!afterSucceeded) param.restoreOutcome(originalOutcome)
        return param.resultOrThrow()
    }

    private inline fun runCallback(
        phase: String,
        executable: Executable,
        block: () -> Unit,
    ): Boolean {
        return runCatching(block).fold(
            onSuccess = { true },
            onFailure = { error ->
                ModernHookRuntime.log(
                    priority = android.util.Log.ERROR,
                    tag = "ONextBox-LSP",
                    message = "$phase callback failed: ${executable.declaringClass.name}#${executable.name}",
                    throwable = error,
                )
                false
            },
        )
    }
}
