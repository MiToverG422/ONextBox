package com.mi.onextbox.lsp

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.mi.onextbox.lsp.compat.AdaptationHookGate
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Reorder native layout children; never mirror glyphs or replace radio/view models. */
internal object LargeNetworkPositionHooker {
    private const val TAG = "ONextBox-LargeNetworkPosition"
    private val viewReported = AtomicBoolean()
    private val composeReported = AtomicBoolean()
    private val failed = AtomicBoolean()

    fun hook(loader: ClassLoader, compose: Boolean = true) {
        listOf("BigTypeStatusBarMobileViewBinder", "BigTypeStatusBarMobileViewBinderLegacy").forEach { name ->
            runCatching {
                val type = HostClassLookup.load("com.oplus.systemui.statusbar.pipeline.mobile.ui.view.$name", false, loader)
                val method = type.declaredMethods.single { it.name == "fromContext" && it.parameterCount == 1 }
                ModernHookRegistry.installFast("large-network-position:$name", method, XposedInterface.Hooker { chain ->
                    val view = chain.proceed()
                    if (LargeNetworkTypeSettings.enabledXposed() && view is ViewGroup) {
                        runCatching { positionView(view) }.onFailure(::reportFailure)
                    }
                    view
                })
                HookLog.i(TAG, "$name position hook ready")
            }.onFailure { if (name == "BigTypeStatusBarMobileViewBinder") reportFailure(it) }
        }
        // Placement changes already require SystemUI reload. Leave the shared Compose
        // Row path completely untouched when the native right-hand layout is selected.
        if (compose && LargeNetworkTypeSettings.enabledXposed() &&
            LargeNetworkTypeSettings.positionXposed() == LargeNetworkPositionRules.LEFT) {
            runCatching { hookCompose(loader) }.onFailure(::reportFailure)
        }
    }

    private fun positionView(root: ViewGroup) {
        fun find(name: String): View? {
            val id = root.resources.getIdentifier(name, "id", "com.android.systemui")
            return if (id == 0) null else root.findViewById(id)
        }
        val position = LargeNetworkTypeSettings.positionXposed()
        val text = find("mobile_type_text")
        val signal = find("mobile_signal_data_outer")
        val roaming = find("mobile_roaming_text")
        if (text != null && signal != null && roaming != null && text.parent === signal.parent) {
            if (position != LargeNetworkPositionRules.LEFT) return
            // The OEM ConstraintLayout and its LayoutParams belong to the host loader.
            val fields = listOf("startToStart", "startToEnd", "endToStart", "endToEnd")
                .map { text.layoutParams.javaClass.getField(it) }
            val views = listOf(roaming, text, signal)
            val before = views.map { view -> fields.map { it.getInt(view.layoutParams) } }
            val next = listOf(
                listOf(0, -1, text.id, -1),
                listOf(-1, roaming.id, signal.id, -1),
                listOf(-1, text.id, -1, 0),
            )
            try {
                views.forEachIndexed { index, view ->
                    fields.forEachIndexed { column, field -> field.setInt(view.layoutParams, next[index][column]) }
                    view.requestLayout()
                }
            } catch (error: Exception) {
                views.forEachIndexed { index, view ->
                    fields.forEachIndexed { column, field -> field.setInt(view.layoutParams, before[index][column]) }
                }
                throw error
            }
        } else {
            // C16 (and C17 legacy) keeps the drawable label + activity arrows in
            // their own LinearLayout. Move that existing group across the signal.
            val typeGroup = find("mobile_type_alone")?.parent as? LinearLayout ?: return
            val parent = typeGroup.parent as? LinearLayout ?: return
            val signalGroup = find("mobile_combo_real") ?: return
            if (signalGroup.parent !== parent) return
            val typeIndex = parent.indexOfChild(typeGroup)
            val signalIndex = parent.indexOfChild(signalGroup)
            val left = position == LargeNetworkPositionRules.LEFT
            if ((typeIndex < signalIndex) != left) {
                val params = typeGroup.layoutParams
                parent.removeView(typeGroup)
                parent.addView(typeGroup, parent.indexOfChild(signalGroup) + if (left) 0 else 1, params)
            }
        }
        if (viewReported.compareAndSet(false, true)) HookLog.i(TAG, "Native View position applied: $position")
    }

