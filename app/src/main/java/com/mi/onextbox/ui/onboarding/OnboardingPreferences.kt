package com.mi.onextbox.ui.onboarding

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.RawRes
import com.mi.onextbox.R

/** First-run progress and policy acceptance, stored separately from runtime permission checks. */
object OnboardingPreferences {
    const val PREFERENCES_NAME = "onextbox_activation"
    const val CURRENT_SCHEMA_VERSION = 1
    const val CURRENT_POLICY_VERSION = 1

    enum class DraftStep(val persistedValue: String) {
        Welcome("welcome"),
        Language("language"),
        Appearance("appearance"),
        Personalization("personalization"),
        Migration("migration"),
        Agreements("agreements"),
        RootAccess("root_access"),
        Lsposed("lsposed"),
        Complete("complete"),
        ;

        companion object {
            internal fun fromPersistedValue(value: String?): DraftStep =
                entries.firstOrNull { it.persistedValue == value } ?: Welcome
        }
    }

    /** Every entry is required before [complete] can succeed. */
    enum class Agreement(
        internal val persistedBit: Long,
        @RawRes val localizedTextResource: Int,
    ) {
        TermsAndRisk(1L shl 0, R.raw.onboarding_agreement_terms),
        Privacy(1L shl 1, R.raw.onboarding_agreement_privacy),
        PrivilegedAccess(1L shl 2, R.raw.onboarding_agreement_privileged),
        EsimNetworkSecurity(1L shl 3, R.raw.onboarding_agreement_esim),
        OpenSourceLicenses(1L shl 4, R.raw.onboarding_agreement_licenses),
        DeviceAuthorization(1L shl 5, R.raw.onboarding_agreement_authorization),
    }

    data class Snapshot(
        val schemaVersion: Int,
        val policyVersion: Int,
        val completed: Boolean,
        val draftStep: DraftStep,
        val acceptedAgreements: Set<Agreement>,
        val allowBackgroundUpdateChecks: Boolean,
        val completedAtMillis: Long,
    ) {
        val allRequiredAgreementsAccepted: Boolean
            get() = acceptedAgreements.containsAll(Agreement.entries)
    }

    fun read(context: Context): Snapshot {
        val preferences = preferencesOrNull(context)
            ?: return emptySnapshot()
        val schemaVersion = preferences.getInt(KEY_SCHEMA_VERSION, 0)
        val policyVersion = preferences.getInt(KEY_POLICY_VERSION, 0)
        val storedAcceptedMask = preferences.getLong(KEY_ACCEPTED_AGREEMENTS, 0L)
        val acceptedMask = storedAcceptedMask.takeIf {
            schemaVersion == CURRENT_SCHEMA_VERSION && policyVersion == CURRENT_POLICY_VERSION
        } ?: 0L
        val acceptedAgreements = Agreement.entries
            .filterTo(linkedSetOf()) { agreement ->
                acceptedMask and agreement.persistedBit != 0L
            }
        val versionsAreCurrent =
            schemaVersion == CURRENT_SCHEMA_VERSION &&
                policyVersion == CURRENT_POLICY_VERSION
        val allAgreementsAccepted = acceptedAgreements.containsAll(Agreement.entries)
        return Snapshot(
            schemaVersion = schemaVersion,
            policyVersion = policyVersion,
            completed = versionsAreCurrent &&
                allAgreementsAccepted &&
                preferences.getBoolean(KEY_COMPLETED, false),
            draftStep = normalizedDraftStep(
                schemaVersion = schemaVersion,
                policyVersion = policyVersion,
                persistedValue = preferences.getString(KEY_DRAFT_STEP, null),
            ),
            acceptedAgreements = acceptedAgreements,
            allowBackgroundUpdateChecks = preferences.getBoolean(
                KEY_ALLOW_BACKGROUND_UPDATE_CHECKS,
                false,
            ),
            completedAtMillis = preferences.getLong(KEY_COMPLETED_AT_MILLIS, 0L),
        )
    }

    fun isCompleted(context: Context): Boolean = read(context).completed

    /**
     * Background network access is opt-in and is never granted by the checkbox alone: activation
     * and all policies must still be current. This is the gate schedulers and receivers should use.
     */
    fun isBackgroundNetworkAllowed(context: Context): Boolean {
        val snapshot = read(context)
        return snapshot.completed && snapshot.allowBackgroundUpdateChecks
    }

    fun readDraftStep(context: Context): DraftStep = read(context).draftStep

    fun readDraftAgreementIndex(context: Context): Int {
        val preferences = preferencesOrNull(context) ?: return 0
        return preferences.getInt(KEY_DRAFT_AGREEMENT_INDEX, 0)
            .coerceIn(0, Agreement.entries.lastIndex)
    }

    fun saveDraftAgreementIndex(context: Context, index: Int): Boolean =
        edit(context) {
            putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            putInt(KEY_POLICY_VERSION, CURRENT_POLICY_VERSION)
            putInt(
                KEY_DRAFT_AGREEMENT_INDEX,
                index.coerceIn(0, Agreement.entries.lastIndex),
            )
        }

    fun saveDraftStep(context: Context, step: DraftStep): Boolean =
        edit(context) {
            putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            // A draft page belongs to the current policy flow even before the user accepts any
            // agreement. Without this marker an Activity recreation (for example, after changing
            // language) treats every pre-agreement page as a stale-policy draft and jumps straight
            // to Agreements.
            putInt(KEY_POLICY_VERSION, CURRENT_POLICY_VERSION)
            putString(KEY_DRAFT_STEP, step.persistedValue)
        }

