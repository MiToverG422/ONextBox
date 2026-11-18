package com.mi.onextbox.lsp

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import dalvik.system.InMemoryDexClassLoader
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.ByteBuffer

/** Only the Compose ABI/model boundary is adapted; drawing lives in the C16 donor dex. */
internal object C16StackedSignalHooker {
    private const val TAG = "ONextBox-C16Signal"
    private const val PREFIX = "com.android.systemui.statusbar.pipeline.shared.ui.composable."

    fun hook(loader: ClassLoader) {
        runCatching {
            val method = HostClassLookup.load(PREFIX + "OplusStackedMobileIconLayoutStrategy", false, loader)
                .declaredMethods.single { it.name.startsWith("SignalIcon-") && it.parameterCount == 7 }
            var adapter: Adapter? = null
            var unavailable = false
            ModernHookRegistry.installFast("c16-stacked-signal", method, XposedInterface.Hooker { chain ->
                if (unavailable) return@Hooker chain.proceed()
                val renderer = adapter ?: runCatching { Adapter(loader) }.getOrElse {
                    unavailable = true
                    HookEvidence.report("stacked_signal", "unavailable")
                    HookLog.w(TAG, "Host ABI unavailable; retaining stock", it)
                    return@Hooker chain.proceed()
                }.also { adapter = it }
                // Keep the lightweight Compose state subscription even while disabled.
                // Load and validate donor code only when requested, before opening a group.
                val drawingReady = renderer.readState.invoke(renderer.state) == true && renderer.prepare()
                val args = chain.args.toTypedArray()
                // Preflight ALL donor drawing branches before entering any real composition.
                try {
                    val composer = requireNotNull(renderer.start.invoke(args[5], 0x4f4e1637))
                    val enabled = renderer.readState.invoke(renderer.state) == true
                    if (enabled && drawingReady) renderer.render(args, composer)
                    else chain.proceed()
                    val scope = renderer.end.invoke(composer)
                    if (scope != null) {
                        val target = chain.thisObject
                        val restart = Proxy.newProxyInstance(loader, arrayOf(renderer.function2)) { proxy, called, next ->
                            when (called.name) {
                                "invoke" -> {
                                    val replay = args.copyOf()
                                    replay[5] = next!![0]
                                    replay[6] = (replay[6] as Int) or 1
                                    try { method.invoke(target, *replay) }
                                    catch (error: InvocationTargetException) { throw error.targetException }
                                    renderer.unit
                                }
                                "equals" -> proxy === next?.getOrNull(0)
                                "hashCode" -> System.identityHashCode(proxy)
                                "toString" -> "C16StackedSignalRestart"
                                else -> null
                            }
                        }
                        renderer.updateScope.invoke(scope, restart)
                    }
                    null
                } catch (error: InvocationTargetException) {
                    // Never fall back after a group has started: that corrupts Compose's stack.
                    throw error.targetException
                }
            })
            listOf("DefaultStackedMobileIconLayoutStrategy", "BigTypeStackedMobileIconLayoutStrategy",
                "TigoFiveGStackedMobileIconLayoutStrategy", "OplusStackedMobileIconLayoutStrategy\$ComposeDefaultImpls")
                .forEach { name ->
                    HostClassLookup.load(PREFIX + name, false, loader).declaredMethods
                        .filter { it.name.contains("SignalIcon") }.forEach {
                            ModernHookRuntime.requireModule().deoptimize(it)
                        }
                }
            HookLog.i(TAG, "C16 donor adapter installed; only existing dual-SIM stacked branch is affected")
            HookEvidence.report("stacked_signal", "installed")
        }.onFailure {
            HookEvidence.report("stacked_signal", "unavailable")
            HookLog.w(TAG, "Stacked entry unavailable", it)
        }
    }

