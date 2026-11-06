package com.mi.onextbox.lsp

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

internal object ThemeStoreAdRules {
    private val adTypes = setOf(
        "com.oppo.cdo.card.theme.dto.ad.AdCardDto",
        "com.oppo.cdo.card.theme.dto.ad.template.TemplateAdCardDTO",
        "com.oppo.cdo.card.theme.dto.SearchBannerAdCardDto",
        "com.oppo.cdo.card.theme.dto.GoodsAdCardDto",
    )
    val cardGetters = mapOf(
        "page.HomePageDto" to "getCards",
        "page.ViewLayerWrapDto" to "getCards",
        "AssembleCardDto" to "getCards",
        "RichMultiCardDto" to "getCards",
        "MultiTabCardDto" to "getTabs",
        "MultiTabCardDtoV2" to "getTabs",
        "RankListCardDto" to "getRankCards",
        "TopicListCardDto" to "getTopicList",
        "TopicListV2CardDto" to "getTopicList",
        "TabItemCardDto" to "getItems",
        "TabItemCardDtoV2" to "getItems",
    )
    fun isAdType(name: String) = name in adTypes
    fun <T> filterCards(source: List<T>, enabled: Boolean, isAd: (T) -> Boolean): List<T> {
        if (!enabled || source.none(isAd)) return source
        return source.filterNot(isAd)
    }
}

internal object ThemeStoreAdsHooker {
    private const val TAG = "ONextBox-ThemeStoreAds"
    private val delivered = ConcurrentHashMap.newKeySet<String>()
    private fun report(stage: String) {
        if (delivered.add(stage)) HookLog.i(TAG, "Advertising blocked: $stage")
    }

    private fun deoptimize(type: Class<*>?) {
        type?.declaredMethods?.forEach { method ->
            runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                .onFailure { if (delivered.add("deopt:${type.name}")) HookLog.w(TAG, "Ad caller deoptimization unavailable: ${type.name}", it) }
        }
    }

