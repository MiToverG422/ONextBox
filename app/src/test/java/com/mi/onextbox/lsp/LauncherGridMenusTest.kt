package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LauncherGridMenusTest {
    class NativeItem {
        @JvmField var columns = 4
        @JvmField var textRows = 6
        @JvmField var wordlessRows = 7
        @JvmField var selected = false
    }

    class NativeMenu {
        @JvmField var visible = arrayListOf<NativeItem>()
        @JvmField var all = arrayListOf<NativeItem>()
        @JvmField var index = -1
        fun recycler(): Any? = null
    }

    class NativeController(@JvmField val menu: NativeMenu)

    object NativeMethods {
        var withoutWords = false
        var migrating = false
        @JvmStatic fun wordless() = withoutWords
        @JvmStatic fun busy() = migrating
    }

    private lateinit var targets: LauncherGridTargets
    private lateinit var menus: LauncherGridMenus
    private var state = LauncherGridState()
    private var allowed = true

    @Before fun setup() {
        NativeMethods.withoutWords = false
        NativeMethods.migrating = false
        state = LauncherGridState()
        allowed = true
        val item = NativeItem::class.java
        val menu = NativeMenu::class.java
        val wordless = NativeMethods::class.java.getDeclaredMethod("wordless")
        val busy = NativeMethods::class.java.getDeclaredMethod("busy")
        val constructor = menu.getDeclaredConstructor()
        targets = LauncherGridTargets(arrayOf(constructor, wordless, wordless, wordless, item.getDeclaredConstructor(),
            item.getDeclaredField("columns"), item.getDeclaredField("textRows"), item.getDeclaredField("wordlessRows"),
            item.getDeclaredField("selected"), menu.getDeclaredField("visible"), menu.getDeclaredField("all"),
            menu.getDeclaredField("index"), wordless, menu.getDeclaredMethod("recycler"), wordless, wordless,
            item.getDeclaredField("selected"), wordless, emptyArray<java.lang.reflect.Field>(),
            arrayOf(busy), emptyArray<java.lang.reflect.Executable>(), wordless))
        menus = LauncherGridMenus(targets, { state }, { allowed })
    }

    private fun stockMenu(wordless: Boolean = false): NativeMenu {
        NativeMethods.withoutWords = wordless
        val original = NativeItem().apply { selected = true }
        return NativeMenu().apply {
            visible.add(original)
            all.add(original)
            index = 0
        }
    }

    @Test fun enablingAddsAnOptionWithoutSelectingOrRearrangingIt() {
        val menu = stockMenu()
        menus.creating(LauncherGrid()) {
            val pinned = menus.pinned(menu)!!
            menus.update(menu, pinned, false, LauncherGrid(4, 6))
        }
        assertEquals(2, menu.visible.size)
        assertEquals(0, menu.index)
        assertTrue(menu.visible[0].selected)
        assertFalse(menu.visible[1].selected)
        assertEquals(6, menu.visible[0].textRows)
        assertEquals(7, menu.visible[0].wordlessRows)
    }

    @Test fun inlinedMenuReusesTheItemsCreatedByTheLauncher() {
        val menu = stockMenu()
        val custom = NativeItem().apply { textRows = 7; wordlessRows = 8 }
        menu.all.add(custom)
        val inlined = LauncherGridTargets(Array<Any?>(LauncherGridTargets.SCHEMA_SIZE) { targets.values.getOrNull(it) }.apply {
            this[4] = null
            this[22] = NativeController::class.java.getDeclaredField("menu")
        })
        assertSame(menu, inlined.adapter(NativeController(menu)))
        val inlinedMenus = LauncherGridMenus(inlined, { state }, { allowed })
        inlinedMenus.creating(LauncherGrid(6, 10)) {
            inlinedMenus.update(menu, inlinedMenus.pinned(menu)!!, false, LauncherGrid(4, 6))
        }
        assertEquals(2, menu.all.size)
        assertEquals(2, menu.visible.size)
        assertSame(custom, menu.visible[1])
        assertEquals(LauncherGrid(4, 6), inlinedMenus.selected(menu, false))
    }

    @Test fun switchingLabelModeKeepsTheSelectedPairedCustomOption() {
        val menu = stockMenu()
        menus.creating(LauncherGrid()) { menus.pinned(menu) }
        val pinned = menus.pinned(menu)!!
        menus.update(menu, pinned, false, LauncherGrid(4, 7))
        val previous = menus.selectedOption(menu)!!
        NativeMethods.withoutWords = true
        menus.update(menu, pinned, true, previous.grid(true))
        assertEquals(1, menu.index)
        assertEquals(LauncherGrid(4, 8), menus.selected(menu, true))
        assertFalse(menu.visible[0].selected)
        assertEquals(7, menu.visible[1].textRows)
        assertEquals(8, menu.visible[1].wordlessRows)
        NativeMethods.withoutWords = false
        menus.update(menu, pinned, false, previous.grid(false))
        assertEquals(LauncherGrid(), menus.selected(menu, false))
    }

    @Test fun repeatRefreshDoesNotDuplicateTheCustomOption() {
        val menu = stockMenu()
        menus.creating(LauncherGrid(6, 10)) { menus.pinned(menu) }
        repeat(5) { menus.update(menu, menus.pinned(menu)!!, false, LauncherGrid(4, 6)) }
        assertEquals(15, menu.visible.size)
        assertEquals(16, menu.all.size)
    }

    @Test fun aNativeMenuWithoutAnAllOptionsCacheKeepsItsStockItemsOnRefresh() {
        val menu = stockMenu()
        val native = NativeItem().apply { columns = 5; textRows = 7; wordlessRows = 9 }
        menu.visible.add(native)
        menu.all.clear()
        menus.creating(LauncherGrid()) { menus.pinned(menu) }
        val pinned = menus.pinned(menu)!!
        menus.update(menu, pinned, false, LauncherGrid(4, 6))
        assertTrue(menu.all.contains(native))
        menu.visible.clear()
        menu.visible.addAll(menu.all)
        menus.update(menu, pinned, false, LauncherGrid(4, 6))
        assertTrue(menu.visible.contains(native))
        assertEquals(LauncherGrid(4, 6), menus.selected(menu, false))
        assertEquals(1, menu.visible.count { it.selected })
    }

    @Test fun changingTheMaximumDoesNotChangeAnAlreadyOpenMenusLimits() {
        state = LauncherGridState(true, LauncherGrid(5, 8))
        val menu = stockMenu()
        menus.creating(state.grid) { menus.pinned(menu) }
        state = state.copy(grid = LauncherGrid(4, 6))
        menus.update(menu, menus.pinned(menu)!!, false, LauncherGrid(5, 8))
        assertEquals(LauncherGrid(5, 8), menus.selected(menu, false))
        assertFalse(menus.supports(5, 8))
        assertTrue(menus.supports(4, 6))
    }

    @Test fun duplicateEffectiveNativeOptionsDoNotLoseTheSelection() {
        val menu = stockMenu()
        val duplicate = NativeItem().apply { textRows = 6; wordlessRows = 6 }
        menu.all.add(duplicate)
        menu.visible.add(duplicate)
        menus.creating(LauncherGrid()) { menus.pinned(menu) }
        val pinned = menus.pinned(menu)!!
        menus.update(menu, pinned, false, LauncherGrid())
        assertEquals(2, menu.visible.size)
        NativeMethods.withoutWords = true
        menu.visible.clear()
        menu.visible.addAll(menu.all)
        menus.update(menu, pinned, true, LauncherGrid(4, 8))
        assertEquals(3, menu.visible.size)
        assertEquals(2, menu.index)
        assertEquals(1, menu.visible.count { it.selected })
        assertEquals(4, menu.all.size)
        NativeMethods.withoutWords = false
        menus.update(menu, pinned, false, LauncherGrid())
        assertEquals(2, menu.visible.size)
        assertEquals(1, menu.index)
        assertEquals(LauncherGrid(), menus.selected(menu, false))
    }

    @Test fun aNewMenuGetsNewParametersWithoutChangingTheOpenMenu() {
        val first = stockMenu()
        val second = stockMenu()
        menus.creating(LauncherGrid(4, 7)) { menus.pinned(first) }
        menus.creating(LauncherGrid(5, 8)) { menus.pinned(second) }
        assertEquals(LauncherGrid(4, 7), menus.pinned(first)!!.grid)
        assertEquals(LauncherGrid(5, 8), menus.pinned(second)!!.grid)
    }

    @Test fun nestedCreationRestoresTheOuterMenuParameters() {
        val first = stockMenu()
        val second = stockMenu()
        menus.creating(LauncherGrid(4, 7)) {
            menus.creating(LauncherGrid(5, 8)) { menus.pinned(second) }
            menus.pinned(first)
        }
        assertEquals(LauncherGrid(4, 7), menus.pinned(first)!!.grid)
        assertNull(menus.pinned(NativeMenu()))
    }

    @Test fun hotReloadStateOnlyContainsNativeObjectsAndPrimitiveDimensions() {
        val menu = stockMenu()
        menus.creating(LauncherGrid(5, 8)) { menus.pinned(menu) }
        val snapshot = menus.capture()
        assertSame(menu, snapshot.single()[0])
        assertArrayEquals(intArrayOf(5, 8, 0), snapshot.single()[1] as IntArray)
        menus.clear()
        assertNull(menus.pinned(menu))
        menus.restore(snapshot)
        assertEquals(LauncherGrid(5, 8), menus.pinned(menu)!!.grid)
    }

    @Test fun migrationVetoesHotReload() {
        assertTrue(menus.canReload())
        NativeMethods.migrating = true
        assertFalse(menus.canReload())
    }

    @Test fun allBoundedCombinationsStaySelectableWithAndWithoutText() {
        for (columns in LauncherGridRules.MIN_COLUMNS..LauncherGridRules.MAX_COLUMNS) {
            for (rows in LauncherGridRules.MIN_ROWS..LauncherGridRules.MAX_ROWS) {
                val grid = LauncherGrid(columns, rows)
                val menu = stockMenu()
                menus.creating(grid) { menus.pinned(menu) }
                for (wordless in listOf(false, true, false)) {
                    NativeMethods.withoutWords = wordless
                    val expected = grid.copy(rows = grid.rows + if (wordless) 1 else 0)
                    menus.update(menu, menus.pinned(menu)!!, wordless, expected)
                    assertEquals(expected, menus.selected(menu, wordless))
                    assertEquals(1, menu.visible.count { it.selected })
                    assertEquals(menu.visible.size, menu.visible.map { targets.grid(it, wordless) }.distinct().size)
                }
            }
        }
    }

    @Test fun globalValidationUsesTheSwitchAndConfiguredMaximumWithoutAMenu() {
        assertFalse(menus.supports(5, 8))
        state = LauncherGridState(true, LauncherGrid(5, 8))
        assertTrue(menus.supports(5, 8))
        assertFalse(menus.supports(6, 8))
        NativeMethods.withoutWords = true
        assertTrue(menus.supports(5, 9))
        allowed = false
        assertFalse(menus.supports(5, 9))
        allowed = true
        state = state.copy(enabled = false)
        assertFalse(menus.supports(5, 9))
    }

    @Test fun directlyAppliedWordlessGridIsRetainedInTheNativeMenu() {
        val grid = LauncherGrid(3, 4)
        val menu = stockMenu(true)
        menus.creating(grid, true) { menus.pinned(menu) }
        menus.update(menu, menus.pinned(menu)!!, true, grid)
        assertEquals(grid, menus.selected(menu, true))
        assertEquals(1, menu.visible.count { it.selected })
        assertTrue(menu.all.any { it.columns == 3 && it.textRows == 4 && it.wordlessRows == 4 })
    }
}
