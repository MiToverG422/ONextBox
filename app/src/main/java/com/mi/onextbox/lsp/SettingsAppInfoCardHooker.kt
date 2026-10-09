package com.mi.onextbox.lsp

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.TextView
import com.mi.onextbox.R
import com.mi.onextbox.lsp.SettingsAppInfoRules.Field
import com.mi.onextbox.lsp.SettingsAppInfoRules.Snapshot
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy
import java.util.TimeZone
import java.util.WeakHashMap
import java.util.concurrent.Executors

internal object SettingsAppInfoCardHooker {
    private const val TAG = "ONextBox-AppInfo"
    private const val SETTINGS = "com.android.settings"
    private const val FRAGMENT = "com.android.settings.applications.appinfo.AppInfoDashboardFragment"
    private const val GROUP_KEY = "onextbox_app_info_card"
    private const val HEADER_KEY = "onextbox_app_info_header"
    private const val ROW_CLASS = "com.oplus.settings.widget.preference.SettingsSimpleJumpPreference"
    private val main = Handler(Looper.getMainLooper())
    private val reader = Executors.newSingleThreadExecutor { job -> Thread(job, "ONextBox-AppInfo").apply { isDaemon = true } }
    // A recycled native row must never retain another preference's clipboard handler.
    private val copyHandlers = WeakHashMap<View, WeakReference<View.OnLongClickListener>>()

    private class Row(val preference: Any, val title: String, var value: String) {
        var refresh: (() -> Unit)? = null
    }
    private class Card(
        val screen: Any,
        val group: Any,
        val header: Any,
        val rows: Map<Field, Row>,
        val shifted: List<Pair<Any, Int>>,
        var snapshot: Snapshot,
        val nativeRotateHeader: Boolean,
    ) {
        var expanded = false
        var generation = 0
        var headerView: WeakReference<View>? = null
        var rotateView: WeakReference<View>? = null
        var panel: SettingsNativeAppInfoPanel? = null
    }

    fun hook(loader: ClassLoader) {
        val prefix = "$TAG@${System.identityHashCode(loader)}:"
        runCatching {
            val fragment = ModernReflect.findClass(FRAGMENT, loader)
            // These must be official host classes, never the module's Compose or COUI library.
            ModernReflect.findClass(ROW_CLASS, loader)
            ModernReflect.findClass("com.oplus.settings.widget.preference.SettingsPreferenceCategory", loader)
            val preference = ModernReflect.findClass("androidx.preference.Preference", loader)
            SettingsNativeAppInfoPanel.hookDividers(loader, prefix + "panel-divider:")
            val bind = preference.declaredMethods.single { it.name == "onBindViewHolder" && it.parameterCount == 1 }
            ModernHookRegistry.installFast(prefix + "recycle", bind, XposedInterface.Hooker { chain ->
                runCatching {
                    val holder = chain.getArg(0) ?: return@runCatching
                    val view = ModernReflect.getObjectField(holder, "itemView") as? View ?: return@runCatching
                    if (copyHandlers.remove(view) != null) view.setOnLongClickListener(null)
                }
                chain.proceed()
            })
            listOf(
                fragment.getDeclaredMethod("onCreatePreferences", Bundle::class.java, String::class.java),
                fragment.getDeclaredMethod("onResume"),
            ).forEach { target ->
                ModernHookRegistry.installFast(prefix + target.name, target, XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    runCatching { chain.thisObject?.let { update(it, loader) } }
                        .onFailure { HookLog.w(TAG, "App information update failed; stock page retained", it) }
                    result
                })
            }
            HookLog.i(TAG, "Native app information card hooks installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(TAG, "Native app information components unavailable; stock page retained", it)
        }
    }

    private fun update(fragment: Any, loader: ClassLoader) {
        val screen = call(fragment, "getPreferenceScreen") ?: return
        val header = call(screen, "findPreference", HEADER_KEY)
        val existing = header?.let { call(it, "getTag") as? Card }
        if (!LspConfig.isSettingsAppInfoCardEnabledXposed()) {
            existing?.let(::remove)
            return
        }
        val context = call(fragment, "getContext") as? Context ?: return
        val info = call(fragment, "getPackageInfo") as? PackageInfo ?: return
        val app = info.applicationInfo ?: return
        val snapshot = Snapshot(info.packageName, info.versionName, info.longVersionCode,
            app.minSdkVersion, app.targetSdkVersion, info.firstInstallTime, info.lastUpdateTime, app.uid)
        val card = existing ?: insert(context, screen, snapshot, loader) ?: return
        val changedPackage = card.snapshot.packageName != snapshot.packageName || card.snapshot.uid != snapshot.uid
        card.snapshot = snapshot
        if (changedPackage) {
            setExpanded(card, false, context)
            setValue(card.rows.getValue(Field.Installer), text(context, R.string.settings_app_info_unknown))
        }
        populate(card, context)
        readInstaller(card, context.applicationContext, loader)
    }

