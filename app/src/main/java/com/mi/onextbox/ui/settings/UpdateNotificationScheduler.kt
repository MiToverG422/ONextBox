package com.mi.onextbox.ui.settings

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mi.onextbox.MainActivity
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.AppLogStore
import com.mi.onextbox.ui.onboarding.ActivationGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object UpdateNotificationScheduler {
    const val EXTRA_OPEN_SOFTWARE_UPDATE = "com.mi.onextbox.extra.OPEN_SOFTWARE_UPDATE"

    private const val JOB_ID = 0x46425550
    private const val NOTIFICATION_ID = 0x46425550
    private const val CHANNEL_ID = "onextbox_software_updates"
    private const val RUNTIME_PREFS = "onextbox_updater_runtime"
    private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version"
    private const val CHECK_INTERVAL_MS = 2 * 60 * 60 * 1000L
    private const val CHECK_FLEX_MS = 30 * 60 * 1000L
    private val checking = AtomicBoolean(false)
    private val immediateExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ONextBox-UpdateCheck").apply { isDaemon = true }
    }

    fun configure(context: Context, enabled: Boolean) {
        if (enabled && ActivationGate.mayRunBackgroundNetwork(context)) {
            schedule(context)
            checkNow(context)
        } else {
            cancel(context)
        }
    }

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        if (!ActivationGate.mayRunBackgroundNetwork(appContext)) {
            cancel(appContext)
            return
        }
        if (!UpdateChannelPreference.getUpdateNotificationsEnabled(appContext)) {
            cancel(appContext)
            return
        }
        createNotificationChannel(appContext)
        val scheduler = appContext.getSystemService(JobScheduler::class.java)
        val pendingJob = scheduler.getPendingJob(JOB_ID)
        if (
            pendingJob?.intervalMillis == CHECK_INTERVAL_MS &&
            pendingJob.flexMillis == CHECK_FLEX_MS
        ) {
            return
        }
        val job = JobInfo.Builder(
            JOB_ID,
            ComponentName(appContext, UpdateCheckJobService::class.java),
        )
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(CHECK_INTERVAL_MS, CHECK_FLEX_MS)
            .setPersisted(true)
            .build()
        if (scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
            AppLogStore.w("Updater", "Unable to schedule update notification check")
        }
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        appContext.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        appContext.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    fun checkNow(context: Context) {
        val appContext = context.applicationContext
        if (!ActivationGate.mayRunBackgroundNetwork(appContext)) {
            cancel(appContext)
            return
        }
        immediateExecutor.execute {
            runBlocking {
                checkAndNotify(appContext)
            }
        }
    }

    suspend fun checkAndNotify(context: Context) {
        val appContext = context.applicationContext
        if (!ActivationGate.mayRunBackgroundNetwork(appContext)) {
            cancel(appContext)
            return
        }
        if (!UpdateChannelPreference.getUpdateNotificationsEnabled(appContext)) return
        if (!checking.compareAndSet(false, true)) return
        try {
            val update = AppUpdater.checkForUpdate(
                channel = UpdateChannelPreference.get(appContext),
                buildType = UpdateChannelPreference.getBuildType(appContext),
            ) ?: return
            val prefs = appContext.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
            if (prefs.getString(KEY_LAST_NOTIFIED_VERSION, null) == update.versionName) return
            if (!canPostNotifications(appContext)) return
            if (!showUpdateNotification(appContext, update)) return
            prefs.edit().putString(KEY_LAST_NOTIFIED_VERSION, update.versionName).apply()
            AppLogStore.i("Updater", "Update notification posted: ${update.versionName}")
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            AppLogStore.w("Updater", "Update notification check failed: ${error.javaClass.simpleName}")
        } finally {
            checking.set(false)
        }
    }

    fun cancelAvailableUpdateNotification(context: Context) {
        context.applicationContext
            .getSystemService(NotificationManager::class.java)
            .cancel(NOTIFICATION_ID)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.update_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.update_notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun showUpdateNotification(context: Context, update: AvailableUpdate): Boolean {
        createNotificationChannel(context)
        val openUpdateIntent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_SOFTWARE_UPDATE, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val openUpdatePendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openUpdateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = context.getString(R.string.update_notification_body, update.versionName)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_update_notification)
            .setContentTitle(context.getString(R.string.update_notification_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openUpdatePendingIntent)
            .addAction(
                R.drawable.ic_update_notification,
                context.getString(R.string.update_notification_action),
                openUpdatePendingIntent,
            )
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}

class UpdateCheckJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runningJob: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (!ActivationGate.mayRunBackgroundNetwork(applicationContext)) {
            UpdateNotificationScheduler.cancel(applicationContext)
            return false
        }
        runningJob = serviceScope.launch {
            try {
                UpdateNotificationScheduler.checkAndNotify(applicationContext)
                jobFinished(params, false)
            } catch (_: CancellationException) {
                // The system stopped the job and will decide when to run it again.
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        runningJob?.cancel()
        runningJob = null
        return true
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
