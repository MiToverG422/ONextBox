package com.mi.onextbox.diagnostics

import org.junit.Assert.*
import org.junit.Test

class FeatureLoadStateTest {
    @Test fun savedConfigurationWithoutEvidenceDoesNotClaimSuccess() {
        assertEquals(FeatureLoadState.Saved, featureLoadState(true, true, null))
        assertEquals(FeatureLoadState.Unsupported, featureLoadState(true, false, null))
        assertEquals(FeatureLoadState.Disabled, featureLoadState(false, true, null))
    }

    @Test fun distinguishesReloadFailureAndTargetMissFromLoadedTargets() {
        assertEquals(FeatureLoadState.WaitingReload, featureLoadState(true, true, FeatureLoadReceipt(false, 0, 0, false)))
        assertEquals(FeatureLoadState.WaitingReload, featureLoadState(true, true, FeatureLoadReceipt(true, 0, 1, false)))
        assertEquals(FeatureLoadState.Failed, featureLoadState(true, true, FeatureLoadReceipt(true, 0, 0, true)))
        assertEquals(FeatureLoadState.Unmatched, featureLoadState(true, true, FeatureLoadReceipt(true, 0, 0, false)))
        assertEquals(FeatureLoadState.Loaded, featureLoadState(true, true, FeatureLoadReceipt(true, 1, 0, false)))
    }

    @Test fun malformedReceiptsCannotBeUsedAsSuccessEvidence() {
        assertNull(parseFeatureReceipt("installed=7"))
        assertNull(parseFeatureReceipt("enabled=true;installed=-1;staged=0;failed=false"))
        assertNull(parseFeatureReceipt("enabled=maybe;installed=1;staged=0;failed=false"))
        assertEquals(FeatureLoadReceipt(true, 3, 0, false), parseFeatureReceipt("enabled=true;installed=3;staged=0;failed=false"))
    }

    private fun staged() = DiagnosticEvent(10, 3, "host", 1, "feature_init", "feature",
        "enabled=true;installed=0;staged=2;failed=false", 0)
    private fun committed() = DiagnosticEvent(11, 3, "host", 1, "committed", "Hot reload", "", 0)

    @Test fun committedStagingBecomesLoadedWithMatchingProcessAndGeneration() {
        val staged = staged()
        val settled = settledFeatureReceipt(staged, listOf(staged, committed()))
        assertEquals(FeatureLoadReceipt(true, 2, 0, false), settled)
        assertEquals(FeatureLoadState.Loaded, featureLoadState(true, true, settled))
    }

    @Test fun otherProcessesGenerationsAndEarlierCommitsCannotConfirmStaging() {
        val staged = staged()
        for (commit in listOf(committed().copy(process = "other"), committed().copy(generation = 2), committed().copy(time = 9))) {
            assertEquals(2, settledFeatureReceipt(staged, listOf(staged, commit))!!.staged)
        }
        assertEquals(2, settledFeatureReceipt(staged, listOf(committed().copy(time = staged.time), staged))!!.staged)
    }

    @Test fun failedInitializationCannotBecomeSuccessfulAfterACommit() {
        val staged = staged().copy(detail = "enabled=true;installed=0;staged=2;failed=true")
        assertEquals(FeatureLoadState.Failed, featureLoadState(true, true, settledFeatureReceipt(staged, listOf(staged, committed()))))
    }

    @Test fun liveEnablingDoesNotRequireReloadWhenInstalledTargetsReceivedTheNewConfiguration() {
        val installedWhileDisabled = FeatureLoadReceipt(false, 1, 0, false)
        assertEquals(FeatureLoadState.WaitingReload, featureLoadState(true, true, installedWhileDisabled))
        assertEquals(FeatureLoadState.Loaded, featureLoadState(true, true, installedWhileDisabled, configurationCurrent = true))
        assertEquals(FeatureLoadState.WaitingReload, featureLoadState(true, true,
            installedWhileDisabled.copy(installed = 0), configurationCurrent = true))
    }
}
