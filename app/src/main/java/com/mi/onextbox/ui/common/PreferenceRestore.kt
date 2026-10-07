package com.mi.onextbox.ui.common

internal interface PreferenceStore {
    fun read(): Map<String, *>
    fun write(values: Map<String, *>): Boolean
}

/** Applies preference groups with rollback on a failed write. */
internal fun restorePreferences(
    groups: Map<String, Map<String, *>>,
    stores: Map<String, PreferenceStore>,
) {
    require(stores.keys.containsAll(groups.keys)) { "Unknown preference group" }
    val previous = groups.keys.associateWith { stores.getValue(it).read().toMap() }
    val attempted = mutableListOf<String>()
    try {
        groups.forEach { (name, values) ->
            attempted += name
            check(stores.getValue(name).write(values)) { "Failed to write $name" }
        }
    } catch (error: Exception) {
        attempted.asReversed().forEach { name ->
            try {
                check(stores.getValue(name).write(previous.getValue(name))) { "Failed to restore $name" }
            } catch (rollbackError: Exception) {
                error.addSuppressed(rollbackError)
            }
        }
        throw error
    }
}
