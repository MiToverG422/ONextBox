package com.mi.onextbox.ui.settings

import org.junit.Assert.*
import org.junit.Test

class ReleaseNotesLookupTest {
    private fun release(tag: String, name: String = "", assets: List<String> = emptyList(), draft: Boolean = false) =
        ReleaseNotesEntry(tag, name, assets, "Changes", draft)

    @Test fun stableVersionAlwaysUsesMainRepositoryAndNeedsNoApk() {
        val found = ReleaseNotesLookup.find("17.0.0.57", { repo, _ ->
            assertEquals(AppUpdateRules.MAIN_REPOSITORY, repo)
            release("v17.0.0.57")
        }, { _, _ -> error("Unexpected fallback") })
        assertEquals("Changes", found?.body)
    }

    @Test fun ciVersionLooksUpTheRollingReleaseInTheMainRepository() {
        val version = "17.0.3.94-CI-66624d2b"
        val entry = release("ci-latest", "ONextBox CI $version", listOf("onextbox-$version-EX01-release.apk"))
        val found = ReleaseNotesLookup.find(version, { repo, tag ->
            assertEquals(AppUpdateRules.MAIN_REPOSITORY, repo)
            assertEquals("ci-latest", tag)
            entry
        }, { _, _ -> error("Unexpected fallback") })
        assertEquals(entry, found)
        assertFalse(entry.matches("17.0.3.94"))
        assertFalse(entry.matches("17.0.3.93-CI-abcdef12"))
        assertFalse(entry.copy(draft = true).matches(version))
        assertFalse(entry.copy(name = "ONextBox CI 17.0.3.95-CI-abcdef12").matches(version))
    }

    @Test fun missingExactStableTagFallsBackToTitleOrAsset() {
        val version = "17.0.3.94"
        for (entry in listOf(release("release-build", "ONextBox $version"),
            release("release-build", assets = listOf("ONextBox-$version-debug.apk")))) {
            val found = ReleaseNotesLookup.find(version, { _, _ -> null }, { repo, page ->
                assertEquals(AppUpdateRules.MAIN_REPOSITORY, repo)
                assertEquals(1, page)
                listOf(entry)
            })
            assertEquals(entry, found)
        }
    }

    @Test fun wrongVersionDraftOrDifferentChannelCannotMatch() {
        assertFalse(release("V17.0.3.95").matches("17.0.3.94"))
        assertFalse(release("V17.0.3.94", draft = true).matches("17.0.3.94"))
        assertFalse(release("17.0.0.58_Beta").matches("17.0.0.58"))
        assertFalse(release("17.0.0.58").matches("17.0.0.58_Beta"))
    }

    @Test fun fallbackContinuesAcrossFullPagesButStopsAtShortPage() {
        val pages = mutableListOf<Int>()
        val result = ReleaseNotesLookup.find("17.0.3.94", { _, _ -> null }, { _, page ->
            pages += page
            if (page == 1) List(100) { release("17.0.3.95") }
            else listOf(release("17.0.3.94"))
        })
        assertNotNull(result)
        assertEquals(listOf(1, 2), pages)
        assertNull(ReleaseNotesLookup.find("17.0.3.94", { _, _ -> null }, { _, page ->
            assertEquals(1, page)
            emptyList()
        }))
    }

    @Test fun cancellationIsNotTreatedAsMissingRelease() {
        val cancellation = java.util.concurrent.CancellationException()
        try {
            ReleaseNotesLookup.find("17.0.0.58", { _, _ -> throw cancellation }, { _, _ -> emptyList() })
            fail("Cancellation was swallowed")
        } catch (error: java.util.concurrent.CancellationException) {
            assertSame(cancellation, error)
        }
    }
}
