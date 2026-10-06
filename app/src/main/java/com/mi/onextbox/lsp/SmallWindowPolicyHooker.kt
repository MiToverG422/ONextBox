package com.mi.onextbox.lsp

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.media.VolumeShaper
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.View
import android.view.ViewGroup
import com.mi.onextbox.lsp.LspConfig.SmallWindowFeature as Feature
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import kotlin.math.abs
import kotlin.math.min

/** Opt-in C17 phone policies. Never remove tasks, fake foreground state or restart processes. */
internal object SmallWindowPolicyHooker {
    private const val TAG = "ONextBox-SmallWindowPolicy"
    private val intType = Int::class.javaPrimitiveType!!
    private val boolType = Boolean::class.javaPrimitiveType!!
    private val floatType = Float::class.javaPrimitiveType!!
    private fun enabled(feature: Feature) = LspConfig.isSmallWindowFeatureEnabledXposed(feature)

    fun install(loader: ClassLoader) {
        val groups = listOf(
            Feature.HideRecents to { installRecents(loader) },
            Feature.KeepRunning to { installKeepRunning(loader) },
            Feature.LandscapeRatio to { installLandscape(loader) },
            Feature.LargerSize to { installSize(loader) },
            Feature.CompactCaption to { installCaption(loader) },
            Feature.MuteStashed to { installMute(loader) },
            Feature.UnlimitedCount to { installCount(loader) },
        )
        groups.forEach { (feature, install) ->
            if (enabled(feature)) runCatching(install)
                .onSuccess { HookLog.i(TAG, "${feature.name} interfaces installed") }
                .onFailure { HookLog.w(TAG, "${feature.name} interface unavailable; stock behavior retained", it) }
        }
        // One shared callback for both switches, avoiding duplicate hooks on the FPS overloads.
        if (enabled(Feature.KeepRunning) || enabled(Feature.UnlimitedFrameRate)) {
            runCatching { installFrameRate(loader) }
                .onSuccess { HookLog.i(TAG, "Small-window frame-rate interfaces installed") }
                .onFailure { HookLog.w(TAG, "Frame-rate interface unavailable; stock behavior retained", it) }
        }
    }

    private fun type(loader: ClassLoader, name: String) = Reflect.findClass(name, loader)
    private fun wm(loader: ClassLoader, name: String) = type(loader, "com.android.server.wm.$name")
    private fun exact(target: Class<*>, name: String, vararg args: Class<*>): Method =
        Reflect.findMethodExact(target, name, args)

    /** A bad OEM callback disables only that interface, not a critical system process. */
    private fun hook(method: Method, before: Boolean = false, action: (ModernMethodHook.MethodHookParam) -> Unit) {
        val failed = AtomicBoolean(false)
        val callback = object : ModernMethodHook() {
            private fun run(param: MethodHookParam) {
                if (failed.get() || param.throwable != null) return
                runCatching { action(param) }.onFailure {
                    if (failed.compareAndSet(false, true)) HookLog.w(TAG, "${method.name} callback disabled", it)
                }
            }
            override fun beforeHookedMethod(param: MethodHookParam) { if (before) run(param) }
            override fun afterHookedMethod(param: MethodHookParam) { if (!before) run(param) }
        }
        ModernHookRegistry.installCompat("small-window-policy:${method.toGenericString()}", method, callback)
    }

    private fun phone(context: Context?) = context != null &&
        context.resources.configuration.smallestScreenWidthDp in 1..599

