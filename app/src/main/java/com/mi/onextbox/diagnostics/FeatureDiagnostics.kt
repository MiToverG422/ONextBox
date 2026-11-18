package com.mi.onextbox.diagnostics

import android.content.Context
import com.mi.onextbox.lsp.SystemAdaptationDetector
import com.mi.onextbox.lsp.compat.AdaptationRuntimePolicy
import com.mi.onextbox.ui.screens.SharedFeatureDefinitions

internal data class FeatureDiagnosticRow(val title: Int, val state: FeatureLoadState, val process: String?, val time: Long?)

/** Receipts describe a recorded load, never visual success or current process liveness. */
internal suspend fun featureDiagnosticRows(context: Context, events: List<DiagnosticEvent>): List<FeatureDiagnosticRow> {
    val current = DiagnosticRepository.currentEvents(events)
    val receipts = current.filter { it.kind == "feature_init" }
        .groupBy { it.name }.mapValues { (_, rows) -> rows.maxBy { it.time } }
    val version = SystemAdaptationDetector.current()
    val fingerprint = DiagnosticRules.fingerprint(com.mi.onextbox.lsp.frameworkPreferenceValues(
        com.mi.onextbox.lsp.LspPreferenceStore.prefs(context).all, com.mi.onextbox.lsp.SideKeyFeaturePolicy.available,
    ))
    return SharedFeatureDefinitions.all.filter { it.preferenceKey != null }.map { definition ->
        val key = checkNotNull(definition.preferenceKey)
        val latest = receipts[key]
        val receipt = settledFeatureReceipt(latest, current)
        val configurationCurrent = latest != null && current.lastOrNull {
            it.kind == "config" && it.process == latest.process && it.generation == latest.generation
        }?.detail == fingerprint
        FeatureDiagnosticRow(definition.title, featureLoadState(definition.read(context),
            AdaptationRuntimePolicy.allowsFeatureBoolean(version, key), receipt, configurationCurrent),
            latest?.process, latest?.time)
    }
}
