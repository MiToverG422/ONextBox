package com.mi.onextbox.lsp

import java.lang.reflect.Field
import java.lang.reflect.Modifier

internal object LauncherGridPreviewFields {
    fun resolve(owner: Class<*>, referenced: List<Field>, colorOs16: Boolean = false): Array<Field> {
        val fields = referenced.distinct().filter { it.declaringClass == owner }
        val names = if (colorOs16) listOf("mColumn", "mRow", "mPreviewCellWidth", "mPreviewCellHeight",
            "mPreviewCellRadius", "mPreviewPaddingStart", "mPreviewPaddingTop")
        else listOf("t", "u", "c", "d", "p", "o", "n")
        return names.mapIndexed { index, name ->
            val field = fields.singleOrNull { it.name == name }
                ?: throw NoSuchFieldException("Native layout preview draw field is missing: $name")
            require(field.type == Int::class.javaPrimitiveType && !Modifier.isStatic(field.modifiers)) {
                "Native layout preview field has an unsupported type: $name"
            }
            require(index !in 2..4 || !Modifier.isFinal(field.modifiers)) {
                "Native layout preview size field is not writable: $name"
            }
            field.apply { isAccessible = true }
        }.toTypedArray()
    }
}
