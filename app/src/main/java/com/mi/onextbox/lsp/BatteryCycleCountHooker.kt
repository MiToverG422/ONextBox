package com.mi.onextbox.lsp

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig.BatteryFeature
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.io.File
import java.lang.ref.WeakReference
import java.text.NumberFormat
import java.util.concurrent.Executors

internal object BatteryCycleCountHooker {
    private const val TAG = "ONextBox-BatteryCycles"
    private const val PACKAGE = "com.oplus.battery"
    private const val HEALTH_ACTIVITY = "com.oplus.powermanager.fuelgaue.BatteryHealthActivity"
    private const val CAPACITY_PREFERENCE = "com.oplus.powermanager.fuelgaue.BatteryHealthDataPreference"
    private const val CACHE_MS = 30_000L
    private val reader = Executors.newSingleThreadExecutor { job ->
        Thread(job, "ONextBox-BatteryCycles").apply { isDaemon = true }
    }
    private data class Reading(val count: Int?, val timestamp: Long)
    @Volatile private var cached: Reading? = null

    private class Card(
        val column: LinearLayout,
        val value: TextView,
        val children: List<Pair<View, ViewGroup.LayoutParams>>,
    ) {
        var generation = 0
        var pending = false
    }

    fun hook(loader: ClassLoader) {
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

    private fun enabled() = LspConfig.isBatteryFeatureEnabledXposed(BatteryFeature.ShowCycleCount)

    private fun card(root: ViewGroup): Card? = (0 until root.childCount).firstNotNullOfOrNull {
        root.getChildAt(it).tag as? Card
    }

    private fun update(root: ViewGroup) {
        var state = card(root)
        if (!enabled()) {
            if (state != null) restore(root, state)
            return
        }
        if (state == null) state = append(root)
        val current = state
        cached?.let { show(current.value, it.count) }
        if (current.pending) return
        val generation = ++current.generation
        current.pending = true
        val host = root.context.applicationContext
        val rootRef = WeakReference(root)
        val cardRef = WeakReference(current)
        reader.execute {
            val previous = cached
            val reading = if (previous != null && SystemClock.elapsedRealtime() - previous.timestamp < CACHE_MS) {
                previous
            } else Reading(read(host), SystemClock.elapsedRealtime()).also { cached = it }
            rootRef.get()?.post {
                val target = rootRef.get() ?: return@post
                val row = cardRef.get() ?: return@post
                if (row.generation != generation || card(target) !== row) return@post
                row.pending = false
                if (enabled()) show(row.value, reading.count) else restore(target, row)
            }
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
        val bottom = inflater.inflate(layout, null) as ViewGroup
        val cycleTitle = requireNotNull(bottom.findViewById<TextView>(titleId))
        val cycleValue = requireNotNull(bottom.findViewById<TextView>(dataId))
        cycleTitle.id = View.generateViewId()
        cycleValue.id = View.generateViewId()
        cycleTitle.text = moduleString(context, R.string.battery_cycle_count_label, "Cycle count")
        cycleValue.setTextColor(capacity.textColors)
        show(cycleValue, cached?.count)
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
        val state = Card(column, cycleValue, children)
        column.tag = state
        listOf(top, bottom).forEach { row ->
            row.id = View.NO_ID
            row.background = null
            row.setPadding(0, 0, 0, 0)
            column.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
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
        ++state.generation
        state.pending = false
        root.removeView(state.column)
        state.children.forEach { (child, params) ->
            (child.parent as? ViewGroup)?.removeView(child)
            root.addView(child, params)
        }
    }

    private fun read(context: Context): Int? {
        val broadcast = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)
        }.getOrNull()
        val count = BatteryCycleCountRules.resolve(broadcast,
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
        return count
    }

    private fun show(value: TextView, count: Int?) {
        value.text = if (count == null) hostString(value.context, "battery_health_secret_code_info_obtain_fail",
            R.string.battery_cycle_count_unavailable, "Unavailable")
        else NumberFormat.getIntegerInstance(value.resources.configuration.locales[0]).apply {
            isGroupingUsed = false
        }.format(count)
    }

    @SuppressLint("DiscouragedApi") // IDs belong to the host APK and vary between firmware releases.
    private fun resource(context: Context, name: String, type: String) =
        context.resources.getIdentifier(name, type, PACKAGE)

    private fun hostString(context: Context, name: String, fallback: Int, text: String): String {
        val id = resource(context, name, "string")
        return if (id != 0) context.getString(id) else moduleString(context, fallback, text)
    }

    private fun moduleString(context: Context, id: Int, fallback: String): String = runCatching {
        context.createPackageContext("com.mi.onextbox", 0)
            .createConfigurationContext(context.resources.configuration).getString(id)
    }.getOrDefault(fallback)
}
