package com.mi.onextbox.lsp

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernHotReloadSession
import com.mi.onextbox.lsp.compat.SafeModeRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.lang.reflect.Executable
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal object NativeFiveGaUiHooker {
    private const val TAG = "ONextBox-Native5GA-UI"
    private const val REPO = "com.android.systemui.statusbar.pipeline.mobile.data.repository.prod.MobileConnectionRepositoryImpl"
    private const val RESOLVED = "com.android.systemui.statusbar.pipeline.mobile.data.model.ResolvedNetworkType"
    private const val TEXT = "com.android.systemui.statusbar.pipeline.mobile.domain.model.OplusNetworkTypeTextModel"
    private const val GROUP = "com.android.settingslib.SignalIcon\$MobileIconGroup"

    fun hook(loader: ClassLoader) {
        if (!NativeFiveGaSettings.getEnabled() || SafeModeRuntime.blocked) return
        runCatching { Runtime(loader).install() }.onFailure {
            HookLog.w(TAG, "Native 5G-A adapter unavailable; keeping native network presentation", it)
        }
    }

    private class Runtime(private val loader: ClassLoader) {
        private fun type(name: String) = Class.forName(name, false, loader)
        private val main = Handler(Looper.getMainLooper())
        private val frames = ThreadLocal.withInitial { ArrayList<Frame>() }
        private val handles = arrayListOf<XposedInterface.HookHandle>()
        private val sessions = arrayListOf<WeakReference<Session>>()
        private val resolvedTokens = WeakIdentityMap<Token>()
        private val groupTokens = WeakIdentityMap<Token>()
        private val modelTokens = WeakIdentityMap<Token>()
        private val iconTokens = WeakIdentityMap<Token>()
        private val textBindings = WeakIdentityMap<Bound>()
        private val iconBindings = WeakIdentityMap<Bound>()
        private val delivered = AtomicBoolean()
        private val renderedPaths = ConcurrentHashMap.newKeySet<String>()
        private val failed = AtomicBoolean()
        private val flow = type("kotlinx.coroutines.flow.Flow")
        private val stateFlow = type("kotlinx.coroutines.flow.StateFlow")
        private val mutableStateFlow = type("kotlinx.coroutines.flow.MutableStateFlow")
        private val function3 = type("kotlin.jvm.functions.Function3")
        private val function6 = type("kotlin.jvm.functions.Function6")
        private val continuation = type("kotlin.coroutines.Continuation")
        private val repoType = type(REPO)
        private val transformType = type("$REPO\$resolvedNetworkType\$3")
        private val flowFacade = type("kotlinx.coroutines.flow.FlowKt")
        private val flowZip = type("kotlinx.coroutines.flow.FlowKt__ZipKt")
        private val flowShare = type("kotlinx.coroutines.flow.FlowKt__ShareKt")
        private val combine2 = flowFacade.getMethod("combine", flow, flow, function3)
        private val combine5 = flowZip.getMethod("combine", flow, flow, flow, flow, flow, function6)
        private val share = flowShare.getMethod("stateIn", flow, type("kotlinx.coroutines.CoroutineScope"),
            type("kotlinx.coroutines.flow.SharingStarted"), Any::class.java)
        private val mutableFactory = type("kotlinx.coroutines.flow.StateFlowKt").getMethod("MutableStateFlow", Any::class.java)
        private val setRevision = mutableStateFlow.getMethod("setValue", Any::class.java)
        private val getValue = stateFlow.getMethod("getValue")
        private val lookup = type(RESOLVED).getMethod("getLookupKey")
        private val overrideConstructor = type("$RESOLVED\$OverrideNetworkType").getConstructor(String::class.java)
        private val unknown = type("$RESOLVED\$UnknownNetworkType").getField("INSTANCE").get(null)
        private val callbackField = repoType.getDeclaredField("callbackEvents").apply { isAccessible = true }
        private val resolvedField = repoType.getDeclaredField("resolvedNetworkType").apply { isAccessible = true }
        private val transformOwner = transformType.getDeclaredField("this\$0").apply { isAccessible = true }
        private val groupType = type(GROUP)
        private val groupConstructor = groupType.getConstructor(String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        private val groupName = type("com.android.settingslib.SignalIcon\$IconGroup").getField("name")
        private val groupDescription = groupType.getField("dataContentDescription")
        private val groupDataType = groupType.getField("dataType")
        private val textType = type("$TEXT\$DefaultNetworkTypeText")
        private val textConstructor = textType.getConstructor(groupType)
        private val textGroup = textType.getMethod("getIconGroup")
        private val defaultIcon = type("com.android.systemui.statusbar.pipeline.mobile.domain.model.NetworkTypeIconModel\$DefaultIcon")
            .getConstructor(groupType)
        private val suspended = type("kotlin.coroutines.intrinsics.CoroutineSingletons").getField("COROUTINE_SUSPENDED").get(null)
        private val mobileCompanion = type("com.oplus.systemui.statusbar.policy.MobileIconSets").getField("Companion").get(null)
        private val gaKey = type("com.oplus.systemui.statusbar.policy.MobileIconSets\$Companion")
            .getMethod("toDisplayIconKey", Int::class.javaPrimitiveType).invoke(mobileCompanion, 105) as String
        private var subscription: AutoCloseable? = null
        private var prefs: SharedPreferences? = null
        private var revision = 0L
        @Volatile private var snapshot: NativeFiveGaSnapshot? = null
        @Volatile private var installed = false
        @Volatile private var retired = false
        private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

        private inner class Frame(val repository: Any, val context: Context, val scope: Any) {
            var matches = 0
            var combined: Any? = null
            var shared: Any? = null
            var session: Session? = null
        }

        private inner class Session(repository: Any) {
            private val owner = WeakReference(repository)
            private val lineages = HashMap<Int, NativeFiveGaUiLineage>()
            val subId = callInt(repository, "getSubId")
            val pulse = mutableFactory.invoke(null, 0L)!!
            @Volatile var ready = false
            @Volatile var failed = false
            @Volatile var lastPhoneId = -1
            @Volatile var lastDisplay: Any? = null
            @Volatile var lastService: Any? = null
            fun alive() = owner.get() != null
            fun pulse() { setRevision.invoke(pulse, revision) }
            fun nativeCurrent(): Boolean = runCatching {
                val repo = owner.get() ?: return@runCatching false
                val aggregate = getValue.invoke(callbackField.get(repo)) ?: return@runCatching false
                val displayEvent = call(aggregate, "getOnDisplayInfoChanged") ?: return@runCatching false
                val serviceEvent = call(aggregate, "getOnServiceStateChanged") ?: return@runCatching false
                val display = call(displayEvent, "getTelephonyDisplayInfo") ?: return@runCatching false
                val service = call(serviceEvent, "getServiceState") ?: return@runCatching false
                display == lastDisplay && service == lastService &&
                    callInt(display, "getNetworkType") == 20 && callInt(display, "getOverrideNetworkType") == 5 &&
                    inService(service)
            }.getOrDefault(false)
            fun token(): Token? {
                val current = snapshot ?: return null
                val proof = current.slots.singleOrNull { it.subId == subId && it.phoneId == lastPhoneId } ?: return null
                if (!ready || failed || !NativeFiveGaUiPolicy.matches(enabled(), current.source, current.source,
                        lastPhoneId, subId, proof.epoch, proof.phoneId, proof.subId, proof.epoch,
                        proof.ready, proof.qualified, proof.networkType, proof.overrideType, nativeCurrent())) return null
                val lineage = synchronized(lineages) {
                    if (snapshot !== current) return null
                    lineages.getOrPut(proof.phoneId) { NativeFiveGaUiLineage(proof.phoneId, subId) }.also {
                        if (!it.renew(current.source, proof.epoch, lastPhoneId, subId, true)) return null
                    }
                }
                return Token(this, lineage, null)
            }
        }

        private inner class Token(
            session: Session,
            val lineage: NativeFiveGaUiLineage,
            val fallback: Any?,
        ) {
            val session = WeakReference(session)
            val phoneId get() = lineage.phoneId
            val subId get() = lineage.subId
            fun withFallback(value: Any?) = Token(session.get() ?: error("Retired repository"), lineage, value)
        }
        private class Bound(val method: Method, val value: Any, val token: Any)

        private fun enabled() = installed && !retired && !SafeModeRuntime.blocked && NativeFiveGaSettings.getEnabled()
        private fun valid(token: Token): Boolean {
            val owner = token.session.get() ?: return false
            val current = snapshot ?: return false
            val certified = token.lineage.current() ?: return false
            val proof = current.slots.singleOrNull { it.phoneId == token.phoneId && it.subId == token.subId } ?: return false
            return owner.ready && !owner.failed && owner.lastPhoneId == token.phoneId && owner.subId == token.subId &&
                NativeFiveGaUiPolicy.matches(enabled(), current.source, certified.source,
                token.phoneId, token.subId, certified.epoch, proof.phoneId, proof.subId, proof.epoch,
                proof.ready, proof.qualified, proof.networkType, proof.overrideType, owner.nativeCurrent())
        }

        fun install() {
            val constructor = repoType.declaredConstructors.single { c -> c.parameterCount == 15 &&
                c.parameterTypes[0] == Int::class.javaPrimitiveType && c.parameterTypes[1] == Context::class.java &&
                c.parameterTypes[2] == flow && c.parameterTypes[14].name == "kotlinx.coroutines.CoroutineScope" }
            check(gaKey.isNotEmpty())
            try {
                installProvenance()
                installFinalConsumers()
                hook(combine5) { chain ->
                    val frame = frameStack().lastOrNull() ?: return@hook chain.proceed()
                    val original = chain.getArg(5) ?: return@hook chain.proceed()
                    if (original.javaClass != transformType || transformOwner.get(original) !== frame.repository) return@hook chain.proceed()
                    frame.matches++
                    if (frame.matches != 1) return@hook chain.proceed()
                    val session = Session(frame.repository)
                    frame.session = session
                    val pulseTransform = proxy(function3) { args -> args[0] }
                    val pulsed = combine2.invoke(null, chain.getArg(2), session.pulse, pulseTransform)
                    val invoke = function6.getMethod("invoke", *Array(6) { Any::class.java })
                    val wrapped = proxy(function6) { args ->
                        val native = invokeHost(invoke, original, args)
                        if (native === suspended) {
                            session.failed = true
                            failure(IllegalStateException("Unexpected suspended network projection"))
                            native
                        } else runCatching {
                            val display = args[0]?.let { call(it, "getTelephonyDisplayInfo") }
                            val service = args[1]?.let { call(it, "getServiceState") }
                            val sub = args[2]
                            session.lastDisplay = display
                            session.lastService = service
                            session.lastPhoneId = if (sub != null && callInt(sub, "getSubscriptionId") == session.subId)
                                callInt(sub, "getPhoneId") else -1
                            val token = session.token()
                            if (native == null || token == null || lookup.invoke(native) == gaKey) native
                            else overrideConstructor.newInstance(gaKey).also {
                                resolvedTokens[it] = token.withFallback(native)
                                if (delivered.compareAndSet(false, true)) HookLog.i(TAG, "Original phone 5G-A qualification projected for sub=${session.subId}")
                            }
                        }.getOrElse { failure(it); native }
                    }
                    val args = chain.args.toTypedArray()
                    args[2] = pulsed
                    args[5] = wrapped
                    chain.proceed(args).also { frame.combined = it }
                }
                hook(share) { chain ->
                    val frame = frameStack().lastOrNull()
                    if (frame == null || frame.combined == null || chain.getArg(0) !== frame.combined ||
                        chain.getArg(1) !== frame.scope || chain.getArg(3) !== unknown) return@hook chain.proceed()
                    chain.proceed().also { frame.shared = it }
                }
                hook(constructor) { chain ->
                    val frame = Frame(requireNotNull(chain.thisObject), chain.getArg(1) as Context, requireNotNull(chain.getArg(14)))
                    frameStack().add(frame)
                    try {
                        val result = chain.proceed()
                        val session = frame.session
                        if (session != null && frame.matches == 1 && frame.shared != null && resolvedField.get(frame.repository) === frame.shared) {
                            session.ready = true
                            synchronized(sessions) { sessions += WeakReference(session) }
                            bootstrap(frame.context)
                        } else failure(IllegalStateException("Native repository missed the verified Flow contract; restart SystemUI"))
                        result
                    } finally {
                        frameStack().removeAt(frameStack().lastIndex)
                        if (frameStack().isEmpty()) frames.remove()
                    }
                }
                val module = ModernHookRuntime.requireModule()
                listOf(constructor, combine5, share).forEach { module.deoptimize(it) }
                flowFacade.declaredMethods.filter { it.name == "combine" && it.parameterCount == 6 || it.name == "stateIn" && it.parameterCount == 4 }
                    .forEach { module.deoptimize(it) }
                installed = true
                HotReloadLifecycle.register("native-fivega:ui", canPrepare = {
                    synchronized(sessions) { sessions.none { it.get()?.alive() == true } }
                }, canReload = {
                    synchronized(sessions) { sessions.removeAll { it.get()?.alive() != true }; sessions.isEmpty() }
                }, detach = {
                    retired = true
                    snapshot = null
                    refreshNow()
                    subscription?.close()
                    prefs?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
                }, resumeAfterRejection = { refresh() })
                HotReloadLifecycle.afterCommit {
                    val app = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Context
                    app?.let(::bootstrap)
                }
                HookLog.i(TAG, "Native 5G-A UI contract installed before repository construction")
            } catch (error: Throwable) {
                installed = false
                handles.asReversed().forEach { runCatching { it.unhook() } }
                throw error
            }
        }

        private fun installProvenance() {
            val mapperType = type("com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconInteractorImpl\$defaultNetworkType\$1")
            val mapper = mapperType.getDeclaredMethod("invoke", *Array(4) { Any::class.java })
            hook(mapper) { chain ->
                val token = chain.getArg(0)?.let { resolvedTokens[it] } ?: return@hook chain.proceed()
                if (!valid(token)) {
                    val args = chain.args.toTypedArray(); args[0] = token.fallback
                    return@hook chain.proceed(args)
                }
                val original = chain.proceed() ?: return@hook null
                runCatching {
                    val mappings = chain.getArg(1) as Map<*, *>
                    val fallback = requireNotNull(mappings[lookup.invoke(token.fallback)] ?: chain.getArg(2))
                    check(groupType.isInstance(original) && groupType.isInstance(fallback))
                    val copy = groupConstructor.newInstance(groupName.get(original), groupDescription.getInt(original), groupDataType.getInt(original))
                    groupTokens[copy] = token.withFallback(fallback)
                    copy
                }.getOrElse {
                    failure(it)
                    val args = chain.args.toTypedArray(); args[0] = token.fallback
                    chain.proceed(args)
                }
            }
            ModernHookRuntime.requireModule().deoptimize(mapper)
            val modelMapper = type("com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconInteractorImpl\$networkTypeIconGroup\$1")
                .getDeclaredMethod("invoke", *Array(3) { Any::class.java })
            hook(modelMapper) { chain ->
                val token = chain.getArg(0)?.let { groupTokens[it] } ?: return@hook chain.proceed()
                val args = chain.args.toTypedArray(); args[0] = token.fallback
                if (!valid(token)) return@hook chain.proceed(args)
                val original = chain.proceed() ?: return@hook null
                val fallback = invokeHost(modelMapper, chain.thisObject, args)
                modelTokens[original] = token.withFallback(fallback)
                original
            }
            ModernHookRuntime.requireModule().deoptimize(modelMapper)
            for (name in listOf(
                "com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.CellularIconViewModel\$networkTypeIcon\$1",
                "com.oplus.systemui.statusbar.pipeline.mobile.ui.viewmodel.OplusCellularIconViewModelImpl\$networkTypeIcon\$1",
            )) {
                val owner = type(name)
                val method = owner.declaredMethods.single { it.name == "invoke" && it.parameterCount in setOf(3, 5) }
                hook(method) { chain ->
                    val model = chain.getArg(0) ?: return@hook chain.proceed()
                    val group = runCatching { call(model, "getIconGroup") }.getOrNull() ?: return@hook chain.proceed()
                    val groupToken = groupTokens[group] ?: return@hook chain.proceed()
                    val token = modelTokens[model]
                    val args = chain.args.toTypedArray()
                    args[0] = token?.fallback ?: defaultIcon.newInstance(groupToken.fallback)
                    if (token == null || !valid(token)) return@hook chain.proceed(args)
                    val result = chain.proceed()
                    if (result == null || result === suspended) return@hook result
                    iconTokens[result] = token.withFallback(null)
                    val stock = invokeHost(method, chain.thisObject, args)
                    iconTokens[result] = token.withFallback(stock)
                    result
                }
                ModernHookRuntime.requireModule().deoptimize(method)
            }
        }

        private fun installFinalConsumers() {
            val bindingType = type("com.oplus.systemui.statusbar.pipeline.mobile.ui.view.OplusStatusBarMobileViewBinder\$Os17Binding")
            val bind = bindingType.getDeclaredMethod("bindNetworkTypeText", type(TEXT))
            hook(bind) { chain ->
                val host = chain.thisObject ?: return@hook chain.proceed()
                val model = chain.getArg(0)
                val token = model?.takeIf { textType.isInstance(it) }?.let { textGroup.invoke(it) }?.let { groupTokens[it] }
                if (token == null) { textBindings.remove(host); return@hook chain.proceed() }
                val sub = callInt(requireNotNull(call(host, "getViewModel")), "getSubscriptionId")
                val stock = textConstructor.newInstance(token.fallback)
                if (!valid(token) || token.subId != sub) {
                    textBindings.remove(host)
                    val args = chain.args.toTypedArray(); args[0] = stock
                    return@hook chain.proceed(args)
                }
                textBindings[host] = Bound(bind, stock, token)
                rendered("text-view", token)
                chain.proceed()
            }
            val collector = type("com.android.systemui.statusbar.pipeline.mobile.ui.binder.MobileIconBinder\$bind\$2\$2\$1\$2\$2")
            val branch = collector.getDeclaredField("\$r8\$classId").apply { isAccessible = true }
            val viewModel = collector.getDeclaredField("\$viewModel").apply { isAccessible = true }
            val emit = collector.getDeclaredMethod("emit", Any::class.java, continuation)
            hook(emit) { chain ->
                val host = chain.thisObject ?: return@hook chain.proceed()
                if (branch.getInt(host) != 1) return@hook chain.proceed()
                val token = chain.getArg(0)?.let { iconTokens[it] }
                if (token == null) { iconBindings.remove(host); return@hook chain.proceed() }
                val sub = callInt(requireNotNull(viewModel.get(host)), "getSubscriptionId")
                if (!valid(token) || token.subId != sub) {
                    iconBindings.remove(host)
                    val args = chain.args.toTypedArray(); args[0] = token.fallback
                    return@hook chain.proceed(args)
                }
                iconBindings[host] = Bound(emit, chain.getArg(0)!!, token)
                rendered("resource-view", token)
                chain.proceed()
            }
            installComposeConsumer()
            listOf(bind, emit).forEach { ModernHookRuntime.requireModule().deoptimize(it) }
        }

        private fun installComposeConsumer() {
            val owner = type("com.android.systemui.statusbar.pipeline.shared.ui.composable.OplusStackedMobileIconLayoutStrategyKt")
            val method = owner.declaredMethods.single { it.name.startsWith("NetworkTypeText-") && it.parameterCount == 13 }
            hook(method) { chain ->
                val model = chain.getArg(1)
                val token = model?.takeIf { textType.isInstance(it) }?.let { textGroup.invoke(it) }?.let { groupTokens[it] }
                    ?: return@hook chain.proceed()
                val dual = chain.getArg(0)?.let { call(it, "getDualSim") }
                val member = dual != null && listOf("primarySubId", "secondarySubId").any {
                    dual.javaClass.getDeclaredField(it).apply { isAccessible = true }.getInt(dual) == token.subId
                }
                if (member && valid(token)) {
                    rendered("stacked-compose", token)
                    return@hook chain.proceed()
                }
                val args = chain.args.toTypedArray()
                args[1] = textConstructor.newInstance(token.fallback)
                args[11] = ((args[11] as Int) and 0x70.inv()) or 0x20
                chain.proceed(args)
            }
            ModernHookRuntime.requireModule().deoptimize(method)
        }

        private fun bootstrap(context: Context) {
            main.post {
                if (retired || subscription != null) return@post
                runCatching {
                    prefs = ModernHookRuntime.remotePreferences("lsp_features")?.also {
                        it.registerOnSharedPreferenceChangeListener(preferenceListener)
                    }
                    subscription = NativeFiveGaBridge.subscribe(context.applicationContext ?: context) {
                        snapshot = it
                        refresh()
                    }
                }.onFailure(::failure)
            }
        }

        private fun refresh() { main.post { HotReloadLifecycle.withInfrastructureHook({}) { refreshNow() } } }
        private fun refreshNow() {
            revision = NativeFiveGaUiPolicy.nextRevision(revision)
            textBindings.entries().forEach { (owner, binding) ->
                val token = binding.token as Token
                if (!valid(token)) runCatching {
                    binding.method.invoke(owner, binding.value)
                    textBindings.remove(owner)
                }.onFailure(::failure)
            }
            iconBindings.entries().forEach { (owner, binding) ->
                val token = binding.token as Token
                if (!valid(token)) runCatching {
                    binding.method.invoke(owner, token.fallback, null)
                    iconBindings.remove(owner)
                }.onFailure(::failure)
            }
            synchronized(sessions) {
                sessions.removeAll { it.get()?.alive() != true }
                sessions.forEach { ref -> runCatching { ref.get()?.pulse() }.onFailure(::failure) }
            }
        }

        private fun hook(target: Executable, action: (NativeCall) -> Any?) {
            target.isAccessible = true
            handles += ModernHotReloadSession.install(target, XposedInterface.Hooker { chain ->
                HotReloadLifecycle.withInfrastructureHook({ chain.proceed() }) {
                    val call = NativeCall(chain)
                    try { action(call) }
                    catch (error: Throwable) {
                        if (call.nativeFailure != null) throw error
                        installed = false
                        snapshot = null
                        failure(error)
                        refresh()
                        if (call.proceeded) call.result else chain.proceed()
                    }
                }
            })
        }
        private fun proxy(api: Class<*>, invoke: (Array<out Any?>) -> Any?): Any =
            Proxy.newProxyInstance(loader, arrayOf(api)) { proxy, method, args ->
                when (method.name) {
                    "invoke" -> invoke(args ?: emptyArray())
                    "equals" -> proxy === args?.getOrNull(0)
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "ONextBox native 5G-A adapter"
                    else -> error("Unsupported native callback ${method.name}")
                }
            }
        private fun invokeHost(method: Method, owner: Any?, args: Array<out Any?>): Any? =
            try { method.isAccessible = true; method.invoke(owner, *args) }
            catch (error: InvocationTargetException) { throw error.targetException }
        private fun frameStack() = requireNotNull(frames.get())
        private fun call(owner: Any, name: String): Any? = owner.javaClass.getMethod(name).let {
            it.isAccessible = true; it.invoke(owner)
        }
        private fun callInt(owner: Any, name: String) = call(owner, name) as Int
        private fun inService(service: Any) = callInt(service, "getVoiceRegState") == 0 || callInt(service, "getDataRegState") == 0
        private fun rendered(path: String, token: Token) {
            if (renderedPaths.add(path)) HookLog.i(TAG, "Native 5G-A final consumer verified path=$path sub=${token.subId}")
        }
        private fun failure(error: Throwable) {
            if (failed.compareAndSet(false, true)) HookLog.w(TAG, "Native 5G-A UI path rejected; keeping its native result", error)
        }
    }

    private class NativeCall(private val chain: XposedInterface.Chain) {
        val thisObject: Any? get() = chain.thisObject
        val args: List<Any?> get() = chain.args
        var proceeded = false
            private set
        var result: Any? = null
            private set
        var nativeFailure: Throwable? = null
            private set
        fun getArg(index: Int): Any? = chain.getArg(index)
        fun proceed(): Any? = run { chain.proceed() }
        fun proceed(args: Array<out Any?>): Any? = run { chain.proceed(args) }
        private inline fun run(action: () -> Any?): Any? {
            proceeded = true
            try { return action().also { result = it } }
            catch (error: Throwable) { nativeFailure = error; throw error }
        }
    }

    private class WeakIdentityMap<V> {
        private class Key(value: Any, queue: ReferenceQueue<Any>? = null) : WeakReference<Any>(value, queue) {
            private val hash = System.identityHashCode(value)
            override fun hashCode() = hash
            override fun equals(other: Any?) = this === other || other is Key && get()?.let { it === other.get() } == true
        }
        private val queue = ReferenceQueue<Any>()
        private val values = HashMap<Key, V>()
        private fun clean() { while (true) values.remove(queue.poll() ?: break) }
        @Synchronized operator fun get(owner: Any): V? { clean(); return values[Key(owner)] }
        @Synchronized operator fun set(owner: Any, value: V) { clean(); values[Key(owner, queue)] = value }
        @Synchronized fun remove(owner: Any) { clean(); values.remove(Key(owner)) }
        @Synchronized fun entries(): List<Pair<Any, V>> { clean(); return values.mapNotNull { (key, value) -> key.get()?.let { it to value } } }
    }
}
