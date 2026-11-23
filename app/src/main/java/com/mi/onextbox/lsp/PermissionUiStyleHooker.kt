package com.mi.onextbox.lsp

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toolbar
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig.PermissionFeature
import com.mi.onextbox.lsp.compat.ModernHookBridge
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import java.lang.reflect.Executable
import java.util.concurrent.ConcurrentHashMap

/** Selects the APK's existing export UI branches only inside verified presentation calls. */
internal object PermissionUiStyleHooker {
    private const val TAG = "ONextBox-PermissionUI"
    private const val MANAGE_PERMISSIONS_ACTIVITY =
        "com.android.permissioncontroller.permission.ui.ManagePermissionsActivity"
    private val loaders = ConcurrentHashMap.newKeySet<String>()
    private val activeFeature = ThreadLocal<PermissionFeature?>()
    private val reported = ConcurrentHashMap.newKeySet<PermissionFeature>()

    fun hookSettings(loader: ClassLoader) {
        if (!loaders.add("settings:${System.identityHashCode(loader)}")) return
        // The after callback also works when the existing global Settings edition hook is active.
        hookBoolean(loader, "com.oplus.settings.utils.CustomizeFeatureUtils", "isExpVersion", true) {
            activeFeature.get() == PermissionFeature.ExportPermissionPages
        }
        scopeMethods(loader,
            "com.oplus.settings.feature.privacy.AppPermissionJumpPreferenceController",
            PermissionFeature.ExportPermissionPages, "displayPreference", "handleClick")
        hookBoolean(loader, "com.oplus.settings.feature.appmanager.AppInfoFeature", "getIsAndroidFuction", true) {
            enabled(PermissionFeature.ExportPermissionPages)
        }
        val appInfo = ModernReflect.findClassIfExists(
            "com.oplus.settings.feature.appmanager.AppInfoFeature", loader,
        )
        if (appInfo != null) {
            ModernHookBridge.hookAllMethods(appInfo, "retrieveAppEntry", object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable == null && enabled(PermissionFeature.ExportPermissionPages)) {
                        // Store the official routing flag too, in case the adapter's trivial
                        // getter was inlined into an application-details controller.
                        param.thisObject?.let { ModernReflect.setObjectField(it, "mIsAndroidFuction", true) }
                    }
                }
            })
        }
    }

    fun hookPermissionController(loader: ClassLoader, colorOs16: Boolean = false) {
        if (!loaders.add("controller:${System.identityHashCode(loader)}")) return
        // C17 ka.a.f is PermissionController's local UI branch query. Never change the
        // persist.sys.permission.enable property or the system interception service.
        hookBoolean(loader, if (colorOs16) "j8.a" else "ka.a", if (colorOs16) "g" else "f", false) {
            activeFeature.get() == PermissionFeature.ExportPermissionPages ||
                activeFeature.get() == PermissionFeature.NativePermissionDialogs
        }
        val ui = "com.android.permissioncontroller.permission"
        val oplusUi = "com.oplusos.permissioncontroller.permission.ui"
        scopeMethods(loader, MANAGE_PERMISSIONS_ACTIVITY,
            PermissionFeature.ExportPermissionPages, "onCreate")
        scopeMethods(loader, "$oplusUi.ManagePermissionsActivityTrampoline",
            PermissionFeature.ExportPermissionPages, "onCreate")
        scopeMethods(loader, "$ui.ui.ReviewOngoingUsageActivity",
            PermissionFeature.ExportPermissionPages, "onCreate")
        scopeConstructors(loader, "$ui.ui.model.AppPermissionViewModel",
            PermissionFeature.ExportPermissionPages)
        scopeMethods(loader, "$oplusUi.handheld.AppPermissionFragment",
            PermissionFeature.ExportPermissionPages, "onCreate")
        hookExportToolbar(loader, "$ui.ui.handheld.AppPermissionWrapperFragment")

        scopeMethods(loader, "$oplusUi.OplusBaseGrantPermissionsActivity",
            PermissionFeature.NativePermissionDialogs, "onCreate")
        scopeConstructors(loader, "$ui.ui.handheld.GrantPermissionsViewHandlerImpl",
            PermissionFeature.NativePermissionDialogs)
        scopeConstructors(loader, "$ui.ui.model.GrantPermissionsViewModel",
            PermissionFeature.NativePermissionDialogs)
        scopeConstructors(loader, "$ui.ui.model.ReviewPermissionsViewModel",
            PermissionFeature.NativePermissionDialogs)
        scopeMethods(loader, "$ui.ui.ReviewPermissionsActivity",
            PermissionFeature.NativePermissionDialogs, "onCreate")
        scopeMethods(loader, "$ui.ui.v37.RequestLocationButtonPermissionsActivity",
            PermissionFeature.NativePermissionDialogs, "onCreate")
    }

    private fun hookExportToolbar(loader: ClassLoader, wrapperName: String) {
        val wrapper = ModernReflect.findClassIfExists(wrapperName, loader) ?: return
        runCatching {
            // ColorOS 16 and 17 both configure the toolbar in this shared wrapper callback.
            val onActivityCreated = wrapper.superclass.getDeclaredMethod("onActivityCreated", Bundle::class.java)
            ModernHookBridge.hookMethod(onActivityCreated, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null ||
                        !enabled(PermissionFeature.ExportPermissionPages) ||
                        !enabled(PermissionFeature.ExportPermissionToolbarFix) ||
                        LspPreferenceStore.readSystemPropertyToggle("persist.sys.permission.enable") != true) return
                    val fragment = param.thisObject ?: return
                    runCatching { fixExportToolbar(fragment) }
                        .onFailure { HookLog.w(TAG, "Unable to fix international permission toolbar", it) }
                }
            })
        }.onFailure { HookLog.w(TAG, "International permission toolbar hook unavailable", it) }
    }

    private fun fixExportToolbar(fragment: Any) {
        val activity = ModernReflect.callMethod(fragment, "getActivity") as? Activity ?: return
        if (activity.javaClass.name != MANAGE_PERMISSIONS_ACTIVITY) return
        val root = ModernReflect.callMethod(fragment, "getView") as? View ?: return
        val resources = root.resources
        val packageName = activity.packageName
        val appBar = root.findViewById<View>(resources.getIdentifier("app_bar", "id", packageName)) ?: return
        val toolbar = appBar.findViewById<Toolbar>(resources.getIdentifier("action_bar", "id", packageName)) ?: return
        val colors = activity.obtainStyledAttributes(R.styleable.ONextBoxPermissionToolbar)
        try {
            val background = colors.getColor(
                R.styleable.ONextBoxPermissionToolbar_android_colorBackground,
                Color.TRANSPARENT,
            )
            val tint = colors.getColorStateList(R.styleable.ONextBoxPermissionToolbar_android_textColorPrimary) ?: return
            if (Color.alpha(background) != 255) return

            // Cover scrolling content while retaining the page's own theme and collapsing behavior.
            appBar.setBackgroundColor(background)
            appBar.bringToFront()
            toolbar.navigationIcon?.let { toolbar.navigationIcon = it.mutate().apply { setTintList(tint) } }
            toolbar.overflowIcon?.let { toolbar.overflowIcon = it.mutate().apply { setTintList(tint) } }
        } finally {
            colors.recycle()
        }
    }

    private fun enabled(feature: PermissionFeature) = LspConfig.isPermissionFeatureEnabledXposed(feature)

    private fun hookBoolean(
        loader: ClassLoader, className: String, methodName: String, value: Boolean,
        isActive: () -> Boolean,
    ) {
        val target = ModernReflect.findClassIfExists(className, loader)
        val methods = target?.declaredMethods?.filter {
            it.name == methodName && it.returnType == Boolean::class.javaPrimitiveType &&
                it.parameterCount == 0
        }.orEmpty()
        var count = 0
        methods.forEach { method ->
            runCatching {
                ModernHookBridge.hookMethod(method, object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable == null && isActive()) param.result = value
                    }
                })
                count++
            }.onFailure { HookLog.w(TAG, "Unable to hook $className#$methodName", it) }
        }
        HookLog.i(TAG, "$className#$methodName branch hooks: $count")
    }

    private fun scopeMethods(
        loader: ClassLoader, className: String, feature: PermissionFeature, vararg names: String,
    ) {
        val target = ModernReflect.findClassIfExists(className, loader)
        installScopes(className, feature, target?.declaredMethods?.filter { it.name in names }.orEmpty())
    }

    private fun scopeConstructors(loader: ClassLoader, className: String, feature: PermissionFeature) {
        val target = ModernReflect.findClassIfExists(className, loader)
        installScopes(className, feature, target?.declaredConstructors?.toList().orEmpty())
    }

    private fun installScopes(className: String, feature: PermissionFeature, methods: List<Executable>) {
        var count = 0
        methods.forEach { method ->
            runCatching {
                // These small feature queries can be AOT-inlined in their callers. Deoptimize
                // precisely the verified UI caller so the branch hook is actually reached.
                if (!ModernHookRuntime.requireModule().deoptimize(method)) {
                    HookLog.w(TAG, "Caller deoptimization unavailable: $className#${method.name}")
                }
                ModernHookBridge.hookMethodFast(method) { chain ->
                    val selected = enabled(feature)
                    if (!selected) return@hookMethodFast chain.proceed()
                    val previous = activeFeature.get()
                    activeFeature.set(feature)
                    try {
                        if (reported.add(feature)) HookLog.i(TAG, "Applied UI branch: ${feature.key}")
                        chain.proceed()
                    } finally {
                        if (previous == null) activeFeature.remove() else activeFeature.set(previous)
                    }
                }
                count++
            }.onFailure { HookLog.w(TAG, "Unable to scope $className#${method.name}", it) }
        }
        HookLog.i(TAG, "$className presentation hooks: $count")
    }
}
