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
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Collects a passive, privacy-safe C17 eUICC status snapshot in the privileged LPA process. */
internal object EuiccDiagnosticsHooker {
    private const val TAG = "ONextBox-EuiccDiag"
    private const val APPLICATION_CLASS = "com.oplus.euicc.OplusEuiccApplication"
    private const val DOWNLOAD_CHECKER_CLASS = "com.oplus.euicc.DownloadChecker"
    private const val PROFILE_ASSISTANT_FACTORY_CLASS = "p5.a"
    private const val RUS_PROVIDER_CLASS = "x5.a"
    private const val EUICC_INFO_CALLBACK_CLASS =
        "com.oplus.euicc.sdk.profileassistant.EuiccManager\$b"
    private const val SMDP_CALLBACK_CLASS =
        "com.oplus.euicc.sdk.profileassistant.EuiccManager\$e"
    private const val EUICC_INFO_MODEL_CLASS =
        "com.oplus.euicc.sdk.profileassistant.internal.protocol.phase2.models.l"
    private const val SMDP_MODEL_CLASS =
        "com.oplus.euicc.sdk.profileassistant.internal.protocol.phase2.models.c0"
    private const val ESIM_EID_PROPERTY = "persist.sys.oplus.radio.esim.eid"
    private const val ESIM_POWER_PROPERTY = "persist.vendor.oplus.radio.esim.gpio.status"
    private const val ESIM_BINDING_PROPERTY =
        "persist.sys.oplus.radio.esim.binding.pairing.result"
    private const val MOBILE_COUNTRY_PROPERTY = "gsm.operator.iso-country"
    private const val MEP_FEATURE = "android.hardware.telephony.euicc.mep"

