package com.mi.onextbox.ui.onboarding

import android.content.SharedPreferences
import com.mi.onextbox.ui.onboarding.OnboardingPreferences.Agreement
import com.mi.onextbox.ui.onboarding.OnboardingPreferences.CompletionResult
import com.mi.onextbox.ui.onboarding.OnboardingPreferences.DraftStep
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class OnboardingPreferencesTest {
    private val allMask = Agreement.entries.fold(0L) { mask, entry -> mask or entry.persistedBit }

    private fun current(mask: Long = 0L, step: DraftStep = DraftStep.Agreements) = mapOf<String, Any>(
        "schema_version" to OnboardingPreferences.CURRENT_SCHEMA_VERSION,
        "policy_version" to OnboardingPreferences.CURRENT_POLICY_VERSION,
        "accepted_agreements" to mask,
        "draft_step" to step.persistedValue,
        "completed" to false,
    )

    @Test fun freshInstallCannotStartActivatedOrGrantBackgroundNetworkConsent() {
        val store = MemoryPreferences()
        val snapshot = OnboardingPreferences.read(store.preferences)
        assertEquals(DraftStep.Welcome, snapshot.draftStep)
        assertFalse(snapshot.completed)
        assertFalse(snapshot.allowBackgroundUpdateChecks)
        assertTrue(snapshot.acceptedAgreements.isEmpty())
        assertEquals(0, store.commits)
    }

    @Test fun freshInstallCanCompleteAfterEveryRequiredAgreementIsExplicitlySaved() {
        val store = MemoryPreferences()
        assertTrue(OnboardingPreferences.saveDraftStep(store.preferences, DraftStep.Language))
        for ((index, agreement) in Agreement.entries.withIndex()) {
            assertTrue(OnboardingPreferences.saveDraftAgreementIndex(store.preferences, index))
            assertTrue(OnboardingPreferences.setAgreementAccepted(store.preferences, agreement, true))
            assertEquals(Agreement.entries.take(index + 1).toSet(), OnboardingPreferences.read(store.preferences).acceptedAgreements)
        }
        assertTrue(OnboardingPreferences.saveDraftStep(store.preferences, DraftStep.Complete))
        assertEquals(CompletionResult.Completed, OnboardingPreferences.completeWithResult(store.preferences, false, 1234L))
        val saved = OnboardingPreferences.read(store.preferences)
        assertTrue(saved.completed)
        assertEquals(1234L, saved.completedAtMillis)
        assertFalse(saved.allowBackgroundUpdateChecks)
    }

    @Test fun everyMissingAgreementBlocksActivationWithoutAttemptingAWrite() {
        for (missing in Agreement.entries) {
            val store = MemoryPreferences(current(allMask and missing.persistedBit.inv()))
            assertEquals(CompletionResult.AgreementsRequired, OnboardingPreferences.completeWithResult(store.preferences, true, 42L))
            assertFalse(OnboardingPreferences.read(store.preferences).completed)
            assertEquals(0, store.commits)
        }
    }

    @Test fun backgroundNetworkConsentIsOnlyStoredWithSuccessfulActivation() {
        val store = MemoryPreferences(current(allMask) + ("future_metadata" to "keep"))
        assertEquals(CompletionResult.Completed, OnboardingPreferences.completeWithResult(store.preferences, true, 42L))
        assertTrue(OnboardingPreferences.read(store.preferences).allowBackgroundUpdateChecks)
        assertEquals("keep", store.values["future_metadata"])
    }

    @Test fun missingConsentInPostAgreementDraftsResumesAtFirstUnacceptedAgreement() {
        val accepted = allMask and Agreement.Privacy.persistedBit.inv()
        for (step in listOf(DraftStep.RootAccess, DraftStep.Lsposed, DraftStep.Complete)) {
            val store = MemoryPreferences(current(accepted, step) + ("draft_agreement_index" to 5))
            assertEquals(DraftStep.Agreements, OnboardingPreferences.read(store.preferences).draftStep)
            assertEquals(1, OnboardingPreferences.readDraftAgreementIndex(store.preferences))
            assertEquals(0, store.commits)
        }
    }

    @Test fun removedPersonalizationDraftResumesAtMigration() {
        val store = MemoryPreferences(current() + ("draft_step" to "personalization"))
        val snapshot = OnboardingPreferences.read(store.preferences)
        assertEquals(DraftStep.Migration, snapshot.draftStep)
        assertFalse(snapshot.completed)
        assertTrue(snapshot.acceptedAgreements.isEmpty())
    }

    @Test fun validPreAgreementDraftsAndCompletedUsersAreNotReset() {
        val language = MemoryPreferences(current(0L, DraftStep.Language))
        assertEquals(DraftStep.Language, OnboardingPreferences.read(language.preferences).draftStep)
        val activated = MemoryPreferences(current(allMask, DraftStep.Complete) +
            mapOf("completed" to true, "completed_at_millis" to 77L))
        val snapshot = OnboardingPreferences.read(activated.preferences)
        assertTrue(snapshot.completed)
        assertEquals(DraftStep.Complete, snapshot.draftStep)
        assertEquals(77L, snapshot.completedAtMillis)
        assertEquals(0, activated.commits)
    }

    @Test fun stalePolicyIsDistinctFromAWriteFailureAndDoesNotReactivateOldConsent() {
        val store = MemoryPreferences(current(allMask, DraftStep.Complete) +
            mapOf("policy_version" to 0, "completed" to true))
        assertEquals(CompletionResult.PolicyChanged, OnboardingPreferences.completeWithResult(store.preferences, true, 42L))
        assertEquals(0, store.commits)
        assertEquals(DraftStep.Agreements, OnboardingPreferences.read(store.preferences).draftStep)
        assertTrue(OnboardingPreferences.saveDraftAgreementIndex(store.preferences, 0))
        val snapshot = OnboardingPreferences.read(store.preferences)
        assertTrue(snapshot.acceptedAgreements.isEmpty())
        assertFalse(snapshot.completed)
    }

    @Test fun changedSchemaRestartsTheWelcomeFlowAndClearsStaleConsentOnDraftWrite() {
        val store = MemoryPreferences(current(allMask, DraftStep.Complete) +
            mapOf("schema_version" to 0, "completed" to true))
        assertEquals(DraftStep.Welcome, OnboardingPreferences.read(store.preferences).draftStep)
        assertEquals(CompletionResult.PolicyChanged, OnboardingPreferences.completionBlockReason(store.preferences))
        assertTrue(OnboardingPreferences.saveDraftStep(store.preferences, DraftStep.Language))
        assertTrue(OnboardingPreferences.read(store.preferences).acceptedAgreements.isEmpty())
        assertFalse(OnboardingPreferences.read(store.preferences).completed)
    }

    @Test fun acceptingOneNewPolicyCannotRestoreTheOldCompletedMarker() {
        val store = MemoryPreferences(current(allMask) + mapOf("policy_version" to 0, "completed" to true))
        assertTrue(OnboardingPreferences.setAgreementAccepted(store.preferences, Agreement.Privacy, true))
        val snapshot = OnboardingPreferences.read(store.preferences)
        assertEquals(setOf(Agreement.Privacy), snapshot.acceptedAgreements)
        assertFalse(snapshot.completed)
    }

    @Test fun revokingOneAgreementInvalidatesActivationButPreservesOtherChoices() {
        val store = MemoryPreferences(current(allMask) + mapOf("completed" to true, "completed_at_millis" to 77L))
        assertTrue(OnboardingPreferences.setAgreementAccepted(store.preferences, Agreement.Privacy, false))
        val snapshot = OnboardingPreferences.read(store.preferences)
        assertFalse(snapshot.completed)
        assertEquals(0L, snapshot.completedAtMillis)
        assertEquals(Agreement.entries.toSet() - Agreement.Privacy, snapshot.acceptedAgreements)
    }

    @Test fun draftAgreementPageAndIndexArePersistedInOneCommit() {
        val store = MemoryPreferences(current(allMask, DraftStep.Complete))
        assertTrue(OnboardingPreferences.saveDraftAgreementIndex(store.preferences, 2))
        assertEquals(DraftStep.Agreements.persistedValue, store.values["draft_step"])
        assertEquals(2, store.values["draft_agreement_index"])
        assertEquals(allMask, store.values["accepted_agreements"])
        assertEquals(1, store.commits)
    }

    @Test fun draftIndicesAreBoundedAndAnInvalidFinalDraftCannotSkipMissingConsent() {
        val store = MemoryPreferences(current(allMask) + ("draft_agreement_index" to 999))
        assertEquals(Agreement.entries.lastIndex, OnboardingPreferences.readDraftAgreementIndex(store.preferences))
        assertTrue(OnboardingPreferences.saveDraftAgreementIndex(store.preferences, -99))
        assertEquals(0, store.values["draft_agreement_index"])
        val invalid = MemoryPreferences(current(0L, DraftStep.Complete) + ("draft_agreement_index" to 999))
        assertEquals(0, OnboardingPreferences.readDraftAgreementIndex(invalid.preferences))
    }

    @Test fun failedCommitCannotLeaveAnActivatedInMemorySnapshot() {
        val initial = current(allMask) + mapOf("draft_step" to DraftStep.Lsposed.persistedValue, "future_metadata" to "keep")
        val store = MemoryPreferences(initial, failCommit = 1)
        assertEquals(CompletionResult.WriteFailed, OnboardingPreferences.completeWithResult(store.preferences, true, 42L))
        assertEquals(initial, store.values)
        assertFalse(OnboardingPreferences.read(store.preferences).completed)
        assertFalse(OnboardingPreferences.read(store.preferences).allowBackgroundUpdateChecks)
    }

    @Test fun commitExceptionIsReportedAsStorageFailureAndRestoresPreviousActivationFields() {
        val initial = current(allMask)
        val store = MemoryPreferences(initial, throwOnCommit = 1)
        assertEquals(CompletionResult.WriteFailed, OnboardingPreferences.completeWithResult(store.preferences, true, 42L))
        assertEquals(initial, store.values)
    }

    @Test fun unavailableOrMalformedStorageIsNotReportedAsMissingAgreementConsent() {
        assertEquals(CompletionResult.StorageUnavailable, OnboardingPreferences.completeWithResult(null, true, 42L))
        val malformed = MemoryPreferences(mapOf("schema_version" to "bad"))
        assertEquals(CompletionResult.StorageUnavailable, OnboardingPreferences.completeWithResult(malformed.preferences, true, 42L))
        assertFalse(OnboardingPreferences.read(malformed.preferences).completed)
        assertEquals(0, malformed.commits)
    }

    @Test fun failedRecommitPreservesPreviouslyValidActivationAndUpdateChoice() {
        val initial = current(allMask, DraftStep.Complete) + mapOf("completed" to true,
            "completed_at_millis" to 77L, "allow_background_update_checks" to false)
        val store = MemoryPreferences(initial, failCommit = 1)
        assertEquals(CompletionResult.WriteFailed, OnboardingPreferences.completeWithResult(store.preferences, true, 42L))
        assertEquals(initial, store.values)
        assertTrue(OnboardingPreferences.read(store.preferences).completed)
        assertFalse(OnboardingPreferences.read(store.preferences).allowBackgroundUpdateChecks)
    }

    @Test fun unifiedConfirmationSavesAllSixInOneCommitWithoutCompletingActivation() {
        val store = MemoryPreferences()
        assertTrue(OnboardingPreferences.setAllAgreementsAccepted(store.preferences, true))
        val snapshot = OnboardingPreferences.read(store.preferences)
        assertEquals(Agreement.entries.toSet(), snapshot.acceptedAgreements)
        assertFalse(snapshot.completed)
        assertFalse(snapshot.allowBackgroundUpdateChecks)
        assertEquals(1, store.commits)
        assertEquals(CompletionResult.Completed, OnboardingPreferences.completeWithResult(store.preferences, false, 42L))
    }

    @Test fun legacyPartiallyAcceptedDraftNeedsExplicitUnifiedConfirmation() {
        val store = MemoryPreferences(current(Agreement.Privacy.persistedBit) + ("draft_agreement_index" to 4))
        assertEquals(setOf(Agreement.Privacy), OnboardingPreferences.read(store.preferences).acceptedAgreements)
        assertFalse(OnboardingNavigationRules.canContinueAgreements(OnboardingPreferences.read(store.preferences).acceptedAgreements))
        assertTrue(OnboardingPreferences.setAllAgreementsAccepted(store.preferences, true))
        assertTrue(OnboardingNavigationRules.canContinueAgreements(OnboardingPreferences.read(store.preferences).acceptedAgreements))
        assertEquals(1, store.commits)
    }

    @Test fun revokingUnifiedConfirmationClearsEveryAgreementAndOnlyActivationState() {
        val store = MemoryPreferences(current(allMask) + mapOf("completed" to true,
            "completed_at_millis" to 77L, "allow_background_update_checks" to true, "unrelated" to "keep"))
        assertTrue(OnboardingPreferences.setAllAgreementsAccepted(store.preferences, false))
        val snapshot = OnboardingPreferences.read(store.preferences)
        assertTrue(snapshot.acceptedAgreements.isEmpty())
        assertFalse(snapshot.completed)
        assertEquals(0L, snapshot.completedAtMillis)
        assertTrue(snapshot.allowBackgroundUpdateChecks)
        assertEquals("keep", store.values["unrelated"])
        assertEquals(1, store.commits)
    }

    @Test fun failedUnifiedConfirmationRestoresThePreviousConsentAndVersionFields() {
        val initial = mapOf<String, Any>("unrelated" to "keep", "allow_background_update_checks" to false)
        for (throws in listOf(false, true)) {
            val store = MemoryPreferences(initial, failCommit = if (throws) -1 else 1,
                throwOnCommit = if (throws) 1 else -1)
            assertFalse(OnboardingPreferences.setAllAgreementsAccepted(store.preferences, true))
            assertEquals(initial, store.values)
            assertFalse(OnboardingPreferences.read(store.preferences).allRequiredAgreementsAccepted)
        }
    }

    @Test fun newPolicyUnifiedConfirmationDoesNotReinstateAnOldActivation() {
        val store = MemoryPreferences(current(allMask) + mapOf("policy_version" to 0, "completed" to true))
        assertTrue(OnboardingPreferences.setAllAgreementsAccepted(store.preferences, true))
        val snapshot = OnboardingPreferences.read(store.preferences)
        assertTrue(snapshot.allRequiredAgreementsAccepted)
        assertFalse(snapshot.completed)
        assertEquals(0L, snapshot.completedAtMillis)
    }

    /** Models Android's memory-before-disk commit semantics, including failed/throwing writes. */
    private class MemoryPreferences(
        initial: Map<String, Any> = emptyMap(),
        private val failCommit: Int = -1,
        private val throwOnCommit: Int = -1,
    ) {
        val values = initial.toMutableMap()
        var commits = 0
        val preferences = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { proxy, method, args ->
            when (method.name) {
                "getAll" -> HashMap(values)
                "getBoolean", "getInt", "getLong", "getString" -> values[args!![0]] ?: args[1]
                "contains" -> values.containsKey(args!![0])
                "edit" -> editor()
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args!![0]
                "toString" -> "ActivationMemoryPreferences"
                else -> error("Unexpected preference call: ${method.name}")
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            val pending = mutableMapOf<String, Any>()
            val removed = mutableSetOf<String>()
            return Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
                when (method.name) {
                    "putBoolean", "putInt", "putLong", "putString" -> {
                        val key = args!![0] as String
                        val value = args[1]
                        if (value == null) { removed += key; pending.remove(key) }
                        else { pending[key] = value; removed -= key }
                        proxy
                    }
                    "remove" -> { val key = args!![0] as String; removed += key; pending.remove(key); proxy }
                    "commit" -> {
                        removed.forEach(values::remove)
                        values.putAll(pending)
                        commits++
                        if (commits == throwOnCommit) throw IllegalStateException("Simulated write failure")
                        commits != failCommit
                    }
                    else -> error("Unexpected editor call: ${method.name}")
                }
            } as SharedPreferences.Editor
        }
    }
}
