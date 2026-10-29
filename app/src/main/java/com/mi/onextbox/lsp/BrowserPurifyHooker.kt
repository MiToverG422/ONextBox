package com.mi.onextbox.lsp

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap

internal object BrowserPurifyHooker {
    private const val TAG = "BrowserPurify"
    private const val VIEW_STATE = "$TAG:views-v2"
    private const val TAB_SETTING = "com.heytap.browser.platform.settings.DefaultTabSetting"
    private const val ENGINES = "com.heytap.browser.search.impl.engine.DefaultSearchEngines"
    private const val MY = "com.heytap.browser.usercenter.my."
    private val hidden = WeakHashMap<View, Int>()
    private val roots = WeakHashMap<View, Boolean>()
    private val forced = WeakHashMap<View, String>()
    private var modernTabs = false
    private var installed = 0

    fun hook(loader: ClassLoader) {
        hookTabs(loader)
        hookSearch(loader)
        hookAds(loader)
        hookViews(loader)
        val imported = HotReloadLifecycle.restoredState(VIEW_STATE)
        val importedRoots = if (imported is Map<*, *>) imported["roots"] else imported
        (importedRoots as? Array<*>)?.filterIsInstance<View>()?.forEach { roots[it] = true }
        ((imported as? Map<*, *>)?.get("forced") as? Map<*, *>)?.forEach { (view, key) ->
            if (view is View && key is String) forced[view] = key
        }
        HotReloadLifecycle.register(VIEW_STATE,
            capture = { linkedMapOf("roots" to roots.keys.toTypedArray(), "forced" to java.util.HashMap(forced)) },
            detach = { hidden.toMap().forEach { (view, visibility) -> view.visibility = visibility }; hidden.clear() },
            restore = { roots.keys.toList().forEach(::applyTree); forced.toMap().forEach { (view, key) -> hide(view, key, key == "hide_ai_logo") } })
        HotReloadLifecycle.afterCommit {
            roots.keys.toList().forEach(::applyTree)
            forced.toMap().forEach { (view, key) -> hide(view, key, key == "hide_ai_logo") }
        }
        HookLog.i(TAG, "Installed $installed purification interfaces")
    }

    private fun on(key: String) = BrowserSettings.active("browser_$key")
    private fun method(type: Class<*>, name: String, vararg params: Class<*>): Method =
        type.getDeclaredMethod(name, *params).apply { isAccessible = true }

