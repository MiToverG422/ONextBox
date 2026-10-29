package com.mi.onextbox.lsp

import android.app.Service
import android.app.job.JobParameters
import android.content.ContentResolver
import android.content.Intent
import android.os.Handler
import android.os.Bundle
import android.os.Message
import android.provider.Settings
import android.view.Menu
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import org.json.JSONObject
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URI

internal object SoftwareUpdateHooker {
    private const val TAG = "SoftwareUpdate"

    fun hook(loader: ClassLoader, apkPath: String) {
        val prefix = "$TAG@${System.identityHashCode(loader)}"
        @Suppress("UNCHECKED_CAST")
        val saved = DexTargetCache.restoredState("$prefix:targets") as? Map<String, Array<Method>>
        val targets = if (saved?.containsKey(SoftwareUpdateTargets.CACHE_SCHEMA) == true) saved else {
            val refreshed = runCatching {
                System.loadLibrary("dexkit")
                DexKitBridge.create(apkPath).use { SoftwareUpdateTargets(it).resolve(loader) }
            }.onFailure { HookLog.w(TAG, "Updater target resolution unavailable", it) }
            if (saved != null) check(refreshed.isSuccess) { "Updater target cache upgrade failed, restart the scope" }
            saved.orEmpty() + refreshed.getOrDefault(emptyMap())
        }
        HotReloadLifecycle.register("$prefix:targets", capture = { targets })

        fun enabled(feature: SoftwareUpdateFeature) = SoftwareUpdateSettings.enabledXposed(feature)
        fun install(name: String, target: Method, action: (XposedInterface.Chain) -> Any?) {
            ModernHookRegistry.installFast("$prefix:$name", target, XposedInterface.Hooker { action(it) })
            targets["callers:$name"].orEmpty().forEach {
                runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
            }
        }
        fun resolved(name: String, action: (XposedInterface.Chain) -> Any?) {
            runCatching { install(name, targets.getValue(name).single(), action) }
                .onFailure { HookLog.w(TAG, "$name unavailable, stock behavior retained", it) }
        }

        resolved("root") { chain -> if (enabled(SoftwareUpdateFeature.HideRoot)) false else chain.proceed() }
        val bootloaderScope = SoftwareUpdateBootloaderScope()
        resolved("bootloaderReport") { chain ->
            if (enabled(SoftwareUpdateFeature.HideBootloader)) true else chain.proceed()
        }
        resolved("bootloaderGuard") { chain ->
            if (enabled(SoftwareUpdateFeature.HideBootloader) && bootloaderScope.consumeOverride()) false else chain.proceed()
        }
        listOf("localPicker", "localPreflight").forEach { name ->
            resolved(name) { chain ->
                bootloaderScope.enter(enabled(SoftwareUpdateFeature.HideBootloader)) { chain.proceed() }
            }
        }
        resolved("report") { chain ->
            val hideRoot = enabled(SoftwareUpdateFeature.HideRoot)
            val hideBootloader = enabled(SoftwareUpdateFeature.HideBootloader)
            val blockFiles = enabled(SoftwareUpdateFeature.BlockSystemFiles)
            if (!hideRoot && !hideBootloader && !blockFiles) return@resolved chain.proceed()
            val input = chain.args.firstOrNull() as? JSONObject ?: return@resolved chain.proceed()
            val copy = runCatching {
                val text = input.toString()
                require(text.length <= 2_097_152)
                val report = SoftwareUpdateRules.maskSecurityReport(text, hideRoot, hideBootloader)
                JSONObject(SoftwareUpdateDiscoveryRules.disableMergedOpex(report, (chain.args.getOrNull(1) as? URI)?.path, blockFiles))
            }.onFailure { HookLog.w(TAG, "Security report rewrite failed, original request retained") }.getOrNull()
                ?: return@resolved chain.proceed()
            chain.proceed(chain.args.toTypedArray().also { it[0] = copy })
        }
        resolved("downloadGate") { chain -> if (enabled(SoftwareUpdateFeature.DisableAutoDownload)) false else chain.proceed() }
        resolved("nightGate") { chain -> if (enabled(SoftwareUpdateFeature.DisableNightInstall)) false else chain.proceed() }
        runCatching {
            val target = targets.getValue("versionQuery").single()
            val fields = target.declaringClass.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
            val query = fields.single { it.type == String::class.java && Modifier.isFinal(it.modifiers) }.apply { isAccessible = true }
            val handler = fields.single { Handler::class.java.isAssignableFrom(it.type) }.apply { isAccessible = true }
            install("versionQuery", target) { chain ->
                if (SoftwareUpdateDiscoveryRules.blocksVersionResult(query.get(chain.thisObject) as? String,
                        enabled(SoftwareUpdateFeature.BlockSystemUpdate))) {
                    val callback = handler.get(chain.thisObject) as? Handler ?: return@install chain.proceed()
                    if (callback.sendEmptyMessage(SoftwareUpdateDiscoveryRules.NO_OTA_UPDATE)) null else chain.proceed()
                } else chain.proceed()
            }
        }.onFailure { HookLog.w(TAG, "OTA discovery guard unavailable", it) }
        resolved("versionResult") { chain ->
            if (SoftwareUpdateDiscoveryRules.blocksVersionResult(chain.args.getOrNull(1) as? String,
                    enabled(SoftwareUpdateFeature.BlockSystemUpdate))) SoftwareUpdateDiscoveryRules.NO_VERSION_RESULT else chain.proceed()
        }
        runCatching {
            val target = targets.getValue("mergedOpex").single()
            val same = checkNotNull(target.returnType.enumConstants).single { (it as Enum<*>).name == "IS_SAME_LIST" }
            install("mergedOpex", target) { chain ->
                if (enabled(SoftwareUpdateFeature.BlockSystemFiles)) same else chain.proceed()
            }
        }.onFailure { HookLog.w(TAG, "Merged OPEX query guard unavailable", it) }
        resolved("opexRequest") { chain ->
            val result = chain.proceed() as? Bundle ?: return@resolved null
            val status = if (result.containsKey("opexStatus")) result.getInt("opexStatus") else null
            if (SoftwareUpdateDiscoveryRules.hidesOpexInfo(chain.args.getOrNull(1) as? String, status,
                    enabled(SoftwareUpdateFeature.BlockSystemFiles))) {
                Bundle(result).apply {
                    putInt("opexStatus", -1)
                    putString("opexExtraData", "{}")
                }
            } else result
        }
        resolved("worker") { chain ->
            val message = chain.args.firstOrNull() as? Message
            if (chain.thisObject !is Handler || message == null) return@resolved chain.proceed()
            if (SoftwareUpdateDiscoveryRules.blocksOtaMessage(message.what, enabled(SoftwareUpdateFeature.BlockSystemUpdate))) {
                (chain.thisObject as Handler).sendEmptyMessage(SoftwareUpdateDiscoveryRules.NO_OTA_UPDATE)
                return@resolved null
            }
            if (SoftwareUpdateRules.blocksMessage(
                    message.what, message.peekData()?.getBoolean("is_night_install", false) == true,
                    enabled(SoftwareUpdateFeature.DisableAutoDownload), enabled(SoftwareUpdateFeature.DisableNightInstall),
                )) null else chain.proceed()
        }
        listOf("downloadRecommendation", "nightRecommendation").forEach { name ->
            resolved(name) { chain -> if (enabled(SoftwareUpdateFeature.HideRecommendations)) null else chain.proceed() }
        }
        resolved("updatePopup") { chain -> if (enabled(SoftwareUpdateFeature.HideUpdatePopup)) null else chain.proceed() }

        runCatching {
            val service = loader.loadClass("com.oplus.ota.service.OTAService")
            check(Service::class.java.isAssignableFrom(service))
            val start = service.getDeclaredMethod("onStartCommand", Intent::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            check(start.returnType == Int::class.javaPrimitiveType && !Modifier.isStatic(start.modifiers))
            install("service", start) { chain ->
                val action = (chain.args.firstOrNull() as? Intent)?.action
                if (SoftwareUpdateRules.blocksAction(action, enabled(SoftwareUpdateFeature.DisableAutoDownload),
                        enabled(SoftwareUpdateFeature.DisableNightInstall))) Service.START_NOT_STICKY else chain.proceed()
            }
        }.onFailure { HookLog.w(TAG, "Automatic service gate unavailable", it) }

        runCatching {
            val job = loader.loadClass("com.oplus.ota.download.AutoDownloadJobService")
            val start = job.getDeclaredMethod("onStartJob", JobParameters::class.java)
            check(start.returnType == Boolean::class.javaPrimitiveType && !Modifier.isStatic(start.modifiers))
            install("downloadJob", start) { chain ->
                if (enabled(SoftwareUpdateFeature.DisableAutoDownload)) false else chain.proceed()
            }
        }.onFailure { HookLog.w(TAG, "Automatic download job gate unavailable", it) }

        runCatching {
            val entry = loader.loadClass("com.oplus.otaui.activity.EntryActivity")
            check(android.app.Activity::class.java.isAssignableFrom(entry))
            val create = entry.getDeclaredMethod("onCreateOptionsMenu", Menu::class.java)
            check(create.returnType == Boolean::class.javaPrimitiveType && !Modifier.isStatic(create.modifiers))
            val menuScope = SoftwareUpdateMenuScope()
            val controller = SoftwareUpdateMenuController("$prefix:menu", targets["downloadAttestation"]?.singleOrNull())
            val read = Settings.Global::class.java.getDeclaredMethod("getInt", ContentResolver::class.java,
                String::class.java, Int::class.javaPrimitiveType)
            install("developerMenuRead", read) { chain ->
                if (menuScope.overrides(chain.args.getOrNull(1) as? String)) 1 else chain.proceed()
            }
            install("menu", create) { chain ->
                val result = menuScope.enter(enabled(SoftwareUpdateFeature.ShowLocalInstall)) { chain.proceed() }
                val activity = chain.thisObject as? android.app.Activity
                val menu = chain.args.firstOrNull() as? Menu
                if (result == true && activity != null && menu != null) {
                    runCatching { controller.bind(activity, menu) }.onFailure { HookLog.w(TAG, "Updater menu could not be extended", it) }
                }
                result
            }
            ModernHookRuntime.requireModule().deoptimize(create)
            listOf("onResume", "onDestroy").forEach { name ->
                val target = entry.getDeclaredMethod(name)
                install(name, target) { chain ->
                    val activity = chain.thisObject as? android.app.Activity
                    if (name == "onDestroy" && activity != null) controller.remove(activity)
                    val result = chain.proceed()
                    if (name == "onResume" && activity != null) {
                        runCatching { controller.refresh(activity) }
                    }
                    result
                }
            }
        }.onFailure { HookLog.w(TAG, "Updater menu integration unavailable", it) }
    }
}
