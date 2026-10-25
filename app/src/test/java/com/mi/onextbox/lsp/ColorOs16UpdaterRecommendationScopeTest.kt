package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorOs16UpdaterRecommendationScopeTest {
    @Test fun retainsOriginalCallbackAndOnlyOverridesItsRecommendationReads() {
        val scope = ColorOs16UpdaterRecommendationScope()
        var dismissals = 0
        var choiceRecords = 0
        scope.enter {
            dismissals++
            choiceRecords++
            assertEquals(1, scope.replacement("auto_download_network_type", enabled = true))
            assertEquals(1, scope.replacement("can_update_at_night", enabled = true))
            assertNull(scope.replacement("development_settings_enabled", enabled = true))
            assertNull(scope.replacement("auto_download_network_type", enabled = false))
        }
        assertEquals(1, dismissals)
        assertEquals(1, choiceRecords)
        assertNull(scope.replacement("auto_download_network_type", enabled = true))
    }

    @Test fun restoresOuterScopeAndClearsAfterAnException() {
        val scope = ColorOs16UpdaterRecommendationScope()
        scope.enter {
            runCatching { scope.enter<Unit> { error("host callback failed") } }
            assertEquals(1, scope.replacement("can_update_at_night", enabled = true))
        }
        runCatching { scope.enter<Unit> { error("host callback failed") } }
        assertNull(scope.replacement("can_update_at_night", enabled = true))
    }

    @Test fun doesNotAffectOtherThreadsWhileCallbackRuns() {
        val scope = ColorOs16UpdaterRecommendationScope()
        var replacement: Int? = 99
        scope.enter {
            Thread { replacement = scope.replacement("auto_download_network_type", enabled = true) }
                .apply { start(); join() }
        }
        assertNull(replacement)
    }
}
