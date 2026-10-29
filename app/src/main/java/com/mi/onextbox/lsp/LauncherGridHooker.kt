package com.mi.onextbox.lsp

import android.content.Context
import android.util.Pair
import android.view.View
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.util.WeakHashMap

internal object LauncherGridHooker {
    private const val TAG = "LauncherGrid"

    fun hook(loader: ClassLoader, apkPath: String) {
        val key = "$TAG:state@${System.identityHashCode(loader)}"
        val saved = DexTargetCache.restoredState(key) as? Array<*>
        val session = LauncherGridApplySession(loader, apkPath)
        session.restoreTargets(saved)
        HotReloadLifecycle.register(key,
            capture = session::capture,
            detach = session::detach,
            restore = session::restore,
            canReload = session::canReload,
            resumeAfterRejection = session::schedule,
        )
        HotReloadLifecycle.afterCommit { if (saved == null) session.restore(null) }
    }

    fun installMenuHooks(loader: ClassLoader, apkPath: String, values: Array<*>?, menuState: Any?): kotlin.Pair<LauncherGridTargets, LauncherGridMenus> {
        val prefix = "$TAG@${System.identityHashCode(loader)}:"
        return try {
            val metadata = values?.takeIf { it.size == LauncherGridTargets.SCHEMA_SIZE }
            val targets = LauncherGridTargets(metadata ?: LauncherGridTargets.resolve(loader, apkPath).values)
            val menus = LauncherGridMenus(targets)
            menus.restore(menuState)
            fun install(name: String, method: java.lang.reflect.Executable, callback: XposedInterface.Hooker) {
                ModernHookRegistry.installFast("$prefix$name", method, callback)
            }
            install("options", targets.options, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                runCatching { menus.options(result) }.getOrElse {
                    HookLog.w(TAG, "Custom layout option unavailable, retaining native options", it)
                    result
                }
            })
            install("supported", targets.supported, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                if (result == true) result else runCatching {
                    if (menus.supports(chain.getArg(0) as Int, chain.getArg(1) as Int)) true else result
                }.getOrElse {
                    HookLog.w(TAG, "Custom layout validation unavailable, retaining native validation", it)
                    result
                }
            })
            install("refresh", targets.refresh, XposedInterface.Hooker { chain ->
                val adapter = chain.thisObject ?: return@Hooker chain.proceed()
                val snapshot = runCatching {
                    menus.pinned(adapter)?.let { it to menus.selectedOption(adapter) }
                }.getOrElse {
                    HookLog.w(TAG, "Unable to read custom menu selection, retaining native refresh", it)
                    null
                }
                val result = chain.proceed()
                if (snapshot == null) return@Hooker result
                runCatching {
                    val wordless = chain.getArg(0) == true
                    menus.update(adapter, snapshot.first, wordless, snapshot.second?.grid(wordless))
                }
                    .onFailure { HookLog.w(TAG, "Unable to retain custom option during text mode change", it) }
                result
            })
            install("preview", targets.draw, XposedInterface.Hooker { chain ->
                menus.draw(chain.thisObject as? View) { chain.proceed() }
            })
            targets.callers.forEach { check(ModernHookRuntime.requireModule().deoptimize(it)) { "Native layout caller cannot be deoptimized" } }
            install("constructor", targets.constructor, XposedInterface.Hooker { chain ->
                val prepared = runCatching {
                    val state = LauncherGridSettings.stateXposed()
                    if (state.enabled && targets.allowed()) LauncherGridMenus.Pinned(state.grid, targets.wordless()) else null
                }.getOrElse {
                    HookLog.w(TAG, "Custom grid is unavailable for this menu, retaining native layout", it)
                    null
                } ?: return@Hooker chain.proceed()
                menus.creating(prepared.grid, prepared.wordless) {
                    val result = chain.proceed()
                    val adapter = chain.thisObject ?: return@creating result
                    runCatching {
                        val pinned = menus.pinned(adapter)!!
                        val chosen = menus.selected(adapter, targets.wordless())
                        val stored = if (chosen == null) {
                            (targets.currentGrid.invoke(null, chain.getArg(0) as Context) as IntArray)
                                .takeIf { it.size == 2 }?.let { LauncherGrid(it[0], it[1]) }
                        } else chosen
                        menus.update(adapter, pinned, targets.wordless(), stored)
                    }.onFailure { HookLog.w(TAG, "Unable to complete custom layout menu", it) }
                    result
                }
            })
            HookLog.i(TAG, "Native grid range and layout validation hooks installed")
            kotlin.Pair(targets, menus)
        } catch (error: Throwable) {
            ModernHookRegistry.unhookPrefix(prefix)
            throw error
        }
    }
}

