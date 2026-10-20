package com.mi.onextbox.lsp

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.util.WeakHashMap

/** Only the native Settings home owns this preference; no global resource replacement. */
internal object SettingsHomeEntryHooker {
    private const val TAG = "ONextBox-SettingsEntry"
    private const val MODULE = "com.mi.onextbox"
    private const val GROUP_KEY = SettingsHomeGroupSpacing.ENTRY_KEY
    private const val ROW_KEY = "onextbox_home_entry"
    private const val ORDERS_KEY = "onextbox_home_entry_original_orders"
    private const val POSITION_META = "onextbox_entry_position"
    private const val STYLE_META = "onextbox_entry_icon_style"
    private const val STATE_KEY = "settings:home-entry"
    private const val ROW_CLASS = "com.oplus.settings.widget.preference.SettingsSimpleJumpPreference"
    private const val CATEGORY_CLASS = "com.oplus.settings.feature.homepage.HomepageTopCategory"
    private val fragments = WeakHashMap<Any, ClassLoader>()

    fun hook(loader: ClassLoader) {
        HotReloadLifecycle.register(STATE_KEY,
            capture = { fragments.entries.map { (fragment, hostLoader) -> arrayOf(fragment, hostLoader) }.toTypedArray() },
            detach = { fragments.keys.toList().forEach(::remove) },
            restore = { state ->
                (state as? Array<*>)?.forEach { item ->
                    val pair = item as? Array<*> ?: return@forEach
                    val fragment = pair.getOrNull(0) ?: return@forEach
                    val hostLoader = pair.getOrNull(1) as? ClassLoader ?: return@forEach
                    update(fragment, hostLoader)
                }
            },
        )
        val prefix = "$TAG@${System.identityHashCode(loader)}:"
        runCatching {
            val home = ModernReflect.findClass("com.oplus.settings.feature.homepage.OplusTopLevelSettings", loader)
            ModernReflect.findClass(ROW_CLASS, loader)
            ModernReflect.findClass(CATEGORY_CLASS, loader)
            val targets = listOf(home.getDeclaredMethod("onCreate", Bundle::class.java), home.getDeclaredMethod("onResume"))
            targets.forEach { method ->
                ModernHookRegistry.installFast(prefix + method.name, method, XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    runCatching { chain.thisObject?.let { update(it, loader) } }
                        .onFailure { HookLog.w(TAG, "Could not update Settings home entry; stock page retained", it) }
                    result
                })
            }
            val firstGroup = home.getDeclaredMethod("updateFirstGroupCategory")
            ModernHookRegistry.installFast(prefix + firstGroup.name, firstGroup, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                runCatching { chain.thisObject?.let(SettingsHomeGroupSpacing::apply) }
                    .onFailure { HookLog.w(TAG, "Could not update Settings home group spacing", it) }
                result
            })
            home.declaredMethods.firstOrNull { it.name == "onDestroy" && it.parameterCount == 0 }?.let { method ->
                ModernHookRegistry.installFast(prefix + method.name, method, XposedInterface.Hooker { chain ->
                    chain.thisObject?.let { fragment ->
                        runCatching { remove(fragment) }
                            .onFailure { HookLog.w(TAG, "Could not remove Settings home entry", it) }
                        fragments.remove(fragment)
                    }
                    chain.proceed()
                })
            }
            HookLog.i(TAG, "Settings home entry lifecycle hooks installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(TAG, "Native Settings home unavailable; stock page retained", it)
        }
    }

    private fun update(fragment: Any, loader: ClassLoader) {
        if (HotReloadLifecycle.isRetiring) return
        fragments[fragment] = loader
        val screen = call(fragment, "getPreferenceScreen") ?: return
        val existing = call(screen, "findPreference", GROUP_KEY)
        val options = SettingsHomeEntrySettings.optionsXposed()
        if (!options.enabled) {
            if (existing != null) {
                removeGroup(requireNotNull(call(existing, "getParent")), existing)
                call(fragment, "updateFirstGroupCategory")
            }
            return
        }
        if (existing != null) {
            val extras = call(existing, "getExtras") as Bundle
            if (extras.getInt(POSITION_META, -1) == options.position.value &&
                extras.getInt(STYLE_META, -1) == options.iconStyle.value) {
                SettingsHomeGroupSpacing.apply(fragment)
                return
            }
            removeGroup(requireNotNull(call(existing, "getParent")), existing)
            call(fragment, "updateFirstGroupCategory")
        }
        val anchor = call(screen, "findPreference", "airplane_mode")
            ?: call(screen, "findPreference", "wifi_settings") ?: return
        val connection = call(anchor, "getParent") ?: return
        val parent = call(connection, "getParent") ?: return
        val networkOrder = call(connection, "getOrder") as Int
        require(networkOrder < Int.MAX_VALUE) { "Native network group has no stable order" }
        val children = (0 until (call(parent, "getPreferenceCount") as Int)).map { index ->
            requireNotNull(call(parent, "getPreference", index))
        }
        val groupClass = ModernReflect.findClass("androidx.preference.PreferenceGroup", loader)
        val groupOrders = children.filter { groupClass.isInstance(it) && call(it, "isVisible") == true }
            .map { call(it, "getOrder") as Int }
        val footerOrder = call(screen, "findPreference", "empty_preference_key")?.let { call(it, "getOrder") as Int }
        val position = SettingsHomeEntryPlacement.orderFor(options.position, networkOrder, groupOrders, footerOrder)
        val siblings = children.filter { SettingsHomeEntryPlacement.shouldShift(call(it, "getOrder") as Int, position) }
        val orders = Bundle()
        siblings.forEach { sibling ->
            val key = call(sibling, "getKey") as? String
            require(!key.isNullOrBlank() && !orders.containsKey(key)) { "Native home group has no unique key" }
            orders.putInt(key, call(sibling, "getOrder") as Int)
        }
        val context = call(fragment, "getContext") as? Context ?: return
        // Preference context includes the host's home-page theme overlay.
        val themed = call(screen, "getContext") as? Context ?: context
        val layout = hostLayout(themed)
        require(layout != 0) { "Native Settings home row layout unavailable" }
        val row = SettingsHomeEntryAppearance.createRow(themed, ModernReflect.findClass(ROW_CLASS, loader), options.iconStyle)
        call(row, "setKey", ROW_KEY)
        call(row, "setPersistent", false)
        call(row, "setTitle", "ONextBox")
        call(row, "setLayoutResource", layout)
        call(row, "setJumpImageVisible", true)
        // The host Preference launches this explicit intent; no module callback is retained.
        call(row, "setIntent", Intent.makeMainActivity(ComponentName(MODULE, "$MODULE.MainActivity")))
        val group = ModernReflect.newInstance(ModernReflect.findClass(CATEGORY_CLASS, loader), themed, null)
        call(group, "setKey", GROUP_KEY)
        call(group, "setPersistent", false)
        call(group, "setOrder", position)
        call(group, "setMarginTopType", 2)
        // Store only framework-owned data so hot reload can remove and restore the native card.
        (call(group, "getExtras") as Bundle).apply {
            putBundle(ORDERS_KEY, orders)
            putInt(POSITION_META, options.position.value)
            putInt(STYLE_META, options.iconStyle.value)
        }
        SettingsHomeEntryPlacement.attach(
            attachGroup = {
                siblings.forEach { sibling ->
                    val key = call(sibling, "getKey") as String
                    call(sibling, "setOrder", orders.getInt(key) + 1)
                }
                call(parent, "addPreference", group) == true
            },
            attachEntry = { call(group, "addPreference", row) == true },
            rollback = { removeGroup(parent, group) },
        )
        SettingsHomeGroupSpacing.apply(fragment)
        HookLog.i(TAG, "Settings home entry attached")
    }

    private fun remove(fragment: Any) {
        val screen = call(fragment, "getPreferenceScreen") ?: return
        val group = call(screen, "findPreference", GROUP_KEY) ?: return
        removeGroup(requireNotNull(call(group, "getParent")), group)
        call(fragment, "updateFirstGroupCategory")
    }

    private fun removeGroup(parent: Any, group: Any) {
        if (call(parent, "findPreference", GROUP_KEY) === group) {
            check(call(parent, "removePreference", group) == true) { "Could not remove Settings entry group" }
        }
        val extras = call(group, "getExtras") as Bundle
        val orders = extras.getBundle(ORDERS_KEY) ?: return
        orders.keySet().forEach { key ->
            val sibling = call(parent, "findPreference", key) ?: return@forEach
            val original = orders.getInt(key)
            if (SettingsHomeEntryPlacement.shouldRestore(call(sibling, "getOrder") as Int, original)) {
                call(sibling, "setOrder", original)
            }
        }
        extras.remove(ORDERS_KEY)
    }

    @SuppressLint("DiscouragedApi")
    private fun hostLayout(context: Context): Int = context.resources.getIdentifier(
        "jump_preference_simple_layout_no_summary_homepage", "layout", "com.android.settings")

    private fun call(owner: Any, name: String, vararg args: Any?) = ModernReflect.callMethod(owner, name, *args)
}
