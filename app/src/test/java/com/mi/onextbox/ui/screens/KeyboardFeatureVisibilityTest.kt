package com.mi.onextbox.ui.screens

import com.mi.onextbox.R
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class KeyboardFeatureVisibilityTest {
    @Test fun emojiAndOtherKeyboardSearchEntriesAreVisible() {
        assertTrue(KeyboardFeatureVisibility.emojiGroupVisible)
        assertTrue(KeyboardFeatureVisibility.isSearchEntryVisible(R.string.keyboard_emoji_group))
        assertTrue(KeyboardFeatureVisibility.isSearchEntryVisible(R.string.keyboard_flag_emoji_title))
        listOf(
            R.string.keyboard_page_title,
            R.string.keyboard_material_group,
            R.string.keyboard_material_unlock_title,
            R.string.keyboard_layout_group,
            R.string.keyboard_bottom_lift_fix_title,
            R.string.keyboard_hide_dismiss_title,
        ).forEach { assertTrue(KeyboardFeatureVisibility.isSearchEntryVisible(it)) }
    }

    @Test fun sharedPageRetainsTheEmojiGroupAndItsExistingSetting() {
        val page = listOf(
            File("src/main/java/com/mi/onextbox/ui/screens/KeyboardFeaturesPage.kt"),
            File("app/src/main/java/com/mi/onextbox/ui/screens/KeyboardFeaturesPage.kt"),
        ).first { it.isFile }.readText()
        val emojiGroup = page.substringAfter("if (KeyboardFeatureVisibility.emojiGroupVisible) {")
        assertTrue(emojiGroup.contains("SettingsSection(title = stringResource(R.string.keyboard_emoji_group))"))
        assertTrue(emojiGroup.contains("title = stringResource(R.string.keyboard_flag_emoji_title)"))
        assertTrue(emojiGroup.contains("LspConfig.setKeyboardFlagEmojiEnabled(context, checked)"))
        assertTrue(page.contains("LspConfig.isKeyboardFlagEmojiEnabled(context)"))
        assertFalse(page.contains("LspConfig.setKeyboardFlagEmojiEnabled(context, false)"))
        assertFalse(page.contains("LocalAppUiStyle"))
    }
}
