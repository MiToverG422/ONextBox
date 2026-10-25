package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class LauncherGridPreviewFieldsTest {
    class NativePreview {
        @JvmField var t = 4
        @JvmField var u = 7
        @JvmField var c = 12
        @JvmField var d = 12
        @JvmField var p = 3
        @JvmField val o = 8
        @JvmField val n = 10
        @JvmField var q = 2
    }

    class AliasPreview {
        @JvmField var f14740t = 4
        @JvmField var f14741u = 7
    }

    class ColorOs16Preview {
        @JvmField var mColumn = 5
        @JvmField var mRow = 9
        @JvmField var mPreviewCellWidth = 12
        @JvmField var mPreviewCellHeight = 12
        @JvmField var mPreviewCellRadius = 3
        @JvmField val mPreviewPaddingStart = 8
        @JvmField val mPreviewPaddingTop = 10
    }

    @Test fun colorOs16NamedFieldsResolveWithoutAcceptingTheC17FieldLayout() {
        val type = ColorOs16Preview::class.java
        val fields = LauncherGridPreviewFields.resolve(type, type.declaredFields.reversed(), colorOs16 = true)
        val preview = ColorOs16Preview()
        assertEquals(5, fields[0].getInt(preview))
        assertEquals(9, fields[1].getInt(preview))
        fields[2].setInt(preview, 7)
        assertEquals(7, preview.mPreviewCellWidth)
        assertEquals(8, fields[5].getInt(preview))
        assertThrows(NoSuchFieldException::class.java) {
            LauncherGridPreviewFields.resolve(type, type.declaredFields.toList())
        }
    }

    class WrongTypePreview {
        @JvmField var t = 4f
    }

    class StaticPreview {
        companion object { @JvmField var t = 4 }
    }

    class ImmutablePreview {
        @JvmField var t = 4
        @JvmField var u = 7
        @JvmField val c = 12
    }

    @Test fun rawDexFieldsResolveInRoleOrderRegardlessOfReferenceOrder() {
        val type = NativePreview::class.java
        val fields = type.declaredFields.reversed()
        val resolved = LauncherGridPreviewFields.resolve(type, fields + fields)
        assertEquals(listOf("t", "u", "c", "d", "p", "o", "n"), resolved.map { it.name })
        val preview = NativePreview()
        assertEquals(4, resolved[0].getInt(preview))
        assertEquals(7, resolved[1].getInt(preview))
        resolved[2].setInt(preview, 9)
        assertEquals(9, preview.c)
        assertEquals(8, resolved[5].getInt(preview))
        assertEquals(10, resolved[6].getInt(preview))
    }

    @Test(expected = NoSuchFieldException::class)
    fun jadxDisplayAliasesAreNotAcceptedAsNativeFields() {
        val type = AliasPreview::class.java
        LauncherGridPreviewFields.resolve(type, type.declaredFields.toList())
    }

    @Test(expected = NoSuchFieldException::class)
    fun requiredFieldsMustActuallyBeReferencedByNativeDrawing() {
        val type = NativePreview::class.java
        LauncherGridPreviewFields.resolve(type, type.declaredFields.filter { it.name != "p" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedFieldTypesFailBeforeAnyPreviewMutation() {
        val type = WrongTypePreview::class.java
        LauncherGridPreviewFields.resolve(type, type.declaredFields.toList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun staticFieldsCannotBeMistakenForPerViewDimensions() {
        val type = StaticPreview::class.java
        LauncherGridPreviewFields.resolve(type, type.declaredFields.toList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun cellSizeFieldsMustBeWritable() {
        val type = ImmutablePreview::class.java
        LauncherGridPreviewFields.resolve(type, type.declaredFields.toList())
    }

    @Test(expected = NoSuchFieldException::class)
    fun fieldsFromAnotherDeclaringClassDoNotMatch() {
        LauncherGridPreviewFields.resolve(NativePreview::class.java, ImmutablePreview::class.java.declaredFields.toList())
    }
}