    private fun install(method: Method, callback: (ModernMethodHook.MethodHookParam) -> Unit) {
        ModernHookRegistry.installCompat("$TAG:${method.toGenericString()}", method, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (ThemeStoreSettings.adsEnabledXposed()) callback(param)
            }
        })
    }

    private fun hookSplash(loader: ClassLoader): Int {
        val manager = ModernReflect.findClassIfExists("com.nearme.themespace.ad.l", loader) ?: return 0
        val activity = ModernReflect.findClass("androidx.fragment.app.FragmentActivity", loader)
        val callback = ModernReflect.findClass("com.nearme.themespace.ad.h", loader)
        val splash = ModernReflect.findClass("com.oppo.cdo.card.theme.dto.SplashDto", loader)
        val handlerField = manager.declaredFields.single { it.type == Handler::class.java }.apply { isAccessible = true }
        val callbackField = manager.declaredFields.single { it.type == callback }.apply { isAccessible = true }
        val showEmpty = manager.getDeclaredMethod("J", splash).apply { isAccessible = true }
        // Reuse the application's lifecycle Runnable and message dispatcher. No module
        // callback is retained by its Handler, and completion stays on the host thread.
        val lifecycleTask = ModernReflect.findClass("com.nearme.themespace.ad.l\$c", loader)
            .getDeclaredConstructor(manager, activity).apply { isAccessible = true }
        val request = manager.getDeclaredMethod("a", activity, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        val show = manager.getDeclaredMethod("I", splash)
        install(show) { param ->
            val host = param.thisObject ?: return@install
            val handler = handlerField.get(host) as? Handler ?: return@install
            if (callbackField.get(host) == null) return@install
            if (Looper.myLooper() == handler.looper) {
                handler.removeMessages(4)
                // J(null) is the native no-ad branch: finish tracking and notify the
                // splash owner, rather than merely suppressing the view or its timer.
                showEmpty.invoke(host, null)
            } else if (!handler.sendEmptyMessage(4)) {
                return@install
            }
            param.result = null
            report("splash-display")
        }
        install(request) { param ->
            val host = param.thisObject ?: return@install
            val owner = param.args[0] ?: return@install
            val handler = handlerField.get(host) as? Handler ?: return@install
            if (callbackField.get(host) == null) return@install
            val task = lifecycleTask.newInstance(host, owner) as Runnable
            if (!handler.post(task)) return@install
            // Message 4 normally displays either SDK or cached/self advertising.
            // Route it immediately through the guarded native completion above.
            if (!handler.sendEmptyMessage(4)) return@install
            param.result = null
            report("splash-request")
        }
        deoptimize(manager)
        deoptimize(ModernReflect.findClassIfExists("com.nearme.themespace.ad.f", loader))
        deoptimize(ModernReflect.findClassIfExists("com.nearme.themespace.activities.ThemeActivity\$k", loader))
        return 2
    }

    private fun hookHomepagePopup(loader: ClassLoader): Int {
        val type = ModernReflect.findClassIfExists("com.nearme.themespace.fragments.i7", loader) ?: return 0
        val activity = ModernReflect.findClass("androidx.fragment.app.FragmentActivity", loader)
        val finish = type.getDeclaredMethod("A1", Activity::class.java).apply { isAccessible = true }
        val start = type.getDeclaredMethod("C1", activity, String::class.java, String::class.java)
        install(start) { param ->
            // The popup occupies a startup queue slot. Its native empty-data path
            // releases that slot and continues the remaining home-page tasks.
            finish.invoke(null, param.args[0])
            param.result = null
            report("homepage-popup")
        }
        deoptimize(type)
        deoptimize(ModernReflect.findClassIfExists("sn.b", loader))
        return 1
    }

    private fun hookPromotionPopup(loader: ClassLoader): Int {
        val type = sequenceOf("lo.a\$c", "xq.a\$c")
            .mapNotNull { ModernReflect.findClassIfExists(it, loader) }.firstOrNull() ?: return 0
        val dto = ModernReflect.findClass("com.oppo.cdo.theme.domain.dto.response.PromotionPopupDto", loader)
        install(type.getDeclaredMethod("b", dto)) { param ->
            // Run the native invalid-data branch to clear request state and deliver
            // completion. Payment processing and ordinary dialogs stay untouched.
            if (param.args[0] != null) {
                param.args[0] = null
                report("promotion-popup")
            }
        }
        deoptimize(type)
        return 1
    }

    fun hook(loader: ClassLoader) {
        var installed = 0
        listOf("splash" to ::hookSplash, "homepage popup" to ::hookHomepagePopup, "promotion popup" to ::hookPromotionPopup)
            .forEach { (name, installer) ->
                runCatching { installed += installer(loader) }
                    .onFailure { HookLog.w(TAG, "Ad route unavailable: $name", it) }
            }
        fun getter(className: String, name: String, replacement: (Class<*>) -> Any?) {
            runCatching {
                val type = ModernReflect.findClassIfExists(className, loader) ?: return@runCatching
                val method = type.getDeclaredMethod(name)
                val value = replacement(method.returnType)
                ModernHookRegistry.installFast("$TAG:${method.toGenericString()}", method, XposedInterface.Hooker { chain ->
                    if (ThemeStoreSettings.adsEnabledXposed()) { report("$className.$name"); value } else chain.proceed()
                })
                installed++
            }.onFailure { HookLog.w(TAG, "Ad getter unavailable: $className.$name", it) }
        }
        val splash = "com.oppo.cdo.card.theme.dto.SplashDto"
        getter(splash, "getAdData") { check(!it.isPrimitive); null }
        getter(splash, "getShowTime") { ThemeStoreHookRules.numericValue(it, 0) ?: error("Unsupported splash duration") }
        getter(splash, "getIsSkip") {
            check(it == Boolean::class.javaPrimitiveType || it == Boolean::class.javaObjectType)
            true
        }
        getter("com.oppo.cdo.card.theme.dto.page.HomePageDto", "getAdBanner") { check(!it.isPrimitive); null }

        // Filter concrete advertising cards, keeping ordinary banners, catalog
        // cards, paging metadata and the original response objects untouched.
        ThemeStoreAdRules.cardGetters.forEach { (name, getterName) ->
            runCatching {
                val type = ModernReflect.findClassIfExists("com.oppo.cdo.card.theme.dto.$name", loader) ?: return@runCatching
                val method: Method = type.getMethod(getterName)
                check(List::class.java.isAssignableFrom(method.returnType))
                ModernHookRegistry.installFast("$TAG:${method.toGenericString()}", method, XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    val source = result as? List<*> ?: return@Hooker result
                    val next = ThemeStoreAdRules.filterCards(source, ThemeStoreSettings.adsEnabledXposed()) { card ->
                        generateSequence<Class<*>>(card?.javaClass) { it.superclass }.any { ThemeStoreAdRules.isAdType(it.name) }
                    }
                    if (next !== source) report("cards:$name")
                    next
                })
                installed++
            }.onFailure { HookLog.w(TAG, "Ad card adapter unavailable: $name", it) }
        }
        HookLog.i(TAG, "Theme-store advertising hooks installed: $installed endpoints")
    }
}
