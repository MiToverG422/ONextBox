package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.R
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object CorePatchSettings {
    enum class Option(val key: String, val title: Int, val summary: Int) {
        Downgrade("downgrade", R.string.corepatch_downgrade, R.string.corepatch_downgrade_summary),
        MinSdk("bypass_min_sdk", R.string.corepatch_min_sdk, R.string.corepatch_min_sdk_summary),
        Verification("bypass_verification", R.string.corepatch_verification, R.string.corepatch_verification_summary),
        ResourceArsc("bypass_resource_arsc_restrictions", R.string.corepatch_resource_arsc, R.string.corepatch_resource_arsc_summary),
        Digest("bypass_digest", R.string.corepatch_digest, R.string.corepatch_digest_summary),
        ExactSignature("bypass_exact_sig_match", R.string.corepatch_exact_signature, R.string.corepatch_exact_signature_summary),
        PreviousSignatures("use_previous_signatures", R.string.corepatch_previous_signatures, R.string.corepatch_previous_signatures_summary),
        HiddenApis("allow_hidden_apis_for_system_apps", R.string.corepatch_hidden_apis, R.string.corepatch_hidden_apis_summary),
        SharedUser("bypass_shared_user", R.string.corepatch_shared_user, R.string.corepatch_shared_user_summary),
        VerificationAgent("disable_verification_agent", R.string.corepatch_verification_agent, R.string.corepatch_verification_agent_summary),
        Block("bypass_block", R.string.corepatch_block, R.string.corepatch_block_summary),
    }
    private fun key(option: Option) = "corepatch_${option.key}"
    fun availableOptions(version: SystemAdaptationVersion): List<Option> = Option.entries.filter {
        version != SystemAdaptationVersion.ColorOs16 || it != Option.Block
    }
    fun enabled(context: Context, option: Option) = LspPreferenceStore.prefs(context).getBoolean(key(option), false)
    fun set(context: Context, option: Option, value: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(key(option), value).commit())
        LspConfig.syncReadableState(context)
    }
    @Volatile private var bootOptions: Set<String> = emptySet()
    fun captureBootOptions(): Boolean {
        bootOptions = Option.entries.filter { HookConfigSnapshot.boolean(key(it), false) == true }.map { it.key }.toSet()
        return bootOptions.isNotEmpty()
    }
    fun active(key: String) = CorePatchRules.active(key, bootOptions)
}

internal object CorePatchRules {
    fun active(key: String, selected: Set<String>): Boolean {
        if (key !in selected) return false
        return when (key) {
            "bypass_shared_user" -> "bypass_digest" in selected
            "use_previous_signatures" -> "bypass_digest" in selected && "bypass_verification" in selected
            else -> true
        }
    }
}
