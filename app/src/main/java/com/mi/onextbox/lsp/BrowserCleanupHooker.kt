package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import java.lang.reflect.Method
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData

internal object BrowserCleanupHooker {
    private const val TAG = "BrowserCleanup"
    private data class Target(val method: Method, val key: String, val result: Boolean)
    fun hook(loader: ClassLoader, apkPath: String) {
        val stateKey = "$TAG:targets-v2"
        val restored = DexTargetCache.restoredState(stateKey) as? Map<*, *>
        val targets = restored?.entries?.flatMap { (id, value) ->
            val parts = (id as String).split('|')
            (value as Array<*>).filterIsInstance<Method>().map { Target(it, parts[0], parts[1].toBooleanStrict()) }
        } ?: ApkDexSession.withSession(apkPath) { open ->
            val dex by lazy(open)
            val selected = mutableListOf<Target>()
            fun add(key: String, value: Boolean, resolve: (DexKitBridge) -> List<MethodData>) {
                runCatching {
                    val methods = resolve(dex)
                    require(methods.isNotEmpty())
                    methods.forEach { selected += Target(it.getMethodInstance(loader), key, value) }
                    val callers = methods.flatMap { it.callers }.let { it + it.flatMap { m -> m.callers } }
                    callers.filter { it.isMethod && it.className.startsWith("com.heytap.browser.") }.distinctBy { it.descriptor }
                        .forEach { runCatching { ModernHookRuntime.requireModule().deoptimize(it.getMethodInstance(loader)) } }
                }.onFailure { HookLog.w(TAG, "$key interfaces unavailable; native behavior retained", it) }
            }
            fun preference(key: String, option: String, value: Boolean) = add(option, value) { bridge ->
                bridge.findMethod { matcher {
                    declaredClass = "com.heytap.browser.platform.settings.BaseSettings"
                    returnType = "boolean"; paramCount = 0; usingStrings(key)
                } }.toList()
            }
            preference("pref_setting_simple_mode", "browser_simple_mode", true)
            listOf("pref.key.advertisement.splash", "pref.key.advertisement.iflow", "pref.key.advertisement.shake")
                .forEach { preference(it, "browser_remove_ads", false) }
            preference("key_block_advertisement", "browser_web_adblock", true)
            preference("key_block_advertisement_toast", "browser_hide_adblock_toast", false)
            listOf("preference.key.video_floating_hint", "preference.key.video_slide_left_guide", "pref_key_mix_auto_play_guide_shown")
                .forEach { preference(it, "browser_hide_video_guide", it.endsWith("shown")) }
            add("browser_web_adblock", true) { bridge ->
                val consumer = bridge.findMethod { matcher {
                    declaredClass = "com.heytap.browser.api.webpage.web.q"; usingStrings("isBlockAdvertisementEnabled: %b")
                } }.single()
                listOf(consumer.invokes.single { it.className == "com.heytap.browser.platform.settings.BaseSettings" && it.returnTypeName == "boolean" && it.paramCount == 0 })
            }
            add("browser_hide_adblock_toast", false) { bridge ->
                listOf(bridge.findMethod { matcher { declaredClass = "com.heytap.browser.platform.settings.BaseSettings"; name = "V1"; returnType = "boolean"; paramCount = 0 } }.single())
            }
            add("browser_hide_push_guide", false) { bridge ->
                bridge.findMethod { matcher {
                    returnType = "boolean"; usingStrings("checkOpenPushPermissionDialog allSceneUserDialogCount=%s")
                    paramCount = 4
                } }.toList()
            }
            selected
        }
        targets.forEach { target ->
            ModernHookRegistry.installFast("$TAG:${target.key}:${target.method}", target.method) { chain ->
                if (BrowserSettings.active(target.key)) target.result else chain.proceed()
            }
        }
        HotReloadLifecycle.register(stateKey, capture = { targets.groupBy { "${it.key}|${it.result}" }.mapValues { (_, values) -> values.map { it.method }.toTypedArray() } })
        HookLog.i(TAG, "Installed ${targets.size} browser controls")
    }
}
