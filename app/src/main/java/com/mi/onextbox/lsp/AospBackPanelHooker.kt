package com.mi.onextbox.lsp

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.WeakHashMap

internal object AospBackPanelHooker {
    private const val TAG = "ONextBox-AospBackPanel"

    fun hook(loader: ClassLoader) {
        val key = "$TAG@${System.identityHashCode(loader)}"
        val stateKey = "$key:state"
        val saved = HotReloadLifecycle.restoredState(stateKey) as? Array<*>
        if (!LspConfig.isAospBackPanelEnabledXposed() && saved == null) return
        var owner: NativeVisuals? = null
        runCatching {
            val targets = saved?.getOrNull(0) as? Array<*> ?: resolve(loader)
            val native = NativeVisuals(targets).also { owner = it }
            fun install(index: Int, action: (XposedInterface.Chain) -> Any?) {
                ModernHookRegistry.installFast("$key:$index", targets[index] as Method,
                    XposedInterface.Hooker(action))
            }
            install(7) { chain ->
                try { chain.proceed() } finally { native.destroyed(chain.thisObject) }
            }
            for (index in 1..4) install(index) { chain ->
                if (native.interceptVisual(index, chain.thisObject, chain.args)) null else chain.proceed()
            }
            (targets[23] as Array<*>).filterIsInstance<Method>().forEach {
                ModernHookRuntime.requireModule().deoptimize(it)
            }
            HotReloadLifecycle.register(stateKey,
                capture = { arrayOf(targets, native.snapshot()) },
                detach = { native.detach() },
                restore = { native.restoreSnapshot((it as? Array<*>)?.getOrNull(1)) },
                canReload = { native.canReload() },
            )
            install(0) { chain ->
                val detector = chain.thisObject ?: return@install chain.proceed()
                val event = chain.args.getOrNull(0) as? MotionEvent ?: return@install chain.proceed()
                val handlers = chain.args.getOrNull(1) as? Map<*, *> ?: return@install chain.proceed()
                native.dispatch(detector, event, handlers) { chain.proceed() }
            }
            HookLog.i(TAG, "Native back panel visual routing installed")
        }.onFailure {
            runCatching { owner?.detach() }
            listOf(0, 1, 2, 3, 4, 7).forEach { ModernHookRegistry.unhook("$key:$it") }
            HookLog.w(TAG, "Native back panel unavailable, stock gestures retained", it)
        }
    }

    private class Gesture(
        val panel: Any,
        val manager: Any,
        val down: MotionEvent,
    ) {
        val routing = AospBackPanelGesture(true)
        var fedOnStart = false
        var side = 0
        var rotation = 0
        var point: Any? = null
    }

    private class Frame(val gesture: Gesture?, val event: MotionEvent)

    private class Binding(val stock: Any, val visual: Any, var lastEvent: Long, var destroyed: Boolean = false)

    private class NativeVisuals(targets: Array<*>) {
        private val downVisual = targets[1] as Method
        private val updateVisual = targets[2] as Method
        private val motion = targets[5] as Method
        private val updateState = targets[6] as Method
        private val plugin = targets[11] as Field
        private val allowed = targets[12] as Field
        private val manager = targets[13] as Field
        private val leftPanel = targets[14] as Method
        private val state = targets[15] as Field
        private val callback = targets[16] as Field
        private val gone = targets[17]!!
        private val pointState = targets[18] as Method
        private val panelHandler = targets[19] as Field
        private val panelView = targets[20] as Method
        private val detectorContext = targets[21] as Field
        private val panelContext = targets[22] as Method
        private val eventDisplay = targets[24] as Method
        private val contextDisplay = targets[25] as Method
        private val lock = Any()
        private val gestures = WeakHashMap<Any, Gesture>()
        private val panels = WeakHashMap<Any, Binding>()
        private val frame = ThreadLocal<Frame?>()
        @Volatile private var broken = false

