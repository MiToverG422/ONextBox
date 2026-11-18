package com.mi.onextbox.lsp

internal class LauncherIosIconHandoff<T : Any> {
    var deferred = false
    private var transaction: Any? = null
    private var pending: T? = null

    fun capture(transaction: Any, target: T): T? {
        val previous = pending
        this.transaction = transaction
        pending = target
        return previous
    }

    fun take(transaction: Any): T? {
        if (this.transaction !== transaction) return null
        return abandon()
    }

    fun abandon(): T? {
        val target = pending
        pending = null
        transaction = null
        return target
    }
}
