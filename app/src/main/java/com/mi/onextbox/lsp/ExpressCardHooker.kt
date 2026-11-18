package com.mi.onextbox.lsp

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.WeakHashMap

// Parcel tracking in the launcher, assistant, and Quick Apps.
internal object ExpressCardHooker {
    private const val TAG = "ONextBox-Express"
    private const val DISPATCHER = "com.nearme.instant.platform.dispatch.activity.HapDispatcherActivity"

    private class Host(val packageName: String) {
        val query = ExpressQuerySession()
        val scheduledInputs = WeakHashMap<EditText, Long>()
        val callbacks = WeakHashMap<EditText, Runnable>()
        @Volatile var context: Context? = null
    }

    fun hook(packageName: String, classLoader: ClassLoader) {
        val prefix = "$TAG:$packageName@${System.identityHashCode(classLoader)}"
        if (packageName !in ExpressCardRules.packages ||
            (!enabled() && HotReloadLifecycle.restoredState(prefix) == null)) return
        val host = Host(packageName)
        HotReloadLifecycle.register(prefix, capture = { arrayOf(host.context, host.query.exportReloadState()) },
            canReload = { host.query.pending(SystemClock.uptimeMillis()) == null && !host.query.imeSuppressed(SystemClock.uptimeMillis()) },
            detach = {
                host.callbacks.entries.toList().forEach { (view, callback) -> view.removeCallbacks(callback) }
                host.callbacks.clear()
                host.scheduledInputs.clear()
                host.context = null
            }, restore = { state ->
                val saved = state as? Array<*>
                host.context = saved?.getOrNull(0) as? Context
                host.query.importReloadState(saved?.getOrNull(1))
            })
        HotReloadLifecycle.afterCommit {
            if (host.context == null) host.context = runCatching {
                ModernReflect.callStaticMethod(HostClassLookup.load("android.app.ActivityThread"), "currentApplication") as? Application
            }.getOrNull()?.takeIf { it.packageName == packageName }
        }
        installMethods(classLoader, Application::class.java.name, prefix,
            { it.name == "attach" && it.parameterTypes.contentEquals(arrayOf(Context::class.java)) },
        ) { chain ->
            val result = chain.proceed()
            host.context = chain.thisObject as? Application
            result
        }
        hookActivityStarts(host, classLoader, prefix)
        hookWechat(host, classLoader, prefix)
        if (packageName == ExpressCardRules.PLATFORM) {
            hookDispatcher(host, classLoader, prefix)
            hookActivityQueries(host, classLoader, prefix)
            hookPackageQueries(host, classLoader, prefix)
            hookSearchInput(host, classLoader, prefix)
            hookKeyboard(host, classLoader, prefix)
        }
        HookLog.i(TAG, "Express hooks installed in $packageName")
    }

    private fun enabled(): Boolean = LspConfig.isExpressNoMiniProgramEnabledXposed()

    private fun hookActivityStarts(host: Host, loader: ClassLoader, prefix: String) {
        for (className in listOf(ContextWrapper::class.java.name, Activity::class.java.name)) {
            installMethods(loader, className, prefix, { method ->
                method.name in setOf("startActivity", "startActivityForResult") &&
                    method.returnType == java.lang.Void.TYPE &&
                    method.parameterTypes.size in 1..3 && method.parameterTypes.firstOrNull() == Intent::class.java
            }) { chain ->
                if (!enabled()) return@installMethods chain.proceed()
                val intent = chain.getArg(0) as? Intent ?: return@installMethods chain.proceed()
                val replacement = rewrite(host, intent) ?: return@installMethods chain.proceed()
                val args = chain.args.toTypedArray()
                args[0] = replacement
                chain.proceed(args)
            }
        }
    }

