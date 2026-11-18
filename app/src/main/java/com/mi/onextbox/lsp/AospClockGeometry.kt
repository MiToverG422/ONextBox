package com.mi.onextbox.lsp

import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import com.mi.onextbox.lsp.compat.ModernHookBridge
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import java.lang.reflect.Method

internal class AospClockGeometry(loader: ClassLoader) {
    private data class Selection(val section: Any, val largeState: Any, val context: Context,
        val smallId: Int, val placeholderId: Int, val paddingDp: Float)
    private var selected: Selection? = null
    private var notificationTop = 0f
    private var compact = false
    private var panel: Any? = null
    private var refreshPending = false
    private val handler = Handler(Looper.getMainLooper())
    private val state = HostClassLookup.load("kotlinx.coroutines.flow.StateFlow", false, loader).getMethod("getValue")
    private val constraintType = HostClassLookup.load("androidx.constraintlayout.widget.ConstraintSet", false, loader)
    private val connect: Method = constraintType.getMethod("connect", *Array(5) { Int::class.javaPrimitiveType })
    private val constraint = constraintType.getMethod("getConstraint", Int::class.javaPrimitiveType)

    fun select(section: Any, model: Any, context: Context, reference: Resources) {
        val viewIds = HostClassLookup.load("com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds", false, section.javaClass.classLoader)
        val id = viewIds.getMethod("getLOCKSCREEN_CLOCK_VIEW_SMALL").invoke(viewIds.getField("INSTANCE").get(null)) as Int
        val padId = reference.getIdentifier("clock_padding_start", "dimen", "com.android.systemui")
        val padding = if (padId != 0) reference.getDimension(padId) / reference.displayMetrics.density else 0f
        selected = Selection(section, requireNotNull(ModernReflect.getObjectField(model, "isLargeClockVisible")), context,
            id, context.resources.getIdentifier("nssl_placeholder", "id", "com.android.systemui"), padding)
    }

    fun install(loader: ClassLoader) {
        val panelType = HostClassLookup.load("com.android.systemui.shade.NotificationPanelViewController", false, loader)
        val showing = panelType.getMethod("isKeyguardShowing")
        val position = panelType.getMethod("positionClockAndNotifications", Boolean::class.javaPrimitiveType)
        val interactor = HostClassLookup.load("com.android.systemui.keyguard.domain.interactor.KeyguardClockInteractor", false, loader)
        ModernHookBridge.hookAllMethods(interactor, "setNotificationStackDefaultTop", object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val selection = selected ?: return
                if (param.thisObject !== ModernReflect.getObjectField(selection.section, "clockInteractor")) return
                val top = param.args[0] as? Float ?: return
                val nextCompact = state.invoke(selection.largeState) == false
                if (notificationTop == top && compact == nextCompact) return
                notificationTop = top
                compact = nextCompact
                if (refreshPending || panel == null) return
                refreshPending = true
                handler.post {
                    refreshPending = false
                    if (AospUiSettings.enabledXposed(AospUiSettings.LOCKSCREEN)) {
                        runCatching { panel?.let { position.invoke(it, false) } }
                            .onFailure { HookLog.w("ONextBox-AospClock", "Notification layout refresh unavailable", it) }
                    }
                }
            }
        })
        ModernHookBridge.hookAllMethods(panelType, "updateClockAppearance", object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val owner = param.thisObject ?: return
                panel = owner
                val selection = selected ?: return
                if (!AospUiSettings.enabledXposed(AospUiSettings.LOCKSCREEN) || notificationTop <= 0f) return
                if (state.invoke(selection.largeState) != false || showing.invoke(owner) != true) return
                if (ModernReflect.getObjectField(owner, "mDozing") == true) return
                val result = ModernReflect.getObjectField(owner, "mClockPositionResult") ?: return
                val top = notificationTop.toInt()
                ModernReflect.setObjectField(result, "stackScrollerPadding", top)
                ModernReflect.setObjectField(result, "stackScrollerPaddingExpanded", top)
            }
        })
        val clock = HostClassLookup.load("com.android.systemui.keyguard.ui.view.layout.sections.ClockSection", false, loader)
        ModernHookBridge.hookAllMethods(clock, "applyDefaultConstraints", object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val selection = selected ?: return
                if (!AospUiSettings.enabledXposed(AospUiSettings.LOCKSCREEN)) return
                if (param.thisObject !== selection.section) return
                val padding = (selection.paddingDp * selection.context.resources.displayMetrics.density).toInt()
                connect.invoke(param.args[0], selection.smallId, 6, 0, 6, padding)
            }
        })
        val notifications = HostClassLookup.load("com.android.systemui.keyguard.ui.view.layout.sections.DefaultNotificationStackScrollLayoutSection", false, loader)
        ModernHookBridge.hookAllMethods(notifications, "applyConstraints", object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val selection = selected ?: return
                if (!AospUiSettings.enabledXposed(AospUiSettings.LOCKSCREEN)) return
                if (state.invoke(selection.largeState) == true) return
                val id = selection.placeholderId
                if (id == 0) return
                val constraints = param.args[0] ?: return
                val original = constraint.invoke(constraints, id)
                val layout = original?.let { ModernReflect.getObjectField(it, "layout") }
                val margin = layout?.let { ModernReflect.getIntField(it, "topMargin").coerceAtLeast(0) } ?: 0
                connect.invoke(constraints, id, 3, selection.smallId, 4, margin)
            }
        })
    }
}
