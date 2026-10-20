package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SettingsHomeGroupSpacingTest {
    private val account = "user_center_category"
    private val entry = SettingsHomeGroupSpacing.ENTRY_KEY
    private val network = "connect_settings_category"
    private fun group(key: String, order: Int, visible: Boolean = true) = SettingsHomeGroupSpacing.Group(key, order, visible)

    @Test fun hiddenAccountMakesTheTopEntryFirstAndKeepsTheNetworkCardGap() {
        val first = SettingsHomeGroupSpacing.firstGroup(listOf(group(account, 0, false), group(entry, 1), group(network, 2)), false)
        assertEquals(entry, first)
        assertNotEquals(network, first)
    }

    @Test fun showingTheAccountAgainRestoresSpacingAboveTheEntry() {
        val first = SettingsHomeGroupSpacing.firstGroup(listOf(group(account, 0), group(entry, 1), group(network, 2)), false)
        assertEquals(account, first)
        assertNotEquals(entry, first)
    }

    @Test fun middleAndBottomEntriesDoNotTakeTheFirstNetworkGroupsPadding() {
        listOf(5, 5100).forEach { position ->
            assertEquals(network, SettingsHomeGroupSpacing.firstGroup(
                listOf(group(account, 0, false), group(entry, position), group(network, 1)), false))
        }
    }

    @Test fun aDisplayedMessageBannerRequiresNativeSpacingAboveEveryCard() {
        assertNull(SettingsHomeGroupSpacing.firstGroup(listOf(group(account, 0), group(entry, 1), group(network, 2)), true))
        assertNull(SettingsHomeGroupSpacing.firstGroup(listOf(group(account, 0, false), group(entry, 1), group(network, 2)), true))
    }

    @Test fun hidingTheUpdateBannerAllowsTheVisibleEntryToBecomeFirst() {
        val groups = listOf(group(account, 0, false), group(entry, 1), group(network, 2))
        assertNull(SettingsHomeGroupSpacing.firstGroup(groups, true))
        assertEquals(entry, SettingsHomeGroupSpacing.firstGroup(groups, false))
    }

    @Test fun aDisabledOrInvisibleEntryLetsTheNetworkGroupBeFirst() {
        assertEquals(network, SettingsHomeGroupSpacing.firstGroup(listOf(group(account, 0, false), group(network, 1)), false))
        assertEquals(network, SettingsHomeGroupSpacing.firstGroup(listOf(group(entry, 0, false), group(network, 1)), false))
    }

    @Test fun actualOrdersWinOverEnumerationAndTiesKeepNativeSiblingOrder() {
        assertEquals(entry, SettingsHomeGroupSpacing.firstGroup(listOf(group(network, 2), group(entry, 1)), false))
        assertEquals(entry, SettingsHomeGroupSpacing.firstGroup(listOf(group(entry, 1), group(network, 1)), false))
        assertNull(SettingsHomeGroupSpacing.firstGroup(emptyList(), false))
        assertNull(SettingsHomeGroupSpacing.firstGroup(listOf(group(entry, 1, false)), false))
    }

    private fun source(name: String): String = listOf(File("src/main/java/com/mi/onextbox/lsp/$name.kt"),
        File("app/src/main/java/com/mi/onextbox/lsp/$name.kt")).first { it.isFile }.readText()

    @Test fun insertionReuseNativeRefreshAndAccountCleanupUseTheSameSpacingDecision() {
        val hook = source("SettingsHomeEntryHooker")
        assertTrue(hook.contains("home.getDeclaredMethod(\"updateFirstGroupCategory\")"))
        assertTrue(hook.contains("chain.thisObject?.let(SettingsHomeGroupSpacing::apply)"))
        assertEquals(2, Regex("SettingsHomeGroupSpacing\\.apply\\(fragment\\)").findAll(hook).count())
        assertTrue(source("SettingsHomeCleanupHooker").contains("SettingsHomeGroupSpacing.apply(home)"))
        assertFalse(source("SettingsHomeCleanupHooker").contains("call(connection, \"setIsFirstGroup\", noMessages)"))
        val remove = hook.substringAfter("private fun remove(fragment: Any)").substringBefore("private fun removeGroup(")
        assertTrue(remove.contains("call(fragment, \"updateFirstGroupCategory\")"))
    }

    @Test fun spacingUsesDisplayedSiblingsAndTheNativeDensityAwareCategorySetter() {
        val text = source("SettingsHomeGroupSpacing")
        listOf("getPreferenceCount", "getPreference", "getOrder", "isVisible", "color_message_preference",
            "setIsFirstGroup", "HotReloadLifecycle.isRetiring").forEach { assertTrue(it, text.contains(it)) }
        listOf("setPadding", "layoutParams", "setCustomTopMarginPx", "mMessageEntries", "postDelayed")
            .forEach { assertFalse(it, text.contains(it)) }
    }
}
