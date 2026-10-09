package com.mi.onextbox.lsp

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.mi.onextbox.lsp.LspConfig.StatusBarInteractionFeature
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.util.Collections
import java.util.WeakHashMap

/** Change only the OEM scroll-to-top trigger and its application allowlist gate. */
internal object StatusBarScrollToTopHooker {
    private const val TAG = "ONextBox-ScrollToTop"
    private data class TouchState(
        val doubleTapSlop: Float,
        val tracker: StatusBarDoubleTapTracker = StatusBarDoubleTapTracker(),
    )
    private val states = Collections.synchronizedMap(WeakHashMap<Any, TouchState>())

    fun hookSystemUi(loader: ClassLoader) {
        val key = "$TAG:SystemUI@${System.identityHashCode(loader)}:"
        runCatching {
            val helper = ModernReflect.findClass("com.oplus.systemui.statusbar.util.StatusbarClickHelper", loader)
            val click = helper.getDeclaredMethod("scrollToTop", MotionEvent::class.java)
            val parameters = helper.getDeclaredMethod("checkClickParams", Context::class.java)
            val touchSlop = helper.getDeclaredField("mTouchSlop").apply { isAccessible = true }
            val longPressTimeout = helper.getDeclaredField("mLongPressTimeOut").apply { isAccessible = true }
            require(touchSlop.type == Integer.TYPE && longPressTimeout.type == Integer.TYPE)

            ModernHookRegistry.installFast("${key}parameters", parameters, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                val owner = chain.thisObject ?: return@Hooker result
                val context = chain.getArg(0) as? Context ?: return@Hooker result
                runCatching {
                    // Reattachment/configuration changes invalidate a partially completed pair.
                    states[owner] = TouchState(ViewConfiguration.get(context).scaledDoubleTapSlop.toFloat())
                }.onFailure { HookLog.w(TAG, "Unable to initialize double-tap parameters", it) }
                result
            })
            ModernHookRegistry.installFast("${key}click", click, XposedInterface.Hooker { chain ->
                val owner = chain.thisObject ?: return@Hooker chain.proceed()
                val state = states[owner] ?: return@Hooker chain.proceed()
                if (!LspConfig.isStatusBarInteractionFeatureEnabledXposed(StatusBarInteractionFeature.DoubleTapToTop)) {
                    state.tracker.cancel()
                    return@Hooker chain.proceed()
                }
                val event = chain.getArg(0) as? MotionEvent ?: return@Hooker chain.proceed()
                val doubleTap = runCatching {
                    synchronized(state) {
                        if (event.pointerCount != 1) {
                            state.tracker.cancel()
                            false
                        } else when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                state.tracker.onDown(event.eventTime, event.x, event.y)
                                false
                            }
                            MotionEvent.ACTION_MOVE -> {
                                state.tracker.onMove(event.x, event.y, touchSlop.getInt(owner).toFloat())
                                false
                            }
                            MotionEvent.ACTION_UP -> state.tracker.onUp(
                                event.eventTime, event.x, event.y,
                                touchSlop.getInt(owner).toFloat(), longPressTimeout.getInt(owner).toLong(),
                                ViewConfiguration.getDoubleTapTimeout().toLong(), state.doubleTapSlop,
                            )
                            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE,
                            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                                state.tracker.cancel()
                                false
                            }
                            else -> false
                        }
                    }
                }.getOrElse {
                    state.tracker.cancel()
                    HookLog.w(TAG, "Double-tap handling failed; stock gesture retained", it)
                    return@Hooker chain.proceed()
                }
                // Forward both DOWN events and only the qualifying UP to the OEM helper.
                // Keep its native broadcast and 800 ms throttling; never consume the outer touch.
                if (event.actionMasked == MotionEvent.ACTION_UP && !doubleTap) null else chain.proceed()
            })
            ModernReflect.findClassIfExists("com.oplus.systemui.statusbar.phone.PhoneStatusBarViewExImpl", loader)
                ?.declaredMethods?.filter { it.name in setOf("handleClick", "onAttachedToWindow") }?.forEach {
                    runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
                        .onFailure { error -> HookLog.w(TAG, "Unable to deoptimize status bar ${it.name}", error) }
                }
            HookLog.i(TAG, "Status bar double-tap hook installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(key)
            HookLog.w(TAG, "Status bar click hooks unavailable; stock behavior retained", it)
        }
    }

    fun hookSystemServer(loader: ClassLoader) {
        val key = "$TAG:SystemServer@${System.identityHashCode(loader)}:"
        runCatching {
            val helper = ModernReflect.findClass("com.android.server.OplusScrollToTopRusHelper", loader)
            val allowlist = helper.getDeclaredMethod("isInWhiteList", String::class.java)
            require(allowlist.returnType == Boolean::class.javaPrimitiveType)
            ModernHookRegistry.installFast("${key}allowlist", allowlist, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                val original = result as? Boolean ?: return@Hooker result
                StatusBarScrollToTopRules.isAllowedPackage(
                    LspConfig.isStatusBarInteractionFeatureEnabledXposed(StatusBarInteractionFeature.RemoveToTopWhitelist),
                    chain.getArg(0) as? String, original,
                )
            })
            // Keep the native per-activity blacklist and all focus/window checks intact.
            ModernReflect.findClassIfExists("com.android.server.am.OplusScrollToTopSystemManager", loader)
                ?.declaredMethods?.filter { it.name == "preBindApplication" }?.forEach {
                    runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
                        .onFailure { error -> HookLog.w(TAG, "Unable to deoptimize app allowlist binding", error) }
                }
            HookLog.i(TAG, "Scroll-to-top application allowlist hook installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(key)
            HookLog.w(TAG, "Scroll-to-top allowlist hooks unavailable; stock behavior retained", it)
        }
    }
}
