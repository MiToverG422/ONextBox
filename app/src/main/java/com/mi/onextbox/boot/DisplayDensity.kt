package com.mi.onextbox.boot

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import com.mi.onextbox.lsp.DisplayDensitySettings
import com.mi.onextbox.lsp.DisplayDensitySettings.Action
import com.mi.onextbox.lsp.DisplayDensitySettings.DEFAULT
import com.mi.onextbox.lsp.DisplayDensitySettings.KEY
import com.mi.onextbox.lsp.DisplayDensitySettings.Request
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.ModernXposedPreferenceSync
import org.lsposed.hiddenapibypass.HiddenApiBypass

internal object DisplayDensity {
    data class State(val physical: Int, val overrideDpi: Int?, val enabled: Boolean, val keep: Boolean) {
        val effective get() = overrideDpi ?: physical
        val range get() = minOf(160, effective)..maxOf(960, effective, physical)
    }

    private val windowManagerGlobal by lazy { Class.forName("android.view.WindowManagerGlobal") }
    private val windowManagerInterface by lazy { Class.forName("android.view.IWindowManager") }

    private fun legacy(ctx: Context) = ctx.createDeviceProtectedStorageContext()
        .getSharedPreferences("display_density", Context.MODE_PRIVATE)

    fun hasLegacyConfiguration(ctx: Context) = legacy(ctx).contains("previous_override")

    private fun user(ctx: Context) = ctx.applicationInfo.uid / 100_000
    private fun boot(ctx: Context) = Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT)
    private fun request(ctx: Context) = DisplayDensitySettings.request(LspPreferenceStore.prefs(ctx).getString(KEY, null))
        ?.takeIf { it.user == user(ctx) }
    private fun result(ctx: Context) = DisplayDensitySettings.result(
        Settings.Global.getString(ctx.contentResolver, DisplayDensitySettings.resultKey(user(ctx))),
    )

    fun resultUri(ctx: Context) = Settings.Global.getUriFor(DisplayDensitySettings.resultKey(user(ctx)))

    fun enabled(ctx: Context): Boolean {
        val request = request(ctx) ?: return hasLegacyConfiguration(ctx)
        val result = result(ctx)
        return request.action != Action.Restore && (result?.id != request.id || result.active)
    }

    fun read(ctx: Context): State = synchronized(this) {
        if (hasLegacyConfiguration(ctx)) check(migrateLegacy(ctx)) { "Display density migration is pending" }
        val result = result(ctx)
        check(result?.boot == boot(ctx)) { "The system display density hook is unavailable" }
        val service = requireNotNull(HiddenApiBypass.invoke(windowManagerGlobal, null, "getWindowManagerService"))
        val physical = HiddenApiBypass.invoke(windowManagerInterface, service, "getInitialDisplayDensity", Display.DEFAULT_DISPLAY) as Int
        val current = HiddenApiBypass.invoke(windowManagerInterface, service, "getBaseDisplayDensity", Display.DEFAULT_DISPLAY) as Int
        check(physical > 0 && current > 0)
        val saved = request(ctx)
        val active = enabled(ctx)
        State(physical, current.takeIf { it != physical }, active,
            active && (saved?.keep ?: legacy(ctx).getBoolean("keep_on_boot", false)))
    }

    fun enable(ctx: Context): Unit = synchronized(this) {
        val state = read(ctx)
        if (state.enabled) return@synchronized
        val original = state.overrideDpi ?: DEFAULT
        publish(ctx, Request(DisplayDensitySettings.requestId(), user(ctx), boot(ctx), original, original, false, Action.Save))
    }

    fun set(ctx: Context, dpi: Int): Unit = synchronized(this) {
        val state = read(ctx)
        require(state.enabled && dpi in state.range)
        if (state.effective == dpi) return@synchronized
        val saved = checkNotNull(request(ctx))
        val current = state.overrideDpi ?: DEFAULT
        val original = if (current == DisplayDensitySettings.overrideFor(saved.target, state.physical)) saved.original else current
        publish(ctx, saved.copy(id = DisplayDensitySettings.requestId(), boot = boot(ctx), original = original,
            target = DisplayDensitySettings.overrideFor(dpi, state.physical), action = Action.Adjust))
    }

    fun keep(ctx: Context, enabled: Boolean): Unit = synchronized(this) {
        check(enabled(ctx))
        val saved = checkNotNull(request(ctx))
        publish(ctx, saved.copy(id = DisplayDensitySettings.requestId(), boot = boot(ctx), keep = enabled, action = Action.Save))
    }

    fun restore(ctx: Context): Unit = synchronized(this) {
        if (!enabled(ctx)) return@synchronized
        val saved = checkNotNull(request(ctx))
        publish(ctx, saved.copy(id = DisplayDensitySettings.requestId(), boot = boot(ctx), keep = false, action = Action.Restore))
    }

    // Publish the older settings even if the updated Hook will first load on the next reboot.
    fun migrateLegacy(ctx: Context): Boolean = synchronized(this) {
        val preferences = legacy(ctx)
        if (!hasLegacyConfiguration(ctx)) return@synchronized true
        val saved = request(ctx) ?: run {
            val currentBoot = boot(ctx)
            val original = preferences.getInt("previous_override", DEFAULT)
            val target = preferences.getInt("requested_override", original)
            require(DisplayDensitySettings.validOverride(original) && DisplayDensitySettings.validOverride(target))
            val migration = Request(DisplayDensitySettings.requestId(), user(ctx),
                preferences.getInt("boot_count", currentBoot).coerceIn(0, currentBoot),
                original, target, preferences.getBoolean("keep_on_boot", false), Action.Save)
            ModernXposedPreferenceSync.setStringAndSync(ctx, KEY, migration.encode())
            migration
        }
        val accepted = result(ctx)
        if (accepted?.id != saved.id || accepted.boot != boot(ctx) || !accepted.success) return@synchronized false
        preferences.edit().clear().commit()
    }

    private fun publish(ctx: Context, next: Request) {
        val previous = request(ctx)?.takeIf { enabled(ctx) }
        ModernXposedPreferenceSync.setStringAndSync(ctx, KEY, next.encode())
        try {
            val deadline = SystemClock.elapsedRealtime() + 4_000
            do {
                val result = result(ctx)
                if (result?.id == next.id && result.boot == next.boot) {
                    check(result.success) { "WindowManager rejected the display density change" }
                    if (hasLegacyConfiguration(ctx)) check(legacy(ctx).edit().clear().commit())
                    return
                }
                Thread.sleep(50)
            } while (SystemClock.elapsedRealtime() < deadline)
            error("WindowManager did not confirm the display density change")
        } catch (error: Exception) {
            val rollback = previous?.copy(id = DisplayDensitySettings.requestId(), boot = next.boot, action = Action.Save)
            runCatching { ModernXposedPreferenceSync.setStringAndSync(ctx, KEY, rollback?.encode().orEmpty()) }
                .onFailure(error::addSuppressed)
            throw error
        }
    }
}
