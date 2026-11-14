package com.mi.onextbox.lsp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.SystemClock
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

internal object MultiAppRestrictionHooker {
    private const val TAG = "MultiAppRestrictions"
    private val excluded = setOf("android", "com.mi.onextbox", "com.android.systemui",
        "com.android.launcher", "com.android.settings", SystemCustomizationSettings.MULTI_APP,
        "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.android.permissioncontroller", "com.google.android.permissioncontroller")
    @Volatile private var packages: Set<String> = emptySet()
    @Volatile private var refreshedAt = 0L
    private val querying = ThreadLocal<Boolean>()
    private val collecting = AtomicBoolean()

    private fun active() = SystemCustomizationSettings.active(SystemCustomizationSettings.Feature.MultiAppRestrictions)
    private fun extendedSlots() = SystemCustomizationSettings.active(SystemCustomizationSettings.Feature.MultiAppPerAppLimit)

    private fun context(loader: ClassLoader, system: Boolean): Context? = runCatching {
        val thread = Class.forName("android.app.ActivityThread", false, loader)
        if (!system) thread.getDeclaredMethod("currentApplication").invoke(null) as? Context
        else thread.getDeclaredMethod("getSystemContext").invoke(
            thread.getDeclaredMethod("currentActivityThread").invoke(null)) as? Context
    }.getOrNull()

