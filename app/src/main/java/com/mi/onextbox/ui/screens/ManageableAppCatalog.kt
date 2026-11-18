package com.mi.onextbox.ui.screens

import android.content.Intent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

internal data class ManageableApp(val packageName: String, val uid: Int, val label: String)

internal object ManageableAppCatalog {
    private val cache = VersionedCatalogCache<String, List<ManageableApp>>()
    private val watching = AtomicBoolean()
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()

    fun invalidate() {
        val current = cache.invalidate()
        mutableRevision.update { maxOf(it, current) }
    }

    /** Call on an IO worker; only immutable names/UIDs/labels are retained. */
    fun load(context: Context, locale: Locale): List<ManageableApp> {
        watch(context.applicationContext)
        return cache.get(locale.toLanguageTag()) {
            val started = System.nanoTime()
            val pm = context.packageManager
            manageableApplications(pm).map { info ->
                ManageableApp(info.packageName, info.uid,
                    runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName))
            }.also {
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.event("catalog", "Manageable applications",
                    "${it.size} apps; ${locale.toLanguageTag()}", (System.nanoTime() - started) / 1_000_000)
            }
        }
    }

    private fun watch(app: Context) {
        if (!watching.compareAndSet(false, true)) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = invalidate()
        }
        try {
            ContextCompat.registerReceiver(app, receiver, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            ContextCompat.registerReceiver(app, receiver, IntentFilter().apply {
                addAction(Intent.ACTION_LOCALE_CHANGED)
                addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE)
                addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (error: Exception) {
            // A partially registered receiver must not leak when initialization is retried.
            runCatching { app.unregisterReceiver(receiver) }
            watching.set(false)
            throw error
        }
    }
}

/** Management lists must not depend on whether an app can currently be launched. */
internal fun manageableApplications(pm: PackageManager): List<ApplicationInfo> {
    val flags = PackageManager.MATCH_DISABLED_COMPONENTS or
        PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES
    val launcherPackages = pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), flags,
    ).mapNotNull { it.activityInfo?.packageName }.toSet()
    return pm.getInstalledApplications(flags).filter { info ->
        includeInManagementCatalog(
            installed = info.flags and ApplicationInfo.FLAG_INSTALLED != 0,
            system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            hasLauncher = info.packageName in launcherPackages,
        )
    }.distinctBy { it.packageName }
}

internal fun includeInManagementCatalog(installed: Boolean, system: Boolean, hasLauncher: Boolean): Boolean =
    installed && (!system || hasLauncher)
