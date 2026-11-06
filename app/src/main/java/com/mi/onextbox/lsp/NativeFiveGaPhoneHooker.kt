package com.mi.onextbox.lsp

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.PersistableBundle
import android.os.Process
import android.provider.Settings
import android.telephony.TelephonyDisplayInfo
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernHotReloadSession
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import com.mi.onextbox.lsp.compat.SafeModeRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.ArrayDeque
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal object NativeFiveGaPhoneHooker {
    private const val TAG = "ONextBox-Native5GA"
    private const val SST_CLASS = "com.oplus.internal.telephony.OplusServiceStateTrackerImpl"
    private var discovery: Discovery? = null

    @Synchronized
    fun hook(loader: ClassLoader) {
        if (Process.myUid() != NativeFiveGaProtocol.PHONE_UID || Application.getProcessName() != "com.android.phone") return
        if (discovery != null) return
        Discovery(loader).also { discovery = it }.start()
    }

    private class Discovery(private val entryLoader: ClassLoader) {
        private val main = Handler(Looper.getMainLooper())
        private val observers = arrayListOf<XposedInterface.HookHandle>()
        private val scheduled = AtomicBoolean(false)
        @Volatile private var candidate: ClassLoader? = null
        @Volatile private var finished = false
        @Volatile private var retired = false
        @Volatile private var installing = false
        @Volatile private var attached = false
        private var attempts = 0
        private val poll = Runnable { schedule() }

        fun start() {
            HotReloadLifecycle.registerReplay("phone:native-five-ga-loader",
                capture = { candidate.takeIf { attached } },
                replay = { saved -> (saved as? ClassLoader)?.let(::attach) })
            HotReloadLifecycle.register("phone:native-five-ga-discovery",
                canReload = { finished && !installing },
                detach = { retired = true; stopObservers(); main.removeCallbacks(poll) },
                resumeAfterRejection = { if (!finished) schedule() })
            HotReloadLifecycle.afterCommit {
                if (retired || finished) return@afterCommit
                runCatching {
                    candidate = existingLoader()
                    if (candidate == null) observeLoads()
                    schedule()
                }.onFailure(::unavailable)
            }
        }

        private fun existingLoader(): ClassLoader? {
            runCatching { Class.forName(SST_CLASS, false, entryLoader) }.getOrNull()?.classLoader?.let { return it }
            val factory = runCatching {
                Class.forName("com.android.internal.telephony.PhoneFactory", false, entryLoader)
            }.getOrNull() ?: return null
            val phones = runCatching { factory.getMethod("getPhones").invoke(null) as? Array<*> }.getOrNull() ?: return null
            for (phone in phones.filterNotNull()) {
                val owner = runCatching {
                    val sst = Reflect.callMethod(phone, "getServiceStateTracker") ?: return@runCatching null
                    Reflect.getObjectField(sst, "mInterfaceImpl")
                }.getOrNull() ?: continue
                if (owner.javaClass.name == SST_CLASS) return owner.javaClass.classLoader
            }
            return null
        }

        private fun observeLoads() {
            val methods = arrayListOf<Method>()
            methods += ClassLoader::class.java.declaredMethods.filter {
                it.name == "loadClass" && it.returnType == Class::class.java && it.parameterTypes.firstOrNull() == String::class.java
            }
            val dexLoader = Class.forName("dalvik.system.BaseDexClassLoader")
            methods += dexLoader.declaredMethods.filter {
                it.name == "findClass" && it.returnType == Class::class.java && it.parameterTypes.firstOrNull() == String::class.java
            }
            check(methods.isNotEmpty()) { "Native telephony loader discovery unavailable" }
            for (method in methods.distinct()) {
                method.isAccessible = true
                runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                observers += ModernHotReloadSession.install(method) { chain ->
                    if (retired || finished || chain.args.firstOrNull() != SST_CLASS) chain.proceed()
                    else {
                        val result = chain.proceed()
                        val type = result as? Class<*>
                        if (type?.name == SST_CLASS) type.classLoader?.let {
                            candidate = it
                            schedule()
                        }
                        result
                    }
                }
            }
            HookLog.i(TAG, "Waiting for the native telephony extension loader")
        }

        private fun schedule() {
            if (retired || finished || !scheduled.compareAndSet(false, true)) return
            main.post {
                scheduled.set(false)
                if (retired || finished) return@post
                HotReloadLifecycle.withInfrastructureHook({}) {
                    runCatching {
                        attempts++
                        val actual = candidate ?: existingLoader()
                        if (actual != null) {
                            attach(actual)
                        } else if (attempts < 60) {
                            main.removeCallbacks(poll)
                            main.postDelayed(poll, 1000)
                        } else unavailable(IllegalStateException("Native telephony extension did not become available"))
                    }.onFailure(::unavailable)
                }
            }
        }

        private fun attach(actual: ClassLoader) {
            if (retired || attached) return
            installing = true
            try {
                Session(actual).install()
                candidate = actual
                attached = true
                finished = true
                stopObservers()
                main.removeCallbacks(poll)
                HookLog.i(TAG, "Native telephony extension loader attached")
            } finally { installing = false }
        }

        private fun stopObservers() {
            observers.asReversed().forEach { runCatching { it.unhook() } }
            observers.clear()
        }

        private fun unavailable(error: Throwable) {
            finished = true
            stopObservers()
            main.removeCallbacks(poll)
            HookLog.w(TAG, "Native 5G-A phone contract unavailable; original policy retained", error)
        }
    }

    private class Session(private val loader: ClassLoader) {
        private val sstType = Reflect.findClass("com.oplus.internal.telephony.OplusServiceStateTrackerImpl", loader)
        private val networkType = Reflect.findClass("com.oplus.internal.telephony.OplusNetworkManagerImpl", loader)
        private val dicType = Reflect.findClass("com.android.internal.telephony.DisplayInfoController", loader)
        private val ntcType = Reflect.findClass("com.android.internal.telephony.NetworkTypeController", loader)
        private val stockSst = Reflect.findClass("com.android.internal.telephony.ServiceStateTracker", loader)
        private val phoneType = Reflect.findClass("com.android.internal.telephony.Phone", loader)
        private val phoneFactory = Reflect.findClass("com.android.internal.telephony.PhoneFactory", loader)
        private val localConfig = Reflect.findClass("com.oplus.internal.telephony.localbus.LocalCarrierConfigManager", loader)
        private val nrcaType = Reflect.findClass("com.oplus.telephony.RadioNrcaInfo", loader)
        private val util = Reflect.findClass("com.oplus.internal.telephony.utils.OplusServiceStateTrackerUtil", loader)
        private val timerType = Reflect.findClass("com.oplus.internal.telephony.OplusServiceStateTrackerImpl\$3", loader)
        private val intType = Int::class.javaPrimitiveType!!
        private val sstGate = method(sstType, "hasFeature", String::class.java, intType)
        private val managerGate = method(networkType, "hasFeature", String::class.java, intType)
        private val onNrca = method(sstType, "onNrcaInfoChanged", nrcaType)
        private val nativeReset = method(sstType, "init5GADateInfo")
        private val nativeState = method(sstType, "get5GAState")
        private val nativeNewState = method(sstType, "getNew5GAState")
        private val inner = method(networkType, "getDisplayNetworkTypeFor5GA", intType, intType, intType)
        private val outer = method(networkType, "oplusUpdateTelephonyDisplayInfo", TelephonyDisplayInfo::class.java, dicType, ntcType, intType)
        private val dicUpdate = method(dicType, "updateTelephonyDisplayInfo")
        private val getPhoneId = phoneType.getMethod("getPhoneId")
        private val getSubId = phoneType.getMethod("getSubId")
        private val getPort = phoneType.getMethod("getUiccPort")
        private val getConfig = method(localConfig, "getConfigForSubId", intType)
        private val validNrca = method(util, "isVaildNrcaInfo", nrcaType)
        private val countNrca = method(util, "getTotalDLCellCountFromNrcaInfo", nrcaType)
        private val bandwidthNrca = method(util, "getTotalDLBandwidthKHzFromNrcaInfo", nrcaType)
        private val propsGet = Class.forName("android.os.SystemProperties").getMethod("get", String::class.java, String::class.java)
        private val source = UUID.randomUUID().toString()
        private val main = Handler(Looper.getMainLooper())
        private val records = ConcurrentHashMap<Int, Record>()
        private val handles = arrayListOf<XposedInterface.HookHandle>()
        private val gateBypass = ThreadLocal.withInitial { false }
        private val dicFrames = ThreadLocal.withInitial { ArrayDeque<DisplayFrame>() }
        private val outerFrames = ThreadLocal.withInitial { ArrayDeque<OuterFrame>() }
        private val timerOwners = WeakHashMap<Any, TimerOwner>()
        private val publicationLock = Any()
        private val publicationPending = AtomicBoolean(false)
        private var sequence = 0L
        private var lastSlots: List<NativeFiveGaSlot>? = null
        private var lastStatus = ""
        private var preferences: SharedPreferences? = null
        private var context: Context? = null
        @Volatile private var complete = false
        @Volatile private var retired = false
        private var bootstrapAttempts = 0
        private var registeredReceiver = false

        private data class Identity(val subId: Int, val profile: String, val port: Int, val config: Int)
        private class Record(val phoneId: Int, val phone: Any, val sst: Handler, val context: Context) {
            val lock = Any()
            var identity: Identity? = null
            var currentSubId = -1
            var currentProfile = ""
            var epoch = 0L
            var ready = false
            var armed = false
            var owned = false
            var dirty = false
            var fresh = false
            var qualified = false
            var reason = "waiting_sim"
            var count = 0
            var bandwidth = 0
            var network = 0
            var override = 0
            var cleanupFailed = false
        }
        private class DisplayFrame(val controller: Any, val record: Record?, val epoch: Long) {
            var branch = false
        }
        private class OuterFrame(val display: DisplayFrame?, val record: Record?, val epoch: Long) {
            var branch = false
        }
        private data class TimerOwner(val record: Record, val epoch: Long, val timer: Any?, val moduleOwned: Boolean)

        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> schedule() }
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                invalidateAll()
                schedule()
            }
        }
        private val refresh = Runnable {
            HotReloadLifecycle.withInfrastructureHook({}) {
                if (!retired) runCatching {
                    bootstrap()
                    records.values.forEach(::scheduleRecord)
                    publish()
                    if (enabled() || (preferences == null || records.isEmpty()) && bootstrapAttempts < 30)
                        main.postDelayed(refreshRunnable(), 2000)
                }.onFailure { failClosed(it) }
            }
        }
        private fun refreshRunnable(): Runnable = refresh

        private fun method(owner: Class<*>, name: String, vararg parameters: Class<*>) =
            owner.getDeclaredMethod(name, *parameters).apply { isAccessible = true }

        private fun enabled() = complete && !retired && NativeFiveGaSettings.getEnabled() && !SafeModeRuntime.blocked

        private fun installRaw(target: Executable, action: (XposedInterface.Chain) -> Any?) {
            target.isAccessible = true
            handles += ModernHotReloadSession.install(target) { chain ->
                HotReloadLifecycle.withInfrastructureHook({ chain.proceed() }) { action(chain) }
            }
        }

        fun install() {
            check(Handler::class.java.isAssignableFrom(sstType))
            check(sstGate.returnType == Boolean::class.javaPrimitiveType && managerGate.returnType == Boolean::class.javaPrimitiveType)
            check(inner.returnType == intType && outer.returnType == TelephonyDisplayInfo::class.java)
            check(nativeState.returnType == Boolean::class.javaPrimitiveType && validNrca.returnType == Boolean::class.javaPrimitiveType)
            sstType.getDeclaredField("mPhone"); sstType.getDeclaredField("m5GASmoothTimer")
            stockSst.getDeclaredField("mInterfaceImpl"); timerType.getDeclaredField("this\$0")
            dicType.getDeclaredField("mPhone"); dicType.getDeclaredField("mTelephonyDisplayInfo")
            val timerRun = method(timerType, "run")
            val updateState = method(sstType, "updatae5GAState", intType, intType, intType)
            val requiredCallers = listOf(sstGate, managerGate, onNrca,
                method(sstType, "handleMessage", Message::class.java),
                method(sstType, "updateDisplayInfoForNRPhysicalChanged", List::class.java, List::class.java),
                method(stockSst, "handleMessage", Message::class.java),
                method(networkType, "getCustomizedDisplayNetworkType", intType, intType, intType),
                inner, outer, dicUpdate, nativeState, nativeNewState, nativeReset, updateState, timerRun)
            requiredCallers.forEach { check(ModernHookRuntime.requireModule().deoptimize(it)) { "Cannot deoptimize ${it.name}" } }
            try {
                for (gate in listOf(sstGate, managerGate)) installRaw(gate) { chain ->
                    val original = chain.proceed()
                    if (original == true || gateBypass.get() == true || chain.args[0] != NativeFiveGaPhonePolicy.FEATURE) original
                    else runCatching {
                        val record = records[chain.args[1] as Int]
                        record != null && synchronized(record.lock) { live(record) }
                    }.getOrDefault(false) || original == true
                }
                sstType.declaredConstructors.forEach { constructor -> installRaw(constructor) { chain ->
                    val result = chain.proceed()
                    runCatching { remember(chain.thisObject); schedule() }.onFailure(::failClosed)
                    result
                } }
                installRaw(onNrca) { chain ->
                    val record = remember(chain.thisObject)
                    if (record == null) chain.proceed() else synchronized(record.lock) {
                        runCatching { observeInput(record, chain.args.firstOrNull()) }.onFailure { revoke(record, "restart_required") }
                        try { chain.proceed() } finally { runCatching {
                            observeNative(record)
                            if (live(record) && record.fresh && !record.qualified && nativeState.invoke(record.sst) == true) {
                                Reflect.callMethod(record.phone, "getDisplayInfoController")?.let { dicUpdate.invoke(it) }
                            }
                            publish()
                        }.onFailure(::failClosed) }
                    }
                }
                installRaw(nativeReset) { chain ->
                    val record = records.values.firstOrNull { it.sst === chain.thisObject }
                    if (record == null) chain.proceed() else synchronized(record.lock) {
                        record.fresh = false; record.qualified = false
                        val result = chain.proceed()
                        runCatching { publish() }.onFailure(::failClosed)
                        result
                    }
                }
                installRaw(dicUpdate) { chain ->
                    val phone = runCatching { Reflect.getObjectField(chain.thisObject!!, "mPhone") }.getOrNull()
                    val record = phone?.let { records[getPhoneId.invoke(it) as Int] }
                    val frame = DisplayFrame(chain.thisObject!!, record, record?.epoch ?: -1L)
                    val stack = requireNotNull(dicFrames.get()); stack.addLast(frame)
                    var succeeded = false
                    try { chain.proceed().also { succeeded = true } } finally {
                        stack.removeLast()
                        runCatching { finishDisplay(frame, succeeded); publish() }.onFailure(::failClosed)
                    }
                }
                installRaw(outer) { chain ->
                    val record = records[chain.args[3] as Int]
                    val frame = OuterFrame(requireNotNull(dicFrames.get()).lastOrNull()?.takeIf { it.controller === chain.args[1] }, record, record?.epoch ?: -1L)
                    val stack = requireNotNull(outerFrames.get()); stack.addLast(frame)
                    try {
                        chain.proceed().also { result ->
                            val display = result as? TelephonyDisplayInfo
                            frame.display?.takeIf { it.record === record && it.epoch == frame.epoch }?.let {
                                it.branch = frame.branch && display?.networkType == 20 && display.overrideNetworkType == 5 &&
                                    frame.record?.epoch == frame.epoch
                            }
                        }
                    } finally { stack.removeLast() }
                }
                installRaw(inner) { chain ->
                    val result = chain.proceed()
                    runCatching {
                        val frame = requireNotNull(outerFrames.get()).lastOrNull()
                        val record = frame?.record
                        if (record != null && record.phoneId == chain.args[2] && record.epoch == frame.epoch) synchronized(record.lock) {
                            frame.branch = NativeFiveGaPhonePolicy.certified(live(record), record.fresh,
                                result == 5 && chain.args[1] == 20, nativeState.invoke(record.sst) == true, 20, result as? Int ?: 0)
                        }
                    }.onFailure(::failClosed)
                    result
                }
                timerType.declaredConstructors.forEach { constructor -> installRaw(constructor) { chain ->
                    val result = chain.proceed()
                    runCatching {
                        val owner = Reflect.getObjectField(chain.thisObject!!, "this\$0")
                        records.values.firstOrNull { it.sst === owner }?.let { record ->
                            synchronized(timerOwners) { timerOwners[chain.thisObject!!] = TimerOwner(record, record.epoch, Reflect.getObjectField(owner!!, "m5GASmoothTimer"), record.owned) }
                        }
                    }.onFailure(::failClosed)
                    result
                } }
                installRaw(timerRun) { chain ->
                    val owner = synchronized(timerOwners) { timerOwners[chain.thisObject] }
                    if (owner == null || !owner.moduleOwned) chain.proceed() else synchronized(owner.record.lock) {
                        var factoryOwned = false
                        val current = runCatching {
                            val sameTimer = Reflect.getObjectField(owner.record.sst, "m5GASmoothTimer") === owner.timer
                            if (sameTimer && originalAdvanced(owner.record)) {
                                if (sameIdentity(owner.record)) {
                                    revoke(owner.record, "waiting_native")
                                    owner.record.owned = false
                                    factoryOwned = true
                                    true
                                } else {
                                    owner.record.cleanupFailed = true
                                    revoke(owner.record, "restart_required")
                                    false
                                }
                            } else owner.epoch == owner.record.epoch && live(owner.record) && sameTimer
                        }.getOrElse {
                            failClosed(it); false
                        }
                        if (current) chain.proceed().also { if (factoryOwned) publish() } else null
                    }
                }
                val configChanged = method(localConfig, "handleCarrierConfigChanged", intType, intType)
                installRaw(configChanged) { chain ->
                    records[chain.args[0] as Int]?.let { synchronized(it.lock) { revoke(it, "waiting_config"); it.dirty = true } }
                    val result = chain.proceed()
                    schedule(); result
                }
                complete = true
                HotReloadLifecycle.register("phone:native-five-ga", canPrepare = {
                    records.values.none { record -> synchronized(record.lock) { record.owned } }
                }, canReload = {
                    records.values.none { record -> synchronized(record.lock) { record.owned } }
                },
                    detach = {
                        retired = true; complete = false; invalidateAll(); NativeFiveGaBridge.retirePublisher()
                        main.removeCallbacks(refresh)
                        preferences?.unregisterOnSharedPreferenceChangeListener(listener); preferences = null
                        if (registeredReceiver) context?.unregisterReceiver(receiver)
                    }, resumeAfterRejection = { schedule() })
                HotReloadLifecycle.afterCommit { observePreferences(); schedule() }
                HookLog.i(TAG, "Native 5G-A phone contract installed")
            } catch (error: Throwable) {
                complete = false
                handles.asReversed().forEach { runCatching { it.unhook() } }
                throw error
            }
        }

        private fun schedule() { if (!retired) { main.removeCallbacks(refresh); main.post(refresh) } }

        private fun observePreferences() {
            if (preferences == null) ModernHookRuntime.remotePreferences("lsp_features")?.let {
                it.registerOnSharedPreferenceChangeListener(listener); preferences = it
            }
        }

        private fun bootstrap() {
            observePreferences()
            bootstrapAttempts++
            if (context == null) context = runCatching {
                Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Context
            }.getOrNull()
            if (!registeredReceiver) context?.let {
                val filter = IntentFilter().apply {
                    addAction("android.intent.action.SIM_STATE_CHANGED")
                    addAction("android.telephony.action.SIM_APPLICATION_STATE_CHANGED")
                    addAction("android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED")
                    addAction("android.telephony.action.CARRIER_CONFIG_CHANGED")
                    addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                }
                it.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                registeredReceiver = true
            }
            val phones = runCatching { phoneFactory.getMethod("getPhones").invoke(null) as? Array<*> }.getOrNull() ?: return
            val present = hashSetOf<Int>()
            phones.filterNotNull().forEach { phone ->
                present += getPhoneId.invoke(phone) as Int
                val stock = Reflect.callMethod(phone, "getServiceStateTracker") ?: return@forEach
                remember(Reflect.getObjectField(stock, "mInterfaceImpl"))
            }
            records.entries.toList().filter { it.key !in present }.forEach { (slot, record) ->
                records.remove(slot, record)
                synchronized(record.lock) { revoke(record, "waiting_sim") }
            }
        }

        private fun remember(owner: Any?): Record? {
            if (!sstType.isInstance(owner)) return null
            val phone = Reflect.getObjectField(owner!!, "mPhone") ?: return null
            val phoneId = getPhoneId.invoke(phone) as Int
            if (phoneId !in 0..1) return null
            records[phoneId]?.takeIf { it.sst === owner }?.let { return it }
            val app = Reflect.callMethod(phone, "getContext") as? Context ?: return null
            context = app
            val record = Record(phoneId, phone, owner as Handler, app)
            records.put(phoneId, record)?.let { synchronized(it.lock) { revoke(it, "waiting_sim") } }
            scheduleRecord(record)
            return record
        }

        private fun scheduleRecord(record: Record) {
            record.sst.post { HotReloadLifecycle.withInfrastructureHook({}) {
                if (!retired) runCatching { synchronized(record.lock) { reconcile(record) }; publish() }.onFailure(::failClosed)
            } }
        }

        private fun identity(record: Record): Pair<Identity?, String> {
            val subId = getSubId.invoke(record.phone) as Int
            val profile = propsGet.invoke(null, "sys.carrier_id${record.phoneId}", "") as String
            record.currentSubId = subId; record.currentProfile = profile
            if (!NativeFiveGaPhonePolicy.supportedProfile(profile)) return null to "unsupported"
            if (subId < 0 || Reflect.callMethod(Reflect.callMethod(record.phone, "getIccCard") ?: return null to "waiting_sim", "getState").toString() != "LOADED")
                return null to "waiting_sim"
            val port = getPort.invoke(record.phone) ?: return null to "waiting_sim"
            val portIndex = Reflect.callMethod(port, "getPortIdx") as? Int ?: return null to "waiting_sim"
            val config = runCatching {
                getConfig.invoke(method(localConfig, "getInstance").invoke(null), subId) as? PersistableBundle
            }.getOrNull()
                ?: return null to "waiting_config"
            val thresholds = config.getStringArray(NativeFiveGaPhonePolicy.THRESHOLDS)
            if (!NativeFiveGaPhonePolicy.configReady(config.getBoolean("carrier_config_applied_bool", false), thresholds))
                return null to "waiting_config"
            val key = listOf(thresholds!!.toList(), config.getLong(NativeFiveGaPhonePolicy.SMOOTHING, 20000),
                config.getBoolean("carrier_oplus_nsa_5ga_enable"), config.getBoolean("carrier_oplus_nsa_5gplus_enable")).hashCode()
            return Identity(subId, profile, portIndex, key) to "waiting_native"
        }

        private fun live(record: Record): Boolean {
            return NativeFiveGaPhonePolicy.mayRestore(enabled(), SafeModeRuntime.blocked, record.ready && record.armed && !record.dirty, record.owned) &&
                sameIdentity(record) && !originalAdvanced(record)
        }

        private fun sameIdentity(record: Record): Boolean {
            val id = record.identity ?: return false
            return records[record.phoneId] === record &&
                getSubId.invoke(record.phone) == id.subId && propsGet.invoke(null, "sys.carrier_id${record.phoneId}", "") == id.profile &&
                getPort.invoke(record.phone)?.let { Reflect.callMethod(it, "getPortIdx") } == id.port &&
                Reflect.callMethod(Reflect.callMethod(record.phone, "getIccCard") ?: return false, "getState").toString() == "LOADED"
        }

        private fun originalAdvanced(record: Record): Boolean {
            gateBypass.set(true)
            return try { listOf(NativeFiveGaPhonePolicy.FEATURE, "oplus.software.radio.icon_nrad_5gplus",
                "oplus.software.radio.icon_nrad_5gplus_plus", "oplus.software.radio.icon_nrad_5gdot_5g")
                .any { sstGate.invoke(record.sst, it, record.phoneId) == true }
            } finally { gateBypass.set(false) }
        }

        private fun reconcile(record: Record) {
            if (records[record.phoneId] !== record) return
            val (identity, reason) = identity(record)
            val changed = record.dirty || identity != record.identity
            if (record.owned && originalAdvanced(record)) {
                revoke(record, "waiting_native")
                record.owned = false
            }
            if (!enabled() || identity == null || changed) {
                revoke(record, if (!enabled()) "disabled" else reason)
                if (record.owned) resetOwned(record)
                record.identity = identity; record.dirty = false
                record.ready = identity != null
            }
            if (!enabled() || identity == null || record.cleanupFailed) return
            if (!record.armed) {
                record.owned = !originalAdvanced(record)
                if (!record.owned) { record.reason = "waiting_native"; return }
                resetOwned(record)
                if (record.cleanupFailed || !record.owned) return
                record.epoch++; record.armed = true; record.reason = "waiting_native"
                if (!record.sst.hasMessages(31)) record.sst.sendEmptyMessage(31)
                HookLog.i(TAG, "Native 5G-A armed: slot=${record.phoneId}, profile=${identity.profile}, epoch=${record.epoch}")
            }
            observeNative(record)
        }

        private fun resetOwned(record: Record) {
            record.armed = false; record.fresh = false; record.qualified = false
            runCatching {
                if (originalAdvanced(record)) {
                    record.owned = false
                    record.reason = "waiting_native"
                    return
                }
                nativeReset.invoke(record.sst)
                Reflect.callMethod(record.phone, "getDisplayInfoController")?.let { dicUpdate.invoke(it) }
            }.onFailure { record.cleanupFailed = true; record.reason = "restart_required" }
        }

        private fun observeInput(record: Record, input: Any?) {
            if (!live(record)) { record.fresh = false; record.qualified = false; return }
            val service = Reflect.callMethod(record.phone, "getServiceState")
            val rat = service?.let { Reflect.callMethod(it, "getDataNetworkType") } as? Int ?: 0
            val radio = Reflect.getObjectField(record.phone, "mCi")?.let { Reflect.callMethod(it, "getRadioState") } as? Int ?: 0
            val airplane = Settings.Global.getInt(record.context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
            val domainValid = rat == 20 && radio == 1 && !airplane
            val valid = input != null && domainValid && validNrca.invoke(null, input) == true
            if (valid) {
                record.fresh = true
                record.count = countNrca.invoke(null, input) as Int
                record.bandwidth = bandwidthNrca.invoke(null, input) as Int
            } else if (!domainValid || !record.fresh) {
                record.fresh = false; record.qualified = false; record.count = 0; record.bandwidth = 0
            }
        }

        private fun observeNative(record: Record) {
            if (!live(record)) { record.qualified = false; return }
            if (nativeState.invoke(record.sst) != true) record.qualified = false
            record.reason = if (record.qualified) {
                if (nativeNewState.invoke(record.sst) == true) "qualified" else "holding"
            } else "waiting_native"
        }

        private fun finishDisplay(frame: DisplayFrame, succeeded: Boolean) {
            val record = frame.record ?: return
            synchronized(record.lock) {
                val output = Reflect.getObjectField(frame.controller, "mTelephonyDisplayInfo") as? TelephonyDisplayInfo
                val previousNetwork = record.network
                record.network = output?.networkType ?: 0; record.override = output?.overrideNetworkType ?: 0
                if (record.network != 20) {
                    record.fresh = false; record.count = 0; record.bandwidth = 0
                }
                record.qualified = NativeFiveGaPhonePolicy.certified(live(record), record.fresh,
                    succeeded && frame.branch && frame.epoch == record.epoch, nativeState.invoke(record.sst) == true,
                    record.network, record.override)
                observeNative(record)
                if (previousNetwork != 20 && record.network == 20 && !record.fresh && live(record) && !record.sst.hasMessages(31)) {
                    record.sst.sendEmptyMessage(31)
                }
            }
        }

        private fun revoke(record: Record, reason: String) {
            record.epoch++; record.armed = false; record.qualified = false; record.fresh = false
            record.count = 0; record.bandwidth = 0; record.reason = reason
        }

        private fun invalidateAll() {
            records.values.forEach { synchronized(it.lock) { revoke(it, if (enabled()) "waiting_sim" else "disabled"); it.dirty = true } }
            publish()
        }

        private fun failClosed(error: Throwable) {
            complete = false
            records.values.forEach { record -> record.sst.post {
                synchronized(record.lock) {
                    revoke(record, "restart_required")
                    if (record.owned) resetOwned(record)
                }
                publish()
            } }
            publish()
            HookLog.w(TAG, "Native 5G-A paused; scope restart required", error)
        }

        private fun publish() {
            if (publicationPending.compareAndSet(false, true)) main.post {
                publicationPending.set(false)
                runCatching { publishNow() }.onFailure {
                    HookLog.w(TAG, "Native 5G-A status publication unavailable", it)
                }
            }
        }

        private fun publishNow() {
            val app = context ?: return
            val slots = records.values.sortedBy { it.phoneId }.map { record -> synchronized(record.lock) {
                val id = record.identity
                NativeFiveGaSlot(record.phoneId, id?.subId ?: record.currentSubId, id?.profile ?: record.currentProfile, record.epoch,
                    record.qualified && enabled(), record.network, record.override, record.ready,
                    if (!complete || record.cleanupFailed) "restart_required" else if (!enabled()) "disabled" else record.reason,
                    record.count, record.bandwidth)
            } }
            val status = when {
                !complete -> "restart_required"
                !enabled() -> "disabled"
                slots.isEmpty() -> "waiting_sim"
                slots.any { it.qualified } -> if (slots.any { it.reason == "qualified" }) "qualified" else "holding"
                else -> slots.first().reason
            }
            synchronized(publicationLock) {
                if (lastSlots == slots && lastStatus == status) return
                lastSlots = slots; lastStatus = status
                NativeFiveGaBridge.publish(app, NativeFiveGaSnapshot(source, ++sequence, slots, status))
            }
        }
    }
}
