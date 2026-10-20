package com.mi.onextbox.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

internal object ModuleLauncherIcon {
    private fun component(context: Context) =
        ComponentName(context.packageName, "${context.packageName}.LauncherAlias")

    fun hidden(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    fun setHidden(context: Context, hidden: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component(context),
            if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.DONT_KILL_APP,
        )
    }
}
