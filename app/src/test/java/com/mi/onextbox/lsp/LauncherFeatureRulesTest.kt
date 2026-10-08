package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherFeatureRulesTest {
    @Test fun `clear button hiding takes priority over the old style`() {
        assertEquals(LauncherFeatureRules.ClearButton.Hidden, LauncherFeatureRules.clearButton(true, true))
        assertEquals(LauncherFeatureRules.ClearButton.Hidden, LauncherFeatureRules.clearButton(false, true))
        assertEquals(LauncherFeatureRules.ClearButton.Old, LauncherFeatureRules.clearButton(true, false))
        assertEquals(LauncherFeatureRules.ClearButton.Default, LauncherFeatureRules.clearButton(false, false))
    }

    @Test fun `badge priority matches native icon rendering`() {
        assertEquals(LauncherFeatureRules.Badge.Shortcut, LauncherFeatureRules.badge(true, 31))
        assertEquals(LauncherFeatureRules.Badge.Instant, LauncherFeatureRules.badge(false, 31))
        assertEquals(LauncherFeatureRules.Badge.Archive, LauncherFeatureRules.badge(false, 21))
        assertEquals(LauncherFeatureRules.Badge.Clone, LauncherFeatureRules.badge(false, 5))
        assertEquals(LauncherFeatureRules.Badge.Work, LauncherFeatureRules.badge(false, 1))
        assertEquals(LauncherFeatureRules.Badge.None, LauncherFeatureRules.badge(false, 0))
    }

    @Test fun `clear icon has bounds before the first layout`() {
        assertEquals(
            LauncherFeatureRules.IconBounds(16, 16, 32, 32),
            LauncherFeatureRules.clearIconBounds(0, 0, 16, 16, 48),
        )
    }

    @Test fun `clear icon stays centered after button relayout`() {
        assertEquals(
            LauncherFeatureRules.IconBounds(68, 16, 84, 32),
            LauncherFeatureRules.clearIconBounds(152, 48, 16, 16, 48),
        )
        assertEquals(
            LauncherFeatureRules.IconBounds(16, 16, 32, 32),
            LauncherFeatureRules.clearIconBounds(48, 48, 16, 16, 48),
        )
    }

    @Test fun `clear icon uses pixel dimensions at different display densities`() {
        assertEquals(
            LauncherFeatureRules.IconBounds(40, 40, 80, 80),
            LauncherFeatureRules.clearIconBounds(120, 120, 40, 40, 120),
        )
    }

    @Test fun `clear icon bounds remain valid with invalid intrinsic sizes`() {
        assertEquals(
            LauncherFeatureRules.IconBounds(23, 0, 24, 48),
            LauncherFeatureRules.clearIconBounds(48, 48, -1, 200, 48),
        )
    }

    @Test fun `unrelated icon badges are retained`() {
        listOf(LauncherFeatureRules.Badge.None, LauncherFeatureRules.Badge.Instant, LauncherFeatureRules.Badge.Archive)
            .forEach { assertFalse(LauncherFeatureRules.hideBadge(it, true, true, true)) }
    }

    @Test fun `badge switches are independent`() {
        assertTrue(LauncherFeatureRules.hideBadge(LauncherFeatureRules.Badge.Shortcut, true, false, false))
        assertFalse(LauncherFeatureRules.hideBadge(LauncherFeatureRules.Badge.Work, true, false, true))
        assertTrue(LauncherFeatureRules.hideBadge(LauncherFeatureRules.Badge.Work, false, true, false))
        assertFalse(LauncherFeatureRules.hideBadge(LauncherFeatureRules.Badge.Clone, true, true, false))
        assertTrue(LauncherFeatureRules.hideBadge(LauncherFeatureRules.Badge.Clone, false, false, true))
    }

    @Test fun `animation copies retain the source badge type and switch`() {
        val state = LauncherBadgeState()
        for (badge in listOf(LauncherFeatureRules.Badge.Shortcut, LauncherFeatureRules.Badge.Work, LauncherFeatureRules.Badge.Clone)) {
            val icon = Any()
            val constantState = Any()
            val restoredIcon = Any()
            state.remember(icon, badge)
            state.copy(icon, constantState)
            state.copy(constantState, restoredIcon)
            assertEquals(badge, state.badge(restoredIcon))
            val shortcut = badge == LauncherFeatureRules.Badge.Shortcut
            val work = badge == LauncherFeatureRules.Badge.Work
            val clone = badge == LauncherFeatureRules.Badge.Clone
            assertTrue(LauncherFeatureRules.hideBadge(state.badge(restoredIcon)!!, shortcut, work, clone))
            assertFalse(LauncherFeatureRules.hideBadge(state.badge(restoredIcon)!!, false, false, false))
        }
    }

    @Test fun `reused drawables do not retain the previous app badge`() {
        val state = LauncherBadgeState()
        val icon = Any()
        val restoredIcon = Any()
        state.remember(icon, LauncherFeatureRules.Badge.Clone)
        state.remember(icon, LauncherFeatureRules.Badge.None)
        state.copy(icon, restoredIcon)
        assertEquals(LauncherFeatureRules.Badge.None, state.badge(restoredIcon))
        assertFalse(LauncherFeatureRules.hideBadge(state.badge(restoredIcon)!!, true, true, true))
        state.copy(Any(), restoredIcon)
        assertNull(state.badge(restoredIcon))
        state.copy(icon, null)
    }

    @Test fun `dock blur requires the dock switch`() {
        assertFalse(LauncherFeatureRules.dockBlur(false, true))
        assertFalse(LauncherFeatureRules.dockBlur(true, false))
        assertFalse(LauncherFeatureRules.dockBlur(false, false))
        assertTrue(LauncherFeatureRules.dockBlur(true, true))
    }

    @Test fun `folder filter removal does not affect other inputs or emoji filters`() {
        assertTrue(LauncherFeatureRules.removeFolderFilter(true, true, true))
        assertFalse(LauncherFeatureRules.removeFolderFilter(false, true, true))
        assertFalse(LauncherFeatureRules.removeFolderFilter(true, false, true))
        assertFalse(LauncherFeatureRules.removeFolderFilter(true, true, false))
    }

    @Test fun `folder width crop is skipped only for its own name watcher`() {
        assertTrue(LauncherFeatureRules.skipFolderNameCrop(true, true))
        assertFalse(LauncherFeatureRules.skipFolderNameCrop(false, true))
        assertFalse(LauncherFeatureRules.skipFolderNameCrop(true, false))
        assertFalse(LauncherFeatureRules.skipFolderNameCrop(false, false))
    }
}
