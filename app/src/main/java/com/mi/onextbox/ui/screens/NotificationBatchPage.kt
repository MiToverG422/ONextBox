package com.mi.onextbox.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.notifications.NotificationAppState
import com.mi.onextbox.notifications.NotificationAppTarget
import com.mi.onextbox.notifications.NotificationBatchClient
import com.mi.onextbox.notifications.NotificationBatchEngine
import com.mi.onextbox.notifications.NotificationBatchOperations
import com.mi.onextbox.notifications.NotificationPermissionKind
import com.mi.onextbox.ui.common.CouiConfirmDialog
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

@Composable
internal fun NotificationBatchPage(modifier: Modifier, backdrop: LayerBackdrop?, onBack: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val operation by NotificationBatchOperations.state.collectAsState()
    var apps by remember { mutableStateOf(emptyList<NotificationBatchApp>()) }
    var states by remember { mutableStateOf(emptyList<NotificationAppState>()) }
    var loading by remember { mutableStateOf(true) }
    var readFailed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var notifications by rememberSaveable { mutableStateOf(true) }
    var banners by rememberSaveable { mutableStateOf(true) }
    var lockScreen by rememberSaveable { mutableStateOf(true) }
    var silent by rememberSaveable { mutableStateOf(false) }
    var liveActivity by rememberSaveable { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableIntStateOf(0) }
    val types = buildSet {
        if (notifications) add(NotificationPermissionKind.Notifications)
        if (banners) add(NotificationPermissionKind.Banner)
        if (lockScreen) add(NotificationPermissionKind.LockScreen)
        if (silent) add(NotificationPermissionKind.Silent)
        if (liveActivity) add(NotificationPermissionKind.LiveActivity)
    }
    val busy = loading || operation.busy
    val plan = NotificationBatchEngine.plan(states, selected.toSet(), types, confirmation == 1)

    LaunchedEffect(context, locale, reload, operation.revision) {
        if (operation.busy) return@LaunchedEffect
        loading = true
        readFailed = false
        try {
            val catalog = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val collator = Collator.getInstance(locale)
                manageableApplications(pm)
                    .map { NotificationBatchApp(NotificationAppTarget(it.packageName, it.uid),
                        runCatching { pm.getApplicationLabel(it).toString() }.getOrDefault(it.packageName)) }
                    .sortedWith { a, b -> collator.compare(a.label, b.label) }
            }
            apps = catalog
            states = NotificationBatchClient.inspect(context, catalog.map { it.target })
            selected = selected.filter { name -> states.any { it.target.packageName == name && it.issue == null } }
            withContext(Dispatchers.IO) { NotificationBatchOperations.loadUndo(context) }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { readFailed = true; states = emptyList() }
        finally { loading = false }
    }

    NotificationBatchLayout(
        modifier = modifier, backdrop = backdrop, onBack = onBack,
        ui = NotificationBatchUiState(apps, states, selected.toSet(), types, operation, loading, readFailed),
        onRefresh = { reload++ }, onAction = { confirmation = it },
        onSelectionChange = { selected = it.toList() },
        onTypesChange = {
            notifications = NotificationPermissionKind.Notifications in it
            banners = NotificationPermissionKind.Banner in it
            lockScreen = NotificationPermissionKind.LockScreen in it
            silent = NotificationPermissionKind.Silent in it
            liveActivity = NotificationPermissionKind.LiveActivity in it
        },
    )
    val actionLabel = stringResource(when (confirmation) {
        1 -> R.string.notification_batch_enable
        2 -> R.string.notification_batch_disable
        else -> R.string.notification_batch_undo
    })
    val typeLabels = types.map { stringResource(notificationPermissionLabel(it)) }.joinToString(" · ")
    CouiConfirmDialog(show = confirmation != 0, title = actionLabel,
        summary = if (confirmation == 3) stringResource(R.string.notification_batch_undo_confirm)
        else stringResource(R.string.notification_batch_confirm, selected.size, typeLabels),
        negativeText = stringResource(R.string.config_clear_confirm_cancel), positiveText = actionLabel,
        onDismissRequest = { confirmation = 0 }, onPositive = {
            val undoing = confirmation == 3
            if (!busy) NotificationBatchOperations.start(context,
                if (undoing) NotificationBatchEngine.undo(operation.undo) else plan, undoing)
            confirmation = 0
        })
}
