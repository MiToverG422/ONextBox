package com.mi.onextbox.ui.settings

import org.junit.Assert.*
import org.junit.Test

class AppUpdateRulesTest {
    @Test fun newVersionSeriesUpgradesBothLegacyReleaseAndLegacyBeta() {
        assertTrue(AppUpdateRules.isNewerVersion("17.0.3.94", "17.0.2.310"))
        assertTrue(AppUpdateRules.isNewerVersion("17.0.3.94", "17.0.2.310_Beta10"))
        assertTrue(AppUpdateRules.isNewerVersion("17.0.3.94-CI-66624d2b", "17.0.2.310"))
    }

    @Test fun installedBuildTypeSelectsTheMatchingPackageIncludingOptimizedDebug() {
        assertEquals(UpdateBuildType.Release, AppUpdateRules.buildType("release"))
        assertEquals(UpdateBuildType.Debug, AppUpdateRules.buildType("debug"))
        assertEquals(UpdateBuildType.Debug, AppUpdateRules.buildType("debugSlim"))
        assertEquals(UpdateBuildType.Debug, AppUpdateRules.buildType("DEBUGSLIM"))
    }

    @Test fun releaseAndDebugAssetsNeverCrossBuildTypes() {
        val release = "onextbox-17.0.3.94-CI-66624d2b-EX01-release.apk"
        val debug = "onextbox-17.0.3.94-CI-66624d2b-EX01-debug.apk"
        assertTrue(AppUpdateRules.matchesApk(release, UpdateBuildType.Release))
        assertFalse(AppUpdateRules.matchesApk(release, UpdateBuildType.Debug))
        assertTrue(AppUpdateRules.matchesApk(debug, UpdateBuildType.Debug))
        assertFalse(AppUpdateRules.matchesApk(debug, UpdateBuildType.Release))
        assertTrue(AppUpdateRules.matchesApk(debug.uppercase(), UpdateBuildType.Debug))
        assertFalse(AppUpdateRules.matchesApk("debug.zip", UpdateBuildType.Debug))
        assertFalse(AppUpdateRules.matchesApk("SHA256SUMS", UpdateBuildType.Release))
    }

    @Test fun releaseChannelAcceptsOnlyFormalReleases() {
        assertTrue(AppUpdateRules.acceptsRelease("V17.0.3.94", false, false, UpdateChannel.Release))
        for (tag in listOf("ci-latest", "17.0.3.94-CI-66624d2b", "17.0.3.94_Beta")) {
            assertFalse(AppUpdateRules.acceptsRelease(tag, false, false, UpdateChannel.Release))
        }
        assertFalse(AppUpdateRules.acceptsRelease("v17.0.3.94", false, true, UpdateChannel.Release))
        assertFalse(AppUpdateRules.acceptsRelease("v17.0.3.94", true, false, UpdateChannel.Release))
        assertFalse(AppUpdateRules.acceptsRelease("v17.0.3.94", false, false, UpdateChannel.Release,
            releaseName = "17.0.3.94_Beta"))
        assertFalse(AppUpdateRules.acceptsRelease("v17.0.3.94", false, false, UpdateChannel.Release,
            assetName = "onextbox-17.0.3.94-CI-66624d2b-release.apk"))
    }

    @Test fun ciChannelAcceptsOnlyItsRollingRelease() {
        assertTrue(AppUpdateRules.acceptsRelease("ci-latest", false, true, UpdateChannel.Ci))
        assertFalse(AppUpdateRules.acceptsRelease("ci-latest", true, true, UpdateChannel.Ci))
        assertFalse(AppUpdateRules.acceptsRelease("v17.0.3.94", false, false, UpdateChannel.Ci))
        assertFalse(AppUpdateRules.acceptsRelease("17.0.3.94_Beta", false, true, UpdateChannel.Ci))
    }

    @Test fun ciVersionComesFromItsApkAndMustMatchThePublishedRelease() {
        val name = "17.0.3.94-CI-66624d2b"
        val title = "ONextBox CI $name"
        val asset = "onextbox-$name-EX01-release.apk"
        assertEquals(name, AppUpdateRules.releaseVersionName("ci-latest", title, asset))
        assertNull(AppUpdateRules.releaseVersionName("ci-latest", title, "onextbox-17.0.3.93-CI-fff-debug.apk"))
        assertNull(AppUpdateRules.releaseVersionName("ci-latest", title, "onextbox-17.0.3.94-release.apk"))
        assertEquals("17.0.3.94", AppUpdateRules.releaseVersionName("V17.0.3.94", "", "onextbox-release.apk"))
    }