    fun isAgreementAccepted(context: Context, agreement: Agreement): Boolean =
        agreement in read(context).acceptedAgreements

    fun areRequiredAgreementsAccepted(context: Context): Boolean =
        read(context).allRequiredAgreementsAccepted

    fun setAgreementAccepted(
        context: Context,
        agreement: Agreement,
        accepted: Boolean,
    ): Boolean {
        val preferences = preferencesOrNull(context) ?: return false
        val versionsAreCurrent =
            preferences.getInt(KEY_SCHEMA_VERSION, 0) == CURRENT_SCHEMA_VERSION &&
                preferences.getInt(KEY_POLICY_VERSION, 0) == CURRENT_POLICY_VERSION
        val currentMask = if (versionsAreCurrent) {
            preferences.getLong(KEY_ACCEPTED_AGREEMENTS, 0L)
        } else {
            0L
        }
        val updatedMask = if (accepted) {
            currentMask or agreement.persistedBit
        } else {
            currentMask and agreement.persistedBit.inv()
        }
        return preferences.edit()
            .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            .putInt(KEY_POLICY_VERSION, CURRENT_POLICY_VERSION)
            .putLong(KEY_ACCEPTED_AGREEMENTS, updatedMask)
            .apply {
                // Revoking a required agreement invalidates activation in the same disk commit.
                if (!accepted) {
                    putBoolean(KEY_COMPLETED, false)
                    putLong(KEY_COMPLETED_AT_MILLIS, 0L)
                }
            }
            .commit()
    }

    fun setBackgroundNetworkAllowed(context: Context, allowed: Boolean): Boolean =
        edit(context) {
            putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            putInt(KEY_POLICY_VERSION, CURRENT_POLICY_VERSION)
            putBoolean(KEY_ALLOW_BACKGROUND_UPDATE_CHECKS, allowed)
        }

/** Commits activation and update consent together, only after all required agreements are accepted. */
    fun complete(context: Context, allowBackgroundUpdateChecks: Boolean): Boolean {
        val preferences = preferencesOrNull(context) ?: return false
        if (preferences.getInt(KEY_SCHEMA_VERSION, 0) != CURRENT_SCHEMA_VERSION) return false
        if (preferences.getInt(KEY_POLICY_VERSION, 0) != CURRENT_POLICY_VERSION) return false
        if (!hasAllAgreements(preferences.getLong(KEY_ACCEPTED_AGREEMENTS, 0L))) return false
        return preferences.edit()
            .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            .putInt(KEY_POLICY_VERSION, CURRENT_POLICY_VERSION)
            .putBoolean(KEY_ALLOW_BACKGROUND_UPDATE_CHECKS, allowBackgroundUpdateChecks)
            .putString(KEY_DRAFT_STEP, DraftStep.Complete.persistedValue)
            .putLong(KEY_COMPLETED_AT_MILLIS, System.currentTimeMillis())
            // Write this last for readability; SharedPreferences commits the editor as one change.
            .putBoolean(KEY_COMPLETED, true)
            .commit()
    }

    /** Clears only activation state; ordinary app configuration remains untouched. */
    fun reset(context: Context): Boolean =
        preferencesOrNull(context)?.edit()?.clear()?.commit() ?: false

    private fun emptySnapshot() = Snapshot(
        schemaVersion = 0,
        policyVersion = 0,
        completed = false,
        draftStep = DraftStep.Welcome,
        acceptedAgreements = emptySet(),
        allowBackgroundUpdateChecks = false,
        completedAtMillis = 0L,
    )

    private fun hasAllAgreements(mask: Long): Boolean = mask and ALL_AGREEMENTS_MASK == ALL_AGREEMENTS_MASK

    private fun normalizedDraftStep(
        schemaVersion: Int,
        policyVersion: Int,
        persistedValue: String?,
    ): DraftStep {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) return DraftStep.Welcome
        val persistedStep = DraftStep.fromPersistedValue(persistedValue)
        if (
            policyVersion != CURRENT_POLICY_VERSION &&
            persistedStep != DraftStep.Welcome
        ) {
            return DraftStep.Agreements
        }
        return persistedStep
    }

    private fun preferencesOrNull(context: Context): SharedPreferences? = runCatching {
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }.getOrNull()

    private inline fun edit(
        context: Context,
        block: SharedPreferences.Editor.() -> Unit,
    ): Boolean {
        val preferences = preferencesOrNull(context) ?: return false
        return preferences.edit().apply(block).commit()
    }

    private val ALL_AGREEMENTS_MASK: Long = Agreement.entries.fold(0L) { mask, agreement ->
        mask or agreement.persistedBit
    }

    private const val KEY_SCHEMA_VERSION = "schema_version"
    private const val KEY_POLICY_VERSION = "policy_version"
    private const val KEY_COMPLETED = "completed"
    private const val KEY_COMPLETED_AT_MILLIS = "completed_at_millis"
    private const val KEY_DRAFT_STEP = "draft_step"
    private const val KEY_DRAFT_AGREEMENT_INDEX = "draft_agreement_index"
    private const val KEY_ACCEPTED_AGREEMENTS = "accepted_agreements"
    private const val KEY_ALLOW_BACKGROUND_UPDATE_CHECKS = "allow_background_update_checks"
}

/** Read-only activation checks safe to call from an Activity, Service, Receiver, or app Context. */
object ActivationGate {
    fun isActivated(context: Context): Boolean = OnboardingPreferences.isCompleted(context)

    fun mayRunBackgroundNetwork(context: Context): Boolean =
        OnboardingPreferences.isBackgroundNetworkAllowed(context)
}
