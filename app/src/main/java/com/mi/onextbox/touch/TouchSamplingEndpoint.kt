package com.mi.onextbox.touch

import java.security.MessageDigest

/** Known OPlus AIDL report-rate endpoint. */
internal data class OplusTouchEndpoint(
    val service: String,
    val panelIndex: Int,
    val node: Int = 182,
) {
    init {
        require(SERVICE.matches(service) && !service.substringAfterLast('/').contains("..")) {
            "Invalid touch service"
        }
        require(panelIndex in 0..1) { "Unsupported panel" }
        require(node == 182) { "Unsupported report-rate node" }
    }

    fun supportCommand(): String = command(2)

    fun readCommand(): String = command(3)

    fun writeCommand(index: Int): String {
        require(index in 0..255) { "Invalid report-rate index" }
        return command(4) + " s16 $index"
    }

    fun bindingId(profile: TouchConfigProfile): String {
        require(profile.node == node && (profile.panelIndex == null || profile.panelIndex == panelIndex)) {
            "Touch profile does not match endpoint"
        }
        require(profile.defaultChipValue != null && profile.defaultChipValue >= 0) {
            "Touch profile has no default mode"
        }
        val ordered = profile.presets.sortedBy { it.index }
        require(ordered.size in 1..255 && ordered.map { it.index } == (1..ordered.size).toList() &&
            ordered.all { it.chipValue >= 0 && (it.hz == null || it.hz in 30..4000) }
        ) { "Invalid touch mode mapping" }
        val canonical = buildString {
            append("panel=").append(panelIndex).append('\n')
            append("node=").append(node).append('\n')
            append("default=").append(profile.defaultChipValue).append('\n')
            for (preset in ordered) {
                append(preset.index).append(':').append(preset.chipValue).append(':')
                append(preset.hz ?: "null").append(':').append(if (preset.isIstMode) 1 else 0).append('\n')
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        val hash = digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        return "oplus:$service:panel=$panelIndex:node=$node:$hash"
    }

    private fun command(transaction: Int): String =
        "/system/bin/service call ${TouchSamplingDiscovery.safeShellQuote(service)} " +
            "$transaction i32 $panelIndex i32 $node"

    private companion object {
        val SERVICE = Regex("vendor\\.oplus\\.hardware\\.touch\\.IOplusTouch/[A-Za-z0-9][A-Za-z0-9_.-]{0,63}")
    }
}
