package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.HotReloadResourceCoordinator
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class DataSimSignalReloadStateTest {
    @Test fun stackedSignalEmptySubscriptionsPassTheActualHandoverValidation() {
        val context = Any()
        val controller = Any()
        val state = DataSimSignalReloadState.capture(context, listOf(controller to emptyList()))
        val coordinator = HotReloadResourceCoordinator { it() }
        coordinator.register("data-sim", capture = { state })
        val exported = coordinator.prepare()["data-sim"] as Map<*, *>
        assertSame(context, exported["context"])
        val rows = exported["controllers"] as List<*>
        val row = rows.single() as List<*>
        assertSame(controller, row[0])
        assertEquals(ArrayList::class.java, row[1]!!.javaClass)
        assertTrue((row[1] as List<*>).isEmpty())
    }

    @Test fun evenTheNoControllerSnapshotUsesOnlyPlatformContainers() {
        val state = DataSimSignalReloadState.capture(null, emptyList())
        val coordinator = HotReloadResourceCoordinator { it() }
        coordinator.register("data-sim", capture = { state })
        val exported = coordinator.prepare()["data-sim"] as Map<*, *>
        assertNull(exported["context"])
        assertEquals(ArrayList::class.java, exported["controllers"]!!.javaClass)
        assertTrue((exported["controllers"] as List<*>).isEmpty())
    }

    @Test fun copiedIdsKeepControllerIdentityAndCannotChangeTheLiveSource() {
        val first = Any()
        val second = Any()
        val source = arrayListOf(3, 7)
        val state = DataSimSignalReloadState.capture(null,
            listOf(first to source, second to listOf(11)))
        source.clear()
        val rows = state["controllers"] as List<*>
        val firstRow = rows[0] as List<*>
        val secondRow = rows[1] as List<*>
        assertSame(first, firstRow[0])
        assertSame(second, secondRow[0])
        assertEquals(listOf(3, 7), firstRow[1])
        assertEquals(listOf(11), secondRow[1])
    }

    @Test fun theActualControllerOwnerUsesTheNeutralSnapshotExporter() {
        val source = listOf(File("src/main/java/com/mi/onextbox/lsp/DataSimSignalHooker.kt"),
            File("app/src/main/java/com/mi/onextbox/lsp/DataSimSignalHooker.kt"))
            .first { it.isFile }.readText()
        val capture = source.substringAfter("fun capture(): Map<String, Any?>")
            .substringBefore("fun detach()")
        assertTrue(capture.contains("DataSimSignalReloadState.capture(context,"))
        assertFalse(capture.contains("listOf(it.key, it.value.source)"))
    }
}
