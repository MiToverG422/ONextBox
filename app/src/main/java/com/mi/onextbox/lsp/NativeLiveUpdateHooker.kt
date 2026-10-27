package com.mi.onextbox.lsp

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_READY_KEY
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import java.lang.ref.WeakReference
import java.lang.reflect.Executable
import java.util.WeakHashMap

internal object NativeLiveUpdateSettings {
    const val KEY = "systemui_native_live_update"
    fun enabled(context: Context) = LspPreferenceStore.prefs(context).getBoolean(KEY, false)
    fun active() = HookConfigSnapshot.boolean(KEY, false) == true
    fun set(context: Context, enabled: Boolean) =
        ModernXposedPreferenceSync.setBooleanAndSync(context, KEY, enabled)
}

/** Only the SystemUI presentation is replaced; notification eligibility and grants stay native. */
internal object NativeLiveUpdatePolicy {
    fun cloudFilter(enabled: Boolean, original: Int) = if (enabled) 0 else original
    fun cloudVisibility(enabled: Boolean, original: Int) = if (enabled) 8 else original
}

internal object NativeLiveUpdateHooker {
    private const val TAG = "ONextBox-NativeLiveUpdate"
    private const val PREFIX = "$TAG:"
    private const val STATE = "systemui:native-live-update"
    private const val BASE = "com.oplus.systemui.statusbar."

    fun hook(loader: ClassLoader) {
        runCatching { Session(loader).install() }.onFailure {
            ModernHookRegistry.unhookPrefix(PREFIX)
            HookLog.w(TAG, "Native notification routing unavailable; stock presentation retained", it)
        }
    }