    private fun installCount(loader: ClassLoader) {
        val service = wm(loader, "FlexibleWindowManagerService")
        val utils = wm(loader, "FlexibleWindowUtils")
        val controller = wm(loader, "FlexibleTaskController")
        service.getDeclaredField("mAtms")
        service.getDeclaredField("mFlexibleTaskController")
        wm(loader, "ActivityTaskManagerService").getDeclaredField("mContext")
        exact(service, "getInstance", Any::class.java)
        exact(controller, "getFlexibleTaskNum")
        val methods = listOf(exact(service, "getMaxWinNum", intType),
            exact(utils, "getMaxWinNum"), exact(utils, "getMaxFlexibleTaskNum"),
            exact(service, "getTasksNumber"))
        methods.forEach { method -> hook(method) { param ->
            if (!enabled(Feature.UnlimitedCount)) return@hook
            // Scenario 1 is application small windows, don't rewrite other scene policies.
            if (method.name == "getMaxWinNum" && param.args.isNotEmpty() && param.args[0] != 1) return@hook
            val host = param.thisObject ?: Reflect.callStaticMethod(service, "getInstance", null) ?: return@hook
            val atms = Reflect.getObjectField(host, "mAtms") ?: return@hook
            if (!phone(Reflect.getObjectField(atms, "mContext") as? Context)) return@hook
            val owner = Reflect.getObjectField(host, "mFlexibleTaskController") ?: return@hook
            val count = Reflect.callMethod(owner, "getFlexibleTaskNum") as Int
            val original = param.result as? Int ?: return@hook
            // Grow with live windows instead of imposing a replacement fixed cap or allocating
            // Integer.MAX_VALUE-sized native lists, leave spare room for pending launches.
            param.result = SmallWindowRules.growingWindowCapacity(original, count,
                if (method.name == "getTasksNumber") 4 else 2)
        } }
    }

    private fun installRecents(loader: ClassLoader) {
        val target = wm(loader, "RecentTasks")
        target.getDeclaredField("mService")
        hook(exact(target, "getRecentTasksImpl", intType, intType, boolType, intType, intType)) { param ->
            if (!enabled(Feature.HideRecents)) return@hook
            val receiver = param.thisObject ?: return@hook
            val service = Reflect.getObjectField(receiver, "mService") ?: return@hook
            val context = Reflect.getObjectField(service, "mContext") as? Context ?: return@hook
            if (!phone(context)) return@hook
            val caller = param.args[4] as Int
            // Only filter the launcher's display query. Dumps and other apps retain the stock list.
            if (context.packageManager.getPackagesForUid(caller)?.contains("com.android.launcher") != true) return@hook
            val original = param.result as? List<*> ?: return@hook
            val filtered = ArrayList<Any?>()
            for (info in original) {
                if (info == null) { filtered.add(info); continue }
                val configuration = Reflect.getObjectField(info, "configuration") ?: return@hook
                val window = Reflect.getObjectField(configuration, "windowConfiguration") ?: return@hook
                val mode = Reflect.callMethod(window, "getWindowingMode") as Int
                val bundle = Reflect.getObjectField(info, "mOplusExtraBundle") as? Bundle
                val small = SmallWindowRules.isSmallWindowTask(mode,
                    bundle?.getInt("androidx.activity.LaunchScenario") ?: 0,
                    bundle?.getBoolean("androidx.activity.LaunchEmbedded") ?: false,
                    bundle?.getBoolean("androidx.activity.HasCaption") ?: false)
                if (!small) filtered.add(info)
            }
            // This is a fresh result list. The underlying RecentTasks records are untouched.
            param.result = filtered
        }
    }

    /** Snapshot only currently attached tasks. No one-argument lookup that might restore a task. */
    private fun stashedUids(loader: ClassLoader): Set<Int> {
        val controller = Reflect.callStaticMethod(wm(loader, "FloatHandleController"), "getInstance") ?: return emptySet()
        if (!phone(Reflect.getObjectField(controller, "mContext") as? Context)) return emptySet()
        val atms = Reflect.getObjectField(controller, "mAtms") ?: return emptySet()
        val lock = Reflect.getObjectField(atms, "mGlobalLock") ?: return emptySet()
        val root = Reflect.getObjectField(atms, "mRootWindowContainer") ?: return emptySet()
        synchronized(lock) {
            val infos = Reflect.callMethod(controller, "getFloatHandleInfoList") as? List<*> ?: return emptySet()
            val ids = infos.filterNotNull().map { Reflect.callMethod(it, "getTaskId") as Int }.toSet()
            val stashed = mutableSetOf<Int>()
            ids.forEach { id ->
                val task = Reflect.callMethod(root, "anyTaskForId", id, 0) ?: return@forEach
                stashed.add(Reflect.getObjectField(task, "effectiveUid") as Int)
            }
            val visibleOther = mutableSetOf<Int>()
            Reflect.callMethod(root, "forAllLeafTasks", Consumer<Any> { task ->
                val id = Reflect.getObjectField(task, "mTaskId") as Int
                if (id !in ids && Reflect.callMethod(task, "isVisible") == true) {
                    visibleOther.add(Reflect.getObjectField(task, "effectiveUid") as Int)
                }
            }, true)
            return SmallWindowRules.exclusiveStashedUids(stashed, visibleOther)
        }
    }

