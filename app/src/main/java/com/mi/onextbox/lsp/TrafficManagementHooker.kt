package com.mi.onextbox.lsp

import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.mi.onextbox.lsp.LspConfig.TrafficFeature
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Native TrafficMonitor controls; each feature has an independent rollback boundary. */
internal object TrafficManagementHooker {
    private const val TAG = "ONextBox-Traffic"
    private const val MODEL = "com.oplus.trafficmonitor.model.NetworkControlDataModel"

    fun hook(loader: ClassLoader, apkPath: String) {
        if (TrafficFeature.entries.none(::enabled)) return
        runCatching {
            System.loadLibrary("dexkit")
            DexKitBridge.create(apkPath).use { bridge ->
                Installer(loader, bridge).install()
            }
        }.onFailure { HookLog.w(TAG, "TrafficMonitor scan unavailable", it) }
    }

    private fun enabled(feature: TrafficFeature) = LspConfig.isTrafficFeatureEnabledXposed(feature)

    private class Installer(val loader: ClassLoader, val dex: DexKitBridge) {
        private val prefix = "$TAG@${System.identityHashCode(loader)}:"

        private val targets = TrafficManagementTargets(dex)

        private fun receiver(method: Method): Any? = if (Modifier.isStatic(method.modifiers)) null else {
            method.declaringClass.declaredFields.single {
                Modifier.isStatic(it.modifiers) && it.type == method.declaringClass
            }.apply { isAccessible = true }.get(null)
        }

        private fun feature(feature: TrafficFeature, block: () -> Unit) {
            if (!enabled(feature)) return
            runCatching(block).onSuccess {
                HookLog.i(TAG, "Installed ${feature.name}")
            }.onFailure {
                ModernHookRegistry.unhookPrefix("$prefix${feature.name}:")
                HookLog.w(TAG, "${feature.name} unavailable; stock behavior retained", it)
            }
        }

        private fun hook(feature: TrafficFeature, target: Method, body: (XposedInterface.Chain) -> Any?) {
            ModernHookRegistry.installFast("$prefix${feature.name}:${target.toGenericString()}", target,
                XposedInterface.Hooker { chain -> if (enabled(feature)) body(chain) else chain.proceed() })
        }

        private fun hook(feature: TrafficFeature, target: MethodData, body: (XposedInterface.Chain) -> Any?) {
            hook(feature, target.getMethodInstance(loader), body)
            // Recompile callers that may already contain an inlined stock check.
            (target.callers + target.callers.flatMap { it.callers }).distinctBy { it.descriptor }.forEach {
                runCatching { ModernHookRuntime.requireModule().deoptimize(it.getMethodInstance(loader)) }
            }
        }

        fun install() {
            feature(TrafficFeature.GoogleNetworkControl) {
                // This is evaluated lazily before the Google list and detail controls are filtered.
                hook(TrafficFeature.GoogleNetworkControl, targets.google()) { false }
            }
            feature(TrafficFeature.OtaNetworkControl) {
                // The same check also guards policy repair, not just the visible Wi-Fi switch.
                targets.ota().forEach { hook(TrafficFeature.OtaNetworkControl, it) { false } }
            }
            feature(TrafficFeature.ShowPreinstalledApps) {
                val classifier = ModernReflect.findClass("com.android.settings.datausage.DataUsageUtils", loader)
                    .getDeclaredMethod("isDataApp", Context::class.java, Int::class.javaPrimitiveType)
                hook(TrafficFeature.ShowPreinstalledApps, classifier) { chain ->
                    val context = chain.getArg(0) as? Context
                    val uid = chain.getArg(1) as Int
                    if (context != null && isManageable(context, uid)) true else chain.proceed()
                }
                val (filter, excludedUid) = targets.preinstalled()
                hook(TrafficFeature.ShowPreinstalledApps, filter) { chain ->
                    val context = chain.getArg(0) as? Context
                    val packageName = chain.getArg(1) as? String
                    if (context != null && packageName in TrafficManagementRules.hiddenPackages) {
                        runCatching {
                            val uid = context.packageManager.getApplicationInfo(packageName!!, 0).uid
                            isManageable(context, uid)
                        }.getOrElse { chain.proceed() }
                    } else chain.proceed()
                }
                hook(TrafficFeature.ShowPreinstalledApps, excludedUid) { -1 }
                ModernReflect.findClass(MODEL, loader).declaredMethods.forEach {
                    runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
                }
                ModernReflect.findClass("com.oplus.trafficmonitor.view.datausagelist.appdatausage.OplusAppDataUsage", loader)
                    .declaredMethods.filter { it.name == "onCreate" }.forEach {
                        ModernHookRuntime.requireModule().deoptimize(it)
                    }
            }
            feature(TrafficFeature.BlockCloudNetworkRules) {
                // Stop both active downloads and the shared sink used by downloaded/pushed lists.
                targets.cloud().forEach { hook(TrafficFeature.BlockCloudNetworkRules, it) { null } }
            }
            feature(TrafficFeature.ShowHiddenControls) {
                targets.hidden().forEach { target ->
                    hook(TrafficFeature.ShowHiddenControls, target) { emptyList<String>() }
                }
            }
            feature(TrafficFeature.RoamingBackgroundMode) { installRoaming() }
            feature(TrafficFeature.RemoveDefaultLimit) {
                // Only suppress the implicit 100 GiB fallback; never overwrite saved user limits.
                hook(TrafficFeature.RemoveDefaultLimit, targets.limit()) { false }
            }
        }

