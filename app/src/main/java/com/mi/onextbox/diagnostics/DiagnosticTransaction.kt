package com.mi.onextbox.diagnostics

internal fun diagnosticTransaction(write: () -> Unit, publish: () -> Unit, rollback: () -> Unit, republish: () -> Unit) {
    try { write(); publish() }
    catch (error: Throwable) {
        runCatching(rollback).onFailure { if (it !== error) error.addSuppressed(it) }
        runCatching(republish).onFailure { if (it !== error) error.addSuppressed(it) }
        throw error
    }
}
