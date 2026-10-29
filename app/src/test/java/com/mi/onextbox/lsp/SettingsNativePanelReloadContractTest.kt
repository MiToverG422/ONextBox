package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** COUI host classes are unavailable in JVM tests; guard the verified teardown contract. */
class SettingsNativePanelReloadContractTest {
    @Test fun nativeRowsOwnPressEffectsWithoutHighlightingTheSharedWrapper() {
        val source = source()
        assertTrue(source.contains("call(view, \"consumeDispatchingEventForState\", false)"))
        assertTrue(source.contains("view.isClickable = false"))
        assertTrue(source.contains("call(view, \"setBackgroundAnimationEnabled\", false)"))
        val bind = source.substringAfter("private fun bind(").substringBefore("private fun applyRowInsets(")
        assertTrue(bind.contains("call(root, \"consumeDispatchingEventForState\", false)"))
        assertTrue(bind.contains("call(root, \"setBackgroundAnimationEnabled\", true)"))
        assertFalse(bind.contains("setOnClickListener"))
        assertFalse(bind.contains("setOnLongClickListener"))
        assertFalse(source.contains("\"setBackgroundAnimationEnabled\", expanded"))
        assertFalse(source.contains("\"setBackgroundAnimationEnabled\", !expanded"))
        val change = source.substringAfter("private fun changeState(").substringBefore("private fun adapter()")
        assertFalse(change.contains("resetBackgroundAnimation"))
    }

    @Test fun headerAndLastRowCoverCardInsetsAndRecyclingKeepsTheExpansionGuard() {
        val source = source()
        val bind = source.substringAfter("private fun bind(").substringBefore("private fun applyRowInsets(")
        assertTrue(bind.contains("if (holders[holder] !== preference) call(root, \"resetBackgroundAnimation\")"))
        assertTrue(bind.contains("applyRowInsets(root, preference)"))
        val insets = source.substringAfter("private fun applyRowInsets(").substringBefore("private fun connector()")
        assertTrue(insets.contains("val top = if (preference === header) cardTopInset else 0"))
        assertTrue(insets.contains("preference === header || preference === rows.lastOrNull()"))
        assertTrue(insets.contains("padding[1] + top, padding[2], padding[3] + bottom"))
        assertTrue(source.contains("list.minimumHeight = root.measuredHeight + 1"))
    }

    @Test fun disposalBypassesTheNullRejectingCouiAdapterOverloads() {
        val detach = source().substringAfter("private fun detachAdapter() {")
            .substringBefore("private fun refreshAll()")
        assertTrue(detach.contains("type(\"androidx.recyclerview.widget.RecyclerView\")"))
        assertTrue(detach.contains(".setType(XposedInterface.Invoker.Type.ORIGIN)"))
        assertTrue(detach.contains(".invokeSpecial(list, *arrayOfNulls<Any>(1))"))
        assertFalse(detach.contains(".invoke(list,"))
        assertFalse(detach.contains("call(list, \"setAdapter\""))
        assertFalse(detach.contains("call(oldConnector, \"setExpandableListAdapter\""))
    }

    @Test fun nativeRecyclingPrecedesClearingTheOldAdapterAndConnector() {
        val detach = source().substringAfter("private fun detachAdapter() {")
            .substringBefore("private fun refreshAll()")
        val recycling = detach.indexOf(".invokeSpecial(list,")
        val unregister = detach.indexOf("call(oldAdapter, \"unregisterAdapterDataObserver\", observer)")
        val clearProxy = detach.indexOf("setObjectField(oldConnector, \"mExpandableListAdapter\", null)")
        val clearAdapter = detach.indexOf("setObjectField(list, \"mAdapter\", null)")
        val clearConnector = detach.indexOf("setObjectField(list, \"mConnector\", null)")
        assertTrue(recycling >= 0 && unregister > recycling)
        assertTrue(clearProxy > unregister && clearAdapter > clearProxy)
        assertTrue(clearConnector > clearAdapter)
        assertTrue(detach.indexOf("view.removeView(list)") > clearConnector)
    }

    private fun source(): String = listOf(
        File("src/main/java/com/mi/onextbox/lsp/SettingsNativeAppInfoPanel.kt"),
        File("app/src/main/java/com/mi/onextbox/lsp/SettingsNativeAppInfoPanel.kt"),
    ).first { it.isFile }.readText()
}