    private class Adapter(private val loader: ClassLoader) {
        private fun type(name: String) = HostClassLookup.load(name, false, loader)
        private val composerType = type("androidx.compose.runtime.Composer")
        val start = composerType.getMethod("startRestartGroup", Int::class.javaPrimitiveType)
        val end = composerType.getMethod("endRestartGroup")
        val function2 = type("kotlin.jvm.functions.Function2")
        val unit = type("kotlin.Unit").getField("INSTANCE").get(null)
        val updateScope = type("androidx.compose.runtime.ScopeUpdateScope").getMethod("updateScope", function2)
        private val snapshots = type("androidx.compose.runtime.SnapshotStateKt")
        val state = snapshots.getMethod("mutableStateOf", Any::class.java, type("androidx.compose.runtime.SnapshotMutationPolicy"))
            .invoke(null, C16StackedSignalSettings.enabledXposed(), snapshots.getMethod("structuralEqualityPolicy").invoke(null))
        val readState = type("androidx.compose.runtime.State").getMethod("getValue")
        private val writeState = type("androidx.compose.runtime.MutableState").getMethod("setValue", Any::class.java)
        private val main = Handler(Looper.getMainLooper())
        private val preferences = requireNotNull(ModernHookRuntime.remotePreferences("lsp_features"))
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == null || key == C16StackedSignalSettings.KEY) main.post {
                writeState.invoke(state, prefs.getBoolean(C16StackedSignalSettings.KEY, false))
            }
        }

        init {
            HotReloadLifecycle.register("c16-signal-state",
                detach = { preferences.unregisterOnSharedPreferenceChangeListener(listener) },
                restore = { preferences.registerOnSharedPreferenceChangeListener(listener) },
                // Live host restart callbacks retain this adapter until SystemUI restarts.
                canReload = { false })
            preferences.registerOnSharedPreferenceChangeListener(listener)
            writeState.invoke(state, preferences.getBoolean(C16StackedSignalSettings.KEY, false))
        }

        private val drawing = DeferredHookResource {
            Drawing(loader)
        }

        fun prepare(): Boolean = drawing.prepare { error ->
            HookEvidence.report("stacked_signal", "unavailable")
            HookLog.w(TAG, "Donor unavailable; retaining stock drawing", error)
        }

        fun render(args: Array<Any?>, composer: Any) = drawing.value().render(args, composer)
    }

    private class Drawing(private val loader: ClassLoader) {
        private fun type(name: String) = HostClassLookup.load(name, false, loader)
        private val context = HostClassLookup.load("android.app.ActivityThread").getMethod("currentApplication")
            .invoke(null) as Context
        private val donor = InMemoryDexClassLoader(ByteBuffer.wrap(
            context.createPackageContext("com.mi.onextbox", 0).assets.open("c16-stacked-signal.dex").use { it.readBytes() }), loader)
        private fun port(name: String) = HostClassLookup.load("com.mi.onextbox.port.c16.$name", true, donor)
        private val four = port("FourBarsDimensions").getField("INSTANCE").get(null)
        private val five = port("FiveBarsDimensions").getField("INSTANCE").get(null)
        private val dimensions = port("StackedMobileIconDimensions")
        private val width = port("BarsDependentDimensions").getMethod("getTotalWidth-XSAIIZE")
        private val height = dimensions.getField("IconHeightSp").getLong(null)
        private val iconSpacing = dimensions.getField("IconSpacingSp").getLong(null)
        private val callback = port("StackedMobileIconKt\$StackedMobileIcon\$3\$1")
            .declaredConstructors.single().apply { isAccessible = true }
        private val dualType = type("com.android.systemui.statusbar.pipeline.mobile.ui.model.DualSim")
        private val primary = dualType.getMethod("getPrimary")
        private val secondary = dualType.getMethod("getSecondary")
        private val cellType = primary.returnType
        private val levels = cellType.getMethod("getNumberOfLevels")
        private val composerType = type("androidx.compose.runtime.Composer")
        private val consume = composerType.getMethod("consume", type("androidx.compose.runtime.CompositionLocal"))
        private val densityLocal = type("androidx.compose.ui.platform.CompositionLocalsKt").getMethod("getLocalDensity").invoke(null)
        private val toDp = type("androidx.compose.ui.unit.FontScaling").getMethod("toDp-GaN1DYA", Long::class.javaPrimitiveType)
        private val modifierType = type("androidx.compose.ui.Modifier")
        private val rowScope = type("androidx.compose.foundation.layout.RowScopeInstance")
            .getField("INSTANCE").get(null)
        private val alignInRow = type("androidx.compose.foundation.layout.RowScope").getMethod("align",
            modifierType, type("androidx.compose.ui.Alignment\$Vertical"))
        private val centerVertically = type("androidx.compose.ui.Alignment\$Companion")
            .getMethod("getCenterVertically")
            .invoke(type("androidx.compose.ui.Alignment").getField("Companion").get(null))
        private val size = type("androidx.compose.foundation.layout.SizeKt").getMethod("size-VpY3zN4",
            modifierType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
        private val horizontalPadding = type("androidx.compose.foundation.layout.PaddingKt").getMethod("padding-VpY3zN4",
            modifierType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
        private val canvas = type("androidx.compose.foundation.CanvasKt").getMethod("Canvas", modifierType,
            type("kotlin.jvm.functions.Function1"), composerType, Int::class.javaPrimitiveType)
        private var reported = false
        init { preflight() }

        fun render(args: Array<Any?>, composer: Any) {
            val dual = args[0]!!
            val bars = maxOf(levels.invoke(primary.invoke(dual)) as Int, levels.invoke(secondary.invoke(dual)) as Int) - 1
            val dims = if (bars == 5) five else four
            val density = consume.invoke(composer, densityLocal)
            // C17's stacked Row defaults to Bottom, while big network-type text has
            // its own CenterVertically parent data. Give the shorter C16 Canvas the
            // same alignment so revealing 4G/5G text cannot pin it to the row bottom.
            // Keep the caller's horizontal padding and the donor's original size.
            val aligned = alignInRow.invoke(rowScope, args[4], centerVertically)
            // C16's outer Row supplied spacing separately from its drawing bounds.
            // Reuse that 2sp separation around this transplanted child, retaining
            // C17's group padding instead of copying the whole C16 group's 4sp inset.
            // Padding MUST precede size: otherwise it shrinks the donor Canvas.
            val spaced = horizontalPadding.invoke(null, aligned, toDp.invoke(density, iconSpacing), 0f)
            val modifier = size.invoke(null, spaced, toDp.invoke(density, width.invoke(dims)), toDp.invoke(density, height))
            canvas.invoke(null, modifier, callback.newInstance(dims, bars, dual, args[2]), composer, 0)
            if (!reported) {
                reported = true
                HookEvidence.report("stacked_signal", "observed")
                HookLog.i(TAG, "C16 original drawing active: $bars bars, dual SIM; donor dex ABI preflight passed")
            }
        }

        private fun preflight() {
            val scopeType = type("androidx.compose.ui.graphics.drawscope.CanvasDrawScope")
            val scope = scopeType.getConstructor().newInstance()
            val draw = scopeType.declaredMethods.single { it.name == "draw-yzxVdVo" }
            val density = type("androidx.compose.ui.unit.DensityImpl")
                .getConstructor(Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).newInstance(3f, 1f)
            val direction = type("androidx.compose.ui.unit.LayoutDirection").getField("Ltr").get(null)
            val hostCanvasType = type("androidx.compose.ui.graphics.AndroidCanvas")
            val hostCanvas = hostCanvasType.getConstructor().newInstance()
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            try {
                hostCanvasType.getDeclaredField("internalCanvas").apply { isAccessible = true }.set(hostCanvas, Canvas(bitmap))
                val packedSize = (96f.toRawBits().toLong() shl 32) or (96f.toRawBits().toLong() and 0xffffffffL)
                val cell = cellType.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                val dual = dualType.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, cellType, cellType)
                for (count in 4..5) for (level in 0..count) for (warning in listOf(false, true)) {
                    val model = dual.newInstance(1, 2, cell.newInstance(level, count + 1, warning, false),
                        cell.newInstance(count - level, count + 1, warning, false))
                    draw.invoke(scope, density, direction, hostCanvas, packedSize,
                        callback.newInstance(if (count == 5) five else four, count, model, -0x100000000L))
                }
                HookLog.i(TAG, "Donor preflight passed: four/five bars, every level, exclamation paths")
            } finally { bitmap.recycle() }
        }
    }
}
