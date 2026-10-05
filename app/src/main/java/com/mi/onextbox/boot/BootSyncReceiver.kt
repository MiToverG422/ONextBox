package com.mi.onextbox.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.refresh.RefreshRateAutoApplier
import com.mi.onextbox.touch.TouchSamplingController
import com.mi.onextbox.ui.common.AppLogStore
import com.mi.onextbox.ui.onboarding.ActivationGate
import com.mi.onextbox.ui.settings.AppUpdater
import com.mi.onextbox.ui.settings.UpdateNotificationScheduler
import java.util.concurrent.Executors

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

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        worker.execute {
            try {
                runCatching {
                    AppLogStore.initialize(appContext)
                    if (!ActivationGate.isActivated(appContext)) {
                        UpdateNotificationScheduler.cancel(appContext)
                        AppLogStore.i("BootSync", "Skipped before first-run activation")
                        return@runCatching
                    }
                    LspConfig.syncTogglesForBoot(appContext)
                    AppLogStore.i("BootSync", "LSP toggles synced on $action")
                    if (action == Intent.ACTION_BOOT_COMPLETED) {
                        TouchSamplingController.applyOnBootIfEnabled(appContext)
                        RefreshRateAutoApplier.applyIfEnabled(appContext)
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
        private val worker = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ONextBox-BootSync").apply { isDaemon = true }
        }
    }
}
