package com.mi.onextbox.lsp

// Binder recipients do not have the module's Kotlin collection classes.
internal fun frameworkStringSet(value: Set<*>): HashSet<String> =
    value.filterIsInstanceTo(HashSet<String>())