    private fun eligible(loader: ClassLoader, system: Boolean): Set<String> {
        if (querying.get() == true) return packages
        val now = SystemClock.elapsedRealtime()
        if (refreshedAt != 0L && now - refreshedAt < 30_000L) return packages
        val context = context(loader, system) ?: return packages
        if (!collecting.compareAndSet(false, true)) return packages
        val token = if (system) Binder.clearCallingIdentity() else null
        querying.set(true)
        try {
            val apps = runCatching { context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE) }
                .getOrNull() ?: return packages
            packages = apps.mapNotNull { item ->
                item.activityInfo?.applicationInfo?.takeIf { it.enabled &&
                    it.uid % 100_000 >= 10_000 && it.packageName !in excluded }?.packageName
            }.toSet()
            refreshedAt = now
        } finally {
            querying.remove()
            collecting.set(false)
            if (token != null) Binder.restoreCallingIdentity(token)
        }
        return packages
    }

    private fun installList(key: String, method: Method, loader: ClassLoader, system: Boolean) {
        ModernHookRegistry.installFast(key, method) { chain ->
            val native = chain.proceed()
            if (!active()) native else (native as? List<*>)?.let {
                val result = LinkedHashSet(it.filterIsInstance<String>())
                result.addAll(eligible(loader, system))
                ArrayList(result)
            } ?: native
        }
    }

    internal fun systemTargets(loader: ClassLoader): Array<Method> {
        val config = Class.forName("com.android.server.am.OplusMultiAppConfigManager", false, loader)
        val list = config.getDeclaredMethod("getAllowedList")
        val allowed = config.declaredMethods.filter { it.name == "isAllowed" &&
            it.returnType == Boolean::class.javaPrimitiveType && it.parameterTypes.firstOrNull() == String::class.java }
        require(allowed.isNotEmpty())
        val service = Class.forName("com.android.server.am.OplusMultiAppManagerService", false, loader)
        return arrayOf(list, service.getDeclaredMethod("hasMaxCreatedNumLimitedFeature"),
            config.getDeclaredMethod("getMaxCreatedNum"), *allowed.toTypedArray())
    }

    fun hookSystem(loader: ClassLoader) {
        val key = "$TAG:system"
        runCatching {
            val targets = systemTargets(loader)
            installList("$key:list", targets[0], loader, true)
            ModernHookRegistry.installFast("$key:limited", targets[1]) { chain ->
                if (active()) false else chain.proceed()
            }
            ModernHookRegistry.installFast("$key:maximum", targets[2]) { chain ->
                val native = chain.proceed() as Int
                if (active() && native > 0) maxOf(native, eligible(loader, true).size) else native
            }
            targets.drop(3).forEachIndexed { index, method ->
                ModernHookRegistry.installFast("$key:allowed:$index", method) { chain ->
                    val name = chain.args.firstOrNull() as? String
                    if (active() && name != null && name in eligible(loader, true)) true else chain.proceed()
                }
            }
            runCatching { targets[0].declaringClass.getDeclaredMethod("getMaxCloneUserNum") }.getOrNull()?.let { cloneSlots ->
                ModernHookRegistry.installFast("$key:clone-slots", cloneSlots) { chain ->
                    if (extendedSlots()) 10 else chain.proceed()
                }
            }
            val callers = targets.map { it.declaringClass }.distinct().flatMap { it.declaredMethods.toList() }
                .filter { it.name in setOf("isAllowed", "getMultiAppList", "getMaxCreatedNum", "getMaxCloneUserNum",
                    "getNextAvailableCloneUserId", "getCanInstallUserId", "isValidMultiAppUserId") }
            callers.forEach { ModernHookRuntime.requireModule().deoptimize(it) }
            hookSystemData(loader, key)
            HookLog.i(TAG, "Native clone eligibility and application-list adapters installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(key)
            HookLog.w(TAG, "System clone eligibility adapter unavailable; native policy retained", it)
        }
    }

    private fun hookSystemData(loader: ClassLoader, key: String) {
        val data = runCatching { Class.forName("com.android.server.pm.OplusMultiAppDataManager", false, loader) }
            .getOrNull() ?: return
        val black = data.getDeclaredMethod("getBlackAppSnapshot").apply { isAccessible = true }
        val lists = data.getDeclaredMethod("getMultiAppInfos", Int::class.javaPrimitiveType)
        val rebuild = data.getDeclaredMethod("initMultiAppInfoList").apply { isAccessible = true }
        val lock = Any()
        var lastMode: Pair<Boolean, Boolean>? = null
        var lastEligible: Set<String> = emptySet()
        ModernHookRegistry.installFast("$key:blacklist", black) { chain ->
            val native = chain.proceed()
            if (!active()) native else (native as? Set<*>)?.filterIsInstance<String>()
                ?.filterNot { it in eligible(loader, true) }?.toSet() ?: native
        }
        ModernHookRegistry.installFast("$key:info-cache", lists) { chain ->
            val enabled = active() to extendedSlots()
            val available = if (enabled.first) eligible(loader, true) else emptySet()
            synchronized(lock) {
                if (lastMode != enabled || lastEligible != available) {
                    val identity = Binder.clearCallingIdentity()
                    try {
                        rebuild.invoke(chain.thisObject)
                        lastMode = enabled
                        lastEligible = available
                        HookLog.i(TAG, "Native clone application cache rebuilt: enabled=$enabled")
                    } finally { Binder.restoreCallingIdentity(identity) }
                }
            }
            chain.proceed()
        }
        data.declaredMethods.filter { it.name in setOf("initMultiAppList", "addMultiInfoToListWithoutSortLocked", "getFilteredCreatedMultiAppInfos") }
            .forEach { ModernHookRuntime.requireModule().deoptimize(it) }
        data.declaredClasses.filter { it.simpleName == "Dependencies" }.flatMap { it.declaredMethods.toList() }
            .filter { it.name in setOf("getMultiAppList", "getMaxCreatedNum", "hasMaxCreatedNumLimitedFeature") }
            .forEach { ModernHookRuntime.requireModule().deoptimize(it) }
    }

    fun hookApp(loader: ClassLoader) {
        val key = "$TAG:app"
        runCatching {
            val platform = Class.forName("com.oplus.multiapp.platform.MultiAppAPI", false, loader)
            installList("$key:list", platform.getDeclaredMethod("getAllowedMultiApp"), loader, false)
            val data = Class.forName("com.oplus.multiapp.data.MultiAppDataManager", false, loader)
            installList("$key:cached-list", data.getDeclaredMethod("getAllowedAppList"), loader, false)
            val black = data.getDeclaredMethod("getBlackApps")
            ModernHookRegistry.installFast("$key:blacklist", black) { chain ->
                val native = chain.proceed()
                if (!active()) native else (native as? List<*>)?.filterNot { it in eligible(loader, false) }?.let(::ArrayList) ?: native
            }
            val helper = Class.forName("com.oplus.multiapp.utils.CustomHelper", false, loader)
            ModernHookRegistry.installFast("$key:limited", helper.getDeclaredMethod("isAppLimited")) { chain ->
                if (active()) false else chain.proceed()
            }
            ModernHookRegistry.installFast("$key:maximum", helper.getDeclaredMethod("getMaxCreateNum")) { chain ->
                val native = chain.proceed() as Int
                if (active() && native > 0) maxOf(native, eligible(loader, false).size) else native
            }
            platform.declaredMethods.filter { it.name == "getAllowedMultiApp" }.forEach {
                ModernHookRuntime.requireModule().deoptimize(it)
            }
            listOf("com.oplus.multiapp.ui.entry.ActivityMainPresenter", "com.oplus.multiapp.data.MultiAppDataAPI",
                "com.oplus.multiapp.ui.entry.FragmentMain", "com.oplus.multiapp.ui.other.FragmentOtherApp",
                "com.oplus.multiapp.ui.other.ActivityOtherAppPresenter").forEach { name ->
                Class.forName(name, false, loader).declaredMethods.forEach { method ->
                    ModernHookRuntime.requireModule().deoptimize(method)
                }
            }
            HookLog.i(TAG, "Native clone application-list and quota adapters installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(key)
            HookLog.w(TAG, "Clone application list adapter unavailable; native list retained", it)
        }
    }
}
