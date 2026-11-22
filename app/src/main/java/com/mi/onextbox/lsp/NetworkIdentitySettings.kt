package com.mi.onextbox.lsp

import android.content.Context
import android.content.SharedPreferences
import com.mi.onextbox.diagnostics.SafeModePolicy
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_READY_KEY

internal object NetworkIdentitySettings {
    const val ENABLED = "network_identity_enabled"
    const val PRESERVE_CALL_REGION = "network_identity_preserve_call_region"
    const val REGION = "network_identity_region"
    const val NUMERIC = "network_identity_numeric"
    const val NAME = "network_identity_name"
    fun slotKey(slot: Int, field: String): String {
        require(slot in 0..1)
        return "network_identity_sim${slot + 1}_$field"
    }
    val keys = setOf(ENABLED, PRESERVE_CALL_REGION, REGION, NUMERIC, NAME) + (0..1).flatMap { slot ->
        listOf("enabled", "region", "numeric", "name").map { slotKey(slot, it) }
    }
    data class Slot(val enabled: Boolean = true, val region: String = "tw", val numeric: String = "46692", val name: String = "Chunghwa Telecom") {
        fun profile() = NetworkIdentityRules.Profile(region, numeric, name).takeIf { enabled && NetworkIdentityRules.valid(it) }
    }
    data class State(val enabled: Boolean = false, val sim1: Slot = Slot(), val sim2: Slot = Slot(), val preserveCallRegion: Boolean = false) {
        fun slot(index: Int): Slot = when (index) { 0 -> sim1; 1 -> sim2; else -> error("Unsupported SIM slot") }
        fun withSlot(index: Int, value: Slot) = when (index) {
            0 -> copy(sim1 = value); 1 -> copy(sim2 = value); else -> error("Unsupported SIM slot")
        }
        fun profiles(): Map<Int, NetworkIdentityRules.Profile> = if (!enabled) emptyMap() else
            (0..1).mapNotNull { index -> slot(index).profile()?.let { index to it } }.toMap()
    }
    // Older builds applied one profile to both SIMs. Inherit it independently until each slot
    // has been saved, including explicitly disabled slots; never re-enable from a legacy key.
    fun fromValues(values: Map<String, *>): State {
        val legacy = Slot(region = values[REGION] as? String ?: "tw",
            numeric = values[NUMERIC] as? String ?: "46692", name = values[NAME] as? String ?: "Chunghwa Telecom")
        fun readSlot(index: Int) = Slot(
            enabled = values[slotKey(index, "enabled")] as? Boolean ?: true,
            region = values[slotKey(index, "region")] as? String ?: legacy.region,
            numeric = values[slotKey(index, "numeric")] as? String ?: legacy.numeric,
            name = values[slotKey(index, "name")] as? String ?: legacy.name)
        return State(values[ENABLED] as? Boolean ?: false, readSlot(0), readSlot(1), values[PRESERVE_CALL_REGION] == true)
    }
    fun read(prefs: SharedPreferences): State = fromValues(prefs.all)
    /** Configuration events only; property callbacks reuse the resulting immutable profiles. */
    fun activeProfiles(values: Map<String, *>, hooksActive: Boolean): Map<Int, NetworkIdentityRules.Profile> =
        if (!hooksActive || values[MODERN_PREFERENCES_READY_KEY] != true || values[SafeModePolicy.MANUAL] == true) emptyMap()
        else fromValues(values).profiles()
    fun state(context: Context) = read(LspPreferenceStore.prefs(context))
    fun save(context: Context, state: State) {
        require(!state.enabled || (0..1).all { !state.slot(it).enabled || state.slot(it).profile() != null })
        val editor = LspPreferenceStore.prefs(context).edit().putBoolean(ENABLED, state.enabled)
            .putBoolean(PRESERVE_CALL_REGION, state.preserveCallRegion)
            .remove(REGION).remove(NUMERIC).remove(NAME)
        (0..1).forEach { index ->
            val slot = state.slot(index)
            editor.putBoolean(slotKey(index, "enabled"), slot.enabled).putString(slotKey(index, "region"), slot.region)
                .putString(slotKey(index, "numeric"), slot.numeric).putString(slotKey(index, "name"), slot.name)
        }
        check(editor.commit())
        LspConfig.syncReadableState(context)
    }
    fun setEnabled(context: Context, enabled: Boolean) = save(context, state(context).copy(enabled = enabled))
}