    private fun insert(context: Context, screen: Any, snapshot: Snapshot, loader: ClassLoader): Card? {
        val notification = call(screen, "findPreference", "key_notification_manager")
            ?: call(screen, "findPreference", "notification_settings") ?: return null
        var anchor = notification
        while (true) {
            val parent = call(anchor, "getParent") ?: return null
            if (parent === screen) break
            anchor = parent
        }
        val anchorOrder = call(anchor, "getOrder") as Int
        require(anchorOrder < Int.MAX_VALUE) { "Notification category has no stable order" }
        val rotateLayout = resource(context, "settings_professional_mode_preference_layout", "layout")
        val nativeRotateHeader = rotateLayout != 0 && ModernReflect.findClassIfExists(
            "com.coui.appcompat.rotateview.COUIRotateView", loader) != null
        val headerLayout = if (nativeRotateHeader) rotateLayout else resource(context, "jump_preference_simple_layout_no_icon", "layout")
        val dataLayout = resource(context, "jump_preference_simple_layout_summary_vertical", "layout")
        require(headerLayout != 0 && dataLayout != 0)
        val groupClass = ModernReflect.findClass("com.oplus.settings.widget.preference.SettingsPreferenceCategory", loader)
        val rowClass = ModernReflect.findClass(ROW_CLASS, loader)
        val group = ModernReflect.newInstance(groupClass, context)
        call(group, "setKey", GROUP_KEY)
        call(group, "setOrder", anchorOrder)
        call(group, "setPersistent", false)
        val header = preference(context, rowClass, HEADER_KEY,
            text(context, R.string.settings_app_info_card_title), headerLayout, 0)
        // Keep the models attached for native preference binding, but render only the single panel.
        call(header, "setVisible", false)
        val unknown = text(context, R.string.settings_app_info_unknown)
        val rows = Field.entries.associateWith { field ->
            val label = text(context, label(field))
            val item = preference(context, rowClass, "onextbox_app_info_${field.name}", label, dataLayout, field.ordinal + 1)
            call(item, "setJumpImageVisible", false)
            call(item, "setVisible", false)
            call(item, "setSummary", unknown)
            Row(item, label, unknown).also { row ->
                bindCallback(item, loader) { holder -> bindCopy(row, holder) }
                clickCallback(item, loader) { true }
            }
        }
        val shifted = (0 until (call(screen, "getPreferenceCount") as Int)).map { index ->
            val item = requireNotNull(call(screen, "getPreference", index))
            item to (call(item, "getOrder") as Int)
        }.filter { (_, order) -> SettingsAppInfoRules.shiftedOrder(order, anchorOrder) != order }
        val card = Card(screen, group, header, rows, shifted, snapshot, nativeRotateHeader)
        call(header, "setTag", card)
        clickCallback(header, loader) {
            if (LspConfig.isSettingsAppInfoCardEnabledXposed()) {
                setExpanded(card, !card.expanded, context, animate = true)
            }
            true
        }
        bindCallback(header, loader) { holder ->
            val root = ModernReflect.getObjectField(holder, "itemView") as View
            card.headerView = WeakReference(root)
            root.stateDescription = text(context, if (card.expanded) R.string.settings_app_info_expanded else R.string.settings_app_info_collapsed)
            if (card.nativeRotateHeader) {
                root.findViewById<View>(resource(context, "expand_list_item_indicator", "id"))?.let { rotate ->
                    card.rotateView = WeakReference(rotate)
                    if (call(rotate, "isExpanded") != card.expanded) {
                        rotate.animate().cancel()
                        call(rotate, "setExpanded", card.expanded, false)
                    }
                }
            }
        }
        try {
            shifted.forEach { (item, order) -> call(item, "setOrder", order + 1) }
            check(call(screen, "addPreference", group) == true)
            // The host assigns a PreferenceManager when the group is attached to the screen.
            check(call(group, "addPreference", header) == true)
            rows.values.forEach { check(call(group, "addPreference", it.preference) == true) }
            val panel = SettingsNativeAppInfoPanel(context, loader, header, rows.values.map { it.preference },
                canToggle = { LspConfig.isSettingsAppInfoCardEnabledXposed() },
                onStateChanged = { expanded, animate -> updateHeader(card, expanded, context, animate) })
            card.panel = panel
            rows.values.forEach { row -> row.refresh = { panel.refreshRow(row.preference) } }
            val layout = ModernReflect.newInstance(ModernReflect.findClass("com.android.settingslib.widget.LayoutPreference", loader), context, panel.view)
            call(layout, "setKey", "onextbox_app_info_panel")
            call(layout, "setOrder", -1)
            call(layout, "setPersistent", false)
            call(layout, "setSelectable", false)
            check(call(group, "addPreference", layout) == true)
        } catch (error: Throwable) {
            remove(card)
            throw error
        }
        HookLog.d(TAG, "App information card inserted before notifications")
        return card
    }