        private fun own(panel: Any, stock: Any? = null, lastEvent: Long = 0L, destroyed: Boolean = false) {
            synchronized(lock) {
                panels[panel]?.let {
                    check(callback.get(panel) === it.visual) { "Native panel callback changed" }
                    return
                }
                val original = stock ?: checkNotNull(callback.get(panel))
                check(callback.get(panel) === original && !Proxy.isProxyClass(original.javaClass))
                val visual = Proxy.newProxyInstance(callback.type.classLoader, arrayOf(callback.type),
                    AospBackPanelVisualCallback)
                val binding = Binding(original, visual, lastEvent, destroyed)
                callback.set(panel, visual)
                panels[panel] = binding
            }
        }

        fun dispatch(detector: Any, event: MotionEvent, handlers: Map<*, *>, action: () -> Any?): Any? {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                removeGesture(detector)
                val enabled = LspConfig.isAospBackPanelEnabledXposed()
                releaseIdlePanels(enabled && !broken)
                if (!broken && event.pointerCount > 0 && AospBackPanelRules.acceptsInput(
                        enabled,
                        event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN), event.pointerCount,
                        event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER,
                    )) {
                    runCatching {
                        val displayId = eventDisplay.invoke(event) as Int
                        val handler = handlers[displayId] ?: return@runCatching
                        val panel = plugin.get(handler) ?: return@runCatching
                        if (!motion.declaringClass.isInstance(panel)) return@runCatching
                        if ((panelHandler.get(panel) as Handler).looper != Looper.myLooper()) return@runCatching
                        val view = panelView.invoke(panel) as View
                        if (!view.isAttachedToWindow) return@runCatching
                        val sourceContext = detectorContext.get(detector) as android.content.Context
                        val targetContext = panelContext.invoke(panel) as android.content.Context
                        if (contextDisplay.invoke(sourceContext) != displayId || contextDisplay.invoke(targetContext) != displayId) {
                            return@runCatching
                        }
                        val viewManager = manager.get(detector) ?: return@runCatching
                        synchronized(lock) { gestures[detector] = Gesture(panel, viewManager, MotionEvent.obtain(event)) }
                    }.onFailure(::unavailable)
                }
            }
            val gesture = synchronized(lock) { gestures[detector] }
            val previous = frame.get()
            frame.set(Frame(gesture, event))
            var success = false
            return try {
                action().also { success = true }
            } finally {
                try {
                    if (gesture != null) completeFrame(detector, gesture, event, success)
                } finally {
                    if (previous == null) frame.remove() else frame.set(previous)
                }
            }
        }

        fun interceptVisual(index: Int, owner: Any?, args: List<Any?>): Boolean {
            if (index == 3) BackGestureHooker.recordFinished(owner, args.firstOrNull())
            val current = frame.get() ?: return false
            val gesture = current.gesture ?: return false
            if (owner !== gesture.manager) return false
            if (index == 1 && !gesture.routing.usingNative && !broken) {
                val event = args.getOrNull(1) as? MotionEvent ?: return false
                if (event !== current.event || event.pointerCount != 1) return false
                gesture.side = args[0] as Int
                gesture.rotation = args[2] as Int
                runCatching {
                    gesture.routing.start {
                        own(gesture.panel)
                        leftPanel.invoke(gesture.panel, gesture.side == 0)
                        send(gesture.panel, gesture.down)
                        send(gesture.panel, event)
                        gesture.fedOnStart = true
                    }
                }.onFailure {
                    abort(gesture)
                    unavailable(it)
                }
            }
            if (!gesture.routing.usingNative) return false
            if (index == 2) gesture.point = args.getOrNull(0)
            if (index == 3) {
                val point = args.getOrNull(0) ?: return false
                runCatching { gesture.routing.finish(pointState.invoke(point) as Int) }
                    .onFailure { gesture.routing.finish(0) }
            }
            return true
        }

        private fun completeFrame(detector: Any, gesture: Gesture, event: MotionEvent, success: Boolean) {
            val terminal = event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL
            val permitted = runCatching { allowed.getBoolean(detector) }.getOrDefault(false)
            try {
                if (gesture.routing.usingNative) {
                    runCatching {
                        when {
                            !success || (!terminal && (!permitted || event.pointerCount != 1)) -> cancel(gesture, event)
                            terminal -> {
                                sendAction(gesture.panel, event, gesture.routing.terminalAction(event.actionMasked))
                                gesture.routing.fail()
                            }
                            gesture.fedOnStart -> gesture.fedOnStart = false
                            else -> send(gesture.panel, event)
                        }
                    }.onFailure {
                        abort(gesture)
                        unavailable(it)
                        if (success && !terminal && permitted) {
                            runCatching {
                                downVisual.invoke(gesture.manager, gesture.side, event, gesture.rotation)
                                gesture.point?.let { point -> updateVisual.invoke(gesture.manager, point) }
                            }
                        }
                    }
                }
            } finally {
                if (terminal || !permitted || !success || event.pointerCount != 1) removeGesture(detector)
            }
        }

        private fun send(panel: Any, event: MotionEvent) {
            synchronized(lock) {
                val binding = checkNotNull(panels[panel])
                check(callback.get(panel) === binding.visual) { "Native panel callback changed" }
                binding.lastEvent = SystemClock.uptimeMillis()
            }
            motion.invoke(panel, event)
        }

        private fun sendAction(panel: Any, event: MotionEvent, action: Int) {
            val copy = MotionEvent.obtain(event)
            try {
                copy.action = action
                send(panel, copy)
            } finally { copy.recycle() }
        }

        private fun cancel(gesture: Gesture, event: MotionEvent) {
            sendAction(gesture.panel, event, MotionEvent.ACTION_CANCEL)
            gesture.routing.fail()
        }

        private fun abort(gesture: Gesture) {
            gesture.routing.fail()
            runCatching {
                val owned = synchronized(lock) {
                    panels[gesture.panel]?.let { callback.get(gesture.panel) === it.visual } == true
                }
                if (owned) updateState.invoke(gesture.panel, gone, true)
            }
        }

        private fun unavailable(error: Throwable) {
            if (!broken) HookLog.w(TAG, "Native visual failed, stock return handling retained", error)
            broken = true
        }

        private fun removeGesture(detector: Any) {
            val removed = synchronized(lock) { gestures.remove(detector) } ?: return
            try {
                runCatching {
                    if (removed.routing.usingNative) cancel(removed, removed.down)
                }.onFailure {
                    abort(removed)
                    unavailable(it)
                }
            } finally { removed.down.recycle() }
        }

        fun destroyed(panel: Any?) {
            if (panel == null) return
            val owned = synchronized(lock) {
                val binding = panels[panel] ?: return
                binding.lastEvent = SystemClock.uptimeMillis()
                binding.destroyed = true
                val matches = gestures.entries.filter { it.value.panel === panel }.map { it.key }
                matches.mapNotNull { gestures.remove(it) }
            }
            owned.forEach { it.routing.fail(); it.down.recycle() }
            runCatching { updateState.invoke(panel, gone, true) }
        }

        fun canReload(): Boolean = synchronized(lock) {
            AospBackPanelRules.canReload(
                gestures.size, panels.keys.all { state.get(it) === gone },
                SystemClock.uptimeMillis(), panels.values.maxOfOrNull { it.lastEvent } ?: 0L,
            )
        }

        fun snapshot(): Array<Array<Any>> = synchronized(lock) {
            panels.entries.map { arrayOf<Any>(it.key, it.value.stock, it.value.lastEvent, it.value.destroyed) }.toTypedArray()
        }

        fun detach() = synchronized(lock) {
            check(gestures.isEmpty()) { "Back gesture is active" }
            check(panels.keys.all { state.get(it) === gone }) { "Back panel is animating" }
            panels.forEach { (panel, binding) -> restoreCallback(panel, binding) }
            panels.clear()
        }

        fun restoreSnapshot(saved: Any?): Unit = synchronized(lock) {
            if (!LspConfig.isAospBackPanelEnabledXposed()) return@synchronized
            (saved as? Array<*>)?.forEach { entry ->
                val row = entry as? Array<*> ?: return@forEach
                val panel = row.getOrNull(0) ?: return@forEach
                val stock = row.getOrNull(1) ?: return@forEach
                if (motion.declaringClass.isInstance(panel)) own(panel, stock,
                    row.getOrNull(2) as? Long ?: 0L, row.getOrNull(3) as? Boolean ?: false)
            }
        }

        private fun restoreCallback(panel: Any, binding: Binding) {
            if (callback.get(panel) === binding.visual) callback.set(panel, binding.stock)
        }

        private fun releaseIdlePanels(keepActive: Boolean) = synchronized(lock) {
            val now = SystemClock.uptimeMillis()
            val retired = panels.entries.filter { (panel, binding) ->
                (!keepActive || binding.destroyed) && state.get(panel) === gone && now - binding.lastEvent >= 700L
            }.map { it.key }
            retired.forEach { panel -> panels.remove(panel)?.let { restoreCallback(panel, it) } }
        }
    }

    private fun resolve(loader: ClassLoader): Array<Any> {
        val detector = loader.loadClass("com.oplus.systemui.navigationbar.gesture.sidegesture.SideGestureDetector")
        val manager = loader.loadClass("com.oplus.systemui.navigationbar.gesture.sidegesture.view.SideGestureViewManager")
        val point = loader.loadClass("com.android.systemui.navigationbar.GesturePointContainerEx")
        val panel = loader.loadClass("com.android.systemui.navigationbar.gestural.BackPanelController")
        val handler = loader.loadClass("com.android.systemui.navigationbar.gestural.DisplayBackGestureHandlerImpl")
        val edge = loader.loadClass("com.android.systemui.navigationbar.gestural.EdgeBackGestureHandler")
        val callback = edge.getDeclaredField("mBackCallback").type
        val callbackInterface = loader.loadClass("com.android.systemui.plugins.NavigationEdgeBackPlugin\$BackCallback")
        check(callbackInterface.isAssignableFrom(callback))
        val state = panel.getDeclaredField("currentState")
        check(state.type.isEnum)
        val gone = checkNotNull(state.type.enumConstants).single { (it as Enum<*>).name == "GONE" }
        fun method(type: Class<*>, name: String, vararg args: Class<*>): Method =
            type.getDeclaredMethod(name, *args).apply {
                check(returnType == Void.TYPE && !Modifier.isStatic(modifiers))
                isAccessible = true
            }
        fun field(type: Class<*>, name: String, expected: Class<*>? = null): Field =
            type.getDeclaredField(name).apply {
                check(!Modifier.isStatic(modifiers) && (expected == null || this.type == expected))
                isAccessible = true
            }
        val integer = Integer.TYPE
        val boolean = java.lang.Boolean.TYPE
        val panelView = panel.declaredMethods.single {
            it.name.startsWith("getBackPanelView\$") && it.parameterCount == 0 && View::class.java.isAssignableFrom(it.returnType)
        }.apply { isAccessible = true }
        val getContext = panel.getMethod("getContext").apply {
            check(returnType == android.content.Context::class.java)
            isAccessible = true
        }
        val targets = arrayOf<Any>(
            method(detector, "onMotionEventImpl", MotionEvent::class.java, Map::class.java),
            method(manager, "onDownEvent", integer, MotionEvent::class.java, integer),
            method(manager, "onUpdateGestureView", point),
            method(manager, "onGestureFinished", point),
            method(manager, "onUpEvent", integer),
            method(panel, "onMotionEvent", MotionEvent::class.java),
            method(panel, "updateArrowState", state.type, boolean),
            method(panel, "onDestroy"),
            method(callback, "cancelBack"),
            method(callback, "setTriggerBack", boolean),
            method(callback, "triggerBack"),
            field(handler, "edgeBackPlugin"),
            field(detector, "mAllowGesture", boolean),
            field(detector, "mSideGestureViewManager"),
            method(panel, "setIsLeftPanel", boolean),
            state.apply { isAccessible = true },
            field(panel, "backCallback", callbackInterface),
            gone,
            point.getDeclaredMethod("getState").apply { check(returnType == integer); isAccessible = true },
            field(panel, "mainHandler", Handler::class.java),
            panelView,
            field(detector, "mContext", android.content.Context::class.java),
            getContext,
        )
        val callers = targets.take(11).filterIsInstance<Method>().toTypedArray()
        val displayId = MotionEvent::class.java.getMethod("getDisplayId").apply {
            check(returnType == integer)
            isAccessible = true
        }
        val contextDisplayId = android.content.Context::class.java.getMethod("getDisplayId").apply {
            check(returnType == integer)
            isAccessible = true
        }
        return arrayOf<Any>(*targets, callers, displayId, contextDisplayId)
    }
}
