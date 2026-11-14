package com.mi.onextbox.lsp

import java.lang.reflect.Field

/** The UI count is overridden only while the native add-entry callback runs. */
internal object EuiccProfileCountOverride {
    fun <T> withUnrestrictedCount(owner: Any, field: Field, action: () -> T): T {
        val count = field.getInt(owner)
        if (count <= 0) return action()
        field.setInt(owner, 0)
        return try {
            action()
        } finally {
            // A native refresh during the callback owns its new value.
            if (field.getInt(owner) == 0) field.setInt(owner, count)
        }
    }
}
