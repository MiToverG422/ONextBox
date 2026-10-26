package com.mi.onextbox.lsp

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.mi.onextbox.lsp.LspConfig.BatteryFeature
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import io.github.libxposed.api.XposedInterface
import java.io.File
import java.lang.ref.WeakReference
import java.text.NumberFormat
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.WeakHashMap

internal object BatteryCycleCountHooker {
    private const val TAG = "ONextBox-BatteryCycles"
    private const val PACKAGE = "com.oplus.battery"
    private const val HEALTH_ACTIVITY = "com.oplus.powermanager.fuelgaue.BatteryHealthActivity"
    private const val CAPACITY_PREFERENCE = "com.oplus.powermanager.fuelgaue.BatteryHealthDataPreference"
    private const val CACHE_MS = 30_000L
    private fun newReader() = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue()) { job ->
        Thread(job, "ONextBox-BatteryCycles").apply { isDaemon = true }
    }
    private var reader = newReader()
    private val main = Handler(Looper.getMainLooper())
    private val roots = WeakHashMap<ViewGroup, Boolean>()
    private data class Reading(val info: BatteryHealthInfo, val timestamp: Long)
    @Volatile private var cached: Reading? = null

    private class Card(
        val column: LinearLayout,
        val rows: Map<BatteryFeature, Pair<View, TextView>>,
        val children: List<Pair<View, ViewGroup.LayoutParams>>,
    ) {
        val readState = BatteryCycleReadState()
    }

    fun hook(loader: ClassLoader) {
        HotReloadLifecycle.register("battery:cycle-cards", capture = { roots.keys.toTypedArray() },
            canReload = { reader.activeCount == 0 && reader.queue.isEmpty() }, detach = {
            roots.keys.toList().forEach { root -> card(root)?.let { restore(root, it) } }
            reader.shutdownNow()
            check(reader.awaitTermination(250, TimeUnit.MILLISECONDS)) { "Battery cycle reader is busy" }
            main.removeCallbacksAndMessages(null)
        }, restore = { state ->
            if (reader.isShutdown) reader = newReader()
            (state as? Array<*>)?.filterIsInstance<ViewGroup>()?.forEach(::update)
        }, resumeAfterRejection = {
            roots.keys.toList().forEach(::update)
        })
        val prefix = "$TAG@${System.identityHashCode(loader)}:"
        runCatching {
            val preference = ModernReflect.findClass(CAPACITY_PREFERENCE, loader)
            val bind = preference.declaredMethods.single {
                it.name == "onBindViewHolder" && it.parameterCount == 1 && it.returnType == Void.TYPE
            }
            ModernHookRegistry.installFast(prefix + "bind", bind, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                runCatching {
                    val holder = chain.getArg(0) ?: return@runCatching
                    val root = ModernReflect.getObjectField(holder, "itemView") as? ViewGroup
                        ?: return@runCatching
                    update(root)
                }.onFailure { HookLog.w(TAG, "Cycle row bind failed; stock capacity retained", it) }
                result
            })
            val activity = ModernReflect.findClass(HEALTH_ACTIVITY, loader)
            val resume = ModernReflect.findMethodExact(activity, "onResume", emptyArray())
            ModernHookRegistry.installFast(prefix + "resume", resume, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                val owner = chain.thisObject as? Activity
                if (owner != null && activity.isInstance(owner)) runCatching {
                    val id = resource(owner, "max_capacity_data", "id")
                    val value = owner.findViewById<View>(id) ?: return@runCatching
                    var parent = value.parent as? ViewGroup
                    val closest = parent
                    while (parent != null) {
                        if (card(parent) != null) { update(parent); return@runCatching }
                        parent = parent.parent as? ViewGroup
                    }
                    closest?.let(::update)
                }.onFailure { HookLog.w(TAG, "Cycle row refresh failed", it) }
                result
            })
            HookLog.i(TAG, "Battery health cycle row hooks installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(TAG, "Battery health layout unavailable; stock behavior retained", it)
        }
    }

    private val informationFeatures = listOf(
        BatteryFeature.ShowCalculatedHealth,
        BatteryFeature.ShowCycleCount, BatteryFeature.ShowHealthStatus,
        BatteryFeature.ShowFullChargeCapacity, BatteryFeature.ShowRatedCapacity,
        BatteryFeature.ShowTypicalCapacity,
    )

    private fun enabled() = informationFeatures.any(LspConfig::isBatteryFeatureEnabledXposed)

    private fun refresh(state: Card, info: BatteryHealthInfo?) {
        state.rows.forEach { (feature, row) ->
            row.first.visibility = if (LspConfig.isBatteryFeatureEnabledXposed(feature)) View.VISIBLE else View.GONE
            show(row.second, feature, info)
        }
    }

    private fun card(root: ViewGroup): Card? = (0 until root.childCount).firstNotNullOfOrNull {
        root.getChildAt(it).tag as? Card
    }

    private fun update(root: ViewGroup) {
        if (HotReloadLifecycle.isRetiring) return
        roots[root] = true
        var state = card(root)
        if (!enabled()) {
            if (state != null) restore(root, state)
            return
        }
        if (state == null) state = append(root)
        val current = state
        refresh(current, cached?.info)
        val generation = current.readState.begin() ?: return
        val host = root.context.applicationContext
        val rootRef = WeakReference(root)
        val cardRef = WeakReference(current)
        fun complete(reading: Reading?, retryAfterRejection: Boolean = false) {
            val completion = Runnable {
                val row = cardRef.get() ?: return@Runnable
                // A vetoed update can retire a reader without detaching its card. Always
                // release this request on the main thread, even when UI work is forbidden.
                if (!row.readState.complete(generation)) return@Runnable
                if (HotReloadLifecycle.isRetiring) return@Runnable
                val target = rootRef.get() ?: return@Runnable
                if (card(target) !== row) return@Runnable
                if (reading == null) {
                    // The rejection resumer may have run while this reader was still
                    // busy. In that ordering its canceled completion owns the retry.
                    if (retryAfterRejection) update(target)
                    return@Runnable
                }
                if (enabled()) refresh(row, reading.info) else restore(target, row)
            }
            if (Looper.myLooper() == Looper.getMainLooper()) completion.run() else main.post(completion)
        }
        try {
            reader.execute {
                var result: Reading? = null
                var retryAfterRejection = false
                try {
                    if (HotReloadLifecycle.isRetiring) {
                        retryAfterRejection = true
                        return@execute
                    }
                    val previous = cached
                    val reading = if (previous != null && SystemClock.elapsedRealtime() - previous.timestamp < CACHE_MS) {
                        previous
                    } else Reading(read(host), SystemClock.elapsedRealtime()).also { cached = it }
                    if (HotReloadLifecycle.isRetiring) {
                        retryAfterRejection = true
                        return@execute
                    }
                    result = reading
                } catch (error: Throwable) {
                    HookLog.w(TAG, "Cycle count read failed", error)
                } finally {
                    complete(result, retryAfterRejection)
                }
            }
        } catch (error: Throwable) {
            complete(null)
            HookLog.w(TAG, "Cycle count reader unavailable", error)
        }
    }

    private fun append(root: ViewGroup): Card {
        val context = root.context
        val titleId = resource(context, "max_capacity_content", "id")
        val dataId = resource(context, "max_capacity_data", "id")
        val layout = resource(context, "battery_health_data", "layout")
        require(titleId != 0 && dataId != 0 && layout != 0)
        val title = root.findViewById<TextView>(titleId)
        val capacity = root.findViewById<TextView>(dataId)
        require(title != null && capacity != null && title.parent === root && capacity.parent === root)
        val inflater = LayoutInflater.from(context)
        val top = inflater.inflate(layout, null) as ViewGroup
        val rows = informationFeatures.associateWith { feature ->
            val row = inflater.inflate(layout, null) as ViewGroup
            val rowTitle = requireNotNull(row.findViewById<TextView>(titleId))
            val rowValue = requireNotNull(row.findViewById<TextView>(dataId))
            rowTitle.id = View.generateViewId()
            rowValue.id = View.generateViewId()
            val (name, fallback) = when (feature) {
                BatteryFeature.ShowCycleCount -> "battery_cycle_count_label" to "Cycle count"
                BatteryFeature.ShowHealthStatus -> "battery_health_status_label" to "Health status"
                BatteryFeature.ShowFullChargeCapacity -> "battery_health_full_charge_label" to "Full charge capacity"
                BatteryFeature.ShowRatedCapacity -> "battery_health_rated_label" to "Rated capacity"
                BatteryFeature.ShowTypicalCapacity -> "battery_health_typical_label" to "Typical capacity"
                BatteryFeature.ShowCalculatedHealth -> "battery_health_calculated_label" to "Actual capacity (calculated)"
                else -> error("Not a battery information feature")
            }
            rowTitle.text = moduleString(context, name, fallback)
            rowValue.setTextColor(capacity.textColors)
            row to rowValue
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = false
        }
        val params = ModernReflect.newInstance(title.layoutParams.javaClass,
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) as ViewGroup.LayoutParams
        // Build all host-specific objects before moving the original capacity views.
        listOf("startToStart", "endToEnd", "topToTop", "bottomToBottom").forEach {
            ModernReflect.setObjectField(params, it, 0)
        }
        val children = (0 until root.childCount).map { root.getChildAt(it).let { child -> child to child.layoutParams } }
        val state = Card(column, rows, children)
        column.tag = state
        (listOf(top) + rows.values.map { it.first }).forEach { row ->
            row.id = View.NO_ID
            row.background = null
            row.setPadding(0, 0, 0, 0)
            column.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        refresh(state, cached?.info)
        top.removeAllViews()
        try {
            children.forEach { (child, childParams) -> root.removeView(child); top.addView(child, childParams) }
            root.addView(column, params)
        } catch (error: Throwable) {
            restore(root, state)
            throw error
        }
        return state
    }

    private fun restore(root: ViewGroup, state: Card) {
        state.readState.invalidate()
        state.column.tag = null
        root.removeView(state.column)
        state.children.forEach { (child, params) ->
            (child.parent as? ViewGroup)?.removeView(child)
            root.addView(child, params)
        }
    }

    private fun read(context: Context): BatteryHealthInfo {
        val broadcast = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val count = BatteryCycleCountRules.resolve(broadcast?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1),
            kernel = { runCatching { File("/sys/class/power_supply/battery/cycle_count").readText() }.getOrNull() },
            diagnostic = {
                runCatching {
                    val manager = ModernReflect.findClass("android.os.OplusBatteryManager", context.classLoader)
                    manager.getMethod("getChgConfig", Int::class.javaPrimitiveType, String::class.java,
                        Int::class.javaPrimitiveType).invoke(manager.getDeclaredConstructor().newInstance(), 20, "", 2) as? String
                }.getOrNull()
            },
        )
        HookLog.d(TAG, "Cycle count read: ${count ?: "unavailable"}")
        return BatteryHealthInfoReader.read(context, broadcast, count)
    }

    private fun show(value: TextView, feature: BatteryFeature, info: BatteryHealthInfo?) {
        val unavailable = hostString(value.context, "battery_health_secret_code_info_obtain_fail",
            "battery_cycle_count_unavailable", "Unavailable")
        if (feature == BatteryFeature.ShowCalculatedHealth) {
            val ratio = BatteryHealthInfoRules.healthRatio(info?.fullChargeCapacity, info?.ratedCapacity)
            value.text = ratio?.let {
                NumberFormat.getPercentInstance(value.resources.configuration.locales[0]).apply {
                    minimumFractionDigits = 2
                    maximumFractionDigits = 2
                    isGroupingUsed = false
                }.format(it)
            } ?: unavailable
            return
        }
        if (feature == BatteryFeature.ShowHealthStatus) {
            val health = when (info?.health) {
                BatteryManager.BATTERY_HEALTH_UNKNOWN -> "unknown" to "Unknown"
                BatteryManager.BATTERY_HEALTH_GOOD -> "good" to "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat" to "Overheated"
                BatteryManager.BATTERY_HEALTH_DEAD -> "dead" to "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage" to "Overvoltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure" to "Failure"
                BatteryManager.BATTERY_HEALTH_COLD -> "cold" to "Cold"
                else -> null
            }
            value.text = health?.let { moduleString(value.context, "battery_health_${it.first}", it.second) } ?: unavailable
            return
        }
        val count = when (feature) {
            BatteryFeature.ShowCycleCount -> info?.cycleCount
            BatteryFeature.ShowFullChargeCapacity -> info?.fullChargeCapacity
            BatteryFeature.ShowRatedCapacity -> info?.ratedCapacity
            BatteryFeature.ShowTypicalCapacity -> info?.typicalCapacity
            else -> null
        }
        value.text = if (count == null) unavailable else NumberFormat.getIntegerInstance(value.resources.configuration.locales[0]).apply {
            isGroupingUsed = false
        }.format(count) + if (feature == BatteryFeature.ShowCycleCount) "" else " mAh"
    }

    @SuppressLint("DiscouragedApi") // IDs belong to the host APK and vary between firmware releases.
    private fun resource(context: Context, name: String, type: String) =
        context.resources.getIdentifier(name, type, PACKAGE)

    private fun hostString(context: Context, name: String, fallback: String, text: String): String {
        val id = resource(context, name, "string")
        return if (id != 0) context.getString(id) else moduleString(context, fallback, text)
    }

    private fun moduleString(context: Context, name: String, fallback: String): String =
        ModuleStrings.get(context, name, fallback = fallback)
}

/** Main-thread request ownership; canceled reads must not leave an attached card busy. */
internal class BatteryCycleReadState {
    private var generation = 0
    var pending = false
        private set

    fun begin(): Int? {
        if (pending) return null
        pending = true
        return ++generation
    }

    fun complete(request: Int): Boolean {
        if (!pending || request != generation) return false
        pending = false
        return true
    }

    fun invalidate() {
        ++generation
        pending = false
    }
}