    private fun installKeepRunning(loader: ClassLoader) {
        val target = wm(loader, "FlexibleTaskPerformanceManager")
        target.getDeclaredField("mHighThermal")
        val freeze = exact(target, "setAppFreezeState", ArrayList::class.java, String::class.java, intType, boolType)
        hook(freeze, before = true) { param ->
            if (!enabled(Feature.KeepRunning) || param.args[2] != 1 ||
                Reflect.getObjectField(param.thisObject!!, "mHighThermal") == true) return@hook
            val uids = stashedUids(loader)
            val original = param.args[0] as? ArrayList<*> ?: return@hook
            param.args[0] = ArrayList(original.filterNot { it in uids })
            // Type 2 (native unfreeze) is deliberately never intercepted.
        }
    }

    private fun installFrameRate(loader: ClassLoader) {
        val target = wm(loader, "FlexibleTaskPerformanceManager")
        val process = wm(loader, "WindowProcessController")
        val policy = wm(loader, "FlexibleTaskPerformanceManager\$FpsPolicy")
        val utils = wm(loader, "FlexibleWindowUtils")
        val task = wm(loader, "Task")
        target.getDeclaredField("mHighThermal")
        target.getDeclaredField("mContext")
        target.getDeclaredField("mAtms")
        process.getDeclaredField("mUid")
        exact(utils, "isFlexibleTaskAndHasCaption", task)
        exact(utils, "getTaskWindowProcessController", task)
        val fps4 = exact(target, "setAppFpsLow", String::class.java, floatType, floatType, process)
        val fps5 = exact(target, "setAppFpsLow", String::class.java, floatType, floatType, process, policy)
        listOf(fps4, fps5).forEach { method ->
            hook(method, before = true) { param ->
                val unlimited = enabled(Feature.UnlimitedFrameRate)
                val keepRunning = enabled(Feature.KeepRunning)
                if (!unlimited && !keepRunning) return@hook
                val receiver = param.thisObject ?: return@hook
                if (!phone(Reflect.getObjectField(receiver, "mContext") as? Context)) return@hook
                val highThermal = Reflect.getObjectField(receiver, "mHighThermal") == true
                if (highThermal) return@hook
                val wpc = param.args[3] ?: return@hook
                val uid = Reflect.getObjectField(wpc, "mUid") as Int
                val smallProcess = unlimited && isSmallWindowProcess(receiver, wpc, utils)
                val stashed = keepRunning && uid in stashedUids(loader)
                if (!SmallWindowRules.removeFrameRateLimit(unlimited, keepRunning, smallProcess, stashed, highThermal)) return@hook
                // 0 removes this OEM VRR limiter; normal app rendering and thermal policy remain.
                param.args[1] = 0f
                if (param.args.size == 5) param.args[4] = null // don't let Panorama recalculate 1 fps
            }
        }
    }

    private fun isSmallWindowProcess(receiver: Any, process: Any, utils: Class<*>): Boolean {
        val atms = Reflect.getObjectField(receiver, "mAtms") ?: return false
        val lock = Reflect.getObjectField(atms, "mGlobalLock") ?: return false
        val root = Reflect.getObjectField(atms, "mRootWindowContainer") ?: return false
        var smallWindow = false
        var visibleOther = false
        synchronized(lock) {
            Reflect.callMethod(root, "forAllLeafTasks", Consumer<Any> { task ->
                if (Reflect.callStaticMethod(utils, "getTaskWindowProcessController", task) !== process) return@Consumer
                val small = Reflect.callMethod(task, "getDisplayId") == 0 &&
                    Reflect.callStaticMethod(utils, "isFlexibleTaskAndHasCaption", task) == true
                if (small) smallWindow = true
                else if (Reflect.callMethod(task, "isVisible") == true) visibleOther = true
            }, true)
        }
        return smallWindow && !visibleOther
    }

