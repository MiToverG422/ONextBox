package com.mi.onextbox.ui.screens

import com.mi.onextbox.notifications.NotificationAppState
import com.mi.onextbox.notifications.NotificationAppTarget
import com.mi.onextbox.notifications.NotificationBatchIssue
import com.mi.onextbox.notifications.NotificationOperationState
import com.mi.onextbox.notifications.NotificationPermissionKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationBatchUiStateTest {
    private val target = NotificationAppTarget("com.test.notifications", 10123)
    private val ready = NotificationBatchUiState(
        states = listOf(NotificationAppState(target, NotificationPermissionKind.entries.associateWith { 1 })),
        selected = setOf(target.packageName),
    )

    @Test fun actionsRequireASelectedAppWithLoadedPermissions() {
        assertTrue(ready.canApply)
        assertFalse(ready.copy(selected = emptySet()).canApply)
        assertFalse(ready.copy(states = emptyList()).canApply)
        assertFalse(ready.copy(selected = setOf("com.test.missing")).canApply)
    }

    @Test fun actionsRequireAtLeastOnePermission() {
        assertFalse(ready.copy(types = emptySet()).canApply)
        NotificationPermissionKind.entries.forEach { assertTrue(ready.copy(types = setOf(it)).canApply) }
    }

    @Test fun loadingAndApplyingDisableActions() {
        assertTrue(ready.copy(loading = true).busy)
        assertFalse(ready.copy(loading = true).canApply)
        assertTrue(ready.copy(operation = NotificationOperationState(busy = true)).busy)
        assertFalse(ready.copy(operation = NotificationOperationState(busy = true)).canApply)
    }

    @Test fun failedReadsCannotLeaveAnActiveActionButton() {
        assertFalse(ready.copy(readFailed = true).canApply)
    }

    @Test fun protectedAndUnavailableAppsDoNotEnableActions() {
        NotificationBatchIssue.entries.forEach { issue ->
            assertFalse(ready.copy(states = listOf(NotificationAppState(target, issue = issue))).canApply)
        }
    }
    @Test fun unsupportedSelectedTypeDoesNotEnableBatchActions() {
        val state = ready.copy(states = listOf(NotificationAppState(target, mapOf(NotificationPermissionKind.Banner to 1))))
        assertFalse(state.copy(types = setOf(NotificationPermissionKind.LiveActivity)).canApply)
        assertTrue(state.copy(types = setOf(NotificationPermissionKind.Banner, NotificationPermissionKind.LiveActivity)).canApply)
    }

}
