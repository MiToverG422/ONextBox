package com.mi.onextbox.diagnostics

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import android.content.pm.PermissionInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.ui.settings.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class AppPermissionStatus(val name: String, val label: String, val granted: Boolean?, val runtime: Boolean)

@Suppress("DEPRECATION")
internal fun readAppPermissions(context: Context): List<AppPermissionStatus> {
    val manager = context.packageManager
    val info = manager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
    return info.requestedPermissions.orEmpty().mapIndexed { index, name ->
        val permission = runCatching { manager.getPermissionInfo(name, 0) }.getOrNull()
        val granted = when (name) {
            Manifest.permission.REQUEST_INSTALL_PACKAGES -> manager.canRequestPackageInstalls()
            Manifest.permission.POST_NOTIFICATIONS -> NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED)
            "android.permission.POST_PROMOTED_NOTIFICATIONS" -> if (Build.VERSION.SDK_INT >= 36)
                context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications() else false
            else -> (info.requestedPermissionsFlags?.getOrNull(index) ?: 0) and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
        }
        AppPermissionStatus(name, permission?.loadLabel(manager)?.toString() ?: name.substringAfterLast('.'), granted,
            permission?.protectionLevel?.and(PermissionInfo.PROTECTION_MASK_BASE) == PermissionInfo.PROTECTION_DANGEROUS)
    }.filterNot { it.name == "android.permission.POST_PROMOTED_NOTIFICATIONS" && Build.VERSION.SDK_INT < 36 }
}

@Composable internal fun DiagnosticPermissions() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    var permissions by remember { mutableStateOf<List<AppPermissionStatus>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<String?>(null) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { revision++ }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(revision) {
        loading = true
        runCatching { withContext(Dispatchers.IO) { readAppPermissions(context) } }
            .onSuccess { permissions = it; failure = null }
            .onFailure { failure = it.message ?: it.toString() }
        loading = false
    }
    fun settings(action: String, notification: Boolean = false) {
        val intent = Intent(action).apply {
            if (notification) putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            else data = Uri.parse("package:" + context.packageName)
        }
        runCatching { context.startActivity(intent) }.recoverCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
        }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
    }
    SettingsGroup {
        SettingsCardRow(stringResource(R.string.diagnostics_permissions), when {
            loading -> stringResource(R.string.diagnostics_loading)
            failure != null -> failure.orEmpty()
            else -> stringResource(R.string.diagnostics_permissions_count, permissions.count { it.granted == true }, permissions.size)
        })
        SettingsDivider()
        SettingsCardRow(stringResource(R.string.diagnostics_permission_settings), "", showArrow = true,
            onClick = { settings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) })
        SettingsDivider()
        SettingsCardRow(stringResource(R.string.diagnostics_notification_settings), "", showArrow = true,
            onClick = { settings(Settings.ACTION_APP_NOTIFICATION_SETTINGS, notification = true) })
    }
    SettingsGroup {
        permissions.forEachIndexed { index, permission ->
            if (index > 0) SettingsDivider()
            val status = stringResource(when (permission.granted) {
                true -> R.string.diagnostics_permission_granted
                false -> R.string.diagnostics_permission_denied
                null -> R.string.home_info_unknown
            })
            val special = permission.name == Manifest.permission.REQUEST_INSTALL_PACKAGES ||
                permission.name == Manifest.permission.POST_NOTIFICATIONS || permission.name == "android.permission.POST_PROMOTED_NOTIFICATIONS"
            val canRequest = special || permission.runtime
            SettingsCardRow(permission.label, status + "\n" + permission.name,
                showArrow = canRequest,
                onClick = if (!canRequest) null else ({
                    when (permission.name) {
                        Manifest.permission.REQUEST_INSTALL_PACKAGES -> settings(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        "android.permission.POST_PROMOTED_NOTIFICATIONS" -> settings("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS", notification = true)
                        Manifest.permission.POST_NOTIFICATIONS -> settings(Settings.ACTION_APP_NOTIFICATION_SETTINGS, notification = true)
                        else -> if (permission.granted == true) settings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            else request.launch(permission.name)
                    }
                }))
        }
    }
}
