package com.mi.onextbox.lsp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("UNUSED_PARAMETER", "unused")
class CouiDividerTargetsTest {
    class MergedPreference(context: Context?, @JvmField var screen: androidx.preference.PreferenceScreen?) : ShortDecoration() {
        @JvmField val paint: Paint? = null
        @JvmField var color = 0
        @JvmField var width = 0
        @JvmField var alpha = 0
        @JvmField var origin = 0
        @JvmField var position = 0
        @JvmField var previous = 0
        @JvmField val child = IntArray(2)
        @JvmField val item = IntArray(2)
        override fun e(canvas: Canvas?, recycler: Recycler?) = Unit
    }
    class FlatState
    class FlatHolder { @JvmField val itemView: View? = null }
    open class FlatDecoration {
        open fun draw(canvas: Canvas?, recycler: Recycler?, state: FlatState?) = Unit
        open fun offsets(rect: Rect?, view: View?, recycler: Recycler?, state: FlatState?) = Unit
    }
    open class FlatCoui(context: Context?) : FlatDecoration() {
        @JvmField var paint: Paint? = null
        @JvmField var divider: Drawable? = null
        @JvmField var color = 0
        @JvmField var alpha = 0
        @JvmField var width = 0
        override fun draw(canvas: Canvas?, recycler: Recycler?, state: FlatState?) = Unit
        fun expanded(canvas: Canvas?, holder: FlatHolder?) = Unit
    }
    class CustomCoui(context: Context?) : FlatCoui(context) {
        override fun draw(canvas: Canvas?, recycler: Recycler?, state: FlatState?) = Unit
    }
    class Fragment {
        @JvmField val divider = PreferenceDecoration()
    }
    class PreferenceDecoration : FlatDecoration() {
        @JvmField var divider: Drawable? = null
        @JvmField var height = 0
        @JvmField var afterLast = true
        override fun draw(canvas: Canvas?, recycler: Recycler?, state: FlatState?) = Unit
    }
    open class ShortDecoration {
        open fun e(canvas: Canvas?, recycler: Recycler?) = Unit
    }
    class COUIRecyclerView {
        class a(context: Context?) : ShortDecoration() {
            @JvmField val paint: Paint? = null
            @JvmField var color = 0
            @JvmField var width = 0
            @JvmField var alpha = 0
            @JvmField var origin = 0
            @JvmField var position = 0
            override fun e(canvas: Canvas?, recycler: Recycler?) = Unit
        }
    }
    class Recycler {
        class State
        class c0 { @JvmField val itemView: View? = null }
        class AmbiguousHolder {
            @JvmField val first: View? = null
            @JvmField val second: View? = null
        }
        open class Decoration {
            open fun onDrawOver(canvas: Canvas?, recycler: Recycler?, state: State?) = Unit
            open fun k(canvas: Canvas?, recycler: Recycler?, state: State?) = Unit
        }
    }

    class Coui {
        class COUIDividerItemDecoration(context: Context?) : Recycler.Decoration() {
            @JvmField var paint: Paint? = null
            @JvmField var divider: Drawable? = null
            @JvmField var color = 0
            @JvmField var alpha = 0
            @JvmField var width = 0
            override fun onDrawOver(canvas: Canvas?, recycler: Recycler?, state: Recycler.State?) = Unit
            fun drawExpandableDivider(canvas: Canvas?, holder: Recycler.c0?) = Unit
            fun drawDividerOuterBackground(canvas: Canvas?, recycler: Recycler?, child: View?) = Unit
        }

        class a(context: Context?) : Recycler.Decoration() {
            @JvmField var b: Paint? = null
            @JvmField var c: Drawable? = null
            @JvmField var d = 0
            @JvmField var e = 0
            @JvmField var f = 0
            override fun k(canvas: Canvas?, recycler: Recycler?, state: Recycler.State?) = Unit
            fun g(canvas: Canvas?, holder: Recycler.c0?) = Unit
            fun h(canvas: Canvas?, holder: Recycler.AmbiguousHolder?) = Unit
        }

        class SelectionDecoration(context: Context?) : Recycler.Decoration() {
            @JvmField var paint: Paint? = null
            @JvmField var color = 0
            @JvmField var alpha = 0
            @JvmField var width = 0
            override fun onDrawOver(canvas: Canvas?, recycler: Recycler?, state: Recycler.State?) = Unit
        }

        class Scrollbar(context: Context?) {
            @JvmField var paint: Paint? = null
            @JvmField var drawable: Drawable? = null
            @JvmField var color = 0
            @JvmField var alpha = 0
            @JvmField var width = 0
            fun onDrawOver(canvas: Canvas?, recycler: Recycler?, state: Recycler.State?) = Unit
        }
    }

    private fun targets() = CouiDividerTargets.resolve(Coui::class.java, Recycler::class.java)

    @Test fun flattenedWirelessClassesAndSubclassOverridesAreRecognized() {
        val targets = CouiDividerTargets.resolveDecoration(CustomCoui::class.java, Recycler::class.java)
        assertEquals(3, targets.size)
        assertTrue(targets.any { it.method.declaringClass == CustomCoui::class.java })
        assertEquals("itemView", targets.single { it.method.name == "expanded" }.itemView?.name)
    }

    @Test fun preferenceDecorationCanBeFoundThroughAFragmentField() {
        assertEquals(listOf("draw"), CouiDividerTargets.resolvePreference(Fragment::class.java, Recycler::class.java).map { it.method.name })
    }

    @Test fun accountPaintOnlyAndTwoArgumentDrawingAreRecognized() {
        val targets = CouiDividerTargets.resolve(COUIRecyclerView::class.java, Recycler::class.java)
        assertEquals(1, targets.size)
        assertEquals("e", targets.single().method.name)
        assertNull(targets.single().itemView)
    }

    @Test fun mergedOtaPreferenceDecorationDoesNotNeedADrawableOrSingleArgumentConstructor() {
        val targets = CouiDividerTargets.resolveDecoration(MergedPreference::class.java, Recycler::class.java)
        assertEquals(listOf("e"), targets.map { it.method.name })
    }

    @Test fun recognizesOriginalAndObfuscatedDrawingEntries() {
        val selected = targets().map { it.method.declaringClass.simpleName to it.method.name }.toSet()
        assertEquals(setOf(
            "COUIDividerItemDecoration" to "onDrawOver",
            "COUIDividerItemDecoration" to "drawExpandableDivider",
            "a" to "k",
            "a" to "g",
        ), selected)
    }

    @Test fun expandedRowsUseTheObfuscatedHolderTypeWithoutGuessingFields() {
        val selected = targets().single { it.method.name == "g" }
        assertEquals(Recycler.c0::class.java, selected.itemView?.declaringClass)
        assertEquals("itemView", selected.itemView?.name)
        assertNull(selected.itemView?.get(Recycler.c0()))
        assertTrue(targets().none { it.method.name == "h" })
    }

    @Test fun leavesBackgroundHelpersSelectionPaintingAndScrollbarsAlone() {
        assertTrue(targets().none { it.method.name == "drawDividerOuterBackground" })
        assertTrue(targets().none { it.method.declaringClass == Coui.SelectionDecoration::class.java })
        assertTrue(targets().none { it.method.declaringClass == Coui.Scrollbar::class.java })
        assertTrue(targets().filter { it.method.parameterCount == 3 }.all { it.itemView == null })
    }
}
