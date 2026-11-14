package com.mi.onextbox.lsp
import android.annotation.SuppressLint

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit

/** Collects a passive, privacy-safe eUICC status snapshot in the privileged LPA process. */
internal object EuiccDiagnosticsHooker {
    private const val TAG = "ONextBox-EuiccDiag"
    private const val APPLICATION_CLASS = "com.oplus.euicc.OplusEuiccApplication"
    private const val DOWNLOAD_CHECKER_CLASS = "com.oplus.euicc.DownloadChecker"
    private const val ESIM_EID_PROPERTY = "persist.sys.oplus.radio.esim.eid"
    private const val ESIM_POWER_PROPERTY = "persist.vendor.oplus.radio.esim.gpio.status"
    private const val ESIM_BINDING_PROPERTY =
        "persist.sys.oplus.radio.esim.binding.pairing.result"
    private const val MOBILE_COUNTRY_PROPERTY = "gsm.operator.iso-country"
    private const val MEP_FEATURE = "android.hardware.telephony.euicc.mep"

    private fun newCollector() = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "OOST-eSIM-diagnostics").apply { isDaemon = true }
    }
    private var collector = newCollector()
    private val pendingCallbacks = AtomicInteger()
    private var refreshReceiver: BroadcastReceiver? = null
    private var application: Application? = null
    private var applicationLoader: ClassLoader? = null
    private var sdkTargets: EuiccCompatibilityTargets.Diagnostics? = null
    private val collecting = AtomicBoolean(false)
    private val receiverRegistered = AtomicBoolean(false)
    private val snapshotLock = Any()

    @Volatile
    private var latestSnapshot: EsimDiagnosticsSnapshot? = null

    fun hook(classLoader: ClassLoader?, google: Boolean = false, apkPath: String? = null): Boolean {
        if (!google) {
            val stateKey = "euicc:diagnostics-targets:v1"
            sdkTargets = runCatching {
                val saved = DexTargetCache.restoredState(stateKey) as? Array<*>
                saved?.let { EuiccCompatibilityTargets.Diagnostics(it) }
                    ?: EuiccCompatibilityTargets.diagnostics(requireNotNull(classLoader), requireNotNull(apkPath))
            }.onFailure { HookLog.e(TAG, "OEM diagnostics SDK targets unavailable", it) }.getOrNull()
            sdkTargets?.let { targets -> HotReloadLifecycle.register(stateKey, capture = { targets.values }) }
        }
        HotReloadLifecycle.register("euicc:diagnostics", capture = {
            arrayOf(application, applicationLoader, latestSnapshot?.lastDownloadResult)
        }, canReload = { !collecting.get() && pendingCallbacks.get() == 0 }, detach = {
            refreshReceiver?.let { application?.unregisterReceiver(it) }
            refreshReceiver = null
            receiverRegistered.set(false)
            collector.shutdownNow()
            check(collector.awaitTermination(250, TimeUnit.MILLISECONDS)) { "eSIM diagnostics worker is busy" }
            application = null
        }, restore = { state ->
            val saved = state as? Array<*>
            val app = saved?.getOrNull(0) as? Application
            val loader = saved?.getOrNull(1) as? ClassLoader
            (saved?.getOrNull(2) as? Int)?.let { latestSnapshot = EsimDiagnosticsSnapshot(lastDownloadResult = it) }
            if (app != null) onApplicationReady(app, loader)
        })
        HotReloadLifecycle.afterCommit {
            val app = currentApplicationContext() as? Application
            if (app != null && app.packageName in RegionalAppPackages.esim) onApplicationReady(app, classLoader)
        }
        val applicationClass = findClass(if (google) "com.google.android.euicc.gms.EuiccGoogleApplication" else APPLICATION_CLASS, classLoader)
        val onCreate = applicationClass?.methods?.singleOrNull { method ->
            method.name == "onCreate" && method.parameterCount == 0 && method.returnType == Void.TYPE
        }
        if (onCreate == null) {
            HookLog.w(TAG, "Compatible eUICC Application.onCreate target was not found")
            return false
        }

        return runCatching {
            installAfterHook("euicc:diagnostics-application", onCreate) { param ->
                val application = param.thisObject as? Application ?: return@installAfterHook
                onApplicationReady(application, classLoader)
            }
            if (!google) hookLastDownloadResult(classLoader)
            HookLog.i(TAG, "Installed passive eSIM diagnostics collector")
            true
        }.onFailure { throwable ->
            HookLog.e(TAG, "Failed to install eSIM diagnostics collector", throwable)
        }.getOrDefault(false)
    }

    private fun onApplicationReady(application: Application, classLoader: ClassLoader?) {
        if (HotReloadLifecycle.isRetiring) return
        val alreadyReady = this.application === application && receiverRegistered.get()
        this.application = application
        applicationLoader = classLoader
        if (collector.isShutdown) collector = newCollector()
        registerRefreshReceiver(application, classLoader)
        if (!alreadyReady) requestCollection(application, classLoader)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag") // The legacy overload is used only below API 33.
    private fun registerRefreshReceiver(context: Context, classLoader: ClassLoader?) {
        if (!receiverRegistered.compareAndSet(false, true)) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent?) {
                if (HotReloadLifecycle.isRetiring) return
                if (intent?.action == EsimDiagnosticsStore.ACTION_REFRESH) {
                    requestCollection(receiverContext.applicationContext, classLoader)
                }
            }
        }
        runCatching {
            val filter = IntentFilter(EsimDiagnosticsStore.ACTION_REFRESH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            refreshReceiver = receiver
        }.onFailure { throwable ->
            receiverRegistered.set(false)
            HookLog.e(TAG, "Could not register the eSIM diagnostics refresh receiver", throwable)
        }
    }

    private fun requestCollection(context: Context, classLoader: ClassLoader?) {
        if (HotReloadLifecycle.isRetiring) return
        if (!collecting.compareAndSet(false, true)) return
        collector.execute {
            try {
                if (HotReloadLifecycle.isRetiring) return@execute
                collect(context.applicationContext, classLoader)
            } catch (throwable: Throwable) {
                HookLog.e(TAG, "eSIM diagnostics collection failed", throwable)
                updateSnapshot(context) { current ->
                    current.copy(
                        capturedAtMillis = System.currentTimeMillis(),
                        hookReady = true,
                        collectionMessage = throwable.javaClass.simpleName,
                    )
                }
            } finally {
                collecting.set(false)
            }
        }
    }

    private fun collect(context: Context, classLoader: ClassLoader?) {
        if (HotReloadLifecycle.isRetiring) return
        val powerState = when (readSystemProperty(ESIM_POWER_PROPERTY)) {
            "0" -> "off"
            "1" -> "on"
            else -> "unknown"
        }
        val baseSnapshot = EsimDiagnosticsSnapshot(
            capturedAtMillis = System.currentTimeMillis(),
            hookReady = true,
            eidMasked = maskIdentifier(readEid(context)),
            powerState = powerState,
            bindingResult = readSystemProperty(ESIM_BINDING_PROPERTY),
            mepSupported = runCatching {
                context.packageManager.hasSystemFeature(MEP_FEATURE)
            }.getOrNull(),
            networkAvailable = readNetworkAvailable(context),
            networkCountries = readNetworkCountries(context),
            locationEnabled = readLocationEnabled(context),
            lastDownloadResult = latestSnapshot?.lastDownloadResult,
        )
        synchronized(snapshotLock) {
            latestSnapshot = baseSnapshot
            publish(context, baseSnapshot)
        }

        if (context.packageName == RegionalAppPackages.ESIM_GLOBAL) {
            collectGoogleStatus(context)
            return
        }
        val assistant = loadProfileAssistant()
        readProfileLimit(context)?.let { limit ->
            updateSnapshot(context) { it.copy(profileLimit = limit) }
        }

        if (assistant == null) {
            updateSnapshot(context) { it.copy(collectionMessage = "profile_assistant_unavailable") }
            return
        }

        if (powerState == "on") {
            readProfiles(assistant)?.let { profileCollection ->
                updateSnapshot(context) {
                    it.copy(
                        profileCount = profileCollection.profiles.size,
                        operationalProfileCount = profileCollection.operationalCount,
                        enabledProfileCount = profileCollection.enabledCount,
                        profiles = profileCollection.profiles,
                    )
                }
            }
            requestEuiccInfo(context, assistant)
            requestSmdpInfo(context, assistant)
        } else {
            updateSnapshot(context) { it.copy(collectionMessage = "esim_power_off") }
        }
    }

    @SuppressLint("MissingPermission")
    private fun collectGoogleStatus(context: Context) {
        val manager = context.getSystemService(android.telephony.euicc.EuiccManager::class.java)
        val eid = runCatching { manager?.eid }.getOrNull()
        val osVersion = runCatching { manager?.euiccInfo?.osVersion }.getOrNull()
        val profiles = runCatching {
            val subscriptions = context.getSystemService(android.telephony.SubscriptionManager::class.java)
            val available = android.telephony.SubscriptionManager::class.java
                .getMethod("getAvailableSubscriptionInfoList").invoke(subscriptions) as? List<*>
            available?.filterIsInstance<android.telephony.SubscriptionInfo>()?.filter { it.isEmbedded }?.map { info ->
                    EsimProfileDiagnostic(
                        displayName = info.displayName?.toString().orEmpty(),
                        serviceProviderName = info.carrierName?.toString().orEmpty(),
                        iccidMasked = maskIdentifier(info.iccId.orEmpty()),
                        enabled = info.simSlotIndex >= 0,
                        portIndex = info.portIndex,
                    )
                }
        }.getOrNull()
        updateSnapshot(context) { it.copy(
            eidMasked = eid?.takeIf(String::isNotBlank)?.let(::maskIdentifier) ?: it.eidMasked,
            firmwareVersion = osVersion.orEmpty(),
            profiles = profiles.orEmpty(),
            profileCount = profiles?.size,
            enabledProfileCount = profiles?.count { profile -> profile.enabled },
        ) }
    }

    private fun loadProfileAssistant(): Any? = runCatching {
        requireNotNull(sdkTargets).factory.invoke(null)
    }.onFailure { throwable ->
        HookLog.e(TAG, "Could not obtain the OEM profile assistant", throwable)
    }.getOrNull()

    private fun readProfileLimit(context: Context): Int? = runCatching {
        val targets = requireNotNull(sdkTargets)
        val provider = targets.provider.invoke(null, context)
        targets.limit.invoke(provider) as? Int
    }.onFailure { throwable ->
        HookLog.e(TAG, "Could not read the current OEM profile limit", throwable)
    }.getOrNull()

    private fun readProfiles(assistant: Any): ProfileCollection? = runCatching {
        val targets = requireNotNull(sdkTargets)
        val registry = requireNotNull(targets.registry.invoke(assistant))
        val profiles = ArrayList<Any>()
        val succeeded = targets.profiles.invoke(registry, profiles) as? Boolean ?: false
        if (!succeeded) return@runCatching null
        val diagnostics = profiles.map { profile ->
            val nickname = invokeNoArg(profile, "z") as? String
            val profileName = invokeNoArg(profile, "getName") as? String
            EsimProfileDiagnostic(
                displayName = nickname?.takeIf { it.isNotBlank() }
                    ?: profileName?.takeIf { it.isNotBlank() }.orEmpty(),
                serviceProviderName = (invokeNoArg(profile, "u") as? String).orEmpty(),
                iccidMasked = maskIdentifier((invokeNoArg(profile, "c") as? String).orEmpty()),
                profileClass = (invokeNoArg(profile, "d") as? Number)?.toInt(),
                enabled = invokeNoArg(profile, "isEnabled") as? Boolean == true,
                portIndex = (invokeNoArg(profile, "q") as? Number)?.toInt(),
            )
        }.sortedWith(
            compareByDescending<EsimProfileDiagnostic> { it.enabled }
                .thenBy { it.displayName.lowercase(Locale.ROOT) }
                .thenBy { it.iccidMasked },
        )
        ProfileCollection(
            profiles = diagnostics,
            operationalCount = diagnostics.count { it.profileClass == 2 },
            enabledCount = diagnostics.count { it.enabled },
        )
    }.onFailure { throwable ->
        HookLog.e(TAG, "Could not read the OEM eSIM profile summary", throwable)
    }.getOrNull()

    private data class ProfileCollection(
        val profiles: List<EsimProfileDiagnostic>,
        val operationalCount: Int,
        val enabledCount: Int,
    )

    private fun requestEuiccInfo(
        context: Context,
        assistant: Any,
    ) {
        if (HotReloadLifecycle.isRetiring) return
        val waiting = EuiccPendingCallback(pendingCallbacks)
        runCatching {
            val targets = requireNotNull(sdkTargets)
            val manager = requireNotNull(targets.manager.invoke(assistant))
            val callbackClass = targets.infoCallback
            val callback = Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass),
            ) { proxy, method, args ->
                when {
                    method.name == "toString" -> "OOST-eSIM-info-callback"
                    method.name == "hashCode" -> System.identityHashCode(proxy)
                    method.name == "equals" -> proxy === args?.getOrNull(0)
                    else -> waiting.complete {
                        HotReloadLifecycle.withHook(fallback = { defaultReturnValue(method.returnType) }) {
                            if (!HotReloadLifecycle.isRetiring && targets.infoModel.isInstance(args?.firstOrNull())) {
                                val info = requireNotNull(args?.firstOrNull())
                                updateSnapshot(context) { current ->
                                    current.copy(
                                        capturedAtMillis = System.currentTimeMillis(),
                                        ppVersion = invokeNoArg(info, "a") as? String ?: "",
                                        firmwareVersion = invokeNoArg(info, "b") as? String ?: "",
                                        freeNonVolatileMemory = (invokeNoArg(info, "c") as? Number)?.toLong(),
                                        freeVolatileMemory = (invokeNoArg(info, "d") as? Number)?.toLong(),
                                        svn = invokeNoArg(info, "e") as? String ?: "",
                                        collectionMessage = "",
                                    )
                                }
                            }
                            defaultReturnValue(method.returnType)
                        }
                    }
                }
            }
            waiting.begin()
            targets.info.invoke(manager, callback)
        }.onFailure { throwable ->
            waiting.cancel()
            HookLog.e(TAG, "Could not request OEM eUICC information", throwable)
        }
    }

    private fun requestSmdpInfo(
        context: Context,
        assistant: Any,
    ) {
        if (HotReloadLifecycle.isRetiring) return
        val waiting = EuiccPendingCallback(pendingCallbacks)
        runCatching {
            val targets = requireNotNull(sdkTargets)
            val manager = requireNotNull(targets.manager.invoke(assistant))
            val callbackClass = targets.addressCallback
            val callback = Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass),
            ) { proxy, method, args ->
                when {
                    method.name == "toString" -> "OOST-eSIM-SM-DP-callback"
                    method.name == "hashCode" -> System.identityHashCode(proxy)
                    method.name == "equals" -> proxy === args?.getOrNull(0)
                    else -> waiting.complete {
                        HotReloadLifecycle.withHook(fallback = { defaultReturnValue(method.returnType) }) {
                            if (!HotReloadLifecycle.isRetiring && targets.addressModel.isInstance(args?.firstOrNull())) {
                                val text = requireNotNull(args?.firstOrNull()).toString()
                                val smdp = Regex("mSmdpAddress='([^']*)'").find(text)?.groupValues?.get(1).orEmpty()
                                val smds = Regex("mSmdsAddress='([^']*)'").find(text)?.groupValues?.get(1).orEmpty()
                                updateSnapshot(context) { current ->
                                    current.copy(
                                        capturedAtMillis = System.currentTimeMillis(),
                                        smdpAddress = smdp,
                                        smdsAddress = smds,
                                    )
                                }
                            }
                            defaultReturnValue(method.returnType)
                        }
                    }
                }
            }
            waiting.begin()
            targets.addresses.invoke(manager, callback)
        }.onFailure { throwable ->
            waiting.cancel()
            HookLog.e(TAG, "Could not request OEM SM-DP+/SM-DS information", throwable)
        }
    }

    private fun hookLastDownloadResult(classLoader: ClassLoader?) {
        val checkerClass = findClass(DOWNLOAD_CHECKER_CLASS, classLoader) ?: return
        val fullCheck = checkerClass.declaredMethods.singleOrNull { method ->
            method.name == "c" && !Modifier.isStatic(method.modifiers) &&
                method.returnType == Int::class.javaPrimitiveType &&
                method.parameterTypes.contentEquals(arrayOf(String::class.java))
        } ?: return
        installAfterHook("euicc:diagnostics-last-download-result", fullCheck) { param ->
            val result = param.result as? Int ?: return@installAfterHook
            val context = currentApplicationContext() ?: return@installAfterHook
            updateSnapshot(context) { current ->
                current.copy(
                    capturedAtMillis = System.currentTimeMillis(),
                    hookReady = true,
                    lastDownloadResult = result,
                )
            }
        }
    }

    private fun updateSnapshot(
        context: Context,
        transform: (EsimDiagnosticsSnapshot) -> EsimDiagnosticsSnapshot,
    ) {
        if (HotReloadLifecycle.isRetiring) return
        synchronized(snapshotLock) {
            val current = latestSnapshot
                ?: EsimDiagnosticsStore.read(context)
                ?: EsimDiagnosticsSnapshot(hookReady = true)
            val updated = transform(current)
            latestSnapshot = updated
            publish(context, updated)
        }
    }

    private fun publish(context: Context, snapshot: EsimDiagnosticsSnapshot) {
        if (HotReloadLifecycle.isRetiring) return
        if (!EsimDiagnosticsStore.publish(context, snapshot)) {
            HookLog.w(TAG, "The privileged eSIM process could not publish its diagnostics snapshot")
        }
    }

    @SuppressLint("MissingPermission") // Runs inside the privileged eSIM process.
    private fun readEid(context: Context): String {
        val telephonyEid = runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@runCatching null
            context.getSystemService(TelephonyManager::class.java)
                ?.uiccCardsInfo
                ?.firstOrNull { it.isEuicc }
                ?.eid
        }.getOrNull()
        return telephonyEid?.takeIf { it.isNotBlank() }
            ?: readSystemProperty(ESIM_EID_PROPERTY)
    }

    private fun readNetworkAvailable(context: Context): Boolean? = runCatching {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
            ?: return@runCatching false
        val network = connectivity.activeNetwork ?: return@runCatching false
        connectivity.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrNull()

    private fun readNetworkCountries(context: Context): String {
        val countries = linkedSetOf<String>()
        fun add(raw: String?) {
            raw?.split(',', ';', ' ', '\t', '\n')
                ?.map { it.trim().uppercase(Locale.ROOT) }
                ?.filterTo(countries) { it.length == 2 }
        }
        val telephony = runCatching {
            context.getSystemService(TelephonyManager::class.java)
        }.getOrNull()
        add(runCatching { telephony?.networkCountryIso }.getOrNull())
        add(readSystemProperty(MOBILE_COUNTRY_PROPERTY))
        return countries.joinToString(",")
    }

    private fun readLocationEnabled(context: Context): Boolean? = runCatching {
        val manager = context.getSystemService(LocationManager::class.java)
        manager?.isLocationEnabled ?: (
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, 0) != 0
        )
    }.getOrNull()

    private fun readSystemProperty(key: String): String = runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java, String::class.java)
            .invoke(null, key, "") as? String
    }.getOrNull()?.trim().orEmpty()

    private fun maskIdentifier(value: String): String {
        val clean = value.trim()
        if (clean.length <= 8) return clean
        return clean.take(4) + "••••••••" + clean.takeLast(4)
    }

    private fun invokeNoArg(instance: Any, name: String): Any? = runCatching {
        instance.javaClass.methods.single { method ->
            method.name == name && method.parameterCount == 0
        }.invoke(instance)
    }.getOrNull()

    private fun defaultReturnValue(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Byte::class.javaPrimitiveType -> 0.toByte()
        Short::class.javaPrimitiveType -> 0.toShort()
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        Char::class.javaPrimitiveType -> '\u0000'
        else -> null
    }

    private fun currentApplicationContext(): Context? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        activityThread.getMethod("currentApplication").invoke(null) as? Context
    }.getOrNull()?.applicationContext

    private fun installAfterHook(
        key: String,
        method: Method,
        after: (ModernMethodHook.MethodHookParam) -> Unit,
    ) {
        ModernHookRegistry.installCompat(
            key = key,
            executable = method,
            callback = object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) = after(param)
            },
        )
    }

    private fun findClass(className: String, classLoader: ClassLoader?): Class<*>? {
        listOf(classLoader, null, ClassLoader.getSystemClassLoader())
            .distinct()
            .forEach { loader ->
                ModernReflect.findClassIfExists(className, loader)?.let { return it }
            }
        return null
    }
}

/** Keep an asynchronous request busy until its entire callback (or retirement fallback) has returned. */
internal class EuiccPendingCallback(private val pending: AtomicInteger) {
    private val waiting = AtomicBoolean(false)

    fun begin() {
        check(waiting.compareAndSet(false, true))
        pending.incrementAndGet()
    }

    fun cancel() {
        if (waiting.compareAndSet(true, false)) pending.decrementAndGet()
    }

    fun <T> complete(action: () -> T): T {
        val ownsCompletion = waiting.compareAndSet(true, false)
        return try { action() } finally {
            if (ownsCompletion) pending.decrementAndGet()
        }
    }
}
