package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class LauncherCategoryModeScopeTest {
    @Test fun `native compatibility only applies during the independent panel call`() {
        val scope = LauncherCategoryModeScope()
        assertFalse(scope.active)
        assertEquals("category", scope.withDrawer {
            assertTrue(scope.active)
            "category"
        })
        assertFalse(scope.active)
    }

    @Test fun `nested search and state callbacks retain the outer context`() {
        val scope = LauncherCategoryModeScope()
        scope.withDrawer {
            scope.withDrawer { assertTrue(scope.active) }
            assertTrue(scope.active)
        }
        assertFalse(scope.active)
    }

    @Test fun `native errors cannot leave the desktop mode overridden`() {
        val scope = LauncherCategoryModeScope()
        assertThrows(IllegalStateException::class.java) {
            scope.withDrawer { throw IllegalStateException("native callback failed") }
        }
        assertFalse(scope.active)
        scope.withDrawer {
            assertThrows(IllegalStateException::class.java) {
                scope.withDrawer { throw IllegalStateException("nested callback failed") }
            }
            assertTrue(scope.active)
        }
        assertFalse(scope.active)
    }

    @Test fun `other threads still observe the real desktop mode`() {
        val scope = LauncherCategoryModeScope()
        val otherThread = AtomicBoolean(true)
        scope.withDrawer {
            val thread = Thread { otherThread.set(scope.active) }
            thread.start()
            thread.join(1000)
            assertFalse(thread.isAlive)
            assertFalse(otherThread.get())
            assertTrue(scope.active)
        }
        assertFalse(scope.active)
    }
}
