package com.mi.onextbox.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mi.onextbox.lsp.LspConfig
import android.os.UserManager
import com.mi.onextbox.touch.TouchSamplingController
import com.mi.onextbox.ui.common.AppLogStore
import com.mi.onextbox.ui.onboarding.ActivationGate
import com.mi.onextbox.ui.settings.AppUpdater
import com.mi.onextbox.ui.settings.UpdateNotificationScheduler
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BootSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        // Credential-encrypted selections are unavailable until the first unlock.
        val unlocked = context.getSystemService(UserManager::class.java).isUserUnlocked
        if (!unlocked && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        worker.execute {
            try {
                if (!unlocked) return@execute
                runCatching {
                    AppLogStore.initialize(appContext)
                    if (!ActivationGate.isActivated(appContext)) {
                        UpdateNotificationScheduler.cancel(appContext)
                        AppLogStore.i("BootSync", "Skipped before first-run activation")
                        return@runCatching
                    }
                    if (LspConfig.syncTogglesForBoot(appContext)) {
                        AppLogStore.i("BootSync", "LSP toggles synced on $action")
                    } else {
                        AppLogStore.w("BootSync", "LSP mirror synchronization failed on $action")
                    }
                    if (action == Intent.ACTION_BOOT_COMPLETED) {
                        TouchSamplingController.applyOnBootIfEnabled(appContext)
                    }
                    if (action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
                        AppUpdater.cleanupInstalledUpdate(appContext)
                        if (ActivationGate.mayRunBackgroundNetwork(appContext)) {
                            UpdateNotificationScheduler.schedule(appContext)
                        } else {
                            UpdateNotificationScheduler.cancel(appContext)
                        }
                    }
                }.onFailure { error ->
                    AppLogStore.w("BootSync", "Sync failed on $action: ${error.javaClass.simpleName}")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val worker = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "ONextBox-BootSync").apply { isDaemon = true }
        }

        internal fun migrateDisplayDensity(context: Context) {
            val appContext = context.applicationContext
            if (DisplayDensity.hasLegacyConfiguration(appContext)) scheduleDensityMigration(appContext, 20)
        }

        private fun scheduleDensityMigration(context: Context, attempts: Int) {
            worker.schedule({
                val result = runCatching { DisplayDensity.migrateLegacy(context) }
                if (result.getOrDefault(false)) return@schedule
                if (attempts > 1) scheduleDensityMigration(context, attempts - 1)
                else result.exceptionOrNull()?.let { android.util.Log.w("ONextBox-DPI", "DPI migration deferred", it) }
            }, 250, TimeUnit.MILLISECONDS)
        }
    }
}