    private fun call(receiver: Any, name: String, vararg args: Any?): Any? {
        val candidates = generateSequence(receiver.javaClass as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .filter { it.name == name && it.parameterCount == args.size && it.parameterTypes.indices.all { i ->
                args[i] == null || it.parameterTypes[i].isInstance(args[i]) ||
                    (it.parameterTypes[i] == Boolean::class.javaPrimitiveType && args[i] is Boolean)
            } }.toList()
        val found = candidates.firstOrNull() ?: error("Missing $name/${args.size}")
        found.isAccessible = true
        return found.invoke(receiver, *args)
    }

    private fun field(receiver: Any, name: String): Any? = generateSequence(receiver.javaClass as Class<*>?) { it.superclass }
        .mapNotNull { runCatching { it.getDeclaredField(name) }.getOrNull() }.first()
        .apply { isAccessible = true }.get(receiver)
    private fun attach(label: String, resolve: () -> Method, action: (XposedInterface.Chain) -> Any?) {
        runCatching {
            val target = resolve()
            ModernHookRuntime.requireModule().deoptimize(target)
            ModernHookRegistry.installFast("$TAG:$label", target) { chain -> action(chain) }
            installed++
        }.onFailure { HookLog.w(TAG, "$label interface unavailable; native behavior retained", it) }
    }
    private fun after(label: String, resolve: () -> Method, action: (XposedInterface.Chain) -> Unit) = attach(label, resolve) { chain ->
        val result = chain.proceed()
        runCatching { action(chain) }.onFailure { HookLog.w(TAG, "$label view update failed", it) }
        result
    }

    private fun hookTabs(loader: ClassLoader) {
        val manager = runCatching { loader.loadClass("com.heytap.browser.trait.tab.BottomTabManager") }.getOrNull() ?: return
        modernTabs = manager.declaredMethods.any { it.name == "l" && it.parameterCount == 0 && List::class.java.isAssignableFrom(it.returnType) }
        attach("tabs", { manager.declaredMethods.single { it.parameterCount == 0 && List::class.java.isAssignableFrom(it.returnType) } }) { chain ->
            val original = chain.proceed() as? List<*> ?: return@attach null
            BrowserPurifyRules.filterTabs(original, { tab ->
                runCatching { call(tab!!, if (modernTabs) "k" else "j") as? Int }.getOrNull()
            }, on("hide_home_tab"), on("hide_video_tab"), on("hide_novel_tab"))
        }
        attach("default-tab", { loader.loadClass(TAB_SETTING).declaredMethods.single { it.parameterCount == 0 && it.returnType.name == "$TAB_SETTING\$b" } }) { chain ->
            val original = chain.proceed()
            if (!on("hide_home_tab")) return@attach original
            runCatching {
                val tabs = call(chain.thisObject!!, "g") as? List<*> ?: return@runCatching original
                tabs.firstOrNull { item -> (call(item!!, "c") as? Int) in setOf(4, 9) } ?: original
            }.getOrDefault(original)
        }
        val home = runCatching { loader.loadClass("com.heytap.browser.main.home.normal.NormalHome") }.getOrNull() ?: return
        val pageGetter = if (modernTabs) "U0" else "W0"
        val setter = if (modernTabs) "s2" else "m2"
        listOf(if (modernTabs) "x2" else "s2", if (modernTabs) "C2" else "p2").forEachIndexed { index, name ->
            attach("home-$name", { home.declaredMethods.single { it.name == name && it.parameterCount == index } }) { chain ->
                if (!on("hide_home_tab")) return@attach chain.proceed()
                val redirected = runCatching {
                    val page = call(chain.thisObject!!, pageGetter) ?: return@runCatching false
                    call(chain.thisObject!!, setter, page)
                    true
                }.getOrDefault(false)
                if (redirected) { if ((chain.executable as Method).returnType == Boolean::class.javaPrimitiveType) true else null }
                else chain.proceed()
            }
        }
        attach("default-tab-choice", { method(loader.loadClass(TAB_SETTING), "i", String::class.java) }) { chain ->
            val isHome = on("hide_home_tab") && runCatching {
                (call(chain.thisObject!!, "g") as List<*>).any { item ->
                    call(item!!, "c") == 1 && call(call(item, "b")!!, "a") == chain.args[0]
                }
            }.getOrDefault(false)
            if (isHome) false else chain.proceed()
        }
    }

    private fun engineInstance(loader: ClassLoader): Any {
        val type = loader.loadClass(ENGINES)
        val companion = type.declaredFields.first { Modifier.isStatic(it.modifiers) && it.type.name.startsWith("$ENGINES\$") }
            .apply { isAccessible = true }.get(null)
        return companion.javaClass.declaredMethods.first { it.parameterCount == 0 && it.returnType == type }
            .apply { isAccessible = true }.invoke(companion)!!
    }
    private fun engines(loader: ClassLoader, instance: Any): List<Any> {
        val helperClass = loader.loadClass("com.heytap.browser.search.impl.engine.m")
        val helper = checkNotNull(helperClass.getDeclaredField("a").get(null))
        return BrowserPurifyRules.engineKeys.map { key ->
            val resource = call(helper, "c", key) ?: error("Missing engine $key")
            call(instance, "T", resource) ?: error("Cannot create engine $key")
        }
    }
    private fun hookSearch(loader: ClassLoader) {
        attach("engine-list", { method(loader.loadClass(ENGINES), "n", Boolean::class.javaPrimitiveType!!) }) { chain ->
            if (on("pure_search")) runCatching { engines(loader, chain.thisObject!!) }.getOrElse { chain.proceed() } else chain.proceed()
        }
        attach("engine-current", { method(loader.loadClass(ENGINES), "J") }) { chain ->
            if (!on("pure_search")) return@attach chain.proceed()
            runCatching {
                val instance = chain.thisObject!!
                val key = BrowserPurifyRules.currentEngineKey(call(instance, "G") as? String)
                engines(loader, instance).single { call(it, "getKey") == key }
            }.getOrElse { chain.proceed() }
        }
        attach("search-channels", { loader.loadClass("com.heytap.browser.ai.page.multi.ui.channel.MultiSearchTabLayout").declaredMethods.single {
            it.parameterTypes.contentEquals(arrayOf(List::class.java, Boolean::class.javaPrimitiveType, Context::class.java))
        } }) { chain ->
            if (!on("replace_top_channel")) return@attach chain.proceed()
            val replacement = runCatching { engines(loader, engineInstance(loader)).let { listOf(it[2], it[0], it[1]) } }.getOrNull()
                ?: return@attach chain.proceed()
            chain.proceed(chain.args.toTypedArray().also { it[0] = replacement })
        }
        attach("search-url", { loader.loadClass("com.heytap.browser.ai.page.multi.MultiSearchInnerPage").declaredMethods.single {
            it.returnType == String::class.java && it.parameterTypes.map { p -> p.name } == listOf("com.heytap.browser.api.search.engine.c", "java.lang.String", "java.lang.String", "java.lang.String")
        } }) { chain ->
            if (!on("replace_top_channel")) return@attach chain.proceed()
            val url = runCatching {
                val key = runCatching { call(chain.args[0]!!, "getKey") as? String }.getOrNull()
                    ?.takeIf { it in BrowserPurifyRules.engineKeys }
                    ?: if (!modernTabs) when (call(field(chain.thisObject!!, "b")!!, "getIndex")) { 1 -> "bing_ov"; 2 -> "google"; else -> null } else null
                if (key == null) return@runCatching null
                val target = engines(loader, engineInstance(loader)).single { call(it, "getKey") == key }
                call(target, "m", chain.args[1]) as? String
            }.getOrNull()
            url ?: chain.proceed()
        }
    }

    private fun hookAds(loader: ClassLoader) {
        attach("ad-sdk", { loader.loadClass("com.bytedance.sdk.openadsdk.TTAdSdk").declaredMethods.single {
            it.name == "init" && it.parameterTypes.map { p -> p.name } == listOf("android.content.Context", "com.bytedance.sdk.openadsdk.TTAdConfig")
        } }) { chain ->
            if (on("remove_ads")) { if ((chain.executable as Method).returnType == Boolean::class.javaPrimitiveType) true else null }
            else chain.proceed()
        }
    }

    private fun hide(view: View, key: String, invisible: Boolean = false) {
        forced[view] = key
        if (!on(key)) return
        hidden.putIfAbsent(view, view.visibility)
        view.visibility = if (invisible) View.INVISIBLE else View.GONE
    }
    private fun hideField(root: Any, name: String, key: String, invisible: Boolean = false) {
        (runCatching { field(root, name) }.getOrNull() as? View)?.let { hide(it, key, invisible) }
    }
    private fun typeKey(view: View): String? = when (view.javaClass.name) {
        "${MY}MyProfileBrowserFunctionContainer" -> "hide_my_functions"
        "${MY}DefaultFunctionContainer" -> "hide_my_night"
        "${MY}banner.MyProfileBanners" -> "hide_my_card"
        "${MY}bookshelf.MyBookshelfHistoryCard" -> "hide_my_novel"
        "${MY}documentation.MyDocCard" -> "hide_my_tools"
        "com.heytap.browser.download.ui.downloadlist.view.StorageCleanView" -> "hide_dl_clean"
        "com.heytap.browser.ai.page.ai.view.AiQueryButtonView" -> "hide_ai_input"
        else -> null
    }
    private fun applyTree(root: View) {
        roots[root] = true
        fun walk(view: View) {
            typeKey(view)?.let { hide(view, it) }
            if (generateSequence(view.javaClass as Class<*>?) { it.superclass }.any { it.name == "com.platform.sdk.center.widget.HeyTapAccountInfoView" }) {
                hideField(view, "mHayTapPlateAndIcon", "hide_my_badge")
                hideField(view, "mBtnSignIn", "hide_my_cash")
                hideField(view, "mImageBtnSignIn", "hide_my_cash")
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) walk(view.getChildAt(index))
        }
        walk(root)
        if (root.javaClass.name == "${MY}MyProfileView") {
            val ids = if (modernTabs) mapOf("cie" to "hide_my_message", "cin" to "hide_my_scan",
                "a8v" to "hide_my_cash", "da3" to "hide_my_cash", "c5n" to "hide_my_badge")
                else mapOf("e59" to "hide_my_message", "e4s" to "hide_my_scan", "wz" to "hide_my_tools")
            ids.forEach { (id, key) ->
                val value = root.resources.getIdentifier(id, "id", BrowserSettings.PACKAGE_NAME)
                if (value != 0) root.findViewById<View>(value)?.let { hide(it, key) }
            }
            if (modernTabs) hideField(root, "q", "hide_my_tools")
        }
        if (root.javaClass.name == "com.heytap.browser.ai.page.ai.vision.AiVisionMainViewContainer") hideField(root, "b", "hide_ai_logo", true)
    }
    private fun hookViews(loader: ClassLoader) {
        attach("visibility", { method(View::class.java, "setVisibility", Int::class.javaPrimitiveType!!) }) { chain ->
            val view = chain.thisObject as? View ?: return@attach chain.proceed()
            if (chain.args[0] != View.VISIBLE) return@attach chain.proceed()
            val key = forced[view] ?: typeKey(view) ?: return@attach chain.proceed()
            if (!on(key)) return@attach chain.proceed()
            hidden.putIfAbsent(view, View.VISIBLE)
            chain.proceed(arrayOf(if (key == "hide_ai_logo") View.INVISIBLE else View.GONE))
        }
        listOf("${MY}MyProfileView", "com.heytap.browser.ai.page.ai.vision.AiVisionMainViewContainer").forEach { name ->
            val type = runCatching { loader.loadClass(name) }.getOrNull() ?: return@forEach
            val attach = generateSequence(type as Class<*>?) { it.superclass }.mapNotNull { c -> c.declaredMethods.firstOrNull { it.name == "onAttachedToWindow" && it.parameterCount == 0 } }.firstOrNull()
            after("attach-$name", { attach ?: error("Missing attachment") }) { chain ->
                (chain.thisObject as? View)?.takeIf { it.javaClass.name == name }?.let(::applyTree)
            }
        }
        after("download-view", { method(loader.loadClass("com.heytap.browser.download.ui.downloadlist.fragment.DownloadManagerFragment"), "onViewCreated", View::class.java, android.os.Bundle::class.java) }) { chain ->
            (chain.args[0] as? View)?.let(::applyTree)
        }
        after("home-settings", { method(loader.loadClass("com.heytap.browser.settings.homepage.HomeTabPreferenceFragment"), "initView", View::class.java) }) { chain ->
            val fields = if (modernTabs) listOf("n", "p", "q", "r") else listOf("m", "p", "q")
            fields.forEach { hideField(chain.thisObject!!, it, "hide_home_tab") }
        }
        listOf("setShowLogoView" to "hide_my_badge", "setShowSignButton" to "hide_my_cash").forEach { (name, key) ->
            attach(name, { method(loader.loadClass("com.platform.sdk.center.widget.HeyTapAccountInfoView"), name, Boolean::class.javaPrimitiveType!!) }) { chain ->
                if (on(key)) chain.proceed(arrayOf(false)) else chain.proceed()
            }
        }
        val agent = "com.heytap.browser.ai.page.ai.vision.agent.b"
        listOf("e" to 1, "f" to 3).forEach { (name, cornerIndex) ->
            after("agent-$name", { loader.loadClass(agent).declaredMethods.single { it.name == name && it.parameterCount == cornerIndex + 1 } }) { chain ->
                (chain.args[cornerIndex] as? View)?.let { hide(it, "hide_ai_corner") }
            }
        }
    }
}
