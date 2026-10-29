package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class AppMarketCleanupRulesTest {
    @Test fun splitPreferencesInheritLegacyOnlyUntilExplicitlySet() {
        assertTrue(AppMarketCleanupRules.enabled(null, true))
        assertFalse(AppMarketCleanupRules.enabled(null, false))
        assertFalse(AppMarketCleanupRules.enabled(false, true))
        assertTrue(AppMarketCleanupRules.enabled(true, false))
        val legacy = true
        assertFalse(AppMarketCleanupRules.enabled(false, legacy))
        assertTrue(AppMarketCleanupRules.enabled(null, legacy))
    }

    @Test fun sharedUpgradeBaseDoesNotMisclassifyDownloadsOrIgnoredApps() {
        val shared = "com.heytap.market.appmanage.core.upgrade.upgrademgr.BaseRecyclerViewDownloadFragment"
        assertEquals(AppMarketCleanupRules.Page.DOWNLOAD, AppMarketCleanupRules.page(sequenceOf(
            "com.heytap.market.appmanage.core.download.downloadmgr.DownloadManageFragment", shared,
        )))
        assertEquals(AppMarketCleanupRules.Page.UPDATE, AppMarketCleanupRules.page(sequenceOf(
            "com.heytap.market.appmanage.core.upgrade.upgrademgr.recyclerview.AppUpdateFragment", shared,
        )))
        assertNull(AppMarketCleanupRules.page(sequenceOf(shared)))
        assertNull(AppMarketCleanupRules.page(sequenceOf("com.heytap.market.appmanage.core.upgrade.upgrademgr.ignore.UpgradeIgnoreAdapter")))
        assertNull(AppMarketCleanupRules.page(sequenceOf("other.AppUpdateFragment")))
    }
}
