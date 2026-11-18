package com.mi.onextbox.lsp

import android.app.Application
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.telephony.ServiceState
import android.telephony.SubscriptionInfo
import android.util.Log
import com.mi.onextbox.diagnostics.SafeModePolicy
import com.mi.onextbox.lsp.compat.AdaptationHookGate
import com.mi.onextbox.lsp.compat.AdaptationRuntimePolicy
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookBridge as Bridge
import com.mi.onextbox.lsp.compat.ModernMethodHook as Hook
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_READY_KEY
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/** System-wide property publication, with genuine values retained inside the phone process. */
internal object NetworkIdentityHooker {
    private const val TAG = "ONextBox-NetworkIdentity"
    private val lock = Any()
    private val bypass = ThreadLocal.withInitial { false }
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private var installed = false
    @Volatile private var engine: NetworkIdentityProperties? = null
    @Volatile private var publishedProfiles: Map<Int, NetworkIdentityRules.Profile> = emptyMap()
    private var application: Context? = null
    private var preferences: SharedPreferences? = null
    private var registered = false
    private var receiverRegistered = false
    private lateinit var propertyGet: Method
    private lateinit var propertySet: Method
    private val configurationDirty = AtomicBoolean(true)
    private var configuredProfiles: Map<Int, NetworkIdentityRules.Profile> = emptyMap()
    private val update = Runnable { HotReloadLifecycle.withInfrastructureHook({}, { refreshSafely() }) }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in NetworkIdentitySettings.keys || key == MODERN_PREFERENCES_READY_KEY ||
            key == SafeModePolicy.MANUAL || key == SafeModePolicy.AUTOMATIC) schedule(configurationChanged = true)
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { schedule() }
    }

    fun hook(loader: ClassLoader, processName: String) {
        if (processName != "com.android.phone" || installed) return
        installed = true
        val properties = ModernReflect.findClassIfExists("android.os.SystemProperties", loader) ?: return
        propertyGet = properties.getDeclaredMethod("get", String::class.java, String::class.java).apply { isAccessible = true }
        val setter = properties.getDeclaredMethod("set", String::class.java, String::class.java)
        propertySet = setter.apply { isAccessible = true }
        Bridge.hookInfrastructureFast(setter) { chain ->
            val key = chain.args[0] as? String
            if (bypass.get() == true || key !in NetworkIdentityRules.properties) chain.proceed()
            else synchronized(lock) {
                // Native telephony updates still run; only published identity is overridden afterwards.
                val result = chain.proceed()
                val current = engine
                val selected = selectedProfiles()
                if (current != null && (current.hasOverrides || selected.isNotEmpty())) {
                    runCatching {
                        raw { current.refreshAfterNativeWrite(key!!, (chain.args[1] as? String).orEmpty(), selected) }
                        publishedProfiles = selected
                    }.onFailure(::restoreAfterFailure)
                }
                result
            }
        }
        properties.declaredMethods.filter { it.name == "get" && it.returnType == String::class.java }.forEach { getter ->
            Bridge.hookInfrastructureFast(getter) { chain ->
                val key = chain.args.firstOrNull() as? String
                if (bypass.get() == true || key !in NetworkIdentityRules.properties) chain.proceed()
                else synchronized(lock) {
                    // Read and interpret under the same lock as publication. Otherwise a getter
                    // could read generation N and compare it with generation N+1's journal.
                    val value = chain.proceed() as String
                    engine?.actual(key!!, value) ?: value
                }
            }
        }
        // Generated sysprop accessors and TelephonyManager wrappers can otherwise inline get/set.
        listOf("android.sysprop.TelephonyProperties", "android.internal.telephony.sysprop.TelephonyProperties",
            "android.telephony.TelephonyManager").forEach { name ->
            ModernReflect.findClassIfExists(name, loader)?.declaredMethods?.filter {
                it.name.contains("operator", true) || it.name.contains("CountryIso", true)
            }?.forEach { runCatching { ModernHookRuntime.requireModule().deoptimize(it) } }
        }
        installCountryService(loader)
        installSubscriptionService(loader)
        Application::class.java.declaredMethods.filter { it.name == "attach" }.forEach { method ->
            Bridge.hookInfrastructureFast(method) { chain ->
                chain.proceed().also { (chain.args.firstOrNull() as? Context)?.let(::startAfterCommit) }
            }
        }
        val current = runCatching {
            ModernReflect.callStaticMethod(ModernReflect.findClass("android.app.ActivityThread", loader), "currentApplication") as? Context
        }.getOrNull()
        if (current != null) startAfterCommit(current)
        HotReloadLifecycle.register("network-identity", detach = {
            unregister()
            synchronized(lock) { raw { engine?.refresh(null) }; publishedProfiles = emptyMap() }
        }, restore = { register(); configurationDirty.set(true); refreshSafely() },
            resumeAfterRejection = { register(); schedule(configurationChanged = true) })
    }

    // Runs in the privileged phone process on the platform's already permission-filtered return
    // value, not in the module UI process. Original SecurityExceptions are never replaced.
    @SuppressLint("MissingPermission")
    private fun installCountryService(loader: ClassLoader) {
        val owner = ModernReflect.findClassIfExists("com.android.phone.PhoneInterfaceManager", loader) ?: return
        Bridge.hookAllMethods(owner, "getNetworkCountryIsoForPhone", object : Hook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                // Keep framework/phone decisions genuine and preserve all original access checks.
                if (param.throwable != null || Binder.getCallingUid() % 100000 < Process.FIRST_APPLICATION_UID) return
                val slot = param.args.firstOrNull() as? Int ?: return
                synchronized(lock) {
                    if (engine == null) return
                    val profile = publishedProfiles[slot] ?: return
                    val numeric = raw { readProperty("gsm.operator.numeric") }
                    if (NetworkIdentityRules.decode(numeric).getOrNull(slot).isNullOrEmpty()) return
                    if (param.result is String) param.result = profile.region
                }
            }
        })
        owner.declaredMethods.filter { it.name in setOf("getServiceStateForSlot", "getServiceStateForSubscriber") &&
            it.returnType == ServiceState::class.java }.forEach { method ->
            Bridge.hookMethod(method, object : Hook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!externalSuccessful(param)) return
                    val original = param.result as? ServiceState ?: return
                    try {
                        if (original.operatorNumeric.isNullOrEmpty()) return
                        val argument = param.args.firstOrNull() as? Int ?: return
                        val slot = if (method.name == "getServiceStateForSlot") argument else runCatching {
                            ModernReflect.callStaticMethod(ModernReflect.findClass("android.telephony.SubscriptionManager", loader),
                                "getSlotIndex", argument) as Int
                        }.getOrDefault(-1)
                        val selected = publishedProfiles[slot] ?: return
                        val copy = ServiceState(original)
                        copy.setOperatorName(selected.name, selected.name, selected.numeric)
                        param.result = copy
                    } catch (_: SecurityException) {
                        // A platform rejecting access keeps its already-filtered original result.
                    }
                }
            })
        }
    }

    private fun externalSuccessful(param: Hook.MethodHookParam) = param.throwable == null && engine != null &&
        Binder.getCallingUid() % 100000 >= Process.FIRST_APPLICATION_UID

    private fun installSubscriptionService(loader: ClassLoader) {
        val owner = ModernReflect.findClassIfExists(
            "com.android.internal.telephony.subscription.SubscriptionManagerService", loader) ?: return
        // Clone the already permission-filtered result. Never mutate the subscription database or
        // remove a check on the Binder entrypoint; internal radio clients keep the genuine record.
        owner.declaredMethods.filter { it.name in setOf("getActiveSubscriptionInfo", "getActiveSubscriptionInfoForSimSlotIndex",
            "getActiveSubscriptionInfoList", "getAllSubInfoList", "getAvailableSubscriptionInfoList") }.forEach { method ->
            Bridge.hookMethod(method, object : Hook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!externalSuccessful(param)) return
                    val selectedProfiles = publishedProfiles
                    fun copy(value: SubscriptionInfo): SubscriptionInfo {
                        if (value.simSlotIndex < 0 || value.mccString.isNullOrEmpty()) return value
                        val selected = selectedProfiles[value.simSlotIndex] ?: return value
                        return runCatching {
                            val builderClass = ModernReflect.findClass("android.telephony.SubscriptionInfo\$Builder", loader)
                            val builder = builderClass.getDeclaredConstructor(SubscriptionInfo::class.java).newInstance(value)
                            ModernReflect.callMethod(builder, "setMcc", selected.numeric.take(3))
                            ModernReflect.callMethod(builder, "setMnc", selected.numeric.drop(3))
                            ModernReflect.callMethod(builder, "setCountryIso", selected.region)
                            ModernReflect.callMethod(builder, "setCarrierName", selected.name)
                            ModernReflect.callMethod(builder, "build") as SubscriptionInfo
                        }.getOrElse { value }
                    }
                    param.result = when (val result = param.result) {
                        is SubscriptionInfo -> copy(result)
                        is List<*> -> result.map { if (it is SubscriptionInfo) copy(it) else it }
                        else -> result
                    }
                }
            })
        }
    }

    private fun startAfterCommit(context: Context) = HotReloadLifecycle.afterCommit {
        if (application != null) return@afterCommit
        runCatching {
            synchronized(lock) {
                val protected = context.createDeviceProtectedStorageContext()
                val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
                check(boot >= 0) { "Boot identity unavailable; refusing unsafe property backup" }
                val journal = protected.getSharedPreferences("onextbox_network_identity_journal_v1", Context.MODE_PRIVATE)
                val saved = if (journal.getInt("boot", -1) != boot) emptyMap() else
                    NetworkIdentityRules.properties.mapNotNull { key ->
                        val original = journal.getString("$key.original", null) ?: return@mapNotNull null
                        val applied = journal.getString("$key.applied", null) ?: return@mapNotNull null
                        key to NetworkIdentityProperties.Record(original, applied, journal.getString("$key.previous", null))
                    }.toMap()
                engine = NetworkIdentityProperties(::readProperty, ::writeProperty, { records ->
                    val editor = journal.edit().clear().putInt("boot", boot)
                    records.forEach { (key, value) ->
                        editor.putString("$key.original", value.original).putString("$key.applied", value.applied)
                            .putString("$key.previous", value.previous)
                    }
                    check(editor.commit()) { "Cannot persist recovery journal" }
                }, saved)
                application = protected
                preferences = ModernHookRuntime.remotePreferences("lsp_features")
            }
            register()
            refreshSafely()
        }.onFailure { log("Initialization failed; no new overrides will be applied", it) }
    }

    private fun selectedProfiles(): Map<Int, NetworkIdentityRules.Profile> =
        if (identityHooksActive()) configuredProfiles else emptyMap()
    private fun identityHooksActive() = AdaptationHookGate.isBoundProfileActive() &&
        AdaptationRuntimePolicy.allowsFeatureValue(HookConfigSnapshot.adaptationVersion(), NetworkIdentitySettings.ENABLED)
    private fun register() {
        if (application == null) return
        if (registered) return
        preferences?.registerOnSharedPreferenceChangeListener(listener)
        registered = true
    }
    private fun updateReceiver(active: Boolean) {
        val context = application ?: return
        if (active == receiverRegistered) return
        if (active) context.registerReceiver(receiver, IntentFilter().apply {
            addAction("android.intent.action.SIM_STATE_CHANGED")
            addAction("android.intent.action.SERVICE_STATE")
        }, Context.RECEIVER_EXPORTED) else context.unregisterReceiver(receiver)
        receiverRegistered = active
    }
    private fun unregister() {
        main.removeCallbacks(update)
        if (!registered) return
        preferences?.unregisterOnSharedPreferenceChangeListener(listener)
        updateReceiver(false)
        registered = false
    }
    private fun schedule(configurationChanged: Boolean = false) {
        if (configurationChanged) configurationDirty.set(true)
        main.removeCallbacks(update); main.postDelayed(update, 100)
    }
    private fun refreshSafely() {
        synchronized(lock) {
            if (bypass.get() == true || engine == null) return
            runCatching {
                if (configurationDirty.getAndSet(false)) {
                    // A failed remote read must not leave an old active profile cached.
                    configuredProfiles = emptyMap()
                    configuredProfiles = NetworkIdentitySettings.activeProfiles(
                        preferences?.all.orEmpty(), identityHooksActive())
                }
                val selected = selectedProfiles()
                raw { engine?.refreshSlots(selected) }
                publishedProfiles = selected
            }.onFailure(::restoreAfterFailure)
            runCatching { updateReceiver(selectedProfiles().isNotEmpty() || engine?.hasOverrides == true) }
                .onFailure { log("Cannot update identity broadcast subscription", it) }
        }
    }
    private fun restoreAfterFailure(error: Throwable) {
        publishedProfiles = emptyMap()
        log("Property refresh failed; attempting restoration", error)
        runCatching { raw { engine?.refresh(null) } }
            .onFailure { failure -> log("Restoration failed; recovery journal retained", failure) }
    }
    private fun readProperty(key: String) = propertyGet.invoke(null, key, "") as String
    private fun writeProperty(key: String, value: String) { propertySet.invoke(null, key, value) }
    private inline fun <T> raw(block: () -> T): T {
        val previous = bypass.get(); bypass.set(true)
        return try { block() } finally { bypass.set(previous) }
    }
    private fun log(message: String, error: Throwable) = ModernHookRuntime.log(Log.WARN, TAG, message, error)
}
