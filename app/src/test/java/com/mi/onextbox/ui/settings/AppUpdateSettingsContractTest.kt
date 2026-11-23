package com.mi.onextbox.ui.settings

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class AppUpdateSettingsContractTest {
    @Test fun oldManualBuildPreferencesCannotOverrideTheInstalledBuildType() {
        val preference = source("ui/settings/AppUpdatePreferences.kt")
        assertTrue(preference.contains("AppUpdateRules.buildType(BuildConfig.BUILD_TYPE)"))
        assertFalse(preference.contains("update_build_type\""))
        assertFalse(preference.contains("fun setBuildType"))
        assertFalse(preference.contains("fun SettingsUpdateBuildTypeDropdown"))
        val updater = source("ui/settings/AppUpdater.kt")
        assertTrue(updater.contains("val buildType = AppUpdatePreferences.currentBuildType"))
        assertFalse(updater.contains("buildType: UpdateBuildType = UpdateBuildType.Release"))
    }

    @Test fun channelDropdownDefaultsToReleaseAndKeepsAnEarlierCiSelection() {
        val preferences = source("ui/settings/AppUpdatePreferences.kt")
        assertTrue(preferences.contains("KEY_UPDATE_CHANNEL = \"update_channel\""))
        assertTrue(preferences.contains("UpdateChannel.Ci.name, \"GitHubCi\" -> UpdateChannel.Ci"))
        assertTrue(preferences.contains("else -> UpdateChannel.Release"))
        assertTrue(preferences.contains("putString(KEY_UPDATE_CHANNEL, channel.name)"))
        val page = source("ui/settings/SettingsMainPage.kt").substringAfter("fun SoftwareUpdateAutoSettingsPage(")
            .substringBefore("private fun SoftwareUpdateMenuRow")
        val row = page.substringAfter("SettingsWindowDropdownPreference(")
        assertTrue(row.contains("R.string.update_channel_title"))
        assertTrue(row.contains("items = listOf(\"Release\", \"CI\")"))
        assertTrue(row.contains("selectedIndex = state.updateChannel.ordinal"))
        assertTrue(row.contains("interactionEnabled = settingsEditable"))
        assertTrue(row.contains("state.onUpdateChannelChange(UpdateChannel.entries[it])"))
    }

    @Test fun softwareUpdateSettingsHaveAnIndependentLaunchPromptAndReadOnlyBuildType() {
        val preferences = source("ui/settings/AppUpdatePreferences.kt")
        assertTrue(preferences.contains("getBoolean(KEY_UPDATE_NOTIFICATIONS, true)"))
        assertTrue(preferences.contains("getBoolean(KEY_LAUNCH_UPDATE_PROMPT, true)"))
        val page = source("ui/settings/SettingsMainPage.kt").substringAfter("fun SoftwareUpdateAutoSettingsPage(")
            .substringBefore("private fun SoftwareUpdateMenuRow")
        assertTrue(page.contains("SettingsGroup"))
        val promptRow = page.substringAfter("title = stringResource(R.string.setting_launch_update_prompt)")
            .substringBefore("SettingsDivider()")
        assertTrue(promptRow.contains("checked = state.launchUpdatePromptEnabled"))
        assertTrue(promptRow.contains("onCheckedChange = state.onLaunchUpdatePromptEnabledChange"))
        val buildRow = page.substringAfter("title = stringResource(R.string.update_build_type_title)")
            .substringBefore("SettingsDivider()")
        assertTrue(page.contains("SettingsCardRow("))
        assertFalse(buildRow.contains("onClick"))
        assertFalse(buildRow.contains("Dropdown"))
    }

    @Test fun allUpdateEntryPointsReadThePersistedChannel() {
        val main = source("ui/settings/SettingsMainPage.kt")
        assertTrue(main.contains("AppUpdater.checkForUpdate(updateChannel)"))
        assertTrue(main.contains("AppUpdatePreferences.setUpdateChannel(context, channel)"))
        val updater = source("ui/settings/AppUpdater.kt")
        assertTrue(updater.contains("channel: UpdateChannel = AppUpdatePreferences.getUpdateChannel(context)"))
        val install = updater.substringAfter("suspend fun installDownloadedUpdate(")
            .substringBefore("markPendingInstallation(")
        assertTrue(install.contains("update.channel != AppUpdatePreferences.getUpdateChannel(context)"))
        val notification = source("ui/settings/UpdateNotificationScheduler.kt")
        assertTrue(notification.contains("val channel = AppUpdatePreferences.getUpdateChannel(appContext)"))
        assertTrue(notification.contains("AppUpdater.checkForUpdate(channel)"))
        assertTrue(notification.contains("channel != AppUpdatePreferences.getUpdateChannel(appContext)"))
        assertTrue(source("ui/Root.kt").contains("update.channel == softwareUpdateState.updateChannel"))
    }

    @Test fun updateChecksUseOnlyTheSelectedChannel() {
        val updater = source("ui/settings/AppUpdater.kt")
        val check = updater.substringAfter("suspend fun checkForUpdate(").substringBefore("suspend fun findReleaseNotes(")
        assertTrue(check.contains("UpdateChannel.Release -> findLatestStableRelease(buildType)"))
        assertTrue(check.contains("UpdateChannel.Ci -> findLatestCiRelease(buildType)"))
        assertFalse(updater.contains("/actions/runs"))
        assertFalse(updater.contains("ONextBox_BetaTesting"))
    }

    @Test fun allLanguagesHaveUpdateChannelText() {
        val root = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val expected = setOf("update_channel_title", "update_channel_summary")
        for (locale in listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant",
            "values-ja", "values-ja-rCN", "values-ko", "values-vi", "values-ru")) {
            val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File(root, "$locale/strings.xml")).getElementsByTagName("string")
            val names = (0 until strings.length).map { strings.item(it).attributes.getNamedItem("name").nodeValue }
            assertEquals("$locale has duplicates", names.size, names.toSet().size)
            assertEquals(locale, expected, names.filter { it.startsWith("update_channel_") }.toSet())
            assertFalse(locale, names.any { it.startsWith("update_beta_") })
            for (index in 0 until strings.length) {
                if (names[index] in expected) assertTrue("$locale:${names[index]}", strings.item(index).textContent.isNotBlank())
            }
        }
    }

    private fun source(path: String): String = listOf(
        File("src/main/java/com/mi/onextbox/$path"), File("app/src/main/java/com/mi/onextbox/$path"),
    ).first { it.isFile }.readText().replace("\r\n", "\n")
}