    private fun preference(context: Context, type: Class<*>, key: String, title: String, layout: Int, order: Int): Any =
        ModernReflect.newInstance(type, context).also {
            call(it, "setKey", key)
            call(it, "setTitle", title)
            call(it, "setLayoutResource", layout)
            call(it, "setOrder", order)
            call(it, "setPersistent", false)
            call(it, "setIconSpaceReserved", false)
            call(it, "setSelectable", true)
        }

    private fun setExpanded(card: Card, expanded: Boolean, context: Context, animate: Boolean = false) {
        card.panel?.setExpanded(expanded, animate)
        if (card.expanded != expanded) updateHeader(card, expanded, context, animate)
    }

    private fun updateHeader(card: Card, expanded: Boolean, context: Context, animate: Boolean) {
        card.expanded = expanded
        if (card.nativeRotateHeader) {
            card.rotateView?.get()?.let { rotate ->
                // COUI owns the rotation curve. Cancel first so a rapid reversal cannot be ignored.
                rotate.animate().cancel()
                call(rotate, "setExpanded", expanded, animate)
            }
        } else {
            val arrow = resource(context, if (expanded) "coui_component_expand_arrow_drop_up" else "coui_component_expand_arrow_drop_down", "drawable")
            require(arrow != 0) { "Native expansion arrow missing" }
            call(card.header, "setJump", context.getDrawable(arrow) as Drawable)
        }
        card.headerView?.get()?.stateDescription = text(context,
            if (expanded) R.string.settings_app_info_expanded else R.string.settings_app_info_collapsed)
    }

    private fun populate(card: Card, context: Context) {
        val data = card.snapshot
        val unknown = text(context, R.string.settings_app_info_unknown)
        val locale = context.resources.configuration.locales[0]
        val pattern = DateFormat.getBestDateTimePattern(locale, "yyyyMMddHHmmss")
        val zone = TimeZone.getDefault()
        setValue(card.rows.getValue(Field.PackageName), data.packageName)
        setValue(card.rows.getValue(Field.Version), SettingsAppInfoRules.version(data.versionName, data.versionCode, unknown))
        setValue(card.rows.getValue(Field.Sdk), text(context, R.string.settings_app_info_sdk_value,
            data.minSdk.takeIf { it > 0 }?.toString() ?: unknown,
            data.targetSdk.takeIf { it > 0 }?.toString() ?: unknown))
        setValue(card.rows.getValue(Field.FirstInstalled), SettingsAppInfoRules.timestamp(data.firstInstalled, pattern, locale, zone, unknown))
        setValue(card.rows.getValue(Field.LastUpdated), SettingsAppInfoRules.timestamp(data.lastUpdated, pattern, locale, zone, unknown))
    }