        private fun installRoaming() {
            val feature = TrafficFeature.RoamingBackgroundMode
            // The native receiver and native policy writer require this OEM telephony API.
            val telephony = ModernReflect.findClass("android.telephony.OplusTelephonyManager", loader)
            require(telephony.getMethod("getRoamingReduction").returnType == Boolean::class.javaPrimitiveType)
            val roaming = targets.roaming()
            val (regionGate, uiGate, writeTarget, nativeRead, readWrapper) = roaming
            val writeWrapper = roaming[5]
            val nativeWrite = writeTarget.getMethodInstance(loader).apply { isAccessible = true }
            val bindRow = ArrayAdapter::class.java.getDeclaredMethod(
                "getView", Int::class.javaPrimitiveType, View::class.java, ViewGroup::class.java,
            )
            hook(feature, bindRow) { chain ->
                val row = chain.proceed()
                runCatching {
                    TrafficRoamingDialogTheme.applyToRow(
                        chain.thisObject as ArrayAdapter<*>, chain.getArg(2) as? ViewGroup, row as? View,
                    )
                }.onFailure { HookLog.w(TAG, "Could not apply native roaming chooser colors", it) }
                row
            }
            hook(feature, nativeRead) { chain ->
                val context = chain.getArg(0) as Context
                val uid = chain.getArg(1) as Int
                val prefs = context.getSharedPreferences("background_data", Context.MODE_PRIVATE)
                val saved = runCatching { prefs.getInt(uid.toString(), -1) }.getOrNull()
                if (saved in 0..2) saved else {
                    // OEM defaults every unconfigured UID to roaming-only. Preserve existing policy instead.
                    val value = runCatching {
                        val policy = requireNotNull(context.getSystemService("netpolicy"))
                        ModernReflect.callMethod(policy, "getUidPolicy", uid) as Int
                    }.getOrDefault(0)
                    TrafficManagementRules.roamingMode(null, value and 1 != 0)
                }
            }
            val nativeReadMethod = nativeRead.getMethodInstance(loader).apply { isAccessible = true }
            hook(feature, readWrapper) { chain ->
                nativeReadMethod.invoke(receiver(nativeReadMethod), chain.getArg(0), chain.getArg(1))
            }
            hook(feature, writeWrapper) { chain ->
                nativeWrite.invoke(receiver(nativeWrite), chain.getArg(0), chain.getArg(1), chain.getArg(2))
                null
            }
            hook(feature, regionGate) { true }
            hook(feature, uiGate) { true }
        }

        private fun isManageable(context: Context, uid: Int): Boolean = runCatching {
            TrafficManagementRules.isApplicationUid(uid) &&
                context.packageManager.getPackagesForUid(uid)?.any { packageName ->
                    context.packageManager.checkPermission("android.permission.INTERNET", packageName) ==
                        PackageManager.PERMISSION_GRANTED
                } == true
        }.getOrDefault(false)
    }
}