    private fun hookCompose(loader: ClassLoader) {
        val strategy = HostClassLookup.load("com.android.systemui.statusbar.pipeline.shared.ui.composable.BigTypeStackedMobileIconLayoutStrategy", false, loader)
        val content = strategy.declaredMethods.single { it.name == "StackedMobileIcon" && it.parameterCount == 8 }
        val row = HostClassLookup.load("androidx.compose.foundation.layout.RowKt", false, loader)
            .declaredMethods.single { it.name == "rowMeasurePolicy" && it.parameterCount == 4 }
        val policy = HostClassLookup.load("androidx.compose.ui.layout.MeasurePolicy", false, loader)
        val measureScope = HostClassLookup.load("androidx.compose.ui.layout.IntrinsicMeasureScope", false, loader)
        val direction = measureScope.getMethod("getLayoutDirection")
        val networkType = HostClassLookup.load("com.android.systemui.statusbar.pipeline.ui.viewmodel.OplusStackedMobileIconViewModel", false, loader)
            .getMethod("getNetworkTypeTextInfo")
        val pendingRow = ThreadLocal<Any>()
        val profile = AdaptationHookGate.bind()
        val policies = IdentityHashMap<Any, Any>()
        ModernHookRegistry.installFast("large-network-position:compose-row", row, XposedInterface.Hooker { chain ->
            val original = chain.proceed() ?: return@Hooker null
            val viewModel = pendingRow.get() ?: return@Hooker original
            // Only the strategy's outer Row. Prefix/suffix text and nested signal
            // rows keep their native policies, even while this call is in scope.
            pendingRow.set(null)
            // With Wi-Fi or no mobile data, the Row may contain just roaming +
            // signal. That pair must never be mistaken for signal + network type.
            if (networkType.invoke(viewModel) == null) return@Hooker original
            synchronized(policies) {
                policies.getOrPut(original) {
                    Proxy.newProxyInstance(policy.classLoader, arrayOf(policy)) { proxy, called, arguments ->
                        val method = checkNotNull(called)
                        when (method.name) {
                            "equals" -> proxy === arguments?.getOrNull(0)
                            "hashCode" -> System.identityHashCode(proxy)
                            "toString" -> "ONextBoxLargeNetworkPlacement"
                            else -> {
                                val args = arguments?.map { it }?.toTypedArray() ?: emptyArray()
                                val children = args.getOrNull(1) as? List<*>
                                if (children != null) {
                                    val rtl = direction.invoke(args[0]).toString() == "Rtl"
                                    args[1] = LargeNetworkPositionRules.arrange(children,
                                        AdaptationHookGate.isActive(profile) && LargeNetworkTypeSettings.enabledXposed(),
                                        LargeNetworkTypeSettings.positionXposed(), rtl)
                                    if (args[1] !== children && composeReported.compareAndSet(false, true))
                                        HookLog.i(TAG, "Native Compose position applied: ${children.size} children")
                                }
                                try { method.invoke(original, *args) }
                                catch (error: InvocationTargetException) { throw error.targetException }
                            }
                        }
                    }
                }
            }
        })
        ModernHookRegistry.installFast("large-network-position:compose-scope", content, XposedInterface.Hooker { chain ->
            val previous = pendingRow.get()
            pendingRow.set(chain.getArg(1))
            try { chain.proceed() } finally {
                if (previous == null) pendingRow.remove() else pendingRow.set(previous)
            }
        })
        // Prevent ART from retaining an inlined native Row policy in this caller.
        runCatching { ModernHookRuntime.requireModule().deoptimize(content) }
            .onFailure { HookLog.w(TAG, "Could not deoptimize native stacked caller", it) }
        HookLog.i(TAG, "Native Compose position hooks ready")
    }

    private fun reportFailure(error: Throwable) {
        if (failed.compareAndSet(false, true)) HookLog.w(TAG, "Position adapter unavailable; native layout retained", error)
    }
}
