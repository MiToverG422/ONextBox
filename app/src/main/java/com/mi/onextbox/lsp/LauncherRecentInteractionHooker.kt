package com.mi.onextbox.lsp

import android.app.ActivityOptions
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.mi.onextbox.lsp.LspConfig.LauncherFeature
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.WeakHashMap

/** Recent-task navigation and task-icon interactions; workspace icons are never hooked. */
internal object LauncherRecentInteractionHooker {
    private const val TAG = "LauncherRecentInteraction"
    private var installed = false

    fun hook(loader: ClassLoader) {
        install("startup") {
            val application = ModernReflect.findClass("com.android.common.LauncherApplication", loader)
            hook("startup", application.getDeclaredMethod("onCreate")) { chain ->
                val result = chain.proceed()
                // OEM feature classes must only be touched after the launcher context is initialized.
                if (!installed) {
                    installed = true
                    install("auto-focus") { hookAutoFocus(loader) }
                    install("app-details") { hookAppDetails(loader) }
                }
                result
            }
        }
    }

    private fun hookAutoFocus(loader: ClassLoader) {
        // Resolve without initializing AppFeatureUtils before LauncherApplication.attachBaseContext.
        val featureUtils = ModernReflect.findClass("com.android.common.util.AppFeatureUtils", loader)
        val gates = featureUtils.declaredMethods.filter {
            it.name == "isSupportAutoFocusToNextPageInOverviewState" &&
                it.returnType == Boolean::class.javaPrimitiveType &&
                (it.parameterCount == 0 || it.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType)))
        }
        require(gates.isNotEmpty())
        gates.forEach { method ->
            hook("auto-focus", method) { chain ->
                if (enabled(LauncherFeature.DisablePreviousTaskAutoFocus)) false else chain.proceed()
            }
        }
        // Keep the feature gate observable in compiled overview-entry and interruption paths.
        for ((className, names) in listOf(
            "com.android.quickstep.o1" to setOf("e"),
            "da.e" to setOf("j"),
            "lq.d" to setOf("a", "c"),
            "com.android.quickstep.views.OplusRecentsViewImpl" to setOf("getToRecentsFocusPage"),
        )) {
            ModernReflect.findClassIfExists(className, loader)?.declaredMethods
                ?.filter { it.name in names }
                ?.forEach { method ->
                    runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                        .onFailure { HookLog.w(TAG, "Unable to deoptimize ${method.name}", it) }
                }
        }
    }

    private class PendingPress(
        val icon: WeakReference<View>,
        val task: Any,
        val index: Int,
        val downTime: Long,
        val press: RecentTaskIconPress,
    ) {
        lateinit var timeout: Runnable
        var cancelingNativeTouch = false
    }

    private fun hookAppDetails(loader: ClassLoader) {
        val taskView = ModernReflect.findClass("com.android.quickstep.views.TaskView", loader)
        val taskType = ModernReflect.findClass("com.android.systemui.shared.recents.model.Task", loader)
        val iconType = ModernReflect.findClass("com.android.quickstep.views.IconView", loader)
        val containers = taskView.getDeclaredMethod("getTaskIdAttributeContainers")
        val containerType = requireNotNull(containers.returnType.componentType)
        val containerIcon = containerType.declaredFields.single { it.type == iconType }.apply { isAccessible = true }
        val containerTask = containerType.declaredFields.single { it.type == taskType }.apply { isAccessible = true }
        val itemInfo = taskView.getDeclaredMethod("getItemInfo", taskType)
        val itemType = ModernReflect.findClass("com.android.launcher3.model.data.ItemInfo", loader)
        val component = itemType.getDeclaredMethod("getTargetComponent")
        val activity = taskView.getDeclaredField("mActivity").apply { isAccessible = true }
        require(Context::class.java.isAssignableFrom(activity.type))
        val utilities = ModernReflect.findClass("com.android.launcher3.Utilities", loader)
        val bounds = utilities.getDeclaredMethod("getViewBounds", View::class.java)
        val details = ModernReflect.findClass("com.android.launcher3.util.OplusPackageManagerHelper", loader)
            .getDeclaredMethod("startDetailsActivityForInfo", Context::class.java, itemType, Rect::class.java, Bundle::class.java)
        val dispatch = taskView.getDeclaredMethod("dispatchTouchEvent", MotionEvent::class.java)
        val pending = WeakHashMap<View, PendingPress>()

        fun taskAt(owner: View, index: Int): Any? =
            (containers.invoke(owner) as? Array<*>)?.getOrNull(index)?.let { containerTask.get(it) }

        fun ready(owner: View): Boolean {
            if (!owner.isAttachedToWindow || !owner.isShown || owner.alpha <= 0f) return false
            val recents = ModernReflect.callMethod(owner, "getRecentsView") ?: return false
            return ModernReflect.callMethod(recents, "canLaunchFullscreenTask") == true &&
                ModernReflect.callMethod(recents, "isSplitSelectionActive") == false &&
                ModernReflect.callMethod(recents, "getOverviewStateEnabled") == true
        }

        fun iconAt(owner: View, event: MotionEvent): Pair<Int, View>? {
            fun hit(icon: View): Boolean = icon.isShown && icon.alpha > 0f &&
                (bounds.invoke(null, icon) as Rect).contains(event.rawX.toInt(), event.rawY.toInt())
            val values = containers.invoke(owner) as? Array<*> ?: return null
            values.forEachIndexed { index, container ->
                val icon = container?.let { containerIcon.get(it) } as? View
                if (icon != null && hit(icon)) return index to icon
            }
            // ColorOS uses a separate visible header icon; do not guess an app for a combined split icon.
            if (values.count { it != null } == 1) {
                val id = owner.resources.getIdentifier("oplus_task_header_app_icon", "id", "com.android.launcher")
                val icon = if (id != 0) owner.findViewById<View>(id) else null
                if (icon != null && hit(icon)) return values.indexOfFirst { it != null } to icon
            }
            return null
        }

        fun clear(owner: View): PendingPress? = pending.remove(owner)?.also {
            owner.removeCallbacks(it.timeout)
            it.press.cancel()
        }

        hook("app-details", dispatch) { chain ->
            val owner = chain.thisObject as? View ?: return@hook chain.proceed()
            val event = chain.getArg(0) as? MotionEvent ?: return@hook chain.proceed()
            val current = pending[owner]
            if (current?.cancelingNativeTouch == true) return@hook chain.proceed()
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    clear(owner)
                    if (enabled(LauncherFeature.RecentIconAppDetails)) {
                        runCatching {
                            if (!ready(owner)) return@runCatching
                            val (index, icon) = iconAt(owner, event) ?: return@runCatching
                            val task = taskAt(owner, index) ?: return@runCatching
                            val target = PendingPress(
                                WeakReference(icon), task, index, event.downTime,
                                RecentTaskIconPress(event.rawX, event.rawY, ViewConfiguration.get(owner.context).scaledTouchSlop),
                            )
                            val ownerRef = WeakReference(owner)
                            target.timeout = Runnable {
                                val view = ownerRef.get() ?: return@Runnable
                                if (pending[view] !== target) return@Runnable
                                runCatching {
                                    val source = target.icon.get() ?: return@runCatching
                                    val info = itemInfo.invoke(view, target.task) ?: return@runCatching
                                    if (component.invoke(info) == null) return@runCatching
                                    val context = activity.get(view) as Context
                                    val options = (ModernReflect.callMethod(context, "getResidentActivityOptions") as ActivityOptions).toBundle()
                                    val sourceBounds = bounds.invoke(null, source)
                                    if (!target.press.trigger(
                                            enabled(LauncherFeature.RecentIconAppDetails),
                                            taskAt(view, target.index) === target.task,
                                            source.isShown && ready(view),
                                        )
                                    ) return@runCatching
                                    // Cancel the native tap/long-press before opening Settings, avoiding task launch on release.
                                    val cancel = MotionEvent.obtain(target.downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
                                    target.cancelingNativeTouch = true
                                    try { view.dispatchTouchEvent(cancel) } finally {
                                        target.cancelingNativeTouch = false
                                        cancel.recycle()
                                    }
                                    view.isPressed = false
                                    details.invoke(null, context, info, sourceBounds, options)
                                }.onFailure { HookLog.w(TAG, "Unable to open recent-task app details", it) }
                            }
                            pending[owner] = target
                            owner.postDelayed(target.timeout, ViewConfiguration.getLongPressTimeout().toLong())
                        }.onFailure { HookLog.w(TAG, "Unable to track recent-task icon press", it) }
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (current?.press?.handled == true) return@hook true
                    if (current != null && !current.press.move(event.rawX, event.rawY)) clear(owner)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (clear(owner)?.press?.handled == true) return@hook true
                }
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                    if (current?.press?.handled == true) return@hook true
                    clear(owner)
                }
            }
            chain.proceed()
        }
        val detach = taskView.getDeclaredMethod("onDetachedFromWindow")
        hook("app-details", detach) { chain ->
            (chain.thisObject as? View)?.let(::clear)
            chain.proceed()
        }
    }

    private fun enabled(feature: LauncherFeature) = LspConfig.isLauncherFeatureEnabledXposed(feature)

    private fun hook(group: String, method: Method, callback: (XposedInterface.Chain) -> Any?) {
        ModernHookRegistry.installFast("$TAG:$group:${method.toGenericString()}", method, XposedInterface.Hooker(callback))
    }

    private fun install(group: String, block: () -> Unit) {
        runCatching(block).onSuccess { HookLog.i(TAG, "$group hooks installed") }.onFailure {
            ModernHookRegistry.unhookPrefix("$TAG:$group:")
            HookLog.w(TAG, "$group hooks unavailable", it)
        }
    }
}
