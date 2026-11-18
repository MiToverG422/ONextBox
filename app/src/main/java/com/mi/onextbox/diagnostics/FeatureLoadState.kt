package com.mi.onextbox.diagnostics

internal enum class FeatureLoadState { Disabled, Unsupported, Saved, WaitingReload, Loaded, Unmatched, Failed }
internal data class FeatureLoadReceipt(val enabled: Boolean, val installed: Int, val staged: Int, val failed: Boolean)

internal fun featureLoadState(enabled: Boolean, supported: Boolean, receipt: FeatureLoadReceipt?,
    configurationCurrent: Boolean = false): FeatureLoadState = when {
    !supported -> FeatureLoadState.Unsupported
    !enabled -> FeatureLoadState.Disabled
    receipt == null -> FeatureLoadState.Saved
    !receipt.enabled && (receipt.installed == 0 || !configurationCurrent) -> FeatureLoadState.WaitingReload
    receipt.failed -> FeatureLoadState.Failed
    receipt.staged > 0 -> FeatureLoadState.WaitingReload
    receipt.installed > 0 -> FeatureLoadState.Loaded
    else -> FeatureLoadState.Unmatched
}

internal fun parseFeatureReceipt(detail: String): FeatureLoadReceipt? = runCatching {
    val fields = detail.split(';').associate { part -> part.trim().split('=', limit = 2).let { it[0] to it[1] } }
    FeatureLoadReceipt(fields.getValue("enabled").toBooleanStrict(), fields.getValue("installed").toInt().also { require(it >= 0) },
        fields.getValue("staged").toInt().also { require(it >= 0) }, fields.getValue("failed").toBooleanStrict())
}.getOrNull()

internal fun settledFeatureReceipt(event: DiagnosticEvent?, events: List<DiagnosticEvent>): FeatureLoadReceipt? {
    val recorded = event?.let { parseFeatureReceipt(it.detail) } ?: return null
    if (recorded.staged == 0) return recorded
    val position = events.indexOfLast { it == event }
    val committed = position >= 0 && events.drop(position + 1).any {
        it.kind == "committed" && it.process == event.process && it.generation == event.generation && it.time >= event.time
    }
    return if (committed) recorded.copy(installed = recorded.installed + recorded.staged, staged = 0) else recorded
}
