package com.mi.onextbox.lsp

import android.content.Context
import android.content.Intent

internal object SettingsRealmeDependencies {
    fun packageName(feature: SettingsRealmeFeature): String? = when (feature) {
        SettingsRealmeFeature.SleepCapsule -> "com.realme.wellbeing"
        SettingsRealmeFeature.HeartRate -> "com.oplus.heartrate.detect"
        else -> null
    }

    fun launchIntent(context: Context, feature: SettingsRealmeFeature): Intent? {
        val pkg = packageName(feature) ?: return null
        return runCatching {
            val pm = context.packageManager
            if (!pm.getApplicationInfo(pkg, 0).enabled) return null
            val intent = pm.getLaunchIntentForPackage(pkg) ?: return null
            val activity = pm.resolveActivity(intent, 0)?.activityInfo ?: return null
            intent.takeIf { activity.enabled && activity.exported && activity.packageName == pkg }
        }.getOrNull()
    }

    fun available(context: Context, feature: SettingsRealmeFeature): Boolean =
        packageName(feature) == null || launchIntent(context, feature) != null

    fun canChange(checked: Boolean, dependencyAvailable: Boolean) = checked || dependencyAvailable
}