    private fun installLandscape(loader: ClassLoader) {
        val target = wm(loader, "FlexibleTaskController")
        val utils = wm(loader, "FlexibleWindowUtils")
        val display = wm(loader, "OplusZoomDisplay")
        val fill = exact(target, "fillFlexibleTaskInfo", wm(loader, "FlexibleTaskInfo\$Builder"),
            Rect::class.java, Intent::class.java, ActivityInfo::class.java, boolType, wm(loader, "DisplayContent"))
        val ratios = exact(target, "getFlexibleTaskAvailableRatioByActivity", wm(loader, "ActivityRecord"), String::class.java, boolType)
        val support = exact(utils, "isSupportFullScreenRatioInFlexibleTask", String::class.java)
        val scope = ThreadLocal<ArrayDeque<Boolean>>()
        listOf(fill, ratios).forEach { method ->
            // Stack is popped even when the original throws. No package-wide/global allowlist rewrite.
            val callback = object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val active = runCatching {
                        enabled(Feature.LandscapeRatio) && phone(Reflect.getObjectField(param.thisObject!!, "mContext") as? Context) &&
                            if (method == fill) param.args[4] == true else {
                                val activity = param.args[0]
                                param.args[2] == true && activity != null && SmallWindowRules.isLandscapeOrientation(
                                    Reflect.callMethod(activity, "getRequestedOrientation") as Int)
                            }
                    }.getOrDefault(false)
                    val stack = scope.get() ?: ArrayDeque<Boolean>().also { scope.set(it) }
                    stack.addLast(active)
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    val stack = scope.get() ?: return
                    val active = stack.removeLast()
                    if (stack.isEmpty()) scope.remove()
                    if (!active || method != ratios || param.throwable != null) return
                    runCatching {
                        val native = param.result as? List<*> ?: return@runCatching
                        val instance = Reflect.callStaticMethod(display, "getInstance") ?: return@runCatching
                        val ratio = SmallWindowRules.landscapeRatio(Reflect.callMethod(instance, "getScreenWidth") as Int,
                            Reflect.callMethod(instance, "getScreenHeight") as Int) ?: return@runCatching
                        // Honor the native unsupported-ratio blacklist and non-resizable app restrictions.
                        if (native.any { it is Float && abs(it - ratio) < 0.01f }) param.result = arrayListOf(ratio)
                    }.onFailure { HookLog.w(TAG, "Landscape ratio retained native list", it) }
                }
            }
            ModernHookRegistry.installCompat("small-window-landscape:${method.name}", method, callback)
        }
        hook(support) { param -> if (scope.get()?.lastOrNull() == true) param.result = true }
    }

    private fun installSize(loader: ClassLoader) {
        val target = wm(loader, "OplusZoomSmallScreenParameter")
        val width = exact(target, "getCurrentRatioMaxVisualWidth", floatType)
        val scale = exact(target, "getCurrRatioMaxFlexibleScale", floatType)
        exact(target, "findRightRatioData", floatType)
        exact(target, "getScreenWidth"); exact(target, "getScreenHeight"); exact(target, "getDensity")
        listOf(width, scale).forEach { method ->
            hook(method) { param ->
                if (!enabled(Feature.LargerSize)) return@hook
                val receiver = param.thisObject ?: return@hook
                val density = Reflect.callMethod(receiver, "getDensity") as Float
                if (!density.isFinite() || density <= 0f) return@hook
                val w = (Reflect.callMethod(receiver, "getScreenWidth") as Int) / density
                val h = (Reflect.callMethod(receiver, "getScreenHeight") as Int) / density
                if (min(w, h) >= 600f) return@hook
                val ratio = param.args[0] as Float
                val data = Reflect.callMethod(receiver, "findRightRatioData", ratio) ?: return@hook
                val original = Reflect.getObjectField(data, "mMaxVisualWidth") as Int
                val minimum = maxOf(Reflect.getObjectField(data, "mMinVisualWidth") as Int,
                    Reflect.getObjectField(data, "mDefaultVisualWidth") as Int)
                val desired = SmallWindowRules.largerWidth(original, minimum, ratio, w, h)
                if (original <= 0 || desired == original) return@hook
                // Both getters use the same bound. Never mutate the cached native ratio table.
                param.result = if (method == width) desired else (param.result as Float) * desired / original
            }
        }
    }

    private fun installCaption(loader: ClassLoader) {
        val target = wm(loader, "FlexibleTaskCaptionView")
        val margin = exact(target, "adjustViewMargin", View::class.java, intType, floatType, intType, floatType)
        val relayout = exact(target, "onRelayoutCaptionView")
        val init = exact(target, "initCaptionView")
        val savedHeights = WeakHashMap<View, Int>()
        val observed = WeakHashMap<View, Boolean>()
        hook(margin, before = true) { param ->
            val owner = param.thisObject as? View ?: return@hook
            if (enabled(Feature.CompactCaption) && phone(owner.context)) {
                val top = param.args[2] as Float
                if (top > 0f) param.args[2] = (top - 4f).coerceAtLeast(1f)
            }
        }
        listOf(init, relayout).forEach { method ->
            hook(method) { param ->
                val owner = param.thisObject as? View ?: return@hook
                if (!phone(owner.context)) return@hook
                if (observed.put(owner, true) == null) {
                    val weak = WeakReference(owner)
                    owner.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                        weak.get()?.let { runCatching { Reflect.callMethod(it, "updateTouchRegion") } }
                    }
                }
                var changed = false
                listOf("mToolbarModeFrame", "mThreeButtonFrame").forEach { field ->
                    val frame = Reflect.getObjectField(owner, field) as? ViewGroup ?: return@forEach
                    val lp = frame.layoutParams ?: return@forEach
                    val original = savedHeights.getOrPut(frame) { lp.height }
                    val targetHeight = (32f * owner.resources.displayMetrics.density).toInt()
                    // Don't shrink wrap-content/match-parent or clip larger native button layouts.
                    val childBottom = (0 until frame.childCount).maxOfOrNull { index ->
                        val child = frame.getChildAt(index)
                        val childLp = child.layoutParams as? ViewGroup.MarginLayoutParams
                        (childLp?.topMargin ?: 0) + maxOf(child.measuredHeight, childLp?.height ?: 0)
                    } ?: 0
                    val height = if (enabled(Feature.CompactCaption) && original > 0 && childBottom > 0)
                        min(original, maxOf(targetHeight, childBottom)) else original
                    if (height != lp.height) { lp.height = height; frame.layoutParams = lp; changed = true }
                }
                if (changed || enabled(Feature.CompactCaption)) {
                    val weak = WeakReference(owner)
                    owner.post { weak.get()?.let { runCatching { Reflect.callMethod(it, "updateTouchRegion") } } }
                }
            }
        }
    }

    /** A separate shaper multiplies media gain; removing it never sets another policy's volume to 1. */
    private fun installMute(loader: ClassLoader) {
        val target = type(loader, "com.android.server.audio.PlaybackActivityMonitor")
        val event = exact(target, "playerEvent", intType, intType, IntArray::class.java, intType)
        val release = exact(target, "releasePlayer", intType, intType)
        val controller = wm(loader, "FloatHandleController")
        val add = exact(controller, "updateInfoMapForAdd", wm(loader, "floathandle.FloatHandleInfo"))
        val remove = exact(controller, "removeFloatHandleInner", intType, boolType, intType)
        val removeAll = exact(controller, "removeAllFloatHandle")
        target.getDeclaredField("mPlayerLock"); target.getDeclaredField("mPlayers")
        val builder = VolumeShaper.Configuration.Builder().setCurve(floatArrayOf(0f, 1f), floatArrayOf(1f, 0f)).setDuration(50)
        Reflect.callMethod(builder, "setId", 0x4f4e58) // separate from AOSP duck/fade IDs 1..4
        Reflect.callMethod(builder, "setOptionFlags", 2)
        val shape = builder.build()
        val operationType = type(loader, "android.media.VolumeShaper\$Operation\$Builder")
        val operationBuilder = Reflect.newInstance(operationType, VolumeShaper.Operation.PLAY)
        Reflect.callMethod(operationBuilder, "createIfNeeded")
        Reflect.callMethod(operationBuilder, "setXOffset", 1f)
        val play = Reflect.callMethod(operationBuilder, "build")!!
        val terminateBuilder = Reflect.newInstance(operationType)
        Reflect.callMethod(terminateBuilder, "terminate")
        val terminate = Reflect.callMethod(terminateBuilder, "build")!!
        val id = Reflect.newInstance(VolumeShaper.Configuration::class.java, 0x4f4e58)
        val handler = Handler(HandlerThread("ONextBox-StashedMedia").apply { start() }.looper)
        val owned = mutableMapOf<Int, Any>() // only this shaper's live playback configurations
        var monitor: WeakReference<Any>? = null
        val warning = AtomicBoolean(false)
        fun warn(error: Throwable) {
            if (warning.compareAndSet(false, true)) HookLog.w(TAG, "Stashed media interface error; cleanup will retry", error)
        }
        lateinit var reconcile: Runnable
        reconcile = Runnable {
            val pam = monitor?.get() ?: return@Runnable
            // Unknown task state must fail open and remove our gain, not leave an app muted.
            val uids = if (enabled(Feature.MuteStashed)) runCatching { stashedUids(loader) }.onFailure(::warn).getOrDefault(emptySet()) else emptySet()
            val players = runCatching {
                val lock = Reflect.getObjectField(pam, "mPlayerLock") ?: return@runCatching emptyMap<Int, Any>()
                synchronized(lock) {
                    @Suppress("UNCHECKED_CAST")
                    HashMap(Reflect.getObjectField(pam, "mPlayers") as Map<Int, Any>)
                }
            }.onFailure(::warn).getOrNull()
            if (players != null) {
                val desired = players.mapNotNullTo(mutableSetOf()) { (piid, config) ->
                    runCatching {
                        val uid = Reflect.callMethod(config, "getClientUid") as Int
                        val attributes = Reflect.callMethod(config, "getAudioAttributes")
                        val usage = attributes?.let { Reflect.callMethod(it, "getUsage") as Int } ?: 0
                        if (SmallWindowRules.shouldMuteMedia(uid, usage, uids) &&
                            (owned[piid] === config || Reflect.callMethod(config, "isActive") == true)) piid else null
                    }.onFailure(::warn).getOrNull()
                }
                val changes = SmallWindowRules.mediaChanges(owned, players, desired)
                changes.discard.forEach { owned.remove(it) }
                changes.restore.forEach { piid ->
                    val config = owned[piid] ?: return@forEach
                    runCatching {
                        Reflect.callMethod(Reflect.callMethod(config, "getPlayerProxy"), "applyVolumeShaper", id, terminate)
                        owned.remove(piid)
                    }.onFailure(::warn)
                }
                changes.mute.forEach { piid ->
                    val config = players[piid] ?: return@forEach
                    runCatching {
                        Reflect.callMethod(Reflect.callMethod(config, "getPlayerProxy"), "applyVolumeShaper", shape, play)
                        owned[piid] = config
                    }.onFailure(::warn)
                }
            }
            // Catches feature disable, same-UID foreground tasks and player death while we own gain.
            // New playback and stash events wake the worker; no polling when nothing is muted.
            if (owned.isNotEmpty()) handler.postDelayed(reconcile, 1000)
        }
        fun schedule() { handler.removeCallbacks(reconcile); handler.post(reconcile) }
        listOf(event, release).forEach { method -> hook(method) { param ->
            param.thisObject?.let { pam -> handler.post { monitor = WeakReference(pam); schedule() } }
        } }
        listOf(add, remove, removeAll).forEach { method -> hook(method) { schedule() } }
    }
}