    private class Session(private val loader: ClassLoader) {
        private val main = Handler(Looper.getMainLooper())
        private val repoType = Reflect.findClass(BASE + "notification.livealert.data.repository.OplusLiveAlertNotificationsRepository", loader)
        private val filtersType = Reflect.findClass(BASE + "notification.livealert.data.repository.OplusLiveAlertFilters", loader)
        private val controllerType = Reflect.findClass(BASE + "seeding.CapsulePluginContainerController", loader)
        private val capsuleType = Reflect.findClass(BASE + "seeding.CapsulePluginContainer", loader)
        private val entryType = Reflect.findClass("com.android.systemui.statusbar.notification.collection.NotificationEntry", loader)
        private val locationType = Reflect.findClass("com.android.systemui.statusbar.phone.StatusBarLocation", loader)
        private val visibilityType = Reflect.findClass("com.android.systemui.statusbar.pipeline.shared.ui.model.VisibilityModel", loader)
        private val binderType = Reflect.findClass("com.android.systemui.statusbar.pipeline.shared.ui.binder.HomeStatusBarViewBinderImpl", loader)
        private val boundsType = Reflect.findClass("com.android.systemui.statusbar.layout.ui.viewmodel.StatusBarBoundsViewModel", loader)
        private val chipVm = "com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipsViewModel"
        private val chipFilterType = Reflect.findClass("$chipVm\$special\$\$inlined\$filterNot\$1\$2", loader)
        private val stateFlowType = Reflect.findClass("kotlinx.coroutines.flow.MutableStateFlow", loader)
        private val hostUnit = Reflect.findClass("kotlin.Unit", loader).getField("INSTANCE").get(null)
        private val chipModelType = Reflect.findClass("com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel", loader)
        private val emptyChipBundle = Reflect.findClass("$chipVm\$ChipBundle", loader)
            .getDeclaredConstructor(chipModelType, chipModelType, chipModelType, List::class.java, Int::class.javaPrimitiveType)
            .newInstance(null, null, null, null, 31)
        private val chipRelay = NativeLiveUpdateChipRelay(emptyChipBundle) { sink, value -> Reflect.callMethod(sink, "setValue", value); Unit }
        private val viewBounds = Reflect.findClass("com.android.systemui.util.ConvenienceExtensionsKt", loader)
            .getDeclaredMethod("getBoundsOnScreen", View::class.java).apply { isAccessible = true }
        private val adjustVisibility = binderType.getDeclaredMethod("adjustVisibility", View::class.java, visibilityType).apply { isAccessible = true }
        private val hidden = visibilityType.getConstructor(Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).newInstance(View.GONE, false)
        private var repository = WeakReference<Any>(null)
        private val controllers = WeakHashMap<Any, Bundle?>()
        private val views = WeakHashMap<View, Int>()
        private var prefs: SharedPreferences? = null
        @Volatile private var ready = false
        @Volatile private var retired = false
        private var replayingIcons = false
        private var applyingViews = false
        private var modeApplied = false
        private val refresh = Runnable {
            HotReloadLifecycle.withInfrastructureHook({}) {
                if (!retired) runCatching { bootstrap(); reconcile() }.onFailure { error ->
                    ready = false
                    runCatching { reconcile(forceStock = true) }
                    HookLog.w(TAG, "Presentation refresh failed; restored stock routing", error)
                }
            }
        }
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in setOf(
                    NativeLiveUpdateSettings.KEY, MODERN_PREFERENCES_READY_KEY,
                )) schedule()
        }

        private fun active() = ready && !retired && NativeLiveUpdateSettings.active()
        private fun schedule() { if (!retired) { main.removeCallbacks(refresh); main.post(refresh) } }
        private fun install(key: String, target: Executable, callback: ModernMethodHook) {
            ModernHookRegistry.installCompat(PREFIX + key, target, callback)
        }

        fun install() {
            // Resolve the entire C17 contract before any routing hook can become active.
            val filter = filtersType.getDeclaredMethod("filterEntry", entryType).also { check(it.returnType == Int::class.javaPrimitiveType) }
            val live = repoType.getDeclaredMethod("isLiveAlert", entryType, Boolean::class.javaPrimitiveType)
            val has = repoType.getDeclaredMethod("hasStatusBarLiveAlert", entryType)
            val hun = repoType.getDeclaredMethod("interceptorHun", entryType)
            listOf(live, has, hun).forEach { check(it.returnType == Boolean::class.javaPrimitiveType) }
            repoType.getDeclaredMethod("reInitLiveAlert")
            repoType.getDeclaredField("notifCollection")
            val collectionType = Reflect.findClass("com.android.systemui.statusbar.notification.collection.NotifCollection", loader)
            collectionType.getDeclaredMethod("dispatchEventsAndRebuildList", String::class.java, Boolean::class.javaPrimitiveType)
            val animate = binderType.getDeclaredMethod("animate", View::class.java, visibilityType)
            val areas = controllerType.getDeclaredMethod("setHiddenIconAreas\$1", locationType, List::class.java)
            val icons = controllerType.getDeclaredMethod("hiddenStatusBarIconView", Bundle::class.java)
            val fake = controllerType.getDeclaredMethod("setCapsuleFakeContainerVisibility", Boolean::class.javaPrimitiveType)
            controllerType.getDeclaredMethod("notifyRect", locationType)
            controllerType.getDeclaredField("capsulePluginContainer")
            controllerType.getDeclaredField("capsuleFakeContainer")
            val bounds = listOf("getStartSideContainerBounds" to "startSideContainerView", "getClockBounds" to "clockView")
                .map { (getter, field) ->
                    boundsType.getDeclaredField(field)
                    boundsType.getDeclaredMethod(getter).also { check(it.returnType == Rect::class.java) } to field
                }
            val chipEmission = chipFilterType.getDeclaredMethod("emit", Any::class.java, Reflect.findClass("kotlin.coroutines.Continuation", loader))
            chipFilterType.getDeclaredField("\$this_unsafeFlow")

            // The OEM filter drops every bundle on fluid devices. Its downstream is the
            // native stateIn MutableStateFlow, whose emit is non-suspending setValue.
            // Leave the global feature flag and every other use of that flag untouched.
            install("native-chip-emission", chipEmission, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val owner = param.thisObject ?: return
                    val sink = Reflect.getObjectField(owner, "\$this_unsafeFlow") ?: return
                    if (!stateFlowType.isInstance(sink)) return
                    val bundle = param.args[0] ?: return
                    val enabled = active()
                    chipRelay.accept(sink, bundle, enabled)
                    if (enabled) param.result = hostUnit
                }
            })

            // C17's callback flows do not send an initial layout. Read the same native views
            // until their first callback arrives, instead of leaving the Compose chip at 0 dp.
            for ((getter, field) in bounds) install("bounds-$field", getter, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!active() || param.throwable != null) return
                    val owner = param.thisObject ?: return
                    val view = Reflect.getObjectField(owner, field) as? View ?: return
                    if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return
                    val actual = viewBounds.invoke(null, view) as Rect
                    if (actual != param.result) param.result = actual
                }
            })

            install("cloud-filter", filter, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (active()) param.result = NativeLiveUpdatePolicy.cloudFilter(true, 0)
                }
            })
            for ((key, method) in listOf("pre-group" to live, "status-bar" to has, "heads-up" to hun)) {
                install(key, method, object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.thisObject?.let { repository = WeakReference(it) }
                        if (active()) param.result = false
                    }
                })
            }
            repoType.declaredConstructors.forEachIndexed { index, constructor ->
                install("repository-$index", constructor, object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable == null) param.thisObject?.let { repository = WeakReference(it); schedule() }
                    }
                })
            }
            controllerType.declaredConstructors.forEachIndexed { index, constructor ->
                install("container-$index", constructor, object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable == null) param.thisObject?.let { rememberController(it); schedule() }
                    }
                })
            }
            for (method in controllerType.declaredMethods.filter { it.name in setOf("onViewAttached", "onPluginAdded", "createCapsuleFakeContainer") }) {
                install("container-${method.name}", method, object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable == null) param.thisObject?.let {
                            rememberController(it)
                            if (active()) { modeApplied = true; applyViews(true) }
                        }
                    }
                })
            }
            install("visibility", adjustVisibility, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val view = param.args[0] as? View ?: return
                    if (!capsuleType.isInstance(view) && !views.containsKey(view)) return
                    val original = Reflect.callMethod(param.args[1], "getVisibility") as Int
                    if (!applyingViews) views[view] = original
                    if (active()) param.args[1] = hidden
                }
            })
            install("reserved-area", areas, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (active()) param.args[1] = java.util.Collections.emptyList<Any>()
                }
            })
            install("hidden-icons", icons, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val owner = param.thisObject ?: return
                    val original = param.args[0] as? Bundle ?: return
                    if (!replayingIcons) controllers[owner] = Bundle(original)
                    if (active()) param.args[0] = Bundle(original).apply { putStringArrayList("hiddenIconList", arrayListOf()) }
                }
            })
            install("fake-visibility", fake, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val owner = param.thisObject ?: return
                    rememberController(owner)
                    (Reflect.getObjectField(owner, "capsuleFakeContainer") as? View)?.let {
                        views[it] = if (param.args[0] == true) View.VISIBLE else View.INVISIBLE
                    }
                    if (active()) param.args[0] = false
                }
            })
            val interactor = Reflect.findClass(BASE + "notification.livealert.data.interactor.OplusLiveAlertNotificationsInteractor", loader)
            val preGroup = Reflect.findClass(BASE + "notification.livealert.data.interactor.OplusLiveAlertNotificationsInteractor\$preGroupFilter\$1", loader)
            val callers = listOf(repoType, filtersType, interactor, preGroup, controllerType, binderType, boundsType, chipFilterType,
                Reflect.findClass("$chipVm\$special\$\$inlined\$map\$3", loader),
                Reflect.findClass("com.android.systemui.statusbar.pipeline.shared.ui.composable.StatusBarRootKt\$\$ExternalSyntheticLambda6", loader),
                Reflect.findClass(BASE + "pipeline.shared.ui.binder.OplusHomeStatusBarViewBinderImpl", loader))
            val pending = java.util.ArrayDeque(callers)
            val visited = HashSet<Class<*>>()
            while (pending.isNotEmpty() && visited.size < 128) {
                val type = pending.removeFirst()
                if (!visited.add(type)) continue
                type.declaredMethods.forEach { runCatching { ModernHookRuntime.requireModule().deoptimize(it) } }
                type.declaredClasses.forEach(pending::addLast)
            }
            ready = true
            HotReloadLifecycle.register(STATE, capture = ::capture,
                detach = {
                    retired = true
                    main.removeCallbacks(refresh)
                    prefs?.unregisterOnSharedPreferenceChangeListener(listener); prefs = null
                    reconcile(forceStock = true)
                    views.clear(); controllers.clear(); repository.clear(); chipRelay.clear()
                },
                restore = { retired = false; importState(it); observe(); schedule() },
                resumeAfterRejection = { retired = false; observe(); schedule() })
            HotReloadLifecycle.afterCommit { importState(HotReloadLifecycle.restoredState(STATE)); observe(); schedule() }
        }

        private fun observe() {
            if (prefs == null) ModernHookRuntime.remotePreferences("lsp_features")?.let {
                it.registerOnSharedPreferenceChangeListener(listener); prefs = it
            }
        }

        private fun rememberController(owner: Any) {
            if (!controllers.containsKey(owner)) controllers[owner] = runCatching {
                val dispatcher = Reflect.getObjectField(owner, "hiddenIconDispatcher") ?: return@runCatching null
                val icons = Reflect.getObjectField(dispatcher, "hiddenIcons") as? List<*> ?: return@runCatching null
                Bundle().apply { putStringArrayList("hiddenIconList", ArrayList(icons.filterIsInstance<String>())) }
            }.getOrNull()
            for (field in listOf("capsulePluginContainer", "capsuleFakeContainer")) {
                (Reflect.getObjectField(owner, field) as? View)?.let {
                    if (!views.containsKey(it) || (!modeApplied && !active())) views[it] = it.visibility
                }
            }
        }

        private fun bootstrap() {
            val thread = Reflect.findClass("android.app.ActivityThread", loader)
            val application = Reflect.callStaticMethod(thread, "currentApplication") ?: return
            val component = Reflect.getObjectField(application, "mSysUIComponent") ?: return
            val dump = Reflect.callMethod(component, "createDumpManager") ?: return
            val entries = Reflect.callMethod(dump, "getDumpables") as? Collection<*> ?: return
            for (entry in entries.toList()) {
                val owner = Reflect.callMethod(entry, "getDumpable") ?: continue
                when {
                    repoType.isInstance(owner) -> repository = WeakReference(owner)
                    controllerType.isInstance(owner) -> rememberController(owner)
                }
            }
        }

        private fun applyViews(enabled: Boolean) {
            for ((view, original) in views.entries.toList()) {
                val visibility = NativeLiveUpdatePolicy.cloudVisibility(enabled, original)
                val alpha = if (visibility == View.VISIBLE) 1f else 0f
                if (view.visibility == visibility && view.alpha == alpha) continue
                applyingViews = true
                try {
                    // The native helper cancels stale animations and restores both alpha and visibility.
                    val model = if (enabled) hidden else Reflect.newInstance(visibilityType, visibility, false)
                    adjustVisibility.invoke(null, view, model)
                } finally { applyingViews = false }
            }
        }

        private fun reconcile(forceStock: Boolean = false) {
            val enabled = !forceStock && active()
            if (!enabled && !modeApplied) return
            // Mark ownership before mutations so partial failures can restore stock state.
            if (enabled) modeApplied = true
            chipRelay.reconcile(enabled)
            controllers.keys.toList().forEach(::rememberController)
            applyViews(enabled)
            for ((owner, savedIcons) in controllers.entries.toList()) {
                replayingIcons = true
                try {
                    if (enabled || savedIcons != null) Reflect.callMethod(owner, "hiddenStatusBarIconView",
                        if (enabled) Bundle().apply { putStringArrayList("hiddenIconList", arrayListOf()) } else Bundle(savedIcons!!))
                } finally { replayingIcons = false }
                locationType.enumConstants.orEmpty().forEach { Reflect.callMethod(owner, "notifyRect", it) }
            }
            repository.get()?.let { repo ->
                Reflect.callMethod(repo, "reInitLiveAlert")
                val collection = Reflect.getObjectField(repo, "notifCollection") ?: return@let
                // Re-run the existing pipeline, preserving privacy, dismissal and user filters.
                Reflect.callMethod(collection, "dispatchEventsAndRebuildList", "ONextBox native Live Update mode", false)
            }
            if (enabled) controllers.keys.toList().forEach { owner ->
                (Reflect.getObjectField(owner, "capsulePluginContainer") as? View)?.rootView?.let(::checkNativeHost)
            }
            modeApplied = enabled
        }

        private fun checkNativeHost(root: View) {
            val parentId = root.resources.getIdentifier("status_bar_start_side_except_heads_up", "id", "com.android.systemui")
            val parent = root.findViewById<ViewGroup>(parentId) ?: return
            for (index in 0 until parent.childCount) {
                val view = parent.getChildAt(index)
                if (view.javaClass.name != "androidx.compose.ui.platform.ComposeView") continue
                runCatching {
                    val state = Reflect.getObjectField(view, "content") ?: return@runCatching
                    val content = Reflect.callMethod(state, "getValue") ?: return@runCatching
                    val block = Reflect.getObjectField(content, "_block") ?: return@runCatching
                    if (!block.javaClass.name.endsWith("StatusBarRootKt\$\$ExternalSyntheticLambda6")) return@runCatching
                    val model = Reflect.getObjectField(block, "f\$0") ?: return@runCatching
                    val chips = Reflect.callMethod(model, "getOngoingActivityChips") ?: return@runCatching
                    val allowed = Reflect.callMethod(chips, "getAreChipsAllowed")
                    val activeChips = Reflect.callMethod(Reflect.callMethod(chips, "getChips"), "getActive") as? Collection<*>
                    val native = Reflect.getObjectField(model, "homeStatusBarViewModel") ?: model
                    val flow = Reflect.callMethod(Reflect.getObjectField(native, "chipsVisibilityModel"), "getValue")
                    val sourceAllowed = Reflect.callMethod(flow, "getAreChipsAllowed")
                    val sourceActive = Reflect.callMethod(Reflect.callMethod(flow, "getChips"), "getActive") as? Collection<*>
                    HookLog.i(TAG, "Native host: attached=${view.isAttachedToWindow}, width=${view.width}, allowed=$allowed, active=${activeChips?.size}, sourceAllowed=$sourceAllowed, sourceActive=${sourceActive?.size}")
                    view.requestLayout()
                }.onFailure { HookLog.w(TAG, "Native host inspection unavailable", it) }
            }
        }

        private fun capture(): Any = java.util.LinkedHashMap<String, Any?>().apply {
            put("repository", repository.get())
            put("controllers", java.util.ArrayList(controllers.map { arrayOf(it.key, it.value?.let(::Bundle)) }))
            put("views", java.util.ArrayList(views.map { arrayOf(it.key, it.value) }))
            put("chipRelay", chipRelay.capture())
        }

        private fun importState(state: Any?) {
            val saved = state as? Map<*, *> ?: return
            chipRelay.restore(saved["chipRelay"], stateFlowType::isInstance)
            saved["repository"]?.let { if (repoType.isInstance(it)) repository = WeakReference(it) }
            (saved["controllers"] as? Collection<*>)?.forEach { item ->
                val pair = item as? Array<*> ?: return@forEach
                val owner = pair.getOrNull(0) ?: return@forEach
                if (controllerType.isInstance(owner)) controllers[owner] = pair.getOrNull(1) as? Bundle
            }
            (saved["views"] as? Collection<*>)?.forEach { item ->
                val pair = item as? Array<*> ?: return@forEach
                val view = pair.getOrNull(0) as? View ?: return@forEach
                val visibility = pair.getOrNull(1) as? Int ?: return@forEach
                views[view] = visibility
            }
        }
    }
}
