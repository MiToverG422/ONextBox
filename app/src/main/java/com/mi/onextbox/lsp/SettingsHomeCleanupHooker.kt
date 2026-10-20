package com.mi.onextbox.lsp

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_READY_KEY
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/** Hide only native home-page UI, without changing accounts, OTA data or other recommendations. */
internal object SettingsHomeCleanupHooker {
    private const val TAG = "ONextBox-SettingsHome"
    private const val STATE = "settings:home-cleanup"
    private const val HOME = "com.oplus.settings.feature.homepage.OplusTopLevelSettings"
    private const val MESSAGES = "com.oplus.settings.feature.homepage.messageentry.MessageEntryController"

    fun hook(loader: ClassLoader) {
        val session = Session(loader)
        if (!session.install()) return
        HotReloadLifecycle.register(STATE, capture = session::capture, detach = session::detach,
            restore = session::restore, resumeAfterRejection = session::schedule)
        HotReloadLifecycle.afterCommit { session.restore(HotReloadLifecycle.restoredState(STATE)) }
    }

    private class Session(private val loader: ClassLoader) {
        private val main = Handler(Looper.getMainLooper())
        private val homes = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
        private val controllers = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
        private val visibility = Collections.synchronizedMap(WeakHashMap<Any, SettingsHomeVisibility>())
        private val writingVisibility = ThreadLocal<Any?>()
        private var preferences: SharedPreferences? = null
        private var messageRefresh: Method? = null
        @Volatile private var detached = false
        private val active get() = !detached && !HotReloadLifecycle.isRetiring
        private val prefix = "$TAG@${System.identityHashCode(loader)}:"
        private val refresh = Runnable {
            HotReloadLifecycle.withHook({}, {
                if (active) {
                    homes.keysSnapshot().forEach { home -> guarded {
                        reconcile(home)
                        // Let the host recompute first-group spacing after either switch changes.
                        call(home, "updateFirstGroupCategory")
                    } }
                    controllers.keysSnapshot().forEach { owner -> guarded { messageRefresh?.invoke(owner) } }
                }
            })
        }
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in SettingsHomeCleanupSettings.keys || key == MODERN_PREFERENCES_READY_KEY) schedule()
        }

        fun install(): Boolean {
            val account = runCatching {
                val home = loader.loadClass(HOME)
                val preference = loader.loadClass("androidx.preference.Preference")
                val setVisible = preference.getDeclaredMethod("setVisible", Boolean::class.javaPrimitiveType)
                ModernHookRegistry.installFast(prefix + "visible", setVisible, XposedInterface.Hooker { chain ->
                    val owner = chain.thisObject
                    if (!active || owner == null || writingVisibility.get() === owner) return@Hooker chain.proceed()
                    val state = visibility[owner]
                    val result = if (state == null) chain.proceed() else {
                        val desired = state.requested(chain.args[0] as Boolean, hidden(owner))
                        chain.proceed(arrayOf<Any?>(desired))
                    }
                    // A device variant can keep an app-updater row in the account category.
                    // Recheck the category when a native sibling changes, rather than hiding it all.
                    if (SettingsHomeCleanupSettings.enabledXposed(SettingsHomeCleanupSettings.ACCOUNT) &&
                        runCatching { call(owner, "getParent")?.let { visibility.containsKey(it) } }.getOrNull() == true) schedule()
                    result
                })
                listOf(home.getDeclaredMethod("onCreate", Bundle::class.java), home.getDeclaredMethod("onResume"),
                    home.getDeclaredMethod("updateFirstGroupCategory")).forEach { method ->
                    ModernHookRegistry.installFast(prefix + method.name, method, XposedInterface.Hooker { chain ->
                        val result = chain.proceed()
                        if (active) chain.thisObject?.let { owner -> guarded { homes[owner] = true; reconcile(owner) } }
                        result
                    })
                }
                listOf("com.android.settingslib.core.AbstractPreferenceController",
                    "com.oplus.settings.feature.homepage.user.UserCenterSafeGuideController").forEach { name ->
                    ModernReflect.findClassIfExists(name, loader)?.declaredMethods?.forEach { method ->
                        runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                    }
                }
                HookLog.i(TAG, "Native Settings home visibility hooks installed")
            }.onFailure {
                ModernHookRegistry.unhookPrefix(prefix)
                HookLog.w(TAG, "Settings home visibility unavailable; stock layout retained", it)
            }.isSuccess
            val messages = installMessages()
            return account || messages
        }

        private fun installMessages(): Boolean = runCatching {
            val type = loader.loadClass(MESSAGES)
            val field = type.getDeclaredField("mMessageEntries").apply { isAccessible = true }
            check(List::class.java.isAssignableFrom(field.type))
            val update = type.getDeclaredMethod("updateMessageEntry").apply { isAccessible = true }
            val empty = type.getDeclaredMethod("isMessageEntriesEmpty")
            messageRefresh = update
            ModernHookRegistry.installFast(prefix + "messages:update", update, XposedInterface.Hooker { chain ->
                val owner = chain.thisObject ?: return@Hooker chain.proceed()
                if (!active) return@Hooker chain.proceed()
                controllers[owner] = true
                if (!SettingsHomeCleanupSettings.enabledXposed(SettingsHomeCleanupSettings.UPDATE)) return@Hooker chain.proceed()
                val original = field.get(owner) as? List<*> ?: return@Hooker chain.proceed()
                val displayed = displayMessages(original)
                SettingsHomeCleanupRules.withDisplayMessages(owner, field, displayed) { chain.proceed() }
            })
            ModernHookRegistry.installFast(prefix + "messages:empty", empty, XposedInterface.Hooker { chain ->
                val owner = chain.thisObject
                if (!active || owner == null || !SettingsHomeCleanupSettings.enabledXposed(SettingsHomeCleanupSettings.UPDATE)) chain.proceed()
                else (field.get(owner) as? List<*>)?.let { displayMessages(it).isEmpty() } ?: chain.proceed()
            })
            // The native delayed task invokes the display method through a compiled runnable.
            listOf(type, ModernReflect.findClassIfExists("com.oplus.settings.feature.homepage.messageentry.g", loader))
                .filterNotNull().forEach { caller -> caller.declaredMethods.forEach { method ->
                    runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                } }
            HookLog.i(TAG, "Native Settings home update-reminder filter installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix + "messages:")
            messageRefresh = null
            HookLog.w(TAG, "Settings update-reminder contract unavailable; native reminders retained", it)
        }.isSuccess

        private fun displayMessages(entries: List<*>): List<*> = SettingsHomeCleanupRules.filterUpdates(entries) { entry ->
            // Unknown data is displayed unchanged; matching translated titles would be unsafe.
            entry != null && runCatching {
                SettingsHomeCleanupRules.isUpdateReminder(
                    ModernReflect.getObjectField(entry, "packageName") as? String,
                    ModernReflect.getObjectField(entry, "targetPackageName") as? String,
                    ModernReflect.getObjectField(entry, "entrancePath") as? String,
                )
            }.getOrDefault(false)
        }

        private fun reconcile(home: Any) {
            val screen = call(home, "getPreferenceScreen") ?: return
            SettingsHomeCleanupRules.accountKeys.forEach { key ->
                call(screen, "findPreference", key)?.let(::applyVisibility)
            }
            val category = call(screen, "findPreference", SettingsHomeCleanupRules.ACCOUNT_CATEGORY)
            category?.let(::applyVisibility)
            val controller = runCatching { ModernReflect.getObjectField(home, "mMessageEntryController") }.getOrNull()
            if (controller != null && messageRefresh?.declaringClass?.isInstance(controller) == true) controllers[controller] = true
            SettingsHomeGroupSpacing.apply(home)
        }

        private fun hidden(preference: Any): Boolean {
            if (!SettingsHomeCleanupSettings.enabledXposed(SettingsHomeCleanupSettings.ACCOUNT)) return false
            return when (call(preference, "getKey")) {
                in SettingsHomeCleanupRules.accountKeys -> true
                SettingsHomeCleanupRules.ACCOUNT_CATEGORY -> {
                    val count = call(preference, "getPreferenceCount") as Int
                    SettingsHomeCleanupRules.shouldHideAccountCategory((0 until count).map { index ->
                        val child = requireNotNull(call(preference, "getPreference", index))
                        (call(child, "getKey") as? String) to (call(child, "isVisible") == true)
                    })
                }
                else -> false
            }
        }

        private fun applyVisibility(preference: Any) {
            val state = synchronized(visibility) {
                visibility.getOrPut(preference) { SettingsHomeVisibility(call(preference, "isVisible") == true) }
            }
            val desired = state.displayed(hidden(preference))
            if (call(preference, "isVisible") != desired) writeVisibility(preference, desired)
        }

        private fun writeVisibility(preference: Any, value: Boolean) {
            val previous = writingVisibility.get()
            writingVisibility.set(preference)
            try { call(preference, "setVisible", value) } finally { writingVisibility.set(previous) }
        }

        fun schedule() {
            HotReloadLifecycle.afterCommit {
                if (active) { main.removeCallbacks(refresh); main.post(refresh) }
            }
        }

        fun capture(): Array<Any?> = arrayOf(
            homes.keysSnapshot().toTypedArray(), controllers.keysSnapshot().toTypedArray(),
            synchronized(visibility) { visibility.entries.map { (preference, state) -> arrayOf(preference, state.nativeVisible) }.toTypedArray() },
        )

        fun detach() {
            preferences?.unregisterOnSharedPreferenceChangeListener(listener)
            preferences = null
            detached = true
            main.removeCallbacksAndMessages(null)
            val original = synchronized(visibility) { visibility.entries.map { it.key to it.value.nativeVisible } }
            visibility.clear()
            original.forEach { (preference, visible) -> writeVisibility(preference, visible) }
            controllers.keysSnapshot().forEach { messageRefresh?.invoke(it) }
            homes.keysSnapshot().forEach { call(it, "updateFirstGroupCategory") }
            homes.clear()
            controllers.clear()
        }

        fun restore(saved: Any?) {
            detached = false
            val state = saved as? Array<*>
            (state?.getOrNull(0) as? Array<*>)?.filterNotNull()?.forEach { homes[it] = true }
            (state?.getOrNull(1) as? Array<*>)?.filterNotNull()?.forEach {
                check(messageRefresh?.declaringClass?.isInstance(it) == true) { "Native message controller has no replacement" }
                controllers[it] = true
            }
            (state?.getOrNull(2) as? Array<*>)?.forEach { row ->
                val values = row as? Array<*> ?: return@forEach
                val preference = values.getOrNull(0) ?: return@forEach
                val visible = values.getOrNull(1) as? Boolean ?: return@forEach
                visibility[preference] = SettingsHomeVisibility(visible)
            }
            if (preferences == null) ModernHookRuntime.remotePreferences("lsp_features")?.let {
                it.registerOnSharedPreferenceChangeListener(listener)
                preferences = it
            }
            schedule()
        }
    }

    private fun call(owner: Any, name: String, vararg args: Any?) = ModernReflect.callMethod(owner, name, *args)
    private fun <K, V> Map<K, V>.keysSnapshot(): List<K> = synchronized(this) { keys.toList() }
    private fun guarded(action: () -> Unit) { runCatching(action).onFailure { HookLog.w(TAG, "Settings home refresh failed; native data retained", it) } }
}