    private fun rewrite(host: Host, intent: Intent): Intent? = runCatching {
        val extras = stringExtras(intent)
        val destination = ExpressCardRules.redirect(host.packageName, intent.action, intent.dataString, extras) ?: return null
        val replacement = if (host.packageName == ExpressCardRules.PLATFORM) {
            Intent(intent).apply {
                component = null
                selector = null
                data = Uri.parse(destination)
                setPackage(ExpressCardRules.PLATFORM)
            }
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(destination))
                .setPackage(ExpressCardRules.PLATFORM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (host.packageName == ExpressCardRules.PLATFORM) host.query.receive(destination, SystemClock.uptimeMillis())
        replacement
    }.getOrNull()

    private fun hookDispatcher(host: Host, loader: ClassLoader, prefix: String) {
        installMethods(loader, DISPATCHER, prefix, { method ->
            (method.name == "onCreate" && method.parameterTypes.contentEquals(arrayOf(Bundle::class.java))) ||
                (method.name == "onNewIntent" && method.parameterTypes.contentEquals(arrayOf(Intent::class.java)))
        }) { chain ->
            if (!enabled()) return@installMethods chain.proceed()
            val activity = chain.thisObject as? Activity ?: return@installMethods chain.proceed()
            val incoming = if (chain.executable.name == "onCreate") activity.intent else chain.getArg(0) as? Intent
            val intent = incoming?.let { rewrite(host, it) ?: it }
            host.query.receive(intent?.dataString, SystemClock.uptimeMillis())
            if (intent != null) activity.intent = intent
            if (chain.executable.name == "onNewIntent" && intent != null) chain.proceed(arrayOf(intent))
            else chain.proceed()
        }
    }

    private fun hookPackageQueries(host: Host, loader: ClassLoader, prefix: String) {
        installMethods(loader, "org.hapjs.pm.DefaultNativePackageProviderImpl", prefix, { method ->
            method.name == "hasPackageInstalled" && method.returnType == java.lang.Boolean.TYPE &&
                method.parameterTypes.size == 2 && String::class.java in method.parameterTypes
        }) { chain ->
            val name = chain.args.firstOrNull { it is String } as? String
            if (enabled() && inExpressPage(host, chain) && ExpressCardRules.isHiddenPackage(name)) false else chain.proceed()
        }
        installMethods(loader, "org.hapjs.pm.DefaultNativePackageProviderImpl", prefix, { method ->
            method.name == "packagesInstalled" && method.parameterTypes.size == 2 &&
                (List::class.java.isAssignableFrom(method.returnType) || method.returnType == Any::class.java)
        }) { chain ->
            val result = chain.proceed()
            if (!enabled() || !inExpressPage(host, chain) || result !is List<*>) return@installMethods result
            if (result.none { it is String && ExpressCardRules.isHiddenPackage(it) }) return@installMethods result
            result.filterNot { it is String && ExpressCardRules.isHiddenPackage(it) }
        }
        installMethods(loader, "org.hapjs.common.utils.PackageUtils", prefix, { method ->
            method.name == "getPackageInfo" && method.returnType == PackageInfo::class.java &&
                method.parameterTypes.size == 3 && String::class.java in method.parameterTypes
        }) { chain ->
            val name = chain.args.firstOrNull { it is String } as? String
            if (enabled() && inExpressPage(host, chain) && ExpressCardRules.isHiddenPackage(name)) null else chain.proceed()
        }
    }

    private fun hookActivityQueries(host: Host, loader: ClassLoader, prefix: String) {
        installMethods(loader, Activity::class.java.name, "$prefix:query", { method ->
            (method.name == "onCreate" && method.parameterTypes.contentEquals(arrayOf(Bundle::class.java))) ||
                (method.name == "onNewIntent" && method.parameterTypes.contentEquals(arrayOf(Intent::class.java)))
        }) { chain ->
            if (enabled()) {
                val activity = chain.thisObject as? Activity
                val intent = if (chain.executable.name == "onCreate") activity?.intent else chain.getArg(0) as? Intent
                host.query.receive(intent?.dataString, SystemClock.uptimeMillis())
            }
            chain.proceed()
        }
    }

    private fun inExpressPage(host: Host, chain: XposedInterface.Chain): Boolean {
        var context = chain.args.firstOrNull { it is Context } as? Context
        repeat(8) {
            if (context is Activity) return ExpressCardRules.isExpressUri((context as Activity).intent?.dataString)
            val wrapper = context as? ContextWrapper ?: return host.query.inExpress
            val base = wrapper.baseContext
            if (base === context) return host.query.inExpress
            context = base
        }
        return host.query.inExpress
    }

    private fun hookWechat(host: Host, loader: ClassLoader, prefix: String) {
        for (className in listOf("com.tencent.mm.opensdk.openapi.BaseWXApiImplV10", "com.tencent.mm.opensdk.openapi.WXApiImplV10")) {
            installMethods(loader, className, prefix, { method ->
                method.name == "sendReq" && method.returnType == java.lang.Boolean.TYPE && method.parameterTypes.size == 1
            }) { chain ->
                if (!enabled()) return@installMethods chain.proceed()
                val request = chain.getArg(0) ?: return@installMethods chain.proceed()
                val fields = ExpressCardRules.requestFields.mapNotNull { name ->
                    (runCatching { ModernReflect.getObjectField(request, name) }.getOrNull() as? String)?.let { name to it }
                }.toMap()
                val fromCard = Throwable().stackTrace.any {
                    it.className.contains("hapjs.card") || it.className.contains("oplus.card") || it.className.contains("pantanal")
                }
                val destination = ExpressCardRules.wechatDestination(fields, fromCard) ?: return@installMethods chain.proceed()
                val context = host.context ?: return@installMethods chain.proceed()
                val redirected = runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(destination))
                        .setPackage(ExpressCardRules.PLATFORM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
                if (redirected) false else chain.proceed()
            }
        }
    }

    private fun hookSearchInput(host: Host, loader: ClassLoader, prefix: String) {
        installMethods(loader, View::class.java.name, prefix, {
            it.name == "onAttachedToWindow" && it.parameterTypes.isEmpty()
        }) { chain ->
            val result = chain.proceed()
            val input = chain.thisObject as? EditText ?: return@installMethods result
            if (!enabled()) return@installMethods result
            val pending = host.query.pending(SystemClock.uptimeMillis()) ?: return@installMethods result
            scheduleSearch(host, input, pending)
            result
        }
    }

    private fun scheduleSearch(host: Host, input: EditText, pending: ExpressQuerySession.Pending) {
        if (HotReloadLifecycle.isRetiring) return
        if (host.scheduledInputs[input] == pending.generation) return
        host.scheduledInputs[input] = pending.generation
        host.callbacks.remove(input)?.let(input::removeCallbacks)
        val callback = object : Runnable {
            override fun run() {
                val now = SystemClock.uptimeMillis()
                if (HotReloadLifecycle.isRetiring || !enabled() || !input.isAttachedToWindow || !host.query.matches(pending, now)) {
                    host.callbacks.remove(input)
                    return
                }
                host.query.suppressIme(pending, now)
                if (input.isShown && input.isEnabled && input.text?.toString() == pending.number) {
                    runCatching {
                        input.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0))
                        input.dispatchKeyEvent(KeyEvent(now, now + 40, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0))
                        val keyboard = input.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        keyboard?.hideSoftInputFromWindow(input.windowToken, 0)
                    }.onFailure { HookLog.w(TAG, "Unable to submit express query", it) }
                    host.query.complete(pending, now)
                    host.callbacks.remove(input)
                    return
                }
                input.postDelayed(this, 100L)
            }
        }
        host.callbacks[input] = callback
        input.postDelayed(callback, 20L)
    }

    private fun hookKeyboard(host: Host, loader: ClassLoader, prefix: String) {
        installMethods(loader, InputMethodManager::class.java.name, prefix, { method ->
            val show = method.name == "showSoftInput" && method.parameterTypes.firstOrNull() == View::class.java
            val toggle = method.name == "toggleSoftInput" || method.name == "toggleSoftInputFromWindow"
            (show || toggle) && method.returnType in setOf(java.lang.Boolean.TYPE, java.lang.Void.TYPE)
        }) { chain ->
            if (enabled() && host.query.imeSuppressed(SystemClock.uptimeMillis())) {
                if ((chain.executable as Method).returnType == java.lang.Boolean.TYPE) false else null
            } else chain.proceed()
        }
    }

    private fun stringExtras(intent: Intent): Map<String, String> = runCatching {
        val extras = intent.extras ?: return emptyMap()
        extras.keySet().mapNotNull { key -> (extras.get(key) as? String)?.let { key to it } }.toMap()
    }.getOrDefault(emptyMap())

    private fun installMethods(
        loader: ClassLoader,
        className: String,
        prefix: String,
        matches: (Method) -> Boolean,
        intercept: (XposedInterface.Chain) -> Any?,
    ) {
        runCatching {
            val target = ModernReflect.findClassIfExists(className, loader) ?: return
            target.declaredMethods.filter(matches).forEach { method ->
                runCatching {
                    ModernHookRegistry.installFast("$prefix:${method.toGenericString()}", method, XposedInterface.Hooker(intercept))
                }.onFailure { HookLog.w(TAG, "Unable to hook $className#${method.name}", it) }
            }
        }.onFailure { HookLog.w(TAG, "Express target unavailable: $className", it) }
    }
}
