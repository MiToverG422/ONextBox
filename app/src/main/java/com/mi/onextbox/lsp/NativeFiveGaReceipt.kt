package com.mi.onextbox.lsp

internal class NativeFiveGaReceipt {
    enum class Result { ACCEPT, IGNORE, INVALID }
    private var source: String? = null
    private var sequence = -1L
    private var invalid = false

    fun receive(nextSource: String?, nextSequence: Long): Result {
        if (invalid) return Result.IGNORE
        if (nextSource.isNullOrBlank() || nextSequence < 0 ||
            (source != null && source != nextSource)) {
            invalid = true
            return Result.INVALID
        }
        if (nextSequence <= sequence) return Result.IGNORE
        source = nextSource
        sequence = nextSequence
        return Result.ACCEPT
    }
}

internal object NativeFiveGaAccess {
    fun publisher(uid: Int) = uid == 1001
    fun reader(uid: Int, moduleUid: Int, systemUiUid: Int = -1) = uid == 1000 ||
        (moduleUid >= 10000 && uid == moduleUid) || (systemUiUid >= 10000 && uid == systemUiUid)
}
