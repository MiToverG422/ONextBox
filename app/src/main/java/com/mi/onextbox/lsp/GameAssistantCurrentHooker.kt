package com.mi.onextbox.lsp

import android.os.Bundle
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.IdentityHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal object GameAssistantCurrentHooker {
    private const val TAG = "ONextBox-GameAssistant"
    private val musicScope = ThreadLocal<Boolean>()
    private data class BookmarkGuard(val list: Any, var reads: Int = 0)
    private val bookmarkGuard = ThreadLocal<BookmarkGuard>()

    fun hook(loader: ClassLoader, apkPath: String) {
        fun adapter(name: String, block: () -> Unit) = runCatching(block)
            .onFailure { HookLog.w(TAG, "$name unavailable; stock behavior retained", it) }
        adapter("Developer options") { GameAssistantDeveloperController.hook(loader) }
        adapter("Current semantic adapters") {
            System.loadLibrary("dexkit")
            DexKitBridge.create(apkPath).use { dex ->
                adapter("Skins") {
                    val skin = dex.findMethod { matcher {
                        usingStrings("OnePlus Ace Pro Genshin Impact Limited Edition", "genshin-hutao")
                        returnType = "java.lang.String"; paramTypes()
                    } }.single()
                    val method = skin.getMethodInstance(loader)
                    require(Modifier.isStatic(method.modifiers))
                    install(method) { chain -> if (on(GameAssistantSettings.SKINS))
                        GameAssistantRules.skinName(GameAssistantSettings.skinXposed()) else chain.proceed() }
                    deoptCallers(skin, loader, twoLevels = true)
                }
                adapter("Promotion dialog") {
                    val dialog = dex.findMethod { matcher {
                        usingStrings("isDialogShow  return"); returnType = "void"
                    } }.single()
                    val method = dialog.getMethodInstance(loader)
                    val types = method.parameterTypes
                    require(types.size == 12 && types.slice(4..9).all { it == String::class.java } &&
                        types[11] == Int::class.javaPrimitiveType &&
                        types[10].methods.any { it.name == "invoke" && it.parameterCount == 1 })
                    install(method) { chain ->
                        if (!on(GameAssistantSettings.PROMOTIONS)) chain.proceed() else {
                            chain.args[10]?.let { ModernReflect.callMethod(it, "invoke", false) }
                            null
                        }
                    }
                    deoptCallers(dialog, loader)
                }
                adapter("Promotion cards") {
                    val target = dex.findMethod { matcher {
                        usingStrings("isSupportShowGameCenterCard: isShowGameCenterCard = ")
                        returnType = "boolean"; paramTypes()
                    } }.single()
                    install(target.getMethodInstance(loader)) { chain ->
                        if (on(GameAssistantSettings.PROMOTIONS)) false else chain.proceed()
                    }
                    deoptCallers(target, loader)
                }
                adapter("Welfare notifications") {
                    val target = dex.findMethod { matcher {
                        usingStrings("checkAndSendWelfareNotification installGameList isNotEmpty")
                        returnType = "java.lang.Object"
                    } }.single()
                    val method = target.getMethodInstance(loader)
                    require(method.parameterCount == 1 &&
                        method.parameterTypes[0].name == "kotlin.coroutines.jvm.internal.ContinuationImpl")
                    val unit = hostUnit(dex, loader)
                    install(method) { chain -> if (on(GameAssistantSettings.PROMOTIONS)) unit else chain.proceed() }
                    deoptCallers(target, loader)
                }
                adapter("Startup animation") {
                    val target = dex.findMethod { matcher {
                        usingStrings("Creating startup animation")
                        name = "invokeSuspend"; returnType = "java.lang.Object"; paramTypes("java.lang.Object")
                    } }.single()
                    require(target.className.endsWith("StartupAnimationFeature\$initialize\$2"))
                    val unit = hostUnit(dex, loader)
                    install(target.getMethodInstance(loader)) { chain ->
                        if (on(GameAssistantSettings.REALME_STARTUP_ANIMATION)) unit else chain.proceed()
                    }
                }
                adapter("Root safety state") { rootSafety(dex, loader) }
                adapter("Sidebar application limit") { sidebar(dex, loader) }
                adapter("Sidebar bookmark limit") { bookmarks(dex, loader) }
                adapter("Music whitelist") { music(dex, loader) }
                adapter("Developer entry callers") {
                    val target = dex.findMethod { matcher {
                        declaredClass = "business.gamespace.service.impl.setting.SettingAssistantImpl"
                        name = "jumpDeveloperMode"; paramTypes(); returnType = "void"
                    } }.single()
                    deoptCallers(target, loader, twoLevels = true)
                }
            }
        }
    }

    private fun on(key: String) = GameAssistantSettings.enabledXposed(key)

    private fun install(method: Method, hook: (XposedInterface.Chain) -> Any?) {
        ModernHookRegistry.installFast("$TAG:current:${method.toGenericString()}", method, XposedInterface.Hooker(hook))
        deopt(method)
        HookLog.i(TAG, "Installed ${method.declaringClass.name}.${method.name}")
    }

    private fun deopt(method: Method) {
        runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
            .onFailure { HookLog.w(TAG, "Caller deoptimization unavailable: ${method.name}", it) }
    }

    private fun deoptCallers(data: MethodData, loader: ClassLoader, twoLevels: Boolean = false) {
        val callers = data.callers.distinctBy { it.descriptor }
        (callers + if (twoLevels) callers.flatMap { it.callers } else emptyList())
            .distinctBy { it.descriptor }.forEach { call ->
                runCatching { call.getMethodInstance(loader) }.getOrNull()?.let(::deopt)
            }
    }

    private fun hostUnit(dex: DexKitBridge, loader: ClassLoader): Any {
        val owner = dex.findMethod { matcher {
            usingStrings("kotlin.Unit"); name = "toString"; returnType = "java.lang.String"; paramTypes()
        } }.single().getMethodInstance(loader).declaringClass
        return owner.declaredFields.single { Modifier.isStatic(it.modifiers) && it.type == owner }
            .apply { isAccessible = true }.get(null)!!
    }

    private fun rootSafety(dex: DexKitBridge, loader: ClassLoader) {
        val target = dex.findMethod { matcher {
            usingStrings("getSupportCoolEx bundle = "); returnType = "android.os.Bundle"; paramTypes()
        } }.single()
        install(target.getMethodInstance(loader)) { chain ->
            val result = chain.proceed()
            if (on(GameAssistantSettings.BLOCK_ROOT) && result is Bundle && result.getInt("isSafe") == 1)
                Bundle(result).apply { putInt("isSafe", 0) } else result
        }
        deoptCallers(target, loader)
        dex.findMethod { matcher {
            name = "safetyStatusChange"; returnType = "void"; paramTypes("java.lang.String", "int")
        } }.filter { it.className.startsWith("business.module.gamefilter.") }.forEach { callback ->
            install(callback.getMethodInstance(loader)) { chain ->
                val args = chain.args.toTypedArray()
                args[1] = GameAssistantRules.safetyStatus(on(GameAssistantSettings.BLOCK_ROOT), args[1] as Int)
                chain.proceed(args)
            }
            deoptCallers(callback, loader)
        }
        ModernReflect.findClassIfExists("com.platform.usercenter.tools.device.UCDeviceInfoUtil", loader)?.let { type ->
            install(type.getDeclaredMethod("isRoot")) { chain ->
                if (on(GameAssistantSettings.BLOCK_ROOT)) false else chain.proceed()
            }
        }
    }

    private fun sidebar(dex: DexKitBridge, loader: ClassLoader) {
        val add = dex.findMethod { matcher {
            usingStrings("addApp size: "); returnType = "boolean"
            paramTypes("business.gamedock.tiles.o1", "boolean")
        } }.single()
        val guard = add.invokes.distinctBy { it.descriptor }.single {
            it.className == add.className && it.returnTypeName == "boolean" && it.paramTypeNames == listOf("boolean")
        }
        install(guard.getMethodInstance(loader)) { chain ->
            if (on(GameAssistantSettings.UNLIMITED)) false else chain.proceed()
        }
        deoptCallers(guard, loader)
    }

    private fun bookmarks(dex: DexKitBridge, loader: ClassLoader) {
        val add = dex.findMethod { matcher {
            usingStrings("addApp size: "); returnType = "boolean"
            paramTypes("business.mainpanel.bookmarks.data.CustomServiceBean")
        } }.single()
        val getter = add.invokes.distinctBy { it.descriptor }.single {
            it.className == add.className && it.paramCount == 0 && it.returnTypeName != "boolean"
        }.getMethodInstance(loader).apply { isAccessible = true }
        val list = add.usingFields.map { it.field }.distinctBy { it.descriptor }
            .single { it.typeName == CopyOnWriteArrayList::class.java.name }.getFieldInstance(loader)
            .apply { isAccessible = true }
        install(CopyOnWriteArrayList::class.java.getDeclaredMethod("size")) { chain ->
            val size = chain.proceed() as Int
            val scope = bookmarkGuard.get()
            if (scope != null && scope.list === chain.thisObject) {
                scope.reads++
                GameAssistantCurrentRules.bookmarkSize(on(GameAssistantSettings.UNLIMITED), scope.reads, size)
            } else size
        }
        install(add.getMethodInstance(loader)) { chain ->
            if (!on(GameAssistantSettings.UNLIMITED)) return@install chain.proceed()
            val adapter = getter.invoke(chain.thisObject)
            val items = list.get(adapter) ?: return@install chain.proceed()
            val previous = bookmarkGuard.get()
            bookmarkGuard.set(BookmarkGuard(items))
            try { chain.proceed() } finally {
                if (previous == null) bookmarkGuard.remove() else bookmarkGuard.set(previous)
            }
        }
        deoptCallers(add, loader)
    }

    private fun music(dex: DexKitBridge, loader: ClassLoader) {
        val resolver = dex.findMethod { matcher { usingStrings("whitelistedPackages loaded size="); paramTypes() } }.single()
        val cache = resolver.usingFields.map { it.field }.distinctBy { it.descriptor }
            .filter { it.typeName == "java.util.Set" && it.className != resolver.className }
            .map { it.getFieldInstance(loader) }.single { Modifier.isStatic(it.modifiers) }
            .apply { isAccessible = true }
        val config = resolver.invokes.distinctBy { it.descriptor }.single {
            it.returnTypeName == "java.lang.Object" && it.paramCount == 3 &&
                it.paramTypeNames.take(2) == listOf("java.lang.String", "java.util.Map")
        }
        val names = dex.findMethod { matcher { usingStrings("media_controller_config"); returnType = "java.util.List"; paramTypes() } }
            .single()
        val controllers = dex.findMethod { matcher {
            usingStrings("getMediaAppsFromControllers begin sessionCount=")
            paramTypes("java.util.Collection", "android.content.pm.PackageManager", "android.content.res.Resources")
        } }.single()
        val lock = Any()
        val wrapped = IdentityHashMap<Any, Any>()
        fun wrap(stock: Any, type: Class<*>): Any = synchronized(lock) {
            if (wrapped.containsKey(stock)) stock else membership(loader, stock, type).also { wrapped[it] = stock }
        }
        HotReloadLifecycle.register("$TAG:current-music", detach = {
            synchronized(lock) { wrapped[cache.get(null)]?.let { cache.set(null, it) }; wrapped.clear() }
        })
        install(config.getMethodInstance(loader)) { chain ->
            val stock = chain.proceed()
            if (musicScope.get() == true && on(GameAssistantSettings.MUSIC) &&
                chain.args[0] == "media_controller_config" && stock is Set<*>) wrap(stock, Set::class.java) else stock
        }
        install(resolver.getMethodInstance(loader)) { chain ->
            val previous = musicScope.get()
            musicScope.set(true)
            try {
                if (on(GameAssistantSettings.MUSIC)) synchronized(lock) {
                    (cache.get(null) as? Set<*>)?.let { stock -> cache.set(null, wrap(stock, Set::class.java)) }
                }
                chain.proceed()
            } finally { if (previous == null) musicScope.remove() else musicScope.set(previous) }
        }
        install(names.getMethodInstance(loader)) { chain ->
            val stock = chain.proceed()
            if (stock is List<*> && musicScope.get() == true && on(GameAssistantSettings.MUSIC))
                membership(loader, stock, List::class.java) else stock
        }
        install(controllers.getMethodInstance(loader)) { chain ->
            val previous = musicScope.get()
            musicScope.set(true)
            try { chain.proceed() } finally { if (previous == null) musicScope.remove() else musicScope.set(previous) }
        }
        deoptCallers(resolver, loader)
        deoptCallers(names, loader)
        deoptCallers(controllers, loader)
    }

    private fun membership(loader: ClassLoader, stock: Any, type: Class<*>): Any =
        Proxy.newProxyInstance(stock.javaClass.classLoader ?: loader, arrayOf(type)) { _, method, args ->
            when (method.name) {
                "contains" -> HotReloadLifecycle.withHook(
                    fallback = { method.invoke(stock, *(args ?: emptyArray())) },
                    action = { GameAssistantRules.musicMembership(on(GameAssistantSettings.MUSIC), args?.firstOrNull() as? String,
                        method.invoke(stock, *(args ?: emptyArray())) == true) })
                else -> method.invoke(stock, *(args ?: emptyArray()))
            }
        }
}

internal object GameAssistantCurrentRules {
    fun bookmarkSize(enabled: Boolean, read: Int, actual: Int): Int =
        if (enabled && read == 2) actual.coerceAtMost(7) else actual
}
