package com.mi.onextbox.boot

import android.content.Context
import android.provider.Settings
import com.mi.onextbox.ui.common.ShellLogger

internal object DisplayDensity {
    data class State(val physical: Int, val overrideDpi: Int?, val enabled: Boolean, val keep: Boolean) {
        val effective get() = overrideDpi ?: physical
        val range get() = minOf(160, effective)..maxOf(960, effective, physical)
    }

    private const val DEFAULT = -1
    private const val BASE = "previous_override"
    private const val TARGET = "requested_override"
    private const val KEEP = "keep_on_boot"
    private const val BOOT = "boot_count"
    private fun prefs(ctx: Context) = ctx.createDeviceProtectedStorageContext()
        .getSharedPreferences("display_density", Context.MODE_PRIVATE)
    private fun boot(ctx: Context) = Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT)

    private fun density(lines: List<String>, prefix: String): Int? {
        val line = lines.firstOrNull { it.trimStart().startsWith(prefix) } ?: return null
        return checkNotNull(line.substringAfter(':').trim().toIntOrNull()?.takeIf { it > 0 })
    }

    internal fun parse(lines: List<String>): Pair<Int, Int?> =
        checkNotNull(density(lines, "Physical density:")) to density(lines, "Override density:")

    internal data class Reconciliation(val write: Int?, val retain: Boolean)

    internal fun reconcile(
        original: Int, target: Int, currentOverride: Int?, onBoot: Boolean, keep: Boolean,
    ): Reconciliation {
        val current = currentOverride ?: DEFAULT
        // A ROM can clear a forced density during boot. A different explicit override belongs to its writer.
        val clearedOnBoot = onBoot && current == DEFAULT && original != target
        if (current != target && !clearedOnBoot) return Reconciliation(null, false)
        val retain = onBoot && keep
        val desired = if (retain) target else original
        return Reconciliation(desired.takeIf { it != current }, retain)
    }

    fun enabled(ctx: Context) = prefs(ctx).contains(BASE)

    fun read(ctx: Context): State = synchronized(this) {
        val output = ShellLogger.exec("DisplayDensity", "wm density")
        check(output.isSuccess)
        val (physical, overrideDpi) = parse(output.out)
        val preferences = prefs(ctx)
        State(physical, overrideDpi, preferences.contains(BASE), preferences.getBoolean(KEEP, false))
    }

    fun enable(ctx: Context): Unit = synchronized(this) {
        val state = read(ctx)
        if (state.enabled) return@synchronized
        val original = state.overrideDpi ?: DEFAULT
        check(prefs(ctx).edit().putInt(BASE, original).putInt(TARGET, original)
            .putInt(BOOT, boot(ctx)).putBoolean(KEEP, false).commit())
    }

    fun set(ctx: Context, dpi: Int): Unit = synchronized(this) {
        val state = read(ctx)
        require(state.enabled && dpi in state.range)
        if (state.effective == dpi) return@synchronized
        val preferences = prefs(ctx)
        val old = preferences.getInt(TARGET, DEFAULT)
        val oldBase = preferences.getInt(BASE, DEFAULT)
        val current = state.overrideDpi ?: DEFAULT
        val base = if (current == old) oldBase else current
        check(preferences.edit().putInt(BASE, base).putInt(TARGET, dpi).commit())
        try {
            applyOverride(dpi)
        } catch (error: Exception) {
            check(preferences.edit().putInt(BASE, oldBase).putInt(TARGET, old).commit())
            throw error
        }
    }

    fun keep(ctx: Context, enabled: Boolean): Unit = synchronized(this) {
        val preferences = prefs(ctx)
        check(preferences.contains(BASE) && preferences.edit().putBoolean(KEEP, enabled).commit())
    }

    fun restore(ctx: Context) = synchronized(this) { reconcileStoredOverride(ctx, null) }

    fun onBoot(ctx: Context): Unit = synchronized(this) {
        val preferences = prefs(ctx)
        if (!preferences.contains(BASE)) return@synchronized
        val currentBoot = boot(ctx)
        if (preferences.getInt(BOOT, currentBoot) == currentBoot) return@synchronized
        reconcileStoredOverride(ctx, currentBoot)
    }

    private fun reconcileStoredOverride(ctx: Context, newBoot: Int?) {
        val preferences = prefs(ctx)
        if (!preferences.contains(BASE)) return
        val change = reconcile(
            preferences.getInt(BASE, DEFAULT), preferences.getInt(TARGET, DEFAULT),
            read(ctx).overrideDpi, onBoot = newBoot != null, keep = preferences.getBoolean(KEEP, false),
        )
        change.write?.let(::applyOverride)
        val editor = preferences.edit()
        if (change.retain) editor.putInt(BOOT, checkNotNull(newBoot)) else editor.clear()
        check(editor.commit())
    }

    private fun applyOverride(dpi: Int) {
        require(dpi == DEFAULT || dpi > 0)
        val value = if (dpi == DEFAULT) "reset" else dpi.toString()
        check(ShellLogger.exec("DisplayDensity", "wm density $value").isSuccess)
    }
}
