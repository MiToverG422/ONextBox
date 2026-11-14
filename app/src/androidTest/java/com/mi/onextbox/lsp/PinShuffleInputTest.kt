package com.mi.onextbox.lsp

import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import java.io.File
import java.util.Random
import org.junit.Assert.*
import org.junit.Test

class PinShuffleInputTest {
    private class NativeGrid(context: Context) : FrameLayout(context) {
        val cells = linkedMapOf<Int, IntArray>()
        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            for (i in 0 until childCount) {
                val key = getChildAt(i)
                val cell = requireNotNull(cells[key.id])
                key.layout(cell[1] * 100, cell[0] * 100, cell[1] * 100 + 80, cell[0] * 100 + 80)
            }
        }
        fun update() {
            requestLayout()
            measure(MeasureSpec.makeMeasureSpec(300, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(400, MeasureSpec.EXACTLY))
            layout(0, 0, 300, 400)
        }
    }

    @Test fun reorderedNativeKeysKeepTheirOwnDisplayedDigitAndClickValue() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val grid = NativeGrid(instrumentation.targetContext)
            val clicked = mutableListOf<String>()
            val numbers = (0..9).map { digit ->
                TextView(grid.context).apply {
                    id = View.generateViewId()
                    text = digit.toString()
                    contentDescription = digit.toString()
                    setOnClickListener { clicked += digit.toString() }
                    grid.cells[id] = if (digit == 0) intArrayOf(3, 1) else intArrayOf((digit - 1) / 3, (digit - 1) % 3)
                    grid.addView(this)
                }
            }
            val sides = listOf(0, 2).map { column ->
                TextView(grid.context).apply {
                    id = View.generateViewId()
                    text = if (column == 0) "Emergency" else "Delete"
                    grid.cells[id] = intArrayOf(3, column)
                    grid.addView(this)
                }
            }
            val positions = PinKeyPositions.capture(grid.cells, numbers.map { it.id })
            grid.update()
            val originals = numbers.map { it.left to it.top }
            val order = PinKeyPositions.randomOrder(Random(24))
            positions.apply(order)
            grid.update()
            numbers.forEachIndexed { digit, key ->
                assertEquals(originals[order[digit]], key.left to key.top)
                assertEquals(digit.toString(), key.text.toString())
                assertEquals(key.text.toString(), key.contentDescription)
                key.performClick()
                assertEquals(key.text.toString(), clicked.last())
            }
            assertEquals((0..9).map(Int::toString), clicked)
            assertEquals(0 to 300, sides[0].left to sides[0].top)
            assertEquals(200 to 300, sides[1].left to sides[1].top)
            assertEquals("Emergency", sides[0].text.toString())
            assertEquals("Delete", sides[1].text.toString())
            positions.restore()
            grid.update()
            assertEquals(originals, numbers.map { it.left to it.top })
        }
    }

    @Test fun installedSystemUiExposesTheNativeGridAndLifecycleContracts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getApplicationInfo("com.android.systemui",
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        val paths = listOf(info.sourceDir) + info.sharedLibraryFiles.orEmpty()
        val loader = PathClassLoader(paths.joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
        val targets = PinShuffleInputHooker.Targets(loader)
        assertTrue(View::class.java.isAssignableFrom(targets.dial))
        assertTrue(View::class.java.isAssignableFrom(targets.matrixField.type))
        assertTrue(Map::class.java.isAssignableFrom(targets.gridField.type))
        for (method in listOf(targets.attach, targets.detach, targets.visibility, targets.enter, targets.disappear)) {
            assertEquals(java.lang.Void.TYPE, method.returnType)
        }
        val key = loader.loadClass("com.coui.appcompat.passworddial.key.PasswordDialKeyView")
        assertTrue(TextView::class.java.isAssignableFrom(key))
    }
}
