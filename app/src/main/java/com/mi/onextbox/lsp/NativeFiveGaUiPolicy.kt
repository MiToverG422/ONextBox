package com.mi.onextbox.lsp

internal object NativeFiveGaUiPolicy {
    fun matches(
        enabled: Boolean,
        source: String?,
        expectedSource: String,
        phoneId: Int,
        subId: Int,
        epoch: Long,
        proofPhoneId: Int,
        proofSubId: Int,
        proofEpoch: Long,
        ready: Boolean,
        qualified: Boolean,
        networkType: Int,
        overrideType: Int,
        inService: Boolean,
    ): Boolean = enabled && source == expectedSource && !source.isNullOrEmpty() &&
        phoneId in 0..1 && subId >= 0 && epoch > 0 &&
        proofPhoneId == phoneId && proofSubId == subId && proofEpoch == epoch &&
        ready && qualified && networkType == 20 && overrideType == 5 && inService

    fun nextRevision(previous: Long): Long = if (previous == Long.MAX_VALUE) 0 else previous + 1
}

internal class NativeFiveGaUiLineage(val phoneId: Int, val subId: Int) {
    data class Certificate(val source: String, val epoch: Long)

    @Volatile private var certificate: Certificate? = null

    fun current(): Certificate? = certificate

    fun renew(source: String, epoch: Long, currentPhoneId: Int, currentSubId: Int, nativeValidated: Boolean): Boolean {
        if (!nativeValidated || source.isEmpty() || epoch <= 0 || phoneId !in 0..1 || subId < 0 ||
            currentPhoneId != phoneId || currentSubId != subId) return false
        certificate = Certificate(source, epoch)
        return true
    }
}
