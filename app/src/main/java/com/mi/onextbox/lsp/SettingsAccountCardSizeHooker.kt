package com.mi.onextbox.lsp

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.View
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_READY_KEY
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.util.Collections
import java.util.WeakHashMap

internal object SettingsAccountCardSizeHooker {
    private const val TAG = "ONextBox-AccountCard"
    private const val STATE = "settings:account-card-size"

    fun hook(loader: ClassLoader) {
        val session = Session(loader)
        if (!session.install()) return
        HotReloadLifecycle.register(STATE, capture = session::capture, detach = session::detach,
            restore = session::restore, resumeAfterRejection = session::schedule)
        HotReloadLifecycle.afterCommit { session.restore(HotReloadLifecycle.restoredState(STATE)) }
    }

    private class Session(private val loader: ClassLoader) {
        private val main = Handler(Looper.getMainLooper())
        private val cards = Collections.synchronizedMap(WeakHashMap<View, SettingsAccountCardGeometry>())
        private var preferences: SharedPreferences? = null
        private var detached = false
        private val active get() = !detached && !HotReloadLifecycle.isRetiring
        private val prefix = "$TAG@${System.identityHashCode(loader)}:"
        private val refresh = Runnable {
            HotReloadLifecycle.withHook({}, { if (active) snapshot().forEach { (_, state) -> apply(state) } })
        }
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in setOf(SettingsAppearanceSettings.ACCOUNT_SIZE, SettingsAppearanceSettings.ACCOUNT_PERCENT,
                    MODERN_PREFERENCES_READY_KEY)) schedule()
        }

        fun install(): Boolean = runCatching {
            val preference = loader.loadClass("com.oplus.settings.feature.homepage.user.UserCenterPreference")
            val holder = loader.loadClass("androidx.preference.PreferenceViewHolder")
            val bind = preference.getDeclaredMethod("onBindViewHolder", holder)
            require(bind.returnType == Void.TYPE)
            ModernHookRegistry.installFast(prefix + "bind", bind, XposedInterface.Hooker { chain ->
                val root = chain.getArg(0)?.let { ModernReflect.getObjectField(it, "itemView") } as? View
                val card = root?.let(::findCard)
                if (active && card != null) cards.remove(card)?.restore()
                val result = chain.proceed()
                if (active && root != null && chain.thisObject?.let(::isHomeAccount) == true) guarded {
                    findCard(root)?.let(::track)
                }
                result
            })
            val home = loader.loadClass("com.oplus.settings.feature.homepage.OplusTopLevelSettings")
            ModernHookRegistry.installFast(prefix + "resumed", home.getDeclaredMethod("onResume"), XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                if (active) chain.thisObject?.let { owner -> guarded {
                    (ModernReflect.callMethod(owner, "getView") as? View)?.let(::findCard)?.let(::track)
                } }
                result
            })
            // Recompute the native squeeze mask after a card-height change on newer Settings.
            ModernReflect.findClassIfExists("com.oplus.settings.feature.homepage.user.UserCenterAccountRowOuterLayout", loader)?.let { outer ->
                runCatching {
                    val applyOffset = outer.getDeclaredMethod("applyCollapseOffsetToLayout").apply { isAccessible = true }
                    val layout = outer.getDeclaredMethod("onLayout", Boolean::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    ModernHookRegistry.installFast(prefix + "layout", layout, XposedInterface.Hooker { chain ->
                        val result = chain.proceed()
                        if (active && chain.getArg(0) == true) {
                            val settings = SettingsAppearanceSettings.stateXposed()
                            if (settings.accountSize && settings.accountPercent != SettingsAppearanceSettings.DEFAULT_PERCENT) {
                                (chain.thisObject as? View)?.let(::findCard)?.let { card ->
                                    if (cards.containsKey(card)) guarded { applyOffset.invoke(chain.thisObject) }
                                }
                            }
                        }
                        result
                    })
                }.onFailure { HookLog.w(TAG, "Native account collapse mask unavailable; card sizing retained", it) }
            }
            HookLog.i(TAG, "Native account-card size hooks installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(TAG, "Account-card size unavailable; native layout retained", it)
        }.isSuccess

        @SuppressLint("DiscouragedApi")
        private fun findCard(root: View): View? {
            val id = root.resources.getIdentifier("user_center_preference", "id", "com.android.settings")
            return (if (id != 0) root.findViewById<View>(id) else null)
                ?: root.takeIf { it.javaClass.name == "com.coui.appcompat.cardlist.COUICardListSelectedItemLayout" }
        }

        private fun isHomeAccount(preference: Any): Boolean {
            if ((ModernReflect.callMethod(preference, "getKey") as? String) !in SettingsHomeCleanupRules.accountKeys) return false
            var parent = ModernReflect.callMethod(preference, "getParent")
            repeat(8) {
                val owner = parent ?: return false
                if (ModernReflect.callMethod(owner, "getKey") == SettingsHomeCleanupRules.ACCOUNT_CATEGORY) return true
                parent = ModernReflect.callMethod(owner, "getParent")
            }
            return false
        }

        private fun track(card: View) {
            val avatar = card.findViewById<View>(android.R.id.icon) ?: return
            val params = avatar.layoutParams ?: return
            if (params.width <= 0 || params.height <= 0) return
            val state = cards.getOrPut(card) { SettingsAccountCardGeometry(card, avatar) }
            apply(state)
        }

        private fun apply(state: SettingsAccountCardGeometry) {
            val settings = SettingsAppearanceSettings.stateXposed()
            if (settings.accountSize) state.apply(settings.accountPercent) else state.restore()
        }

        private fun snapshot() = synchronized(cards) { cards.entries.map { it.key to it.value } }
        fun capture(): Array<View> = snapshot().map { it.first }.toTypedArray()
        fun schedule() = HotReloadLifecycle.afterCommit { if (active) { main.removeCallbacks(refresh); main.post(refresh) } }

        fun detach() {
            detached = true
            preferences?.unregisterOnSharedPreferenceChangeListener(listener)
            preferences = null
            main.removeCallbacksAndMessages(null)
            snapshot().forEach { (_, state) -> state.restore() }
            cards.clear()
        }

        fun restore(saved: Any?) {
            detached = false
            (saved as? Array<*>)?.filterIsInstance<View>()?.forEach { guarded { track(it) } }
            if (preferences == null) ModernHookRuntime.remotePreferences("lsp_features")?.let {
                it.registerOnSharedPreferenceChangeListener(listener)
                preferences = it
            }
            schedule()
        }

        private fun guarded(action: () -> Unit) {
            runCatching(action).onFailure { HookLog.w(TAG, "Account-card refresh failed; native contents retained", it) }
        }
    }
}
