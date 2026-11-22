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

    enum class CompletionResult {
        Completed, AgreementsRequired, PolicyChanged, StorageUnavailable, WriteFailed,
    }

    enum class DraftStep(val persistedValue: String) {
        Welcome("welcome"),
        Language("language"),
        Appearance("appearance"),
        Migration("migration"),
        Agreements("agreements"),
        RootAccess("root_access"),
        Lsposed("lsposed"),
        Complete("complete"),
        ;

        companion object {
            internal fun fromPersistedValue(value: String?): DraftStep =
                when (value) {
                    "personalization" -> Migration
                    else -> entries.firstOrNull { it.persistedValue == value } ?: Welcome
                }
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

    fun read(context: Context): Snapshot = read(preferencesOrNull(context))

    internal fun read(preferences: SharedPreferences?): Snapshot = runCatching {
        if (preferences == null) return@runCatching emptySnapshot()
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
        Snapshot(
            schemaVersion = schemaVersion,
            policyVersion = policyVersion,
            completed = versionsAreCurrent &&
                allAgreementsAccepted &&
                preferences.getBoolean(KEY_COMPLETED, false),
            draftStep = normalizedDraftStep(
                schemaVersion = schemaVersion,
                policyVersion = policyVersion,
                persistedValue = preferences.getString(KEY_DRAFT_STEP, null),
                allAgreementsAccepted = allAgreementsAccepted,
            ),
            acceptedAgreements = acceptedAgreements,
            allowBackgroundUpdateChecks = preferences.getBoolean(
                KEY_ALLOW_BACKGROUND_UPDATE_CHECKS,
                false,
            ),
            completedAtMillis = preferences.getLong(KEY_COMPLETED_AT_MILLIS, 0L),
        )
    }.getOrElse { emptySnapshot() }

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
        return readDraftAgreementIndex(preferencesOrNull(context))
    }

    internal fun readDraftAgreementIndex(preferences: SharedPreferences?): Int = runCatching {
        if (preferences == null) return@runCatching 0
        val snapshot = read(preferences)
        val storedStep = DraftStep.fromPersistedValue(preferences.getString(KEY_DRAFT_STEP, null))
        if (snapshot.policyVersion != CURRENT_POLICY_VERSION ||
            (storedStep in POST_AGREEMENT_STEPS && !snapshot.allRequiredAgreementsAccepted)) {
            return@runCatching firstMissingAgreementIndex(snapshot) ?: 0
        }
        preferences.getInt(KEY_DRAFT_AGREEMENT_INDEX, 0)
            .coerceIn(0, Agreement.entries.lastIndex)
    }.getOrDefault(0)

    internal fun firstMissingAgreementIndex(snapshot: Snapshot): Int? =
        Agreement.entries.indexOfFirst { it !in snapshot.acceptedAgreements }.takeIf { it >= 0 }

    fun saveDraftAgreementIndex(context: Context, index: Int): Boolean =
        saveDraftAgreementIndex(preferencesOrNull(context), index)

    internal fun saveDraftAgreementIndex(preferences: SharedPreferences?, index: Int): Boolean =
        edit(preferences) {
            putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            putInt(KEY_POLICY_VERSION, CURRENT_POLICY_VERSION)
            putString(KEY_DRAFT_STEP, DraftStep.Agreements.persistedValue)
            putInt(
                KEY_DRAFT_AGREEMENT_INDEX,
                index.coerceIn(0, Agreement.entries.lastIndex),
            )
        }

    fun saveDraftStep(context: Context, step: DraftStep): Boolean =
        saveDraftStep(preferencesOrNull(context), step)

    internal fun saveDraftStep(preferences: SharedPreferences?, step: DraftStep): Boolean =
        edit(preferences) {
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
    ): Boolean = setAgreementAccepted(preferencesOrNull(context), agreement, accepted)

    internal fun setAgreementAccepted(
        preferences: SharedPreferences?, agreement: Agreement, accepted: Boolean,
    ): Boolean = runCatching {
        if (preferences == null) return@runCatching false
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
        preferences.edit()
            .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            .putInt(KEY_POLICY_VERSION, CURRENT_POLICY_VERSION)
            .putLong(KEY_ACCEPTED_AGREEMENTS, updatedMask)
            .apply {
                // Revoking a required agreement invalidates activation in the same disk commit.
                if (!accepted || !versionsAreCurrent) {
                    putBoolean(KEY_COMPLETED, false)
                    putLong(KEY_COMPLETED_AT_MILLIS, 0L)
                }
            }
            .commit()
    }.getOrDefault(false)

    /** One explicit confirmation saves all six agreements as one atomic change. */
    fun setAllAgreementsAccepted(context: Context, accepted: Boolean): Boolean =
        setAllAgreementsAccepted(preferencesOrNull(context), accepted)

    internal fun setAllAgreementsAccepted(preferences: SharedPreferences?, accepted: Boolean): Boolean = runCatching {
        if (preferences == null) return@runCatching false
        val previous = preferences.all.toMap()
        val versionsAreCurrent = preferences.getInt(KEY_SCHEMA_VERSION, 0) == CURRENT_SCHEMA_VERSION &&
            preferences.getInt(KEY_POLICY_VERSION, 0) == CURRENT_POLICY_VERSION
        val committed = runCatching {
            preferences.edit()
                .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
                .putInt(KEY_POLICY_VERSION, CURRENT_POLICY_VERSION)
                .putLong(KEY_ACCEPTED_AGREEMENTS, if (accepted) ALL_AGREEMENTS_MASK else 0L)
                .apply {
                    if (!accepted || !versionsAreCurrent) {
                        putBoolean(KEY_COMPLETED, false)
                        putLong(KEY_COMPLETED_AT_MILLIS, 0L)
                    }
                }
                .commit()
        }.getOrDefault(false)
        if (!committed) restoreAfterFailedCommit(preferences, previous, AGREEMENT_KEYS)
        committed
    }.getOrDefault(false)

    fun setBackgroundNetworkAllowed(context: Context, allowed: Boolean): Boolean =
        edit(context) {
            putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            putInt(KEY_POLICY_VERSION, CURRENT_POLICY_VERSION)
            putBoolean(KEY_ALLOW_BACKGROUND_UPDATE_CHECKS, allowed)
        }

    /** Null means validation succeeded, not that activation has already been committed. */
    fun completionBlockReason(context: Context): CompletionResult? =
        completionBlockReason(preferencesOrNull(context))

    internal fun completionBlockReason(preferences: SharedPreferences?): CompletionResult? = try {
        when {
            preferences == null -> CompletionResult.StorageUnavailable
            preferences.getInt(KEY_SCHEMA_VERSION, 0) != CURRENT_SCHEMA_VERSION ||
                preferences.getInt(KEY_POLICY_VERSION, 0) != CURRENT_POLICY_VERSION -> CompletionResult.PolicyChanged
            !hasAllAgreements(preferences.getLong(KEY_ACCEPTED_AGREEMENTS, 0L)) -> CompletionResult.AgreementsRequired
            else -> null
        }
    } catch (_: Exception) {
        CompletionResult.StorageUnavailable
    }

    /** Commits activation and update consent together, only after all required agreements are accepted. */
    fun complete(context: Context, allowBackgroundUpdateChecks: Boolean): Boolean =
        completeWithResult(context, allowBackgroundUpdateChecks) == CompletionResult.Completed

    fun completeWithResult(context: Context, allowBackgroundUpdateChecks: Boolean): CompletionResult =
        completeWithResult(preferencesOrNull(context), allowBackgroundUpdateChecks, System.currentTimeMillis())

    internal fun completeWithResult(
        preferences: SharedPreferences?, allowBackgroundUpdateChecks: Boolean, completedAtMillis: Long,
    ): CompletionResult {
        completionBlockReason(preferences)?.let { return it }
        preferences ?: return CompletionResult.StorageUnavailable
        val previous = runCatching { preferences.all.toMap() }.getOrElse { return CompletionResult.StorageUnavailable }
        val committed = runCatching {
            preferences.edit()
                .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
                .putInt(KEY_POLICY_VERSION, CURRENT_POLICY_VERSION)
                .putBoolean(KEY_ALLOW_BACKGROUND_UPDATE_CHECKS, allowBackgroundUpdateChecks)
                .putString(KEY_DRAFT_STEP, DraftStep.Complete.persistedValue)
                .putLong(KEY_COMPLETED_AT_MILLIS, completedAtMillis)
                // SharedPreferences commits the editor as one change.
                .putBoolean(KEY_COMPLETED, true)
                .commit()
        }.getOrDefault(false)
        if (committed) return CompletionResult.Completed
        // Android applies edits to memory before writing the file. A failed disk commit must not
        // leave completed=true visible to activation/background-network gates in this process.
        restoreAfterFailedCommit(preferences, previous, COMPLETION_KEYS)
        return CompletionResult.WriteFailed
    }

    private fun restoreAfterFailedCommit(preferences: SharedPreferences, previous: Map<String, *>, keys: Set<String>) {
        runCatching {
            preferences.edit().apply {
                keys.forEach { key ->
                    when (val value = previous[key]) {
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is String -> putString(key, value)
                        else -> remove(key)
                    }
                }
            }.commit()
        }
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
        allAgreementsAccepted: Boolean,
    ): DraftStep {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) return DraftStep.Welcome
        val persistedStep = DraftStep.fromPersistedValue(persistedValue)
        if (
            policyVersion != CURRENT_POLICY_VERSION &&
            persistedStep != DraftStep.Welcome
        ) {
            return DraftStep.Agreements
        }
        if (persistedStep in POST_AGREEMENT_STEPS && !allAgreementsAccepted) return DraftStep.Agreements
        return persistedStep
    }

    private fun preferencesOrNull(context: Context): SharedPreferences? = runCatching {
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }.getOrNull()

    private inline fun edit(
        context: Context,
        block: SharedPreferences.Editor.() -> Unit,
    ): Boolean = edit(preferencesOrNull(context), block)

    private inline fun edit(preferences: SharedPreferences?, block: SharedPreferences.Editor.() -> Unit): Boolean =
        runCatching {
            if (preferences == null) return@runCatching false
            val versionsAreCurrent = preferences.getInt(KEY_SCHEMA_VERSION, 0) == CURRENT_SCHEMA_VERSION &&
                preferences.getInt(KEY_POLICY_VERSION, 0) == CURRENT_POLICY_VERSION
            preferences.edit().apply {
                if (!versionsAreCurrent) {
                    putLong(KEY_ACCEPTED_AGREEMENTS, 0L)
                    putBoolean(KEY_COMPLETED, false)
                    putLong(KEY_COMPLETED_AT_MILLIS, 0L)
                }
                block()
            }.commit()
        }.getOrDefault(false)

    private val ALL_AGREEMENTS_MASK: Long = Agreement.entries.fold(0L) { mask, agreement ->
        mask or agreement.persistedBit
    }

    private val POST_AGREEMENT_STEPS = setOf(DraftStep.RootAccess, DraftStep.Lsposed, DraftStep.Complete)
    private val COMPLETION_KEYS = setOf(
        KEY_ALLOW_BACKGROUND_UPDATE_CHECKS, KEY_DRAFT_STEP, KEY_COMPLETED_AT_MILLIS, KEY_COMPLETED,
    )
    private val AGREEMENT_KEYS = setOf(
        KEY_SCHEMA_VERSION, KEY_POLICY_VERSION, KEY_ACCEPTED_AGREEMENTS, KEY_COMPLETED, KEY_COMPLETED_AT_MILLIS,
    )

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