    private val collector = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "OOST-eSIM-diagnostics").apply { isDaemon = true }
    }
    private val collecting = AtomicBoolean(false)
    private val receiverRegistered = AtomicBoolean(false)
    private val snapshotLock = Any()

    @Volatile
    private var latestSnapshot: EsimDiagnosticsSnapshot? = null

    fun hook(classLoader: ClassLoader?): Boolean {
        val applicationClass = findClass(APPLICATION_CLASS, classLoader)
        val onCreate = applicationClass?.declaredMethods?.singleOrNull { method ->
            method.name == "onCreate" && method.parameterCount == 0 && method.returnType == Void.TYPE
        }
        if (onCreate == null) {
            HookLog.w(TAG, "Compatible C17 eUICC Application.onCreate target was not found")
            return false
        }

        return runCatching {
            installAfterHook("euicc:diagnostics-application", onCreate) { param ->
                val application = param.thisObject as? Application ?: return@installAfterHook
                onApplicationReady(application, classLoader)
            }
            hookLastDownloadResult(classLoader)
            HookLog.i(TAG, "Installed passive C17 eSIM diagnostics collector")
            true
        }.onFailure { throwable ->
            HookLog.e(TAG, "Failed to install C17 eSIM diagnostics collector", throwable)
        }.getOrDefault(false)
    }

    private fun onApplicationReady(application: Application, classLoader: ClassLoader?) {
        registerRefreshReceiver(application, classLoader)
        requestCollection(application, classLoader)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag") // The legacy overload is used only below API 33.
    private fun registerRefreshReceiver(context: Context, classLoader: ClassLoader?) {
        if (!receiverRegistered.compareAndSet(false, true)) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent?) {
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
        }.onFailure { throwable ->
            receiverRegistered.set(false)
            HookLog.e(TAG, "Could not register the eSIM diagnostics refresh receiver", throwable)
        }
    }

    private fun requestCollection(context: Context, classLoader: ClassLoader?) {
        if (!collecting.compareAndSet(false, true)) return
        collector.execute {
            try {
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

        val assistant = loadProfileAssistant(classLoader)
        readProfileLimit(context, classLoader)?.let { limit ->
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
            requestEuiccInfo(context, assistant, classLoader)
            requestSmdpInfo(context, assistant, classLoader)
        } else {
            updateSnapshot(context) { it.copy(collectionMessage = "esim_power_off") }
        }
    }

    private fun loadProfileAssistant(classLoader: ClassLoader?): Any? = runCatching {
        val factory = requireNotNull(findClass(PROFILE_ASSISTANT_FACTORY_CLASS, classLoader))
        val getter = factory.declaredMethods.single { method ->
            method.name == "b" && Modifier.isStatic(method.modifiers) && method.parameterCount == 0
        }
        getter.isAccessible = true
        getter.invoke(null)
    }.onFailure { throwable ->
        HookLog.e(TAG, "Could not obtain the OEM profile assistant", throwable)
    }.getOrNull()

    private fun readProfileLimit(context: Context, classLoader: ClassLoader?): Int? = runCatching {
        val providerClass = requireNotNull(findClass(RUS_PROVIDER_CLASS, classLoader))
        val getInstance = providerClass.declaredMethods.single { method ->
            method.name == "d" && Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.contentEquals(arrayOf(Context::class.java))
        }
        val provider = getInstance.invoke(null, context)
        val getLimit = provider.javaClass.methods.single { method ->
            method.name == "e" && method.parameterCount == 0 &&
                method.returnType == Int::class.javaPrimitiveType
        }
        getLimit.invoke(provider) as? Int
    }.onFailure { throwable ->
        HookLog.e(TAG, "Could not read the current OEM profile limit", throwable)
    }.getOrNull()

    private fun readProfiles(assistant: Any): ProfileCollection? = runCatching {
        val registryGetter = assistant.javaClass.methods.single { method ->
            method.name == "c" && method.parameterCount == 0
        }
        val registry = requireNotNull(registryGetter.invoke(assistant))
        val profiles = ArrayList<Any>()
        val getProfiles = registry.javaClass.methods.single { method ->
            method.name == "g" && method.parameterCount == 1 &&
                List::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                method.returnType == Boolean::class.javaPrimitiveType
        }
        val succeeded = getProfiles.invoke(registry, profiles) as? Boolean ?: false
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
        classLoader: ClassLoader?,
    ) {
        runCatching {
            val manager = requireNotNull(
                assistant.javaClass.methods.single { method ->
                    method.name == "b" && method.parameterCount == 0
                }.invoke(assistant),
            )
            val callbackClass = requireNotNull(findClass(EUICC_INFO_CALLBACK_CLASS, classLoader))
            val callback = Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass),
            ) { proxy, method, args ->
                when {
                    method.name == "toString" -> "OOST-eSIM-info-callback"
                    method.name == "hashCode" -> System.identityHashCode(proxy)
                    method.name == "equals" -> proxy === args?.getOrNull(0)
                    args?.firstOrNull()?.javaClass?.name == EUICC_INFO_MODEL_CLASS -> {
                        val info = args.first()
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
                        defaultReturnValue(method.returnType)
                    }
                    else -> defaultReturnValue(method.returnType)
                }
            }
            manager.javaClass.methods.single { method ->
                method.name == "b" && method.parameterTypes.contentEquals(arrayOf(callbackClass))
            }.invoke(manager, callback)
        }.onFailure { throwable ->
            HookLog.e(TAG, "Could not request OEM eUICC information", throwable)
        }
    }

    private fun requestSmdpInfo(
        context: Context,
        assistant: Any,
        classLoader: ClassLoader?,
    ) {
        runCatching {
            val manager = requireNotNull(
                assistant.javaClass.methods.single { method ->
                    method.name == "b" && method.parameterCount == 0
                }.invoke(assistant),
            )
            val callbackClass = requireNotNull(findClass(SMDP_CALLBACK_CLASS, classLoader))
            val callback = Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass),
            ) { proxy, method, args ->
                when {
                    method.name == "toString" -> "OOST-eSIM-SM-DP-callback"
                    method.name == "hashCode" -> System.identityHashCode(proxy)
                    method.name == "equals" -> proxy === args?.getOrNull(0)
                    args?.firstOrNull()?.javaClass?.name == SMDP_MODEL_CLASS -> {
                        val text = args.first().toString()
                        val smdp = Regex("mSmdpAddress='([^']*)'").find(text)?.groupValues?.get(1).orEmpty()
                        val smds = Regex("mSmdsAddress='([^']*)'").find(text)?.groupValues?.get(1).orEmpty()
                        updateSnapshot(context) { current ->
                            current.copy(
                                capturedAtMillis = System.currentTimeMillis(),
                                smdpAddress = smdp,
                                smdsAddress = smds,
                            )
                        }
                        defaultReturnValue(method.returnType)
                    }
                    else -> defaultReturnValue(method.returnType)
                }
            }
            manager.javaClass.methods.single { method ->
                method.name == "a" && method.parameterTypes.contentEquals(arrayOf(callbackClass))
            }.invoke(manager, callback)
        }.onFailure { throwable ->
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