    @Test fun fourthSegmentUsesNumericCommitCount() {
        assertTrue(AppUpdateRules.isNewerVersion("17.0.3.1000", "17.0.3.999"))
        assertTrue(AppUpdateRules.isNewerVersion("17.0.3.100-CI-abcd1234", "17.0.3.94-CI-66624d2b"))
        assertFalse(AppUpdateRules.isNewerVersion("17.0.3.94", "17.0.3.100"))
        assertTrue(AppUpdateRules.isNewerVersion("17.0.4.1000", "17.0.3.9999"))
    }

    @Test fun knownAndroidVersionCodePreventsDowngradesAndAllowsSameCommitRelease() {
        assertTrue(AppUpdateRules.isNewerInstallableVersion("17.0.3.94", "17.0.3.94-CI-66624d2b", 10094, 10094))
        assertFalse(AppUpdateRules.isNewerInstallableVersion("17.0.3.94-CI-66624d2b", "17.0.3.94", 10094, 10094))
        assertFalse(AppUpdateRules.isNewerInstallableVersion("17.0.3.95", "17.0.3.94", 10093, 10094))
        assertTrue(AppUpdateRules.isNewerInstallableVersion("17.0.3.94", "17.0.2.310", 10094, 1))
        assertTrue(AppUpdateRules.isNewerInstallableVersion("17.0.3.94", "17.0.2.310", null, 1))
    }

    @Test fun versionCodeMetadataStaysOutOfTheDisplayedReleaseNotes() {
        val body = "Latest build\n\n<!-- onextbox-version-code: 10094 -->"
        assertEquals(10094L, AppUpdateRules.releaseVersionCode(body))
        assertEquals("Latest build", AppUpdateRules.releaseNotesText(body))
        assertNull(AppUpdateRules.releaseVersionCode("Latest build"))
    }

    @Test fun stableVersionOutranksEveryBetaOfTheSameBaseVersion() {
        assertTrue(AppUpdateRules.isNewerVersion("17.0", "17.0-BETA99"))
        assertFalse(AppUpdateRules.isNewerVersion("17.0-BETA99", "17.0"))
        assertTrue(AppUpdateRules.compareVersions("17.0", "17.0-BETA99") > 0)
    }

    @Test fun betaVersionNumbersAreComparedNumerically() {
        assertTrue(AppUpdateRules.isNewerVersion("17.0-BETA10", "17.0-BETA9"))
        assertFalse(AppUpdateRules.isNewerVersion("17.0-BETA9", "17.0-BETA10"))
        assertTrue(AppUpdateRules.isNewerVersion("17.0-beta.2", "17.0-beta.1"))
        assertTrue(AppUpdateRules.isNewerVersion("17.0-beta-2", "17.0-beta-1"))
        assertEquals(0, AppUpdateRules.compareVersions("v17.0-BETA1", "17.0-beta.1"))
    }

    @Test fun latestMatchingReleaseIsIndependentOfApiOrder() {
        val versions = listOf("17.0.3.94", "17.0.3.1000", "17.0.3.999", "17.0.2.310")
        val comparator = Comparator<String>(AppUpdateRules::compareVersions)
        assertEquals("17.0.3.1000", versions.maxWithOrNull(comparator))
        assertEquals("17.0.3.1000", versions.reversed().maxWithOrNull(comparator))
        assertNull(emptyList<String>().maxWithOrNull(comparator))
    }

    @Test fun ciHashChangesRetainTheExistingLatestCiUpdateBehavior() {
        assertTrue(AppUpdateRules.isNewerVersion("17.0-CI-abcdef12", "17.0-CI-fedcba21"))
        assertFalse(AppUpdateRules.isNewerVersion("17.0-CI-abcdef12", "17.0-CI-abcdef12"))
        assertFalse(AppUpdateRules.isNewerVersion("17.0-CI-abcdef12", "17.0-BETA1"))
    }

    @Test fun malformedOrOversizedVersionsDoNotBecomeUpdates() {
        assertFalse(AppUpdateRules.isNewerVersion("", "17.0"))
        assertFalse(AppUpdateRules.isNewerVersion("not-a-version", "17.0"))
        assertFalse(AppUpdateRules.isNewerVersion("17.999999999999999999", "17.0"))
        assertFalse(AppUpdateRules.isNewerVersion("17.0", "17.0.0"))
    }
}
