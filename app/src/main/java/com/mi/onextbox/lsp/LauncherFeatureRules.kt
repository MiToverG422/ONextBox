package com.mi.onextbox.lsp

import java.util.WeakHashMap

// Launcher badge types and dependent settings.
internal object LauncherFeatureRules {
    enum class Badge { None, Shortcut, Instant, Archive, Clone, Work }
    enum class ClearButton { Default, Old, Hidden }

    fun badge(hasShortcutBadge: Boolean, flags: Int): Badge = when {
        hasShortcutBadge -> Badge.Shortcut
        flags and 2 != 0 -> Badge.Instant
        flags and 16 != 0 -> Badge.Archive
        flags and 4 != 0 -> Badge.Clone
        flags and 1 != 0 -> Badge.Work
        else -> Badge.None
    }

    fun hideBadge(badge: Badge, shortcut: Boolean, work: Boolean, clone: Boolean): Boolean = when (badge) {
        Badge.Shortcut -> shortcut
        Badge.Work -> work
        Badge.Clone -> clone
        else -> false
    }

    fun clearButton(old: Boolean, hide: Boolean): ClearButton = when {
        hide -> ClearButton.Hidden
        old -> ClearButton.Old
        else -> ClearButton.Default
    }

    data class IconBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

    fun clearIconBounds(width: Int, height: Int, iconWidth: Int, iconHeight: Int, fallbackSize: Int): IconBounds {
        val availableWidth = width.takeIf { it > 0 } ?: fallbackSize.coerceAtLeast(1)
        val availableHeight = height.takeIf { it > 0 } ?: fallbackSize.coerceAtLeast(1)
        val drawableWidth = iconWidth.coerceIn(1, availableWidth)
        val drawableHeight = iconHeight.coerceIn(1, availableHeight)
        val left = (availableWidth - drawableWidth) / 2
        val top = (availableHeight - drawableHeight) / 2
        return IconBounds(left, top, left + drawableWidth, top + drawableHeight)
    }

    fun dockBlur(dock: Boolean, blur: Boolean): Boolean = dock && blur

    fun removeFolderFilter(enabled: Boolean, folderInput: Boolean, lengthFilter: Boolean): Boolean =
        enabled && folderInput && lengthFilter

    fun skipFolderNameCrop(enabled: Boolean, folderNameWatcher: Boolean): Boolean =
        enabled && folderNameWatcher
}

// Badge types for icons and their animation copies.
internal class LauncherBadgeState {
    private val badges = WeakHashMap<Any, LauncherFeatureRules.Badge>()

    fun remember(icon: Any, badge: LauncherFeatureRules.Badge) {
        synchronized(badges) { badges[icon] = badge }
    }

    fun badge(icon: Any?): LauncherFeatureRules.Badge? = synchronized(badges) { badges[icon] }

    fun copy(original: Any?, replacement: Any?) {
        if (replacement == null) return
        synchronized(badges) {
            val badge = badges[original]
            if (badge == null) badges.remove(replacement) else badges[replacement] = badge
        }
    }
}
