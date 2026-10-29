package com.mi.onextbox.lsp

import android.os.Handler
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import com.mi.onextbox.lsp.compat.ModernHookRuntime

internal object EngineerModeHooker {
    private const val TAG = "EngineerMode"
    fun hook(loader: ClassLoader, apkPath: String) {
        fun install(type: String, name: String, action: (io.github.libxposed.api.XposedInterface.Chain) -> Any?) {
            runCatching {
                val methods = loader.loadClass(type).declaredMethods.filter { it.name == name && !it.isBridge }
                require(methods.isNotEmpty())
                methods.forEach { ModernHookRegistry.installFast("$TAG:$it", it, action) }
                HookLog.i(TAG, "$type.$name hooked")
            }.onFailure { HookLog.w(TAG, "$type.$name unavailable", it) }
        }
        install("com.oplus.engineermode.security.view.WriteKeyItemView", "initKeyStatus") { chain ->
            val mode = chain.args.firstOrNull()?.let { EngineerKeySettings.mode(it.javaClass.name) } ?: 0
            val what = EngineerDisplayRules.message(mode)
            if (what == null) chain.proceed() else {
                val handler = ModernReflect.getObjectField(chain.thisObject!!, "initKeyStatusHandler") as Handler
                (ModernReflect.getObjectField(chain.thisObject!!, "progressBar") as android.view.View).visibility = android.view.View.GONE
                handler.sendEmptyMessage(what)
                null
            }
        }
        install("com.oplus.engineermode.security.RPMBStatusActivity", "loadKeyStatus") { chain ->
            val mode = chain.args.firstOrNull()?.let { EngineerKeySettings.mode(it.javaClass.name) } ?: 0
            val resource = EngineerDisplayRules.statusResource(mode)
            val activity = chain.thisObject as android.app.Activity
            val label = chain.args.getOrNull(1) as? android.widget.TextView
            val id = resource?.let { activity.resources.getIdentifier(it, "string", SystemExtrasSettings.ENGINEER) } ?: 0
            if (id == 0 || label == null || activity.isFinishing) chain.proceed() else {
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        label.setTextColor(if (mode == 1) android.graphics.Color.RED else android.graphics.Color.GREEN)
                        label.setText(id)
                    }
                }
                null
            }
        }
        install("com.oplus.engineermode.entrance.EngineerFragmentCompat", "removeUnnecessaryPreference") { chain ->
            if (SystemExtrasSettings.active(SystemExtrasSettings.ENGINEER_HIDDEN)) null else chain.proceed()
        }
        install("androidx.preference.Preference", "setVisible") { chain ->
            if (SystemExtrasSettings.active(SystemExtrasSettings.ENGINEER_HIDDEN)) chain.proceed(arrayOf(true)) else chain.proceed()
        }
        install("com.oplus.engineermode.modeltest.ModelTestItemParser", "checkFeatureSupport") { chain ->
            if (SystemExtrasSettings.active(SystemExtrasSettings.ENGINEER_HIDDEN)) true else chain.proceed()
        }
        install("com.oplus.engineermode.modeltest.ModelTestItemParser", "parseModelTestItem") { chain ->
            if (!SystemExtrasSettings.active(SystemExtrasSettings.ENGINEER_HIDDEN)) chain.proceed() else {
                val parser = chain.args[0] as android.content.res.XmlResourceParser
                fun attr(name: String) = parser.getAttributeValue(null, name)
                ModernReflect.newInstance(loader.loadClass("com.oplus.engineermode.modeltest.ModelTestItem"),
                    attr("name"), attr("index"), attr("markposition"), attr("platform"), attr("region"), attr("feature"), attr("title"), null)
            }
        }
        runCatching {
            ApkDexSession.withSession(apkPath) { open ->
                listOf(
                    "com.oplus.engineermode.security.view.WriteKeyItemView" to "initKeyStatus",
                    "com.oplus.engineermode.security.RPMBStatusActivity" to "loadKeyStatus",
                    "com.oplus.engineermode.entrance.EngineerFragmentCompat" to "removeUnnecessaryPreference",
                    "androidx.preference.Preference" to "setVisible",
                ).forEach { (type, method) ->
                    open().findMethod { matcher { declaredClass = type; name = method } }
                        .flatMap { it.callers }.filter { it.isMethod && it.className.startsWith("com.oplus.engineermode.") }
                        .distinctBy { it.descriptor }.forEach {
                            runCatching { ModernHookRuntime.requireModule().deoptimize(it.getMethodInstance(loader)) }
                        }
                }
            }
        }.onFailure { HookLog.w(TAG, "Engineer UI caller deoptimization unavailable", it) }
    }
}

internal object EngineerDisplayRules {
    // 0 = original; 1 = failure; 2 = success; 3 = unsupported.
    fun message(mode: Int): Int? = when (mode) { 1 -> 1; 2 -> 0; 3 -> 2; else -> null }
    fun statusResource(mode: Int): String? = when (mode) {
        1 -> "key_status_fail"
        2 -> "key_status_pass"
        3 -> "key_status_not_support"
        else -> null
    }
}
