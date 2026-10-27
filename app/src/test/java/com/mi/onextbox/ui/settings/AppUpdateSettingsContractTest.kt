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

    @Test fun betaEnrollmentIsDefaultOffAndThereIsNoManualChannelPreference() {
        val preference = source("ui/settings/AppUpdatePreferences.kt")
        assertTrue(preference.contains("getBoolean(KEY_BETA_PLAN, false)"))
        val setter = preference.substringAfter("fun setBetaPlanEnabled").substringBefore("fun getUpdateNotificationsEnabled")
        assertTrue(setter.contains("putBoolean(KEY_BETA_PLAN, enabled)"))
        assertFalse(preference.contains("KEY_UPDATE_CHANNEL"))
        assertFalse(preference.contains("update_channel"))
        assertFalse(preference.contains("enum class UpdateChannel"))
    }

    @Test fun openingOrDismissingTheC17DialogNeverEnrollsWithoutConfirmation() {
        val page = source("ui/settings/SettingsMainPage.kt").substringAfter("fun SoftwareUpdateAutoSettingsPage(")
            .substringBefore("private fun SoftwareUpdateMenuRow")
        assertTrue(page.contains("if (enabled) showBetaConfirmation = true else state.onBetaPlanEnabledChange(false)"))
        assertTrue(page.contains("ColorOs17ConfirmDialog("))
        assertTrue(page.contains("onDismissRequest = { showBetaConfirmation = false }"))
        assertTrue(page.contains("onNegative = { showBetaConfirmation = false }"))
        assertTrue(page.substringAfter("onPositive = {").contains("state.onBetaPlanEnabledChange(true)"))
        assertEquals(1, Regex("state.onBetaPlanEnabledChange\\(true\\)").findAll(page).count())
    }

    @Test fun bothUiStylesUseFourJoinedRowsWithReadOnlyBuildTypeAndNoChannelSelector() {
        val page = source("ui/settings/SettingsMainPage.kt").substringAfter("fun SoftwareUpdateAutoSettingsPage(")
            .substringBefore("private fun SoftwareUpdateMenuRow")
        assertTrue(page.contains("SettingsGroup"))
        val buildRow = page.substringAfter("title = stringResource(R.string.about_update_build_type_title)")
            .substringBefore("SettingsDivider()")
        assertTrue(page.contains("SettingsCardRow("))
        assertFalse(buildRow.contains("onClick"))
        assertFalse(buildRow.contains("Dropdown"))
        assertFalse(page.contains("SettingsUpdateChannelDropdown"))
        assertFalse(page.contains("about_update_channel_title"))
        assertFalse(page.contains("selectedChannel"))
        assertFalse(page.contains("count = 5"))
    }

    @Test fun everyUpdateEntryPointUsesTheSameBetaPlanPreference() {
        val main = source("ui/settings/SettingsMainPage.kt")
        assertTrue(main.contains("AppUpdater.checkForUpdate(betaPlanEnabled)"))
        assertTrue(main.contains("AppUpdatePreferences.setBetaPlanEnabled(context, enabled)"))
        val updater = source("ui/settings/AppUpdater.kt")
        assertTrue(updater.contains("includeBeta = AppUpdatePreferences.getBetaPlanEnabled(appContext)"))
        assertTrue(updater.contains("if (update.isBeta && !AppUpdatePreferences.getBetaPlanEnabled(context))"))
        val install = updater.substringAfter("suspend fun installDownloadedUpdate(")
            .substringBefore("fun cleanupInstalledUpdate(")
        assertTrue(install.substringBefore("markPendingInstallation(")
            .contains("if (update.isBeta && !AppUpdatePreferences.getBetaPlanEnabled(context))"))
        val notification = source("ui/settings/UpdateNotificationScheduler.kt")
        assertTrue(notification.contains("val includeBeta = AppUpdatePreferences.getBetaPlanEnabled(appContext)"))
        assertTrue(notification.contains("includeBeta != AppUpdatePreferences.getBetaPlanEnabled(appContext)"))
    }

    @Test fun betaChecksUseOnlyBetaReleasesAndNoMainRepositoryFallback() {
        val updater = source("ui/settings/AppUpdater.kt")
        val check = updater.substringAfter("suspend fun checkForUpdate(").substringBefore("suspend fun findRelease(")
        assertTrue(check.contains("includeBeta: Boolean = false"))
        assertTrue(check.contains("findLatestRelease(buildType, includeBeta)"))
        assertFalse(check.contains("channel"))
        assertFalse(updater.contains("findLatestCi"))
        assertFalse(updater.contains("/actions/runs"))
        val release = updater.substringAfter("private fun findLatestRelease(").substringBefore("private fun findLatestReleaseAsset(")
        assertTrue(release.contains("AppUpdateRules.repository(includeBeta)"))
        assertTrue(release.contains("releaseUpdate(release, buildType, includeBeta)"))
        assertTrue(release.contains("candidates.maxWithOrNull(compareByUpdateVersion)"))
    }

    @Test fun allUpdateEntryPointsIgnoreTheRemovedCiPreference() {
        for (path in listOf("AppUpdatePreferences.kt", "SettingsMainPage.kt", "AppUpdater.kt", "UpdateNotificationScheduler.kt")) {
            val text = source("ui/settings/$path")
            assertFalse(path, text.contains("UpdateChannel"))
            assertFalse(path, text.contains("selectedChannel"))
            assertFalse(path, text.contains("update_channel"))
        }
        val updater = source("ui/settings/AppUpdater.kt")
        assertFalse(updater.contains("/actions/"))
        assertFalse(updater.contains("/releases/tags/ci-latest"))
        assertTrue(updater.contains("val update = findLatestRelease(buildType, includeBeta)"))
        assertEquals(AppUpdateRules.MAIN_REPOSITORY, AppUpdateRules.repository(false))
        assertEquals(AppUpdateRules.BETA_REPOSITORY, AppUpdateRules.repository(true))
    }

    @Test fun betaUpdatesUseMarkerAwareExtractionAndStrictNumericVersionComparison() {
        val updater = source("ui/settings/AppUpdater.kt")
        assertTrue(updater.contains("val versionName = AppUpdateRules.releaseVersionName("))
        assertTrue(updater.contains("requireBeta = fromBetaRepository"))
        val matching = updater.substringAfter("private fun findLatestRelease(").substringBefore("private fun findLatestReleaseAsset(")
        assertTrue(matching.contains("release.optString(\"name\")"))
        assertTrue(matching.contains("update.fileName"))
        val compare = updater.substringAfter("private fun isNewerUpdate(").substringBefore("private fun isNewerVersion(")
        assertTrue(compare.substringBefore("update.versionCode")
            .contains("if (update.isBeta) return AppUpdateRules.isNewerBetaVersion(update.versionName, BuildConfig.VERSION_NAME)"))
    }

    @Test fun allLanguagesKeepTheSixBetaPlanStringsInTheirMainStringsFile() {
        val root = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val expected = setOf("update_beta_plan_title", "update_beta_plan_summary", "update_beta_join_title",
            "update_beta_join_summary", "update_beta_join_cancel", "update_beta_join_confirm")
        for (locale in listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant",
            "values-ja", "values-ja-rCN", "values-ko", "values-vi", "values-ru")) {
            val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File(root, "$locale/strings.xml")).getElementsByTagName("string")
            val names = (0 until strings.length).map { strings.item(it).attributes.getNamedItem("name").nodeValue }
            assertEquals("$locale has duplicates", names.size, names.toSet().size)
            assertEquals(locale, expected, names.filter { it.startsWith("update_beta_") }.toSet())
            assertFalse(locale, names.contains("about_update_channel_title"))
            assertFalse(locale, names.any { it.startsWith("update_channel_") })
            for (index in 0 until strings.length) {
                if (names[index] in expected) assertTrue("$locale:${names[index]}", strings.item(index).textContent.isNotBlank())
            }
        }
    }

    private fun source(path: String): String = listOf(
        File("src/main/java/com/mi/onextbox/$path"), File("app/src/main/java/com/mi/onextbox/$path"),
    ).first { it.isFile }.readText().replace("\r\n", "\n")
}