internal class LauncherGridMenus(
    private val targets: LauncherGridTargets,
    private val state: () -> LauncherGridState = LauncherGridSettings::stateXposed,
    private val allowed: () -> Boolean = targets::allowed,
) {
    internal data class Pinned(val grid: LauncherGrid, var wordless: Boolean)
    private val owned = WeakHashMap<Any, Pinned>()
    private val building = ThreadLocal<Pinned>()
    private var drawFailureReported = false

    fun <T> creating(grid: LauncherGrid, wordless: Boolean = targets.wordless(), action: () -> T): T {
        val previous = building.get()
        building.set(Pinned(grid, wordless))
        return try { action() } finally {
            if (previous == null) building.remove() else building.set(previous)
        }
    }

    @Synchronized
    fun pinned(adapter: Any): Pinned? = owned[adapter] ?: building.get()?.let {
        Pinned(it.grid, it.wordless).also { pinned -> owned[adapter] = pinned }
    }

    fun options(value: Any?): Any? {
        val maximum = maximum() ?: return value
        val stock = value as? List<*> ?: return value
        val options = stock.map { item ->
            val pair = item as? Pair<*, *> ?: return value
            val rows = pair.second as? Pair<*, *> ?: return value
            LauncherGridOption(pair.first as? Int ?: return value, rows.first as? Int ?: return value,
                rows.second as? Int ?: return value)
        }
        val result = LauncherGridRules.expand(options, maximum)
        if (result === options) return value
        return ArrayList(stock).apply {
            result.drop(options.size).forEach { add(Pair(it.columns, Pair(it.textRows, it.wordlessRows))) }
        }
    }

    private fun maximum(): LauncherGrid? = building.get()?.grid ?: state().takeIf { it.enabled && allowed() }?.grid

    fun supports(columns: Int, rows: Int): Boolean {
        val wordless = targets.wordless()
        val maximum = maximum()
        if (maximum != null && LauncherGridRules.supports(maximum, columns, rows, wordless)) return true
        return ownersSnapshot().any { (adapter, pinned) ->
            (targets.recycler.invoke(adapter) as? View)?.isAttachedToWindow == true &&
                LauncherGridRules.supports(pinned.grid, columns, rows, wordless)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun visible(adapter: Any) = targets.visible.get(adapter) as MutableList<Any>

    fun selected(adapter: Any, wordless: Boolean): LauncherGrid? {
        return selectedOption(adapter)?.grid(wordless)
    }

    fun selectedOption(adapter: Any): LauncherGridOption? {
        val list = visible(adapter)
        val item = list.firstOrNull { targets.selected.getBoolean(it) }
            ?: list.getOrNull(targets.index.getInt(adapter)) ?: return null
        return targets.option(item)
    }

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    fun update(adapter: Any, pinned: Pinned, wordless: Boolean, previous: LauncherGrid?) {
        val visible = visible(adapter)
        val all = targets.all.get(adapter) as MutableList<Any>
        val oldVisible = visible.toList()
        val oldAll = all.toList()
        val oldIndex = targets.index.getInt(adapter)
        val oldFlags = (oldVisible + oldAll).distinct().associateWith { targets.selected.getBoolean(it) }
        try {
            if (all.isEmpty()) all.addAll(visible)
            for (option in LauncherGridRules.expand(emptyList(), pinned.grid)) {
                val item = all.firstOrNull { targets.option(it) == option } ?: targets.item(option).also { all.add(it) }
                if (visible.none { targets.grid(it, wordless) == option.grid(wordless) }) visible.add(item)
            }
            val chosen = previous?.takeIf { grid -> visible.any { targets.grid(it, wordless) == grid } }
                ?: visible.firstOrNull { targets.selected.getBoolean(it) }?.let { targets.grid(it, wordless) }
            val unique = visible.distinctBy { targets.grid(it, wordless) }
            if (unique.size != visible.size) {
                visible.clear()
                visible.addAll(unique)
            }
            (all + visible).distinct().forEach { targets.selected.setBoolean(it, false) }
            val selectedIndex = visible.indexOfFirst { targets.grid(it, wordless) == chosen }
            if (selectedIndex >= 0) {
                targets.selected.setBoolean(visible[selectedIndex], true)
            }
            targets.index.setInt(adapter, selectedIndex)
            pinned.wordless = wordless
        } catch (error: Throwable) {
            visible.clear()
            visible.addAll(oldVisible)
            all.clear()
            all.addAll(oldAll)
            oldFlags.forEach { (item, selected) -> targets.selected.setBoolean(item, selected) }
            targets.index.setInt(adapter, oldIndex)
            throw error
        }
    }

    fun <T> draw(view: View?, action: () -> T): T {
        if (view == null) return action()
        val fields = targets.preview
        val sizing = runCatching {
            val grid = LauncherGrid(fields[0].getInt(view), fields[1].getInt(view))
            if (ownersSnapshot().none { (_, pinned) ->
                LauncherGridRules.supports(pinned.grid, grid.columns, grid.rows, pinned.wordless)
            }) return@runCatching null
            val stock = intArrayOf(fields[2].getInt(view), fields[3].getInt(view), fields[4].getInt(view))
            val size = LauncherGridRules.previewCellSize(view.measuredWidth, view.measuredHeight,
                fields[5].getInt(view), fields[6].getInt(view), grid, stock[0], stock[1]) ?: return@runCatching null
            if (size == stock[0] && size == stock[1]) null else stock to size
        }.getOrElse { reportDrawFailure(it); null } ?: return action()
        val (stock, size) = sizing
        val changed = runCatching {
            fields[2].setInt(view, size)
            fields[3].setInt(view, size)
            fields[4].setInt(view, stock[2].coerceAtMost(size / 2))
        }.onFailure { reportDrawFailure(it); restorePreview(view, stock) }.isSuccess
        if (!changed) return action()
        return try { action() } finally { restorePreview(view, stock) }
    }

    private fun restorePreview(view: View, stock: IntArray) {
        stock.indices.forEach { index ->
            runCatching { targets.preview[index + 2].setInt(view, stock[index]) }.onFailure(::reportDrawFailure)
        }
    }

    private fun reportDrawFailure(error: Throwable) {
        if (!drawFailureReported) {
            drawFailureReported = true
            HookLog.w("LauncherGrid", "Custom preview sizing unavailable, retaining native drawing", error)
        }
    }

    @Synchronized
    private fun ownersSnapshot() = owned.entries.map { it.key to it.value.copy() }

    fun canReload(): Boolean = targets.busy.none { it.invoke(null) == true } &&
        ownersSnapshot().none { (adapter, _) -> (targets.recycler.invoke(adapter) as? View)?.isAttachedToWindow == true }

    @Synchronized
    fun capture(): Array<Array<Any>> = owned.entries.map { (adapter, pinned) ->
        arrayOf(adapter, intArrayOf(pinned.grid.columns, pinned.grid.rows, if (pinned.wordless) 1 else 0))
    }.toTypedArray()

    @Synchronized
    fun clear() = owned.clear()

    @Synchronized
    fun restore(value: Any?) {
        owned.clear()
        (value as? Array<*>)?.forEach { entry ->
            val row = entry as? Array<*> ?: return@forEach
            val adapter = row.getOrNull(0)?.takeIf(targets.constructor.declaringClass::isInstance) ?: return@forEach
            val dimensions = row.getOrNull(1) as? IntArray ?: return@forEach
            if (dimensions.size != 3) return@forEach
            val grid = LauncherGrid(dimensions[0], dimensions[1])
            if (LauncherGridRules.valid(grid)) owned[adapter] = Pinned(grid, dimensions[2] == 1)
        }
    }
}