    private fun readInstaller(card: Card, appContext: Context, loader: ClassLoader) {
        val generation = ++card.generation
        val snapshot = card.snapshot
        val unknown = text(appContext, R.string.settings_app_info_unknown)
        val target = WeakReference(card)
        reader.execute {
            val value = runCatching {
                // The displayed PackageInfo already belongs to the right user, including clones.
                // Query its installer in that user's context, not the Settings process's user.
                val context = call(appContext, "createContextAsUser", UserHandle.getUserHandleForUid(snapshot.uid), 0) as Context
                val utility = ModernReflect.findClass("com.android.settings.applications.AppStoreUtil", loader)
                val source = ModernReflect.callStaticMethod(utility, "getInstallerPackageName", context, snapshot.packageName) as? String
                val name = source?.let { runCatching {
                    context.packageManager.getApplicationInfo(it, 0).loadLabel(context.packageManager).toString()
                }.getOrNull() }
                SettingsAppInfoRules.installer(source, name, unknown)
            }.getOrElse { HookLog.w(TAG, "Installer information unavailable", it); unknown }
            main.post {
                runCatching {
                    val state = target.get() ?: return@runCatching
                    if (generation == state.generation && state.snapshot == snapshot &&
                        call(state.group, "getParent") === state.screen && LspConfig.isSettingsAppInfoCardEnabledXposed()) {
                        setValue(state.rows.getValue(Field.Installer), value)
                    }
                }.onFailure { HookLog.w(TAG, "Installer row update failed; stock page retained", it) }
            }
        }
    }

    private fun setValue(row: Row, value: String) {
        if (row.value == value) return
        row.value = value
        call(row.preference, "setSummary", value)
        row.refresh?.invoke()
    }

    private fun bindCopy(row: Row, holder: Any) {
        val view = ModernReflect.getObjectField(holder, "itemView") as View
        view.findViewById<TextView>(android.R.id.summary)?.apply {
            isSingleLine = false
            maxLines = Int.MAX_VALUE
            ellipsize = null
        }
        val listener = View.OnLongClickListener {
            runCatching {
                val clipboard = view.context.getSystemService(ClipboardManager::class.java)
                checkNotNull(clipboard).setPrimaryClip(ClipData.newPlainText(row.title, row.value))
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                true
            }.getOrElse { HookLog.w(TAG, "App information copy failed", it); false }
        }
        copyHandlers[view] = WeakReference(listener)
        view.setOnLongClickListener(listener)
    }

    private fun remove(card: Card) {
        ++card.generation
        card.rotateView?.get()?.animate()?.cancel()
        card.panel?.dispose()
        call(card.screen, "removePreference", card.group)
        card.shifted.forEach { (item, order) ->
            if (call(item, "getOrder") == order + 1) call(item, "setOrder", order)
        }
    }

    private fun bindCallback(preference: Any, loader: ClassLoader, action: (Any) -> Unit) {
        val type = ModernReflect.findClass("com.oplus.settings.widget.preference.SettingJumpPreference\$CustomBindViewHolder", loader)
        call(preference, "setCustomBindViewHolder", proxy(type) { name, args ->
            if (name == "onBindViewHolder") runCatching { action(requireNotNull(args?.get(0))) }
                .onFailure { HookLog.w(TAG, "App information row binding failed", it) }
            null
        })
    }

    private fun clickCallback(preference: Any, loader: ClassLoader, action: () -> Boolean) {
        val type = ModernReflect.findClass("androidx.preference.Preference\$OnPreferenceClickListener", loader)
        call(preference, "setOnPreferenceClickListener", proxy(type) { name, _ ->
            if (name == "onPreferenceClick") runCatching(action)
                .getOrElse { HookLog.w(TAG, "App information expansion failed", it); true } else null
        })
    }

    private fun proxy(type: Class<*>, action: (String, Array<out Any?>?) -> Any?): Any =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                "toString" -> "ONextBox ${type.simpleName}"
                else -> action(method.name, args)
            }
        }

    private fun label(field: Field): Int = when (field) {
        Field.PackageName -> R.string.settings_app_info_package
        Field.Version -> R.string.settings_app_info_version
        Field.Sdk -> R.string.settings_app_info_sdk
        Field.FirstInstalled -> R.string.settings_app_info_first_installed
        Field.LastUpdated -> R.string.settings_app_info_last_updated
        Field.Installer -> R.string.settings_app_info_installer
    }

    private fun call(owner: Any, name: String, vararg args: Any?) = ModernReflect.callMethod(owner, name, *args)

    @SuppressLint("DiscouragedApi") // These resources belong to the installed Settings APK.
    private fun resource(context: Context, name: String, type: String) = context.resources.getIdentifier(name, type, SETTINGS)

    private fun text(context: Context, id: Int, vararg args: Any): String = context.createPackageContext("com.mi.onextbox", 0)
        .createConfigurationContext(context.resources.configuration).getString(id, *args)
}
