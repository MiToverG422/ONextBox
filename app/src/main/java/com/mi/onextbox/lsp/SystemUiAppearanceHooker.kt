package com.mi.onextbox.lsp

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.mi.onextbox.lsp.SystemUiAppearance as Option
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import java.util.WeakHashMap
import java.lang.ref.WeakReference
import kotlin.math.abs

internal class SystemUiAppearanceHooker(private val loader: ClassLoader) {
    private val main = Handler(Looper.getMainLooper())
    private val refreshTask = Runnable { if (!HotReloadLifecycle.isRetiring) refresh() }
    private var prefs: SharedPreferences? = null
    private val keys = Option.entries.mapTo(hashSetOf()) { it.key } + StatusBarIconLayout.keys
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in keys) {
            main.removeCallbacks(refreshTask)
            main.post(refreshTask)
        }
    }
    private val barClass = Reflect.findClassIfExists("com.android.systemui.statusbar.phone.PhoneStatusBarView", loader)
    private val barExtension = barClass?.declaredMethods?.firstOrNull {
        it.name == "getPhoneStatusBarViewEx" && it.parameterCount == 0
    }?.apply { isAccessible = true }
    private val refreshPadding = barExtension?.returnType?.methods?.firstOrNull {
        it.name == "updateContentsPadding" && it.parameterCount == 0
    }
    private val nativeInsets = WeakHashMap<View, StatusBarIconLayout.State>()
    private val headerClass = Reflect.findClassIfExists("com.oplus.systemui.separate.OplusQSSimpleHeader", loader)
    private val settingsButtonGetter = headerClass?.declaredMethods?.firstOrNull {
        it.name == "getSettingsButton" && it.parameterCount == 0 && View::class.java.isAssignableFrom(it.returnType)
    }
    private val rowClass = Reflect.findClassIfExists("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow", loader)
    private val customClass = Reflect.findClassIfExists("com.oplus.systemui.statusbar.notification.customcard.OplusCustomRow", loader)
    private val bannerClass = Reflect.findClassIfExists("com.oplus.systemui.notification.interruption.fullscreenbanner.view.FullScreenBannerContainer", loader)
    private val customCards = customClass?.let { runCatching { CustomNotificationCorners(it) }.getOrNull() }
    private val banners = bannerClass?.let { runCatching { FullScreenBannerCorners(it) }.getOrNull() }
    private val views = WeakHashMap<View, Unit>()
    private val buttonVisibility = WeakHashMap<View, Int>()
    private val forcedRtl = WeakHashMap<View, Unit>()
    private val transforms = WeakHashMap<View, Transform>()
    internal class Radius(var native: Float, var resting: Float, var selected: Float) {
        // Preserve the OEM radius during height-limited and capsule transition frames.
        fun atRest() = abs(native - resting) < .5f
        fun resolved() = if (atRest()) selected else native
    }
    private val changedRows = WeakHashMap<View, Radius>()
    private var writingRadius = false
    private var corners: NotificationCorners? = null
    private var failedNotificationUpdate = false
    private val sideLayout = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        if (!HotReloadLifecycle.isRetiring && transforms.containsKey(view)) applySide(view)
    }

    fun install() {
        rowClass?.let { type ->
            runCatching { NotificationCorners(type).also { it.observeNativeChanges() } }.onSuccess { corners = it }.onFailure {
                HookLog.w("ONextBox-Appearance", "Notification corner adapter unavailable", it)
            }
        }
        after(barClass, setOf("onAttachedToWindow", "updateLayout"))
        after(headerClass, setOf("onAttachedToWindow", "onInit", "updateClickAbilities"))
        if (corners != null) after(rowClass, setOf("onAttachedToWindow", "setOnKeyguard", "setHeadsUp",
            "setHeadsUpAnimatingAway", "setPinnedStatus", "initialize", "onDensityOrFontScaleChanged", "reInflateViews"))
        if (customCards != null && corners != null) {
            runCatching { customCards?.install() }.onFailure(::reportCornerFailure)
            after(customClass, setOf("onAttachedToWindow", "onConfigurationChanged", "setExpandAnimationRunning", "applyLaunchAnimationParams", "updateBaseRoundness"))
            val controller = Reflect.findClassIfExists("com.android.systemui.statusbar.StatusBarStateControllerImpl", loader)
            controller?.declaredMethods?.firstOrNull { it.name == "setState" && it.parameterCount == 2 }?.let { method ->
                ModernHookRegistry.installCompat("appearance:custom-notification-scene", method, object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.result == true && !HotReloadLifecycle.isRetiring && Option.Corners.current() != 0) {
                            main.removeCallbacks(refreshTask)
                            main.post(refreshTask)
                        }
                    }
                })
            }
        }
        if (banners != null) {
            runCatching { banners?.installTransitions() }.onFailure(::reportCornerFailure)
            after(bannerClass, setOf("onViewAttachedToWindow", "onConfigurationChanged"))
        }
        HotReloadLifecycle.register("systemui-appearance",
            capture = { views.keys.toTypedArray() }, detach = ::detach,
            restore = { state -> (state as? Array<*>)?.filterIsInstance<View>()?.forEach { views[it] = Unit } },
            resumeAfterRejection = ::activate)
        HotReloadLifecycle.afterCommit { activate() }
    }

    private fun activate() {
        if (prefs == null) prefs = ModernHookRuntime.remotePreferences("lsp_features")?.also {
            it.registerOnSharedPreferenceChangeListener(listener)
        }
        val global = runCatching { Reflect.callStaticMethod(Reflect.findClass("android.view.WindowManagerGlobal", loader), "getInstance") }.getOrNull()
        val roots = global?.let { runCatching { Reflect.getObjectField(it, "mViews") as? List<*> }.getOrNull() }
        fun visit(v: View) {
            if (barClass?.isInstance(v) == true || headerClass?.isInstance(v) == true || rowClass?.isInstance(v) == true ||
                customClass?.isInstance(v) == true || bannerClass?.isInstance(v) == true)
                views[v] = Unit
            if (v is ViewGroup) for (i in 0 until v.childCount) visit(v.getChildAt(i))
        }
        roots?.filterIsInstance<View>()?.forEach(::visit)
        refresh()
    }

    private fun after(type: Class<*>?, methods: Set<String>) {
        type?.declaredMethods?.filter { it.name in methods }?.forEach { method ->
            ModernHookRegistry.installCompat("appearance:${method.toGenericString()}", method, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as? View ?: return
                    if (param.throwable != null || HotReloadLifecycle.isRetiring || !view.isAttachedToWindow) return
                    if (method.name == "applyLaunchAnimationParams" && param.args.firstOrNull() != null) return
                    views[view] = Unit
                    update(view)
                }
            })
        }
    }

    private fun refresh() = views.keys.toList().filter(View::isAttachedToWindow).forEach(::update)

    private fun update(view: View) {
        when {
            barClass?.isInstance(view) == true -> updateBar(view)
            headerClass?.isInstance(view) == true -> updateSettingsButton(view)
            corners != null && rowClass?.isInstance(view) == true -> updateCorner(view)
            corners != null && customCards != null && customClass?.isInstance(view) == true -> updateCorner(view)
            banners != null && bannerClass?.isInstance(view) == true -> {
                runCatching {
                    val px = if (Option.Corners.current() != 0 && Option.Popup.current() != 0)
                        Option.Radius.current() / 10f * view.resources.displayMetrics.density else null
                    banners?.update(view, px)
                }.onFailure(::reportCornerFailure)
            }
        }
    }

    private fun updateSettingsButton(header: View) {
        val button = settingsButtonGetter?.invoke(header) as? View ?: header.named("settings_button") ?: return
        val visibility = button.visibility
        if (Option.HideSettings.current() != 0) {
            // Panel expansion and power-mode changes write the current OEM visibility again.
            if (visibility != View.GONE || button !in buttonVisibility) buttonVisibility[button] = visibility
            if (visibility != View.GONE) button.visibility = View.GONE
        } else {
            val original = buttonVisibility.remove(button) ?: return
            if (visibility == View.GONE) button.visibility = original
        }
    }

    private fun View.named(name: String): View? {
        val id = resources.getIdentifier(name, "id", "com.android.systemui")
        return if (id != 0) findViewById(id) else null
    }

    private fun updateBar(bar: View) {
        val contents = bar.named("status_bar_contents") ?: return
        val layout = StatusBarIconLayout.current()
        val mirror = Option.Mirror.current() != 0
        val mirrorChanged = mirror != (contents in forcedRtl)
        if (mirror) {
            if (forcedRtl.put(contents, Unit) == null || contents.layoutDirection != View.LAYOUT_DIRECTION_RTL)
                contents.layoutDirection = View.LAYOUT_DIRECTION_RTL
        } else if (forcedRtl.remove(contents) != null) contents.layoutDirection = View.LAYOUT_DIRECTION_INHERIT
        val previous = nativeInsets.put(bar, layout)
        if (previous == null || previous.nativeX != layout.nativeX || previous.nativeY != layout.nativeY ||
            (layout.nativeX || layout.nativeY) && (mirrorChanged || previous !== layout)) {
            runCatching { barExtension?.invoke(bar)?.let { refreshPadding?.invoke(it) } }
                .onFailure { HookLog.w("ONextBox-Appearance", "Unable to refresh native status bar insets", it) }
        }
        for ((id, start) in listOf("status_bar_start_side_container" to true, "status_bar_end_side_container" to false)) {
            val side = contents.named(id) ?: continue
            if (layout.enabled) {
                if (side !in transforms) {
                    transforms[side] = Transform(WeakReference(contents), start, side.translationX, side.translationY, side.scaleX, side.scaleY,
                        side.isPivotSet, side.pivotX, side.pivotY)
                    side.addOnLayoutChangeListener(sideLayout)
                }
                applySide(side)
            } else restoreSide(side)
        }
    }

    private data class Transform(val contents: WeakReference<View>, val start: Boolean, val x: Float, val y: Float,
        val sx: Float, val sy: Float, val explicitPivot: Boolean, val px: Float, val py: Float)

    private fun applySide(view: View) {
        val base = transforms[view] ?: return
        val contents = base.contents.get() ?: return
        val layout = StatusBarIconLayout.current()
        val left = base.start == (contents.layoutDirection != View.LAYOUT_DIRECTION_RTL)
        val density = view.resources.displayMetrics.density
        val factor = layout.value(if (left) Option.LeftScale else Option.RightScale) / 100f
        view.pivotX = if (left) 0f else view.width.toFloat()
        view.pivotY = view.height / 2f
        view.scaleX = base.sx * factor
        view.scaleY = base.sy * factor
        view.translationX = base.x + (if (layout.nativeX) 0 else layout.value(if (left) Option.LeftX else Option.RightX)) * density
        view.translationY = base.y + (if (layout.nativeY) 0 else layout.value(if (left) Option.LeftY else Option.RightY)) * density
    }

    private fun restoreSide(view: View) {
        val base = transforms.remove(view) ?: return
        view.removeOnLayoutChangeListener(sideLayout)
        view.translationX = base.x
        view.translationY = base.y
        view.scaleX = base.sx
        view.scaleY = base.sy
        if (base.explicitPivot) { view.pivotX = base.px; view.pivotY = base.py } else view.resetPivot()
    }

    private inner class NotificationCorners(type: Class<*>) {
        private val outline = loader.loadClass("com.android.systemui.statusbar.notification.row.ExpandableOutlineView")
        private val state = outline.getMethod("getRoundableState")
        private val setMax = state.returnType.getMethod("setMaxRadius", Float::class.javaPrimitiveType)
        private val stateView = state.returnType.getDeclaredField("targetView").apply { isAccessible = true }
        private val currentMax = outline.getMethod("getMaxRadius")
        private val stock = outline.getMethod("calculateMaxRadius")
        private val popup = type.getMethod("isHeadsUpState")
        private val canShowPopup = type.methods.firstOrNull {
            (it.name == "canShowHeadsUp" || it.name.startsWith("canShowHeadsUp$")) &&
                it.parameterCount == 0 && it.returnType == Boolean::class.javaPrimitiveType
        }
        private val bundle = runCatching { type.getDeclaredField("mIsBundle").apply { isAccessible = true } }.getOrNull()
        private val keyguard = generateSequence(type as Class<*>?) { it.superclass }
            .firstNotNullOf { runCatching { it.getDeclaredField("mOnKeyguard") }.getOrNull() }.apply { isAccessible = true }
        fun scope(view: View): Option {
            if (customCards?.type?.isInstance(view) == true) return customCards.scope(view)
            val locked = keyguard.getBoolean(view)
            if (popup.invoke(view) == true && (!locked || canShowPopup?.invoke(view) == true)) return Option.Popup
            return if (locked) Option.Lock else Option.Shade
        }

        fun observeNativeChanges() {
            ModernHookRegistry.installCompat("appearance:notification-native-radius", setMax, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (writingRadius || changedRows.isEmpty() || HotReloadLifecycle.isRetiring) return
                    val view = stateView.get(param.thisObject) as? View ?: return
                    val tracked = changedRows[view] ?: return
                    tracked.native = param.args[0] as Float
                    param.args[0] = tracked.resolved()
                    customCards?.nativeWrite(view, tracked.atRest())
                }
            })
            // ColorOS writes this property again as stacked lockscreen cards morph or settle.
            val extension = Reflect.findClassIfExists("com.oplus.systemui.statusbar.notification.row.OplusExpandableViewExImpl", loader)
            extension?.declaredMethods?.filter { it.name == "setAnimationCornerRadius" }?.forEach {
                runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
            }
        }

        fun restore(view: View) {
            val tracked = changedRows.remove(view) ?: return
            write(view, tracked.native)
            if (customCards?.type?.isInstance(view) == true) customCards.restore(view)
        }

        fun apply(view: View, dp: Float) {
            val resting = stock.invoke(view) as Float
            var selected = (dp * view.resources.displayMetrics.density).coerceAtLeast(.001f)
            if (rowClass?.isInstance(view) == true && bundle?.getBoolean(view) == true) {
                val header = view.resources.getIdentifier("notification_bundle_header_height", "dimen", "com.android.systemui")
                selected = minOf(selected, if (header != 0) view.resources.getDimension(header) / 2f else resting)
            }
            val tracked = changedRows.getOrPut(view) { Radius(currentMax.invoke(view) as Float, resting, selected) }
            tracked.resting = resting
            tracked.selected = selected
            val resolved = tracked.resolved()
            write(view, resolved)
            if (customCards?.type?.isInstance(view) == true) customCards.apply(view, selected, tracked.atRest())
        }

        private fun write(view: View, amount: Float) {
            writingRadius = true
            try { setMax.invoke(state.invoke(view), amount) } finally { writingRadius = false }
        }
    }

    private fun updateCorner(view: View) {
        val adapter = corners ?: return
        if (Option.Corners.current() == 0 && view !in changedRows) return
        runCatching {
            if (Option.Corners.current() != 0 && adapter.scope(view).current() != 0) {
                adapter.apply(view, Option.Radius.current() / 10f)
            } else adapter.restore(view)
        }.onFailure(::reportCornerFailure)
    }

    private fun reportCornerFailure(error: Throwable) {
        if (!failedNotificationUpdate) HookLog.w("ONextBox-Appearance", "Unable to update notification radius", error)
        failedNotificationUpdate = true
    }

    private fun detach() {
        prefs?.unregisterOnSharedPreferenceChangeListener(listener)
        prefs = null
        main.removeCallbacks(refreshTask)
        transforms.keys.toList().forEach(::restoreSide)
        forcedRtl.keys.toList().forEach { it.layoutDirection = View.LAYOUT_DIRECTION_INHERIT }
        buttonVisibility.forEach { (v, original) -> v.visibility = original }
        changedRows.keys.toList().forEach { runCatching { corners?.restore(it) } }
        runCatching { banners?.clear() }.onFailure(::reportCornerFailure)
        nativeInsets.clear(); forcedRtl.clear(); buttonVisibility.clear(); changedRows.clear(); views.clear()
    }
}
