package com.mi.onextbox.notifications

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Binder
import android.os.IBinder
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.lsposed.hiddenapibypass.HiddenApiBypass

class NotificationBatchRootService : RootService() {
    private val operationLock = Any()
    private val backend by lazy {
        HiddenApiBypass.addHiddenApiExemptions("")
        PlatformNotificationBackend()
    }
    private val controller = object : INotificationBatchController.Stub() {
        override fun inspect(userId: Int, targets: String): String = synchronized(operationLock) {
            val identity = Binder.clearCallingIdentity()
            try {
                val apps = Json.decodeFromString<List<NotificationAppTarget>>(targets)
                require(apps.size <= 100 && apps.distinct().size == apps.size)
                val engine = NotificationBatchEngine(backend, userId)
                Json.encodeToString(apps.map(engine::inspect))
            } finally { Binder.restoreCallingIdentity(identity) }
        }

        override fun apply(userId: Int, mutations: String): String = synchronized(operationLock) {
            val identity = Binder.clearCallingIdentity()
            try {
                val plan = Json.decodeFromString<List<NotificationMutation>>(mutations)
                Json.encodeToString(NotificationBatchEngine(backend, userId).apply(plan))
            } finally { Binder.restoreCallingIdentity(identity) }
        }
    }

    override fun onBind(intent: Intent): IBinder = controller

    private inner class PlatformNotificationBackend : NotificationBatchBackend {
        private val vendor = Class.forName("android.app.OplusNotificationManager").getConstructor().newInstance()
        private val service = Class.forName("android.app.INotificationManager\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null,
                Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, "notification"))
        private val intClass = Int::class.javaPrimitiveType!!
        private val boolClass = Boolean::class.javaPrimitiveType!!

        override fun guard(target: NotificationAppTarget): NotificationBatchIssue? {
            val info = packageManager.javaClass.getMethod("getApplicationInfoAsUser", String::class.java, intClass, intClass)
                .invoke(packageManager, target.packageName,
                    PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES,
                    target.uid / 100000) as ApplicationInfo
            if (info.uid != target.uid || info.flags and ApplicationInfo.FLAG_INSTALLED == 0) {
                return NotificationBatchIssue.IdentityChanged
            }
            if (info.targetSdkVersion < 23 || packageManager.getPackagesForUid(target.uid)?.size != 1) return NotificationBatchIssue.Protected
            val permitted = callVendor("canModifyNotificationPermission", target) as Boolean
            return if (permitted) null else NotificationBatchIssue.Protected
        }

        override fun read(target: NotificationAppTarget, kind: NotificationPermissionKind): Int = when (kind) {
            NotificationPermissionKind.Notifications -> if (service.javaClass
                .getMethod("areNotificationsEnabledForPackage", String::class.java, intClass)
                .apply { isAccessible = true }
                .invoke(service, target.packageName, target.uid) as Boolean) 1 else 0
            NotificationPermissionKind.Banner -> if (callVendor("getAppBanner", target) as Boolean) 1 else 0
            NotificationPermissionKind.LockScreen -> callVendor("getAppVisibility", target) as Int
            NotificationPermissionKind.Silent -> callVendor("getStowOption", target) as Int
            NotificationPermissionKind.LiveActivity -> {
                requireLiveActivitySupport(target)
                if (service.javaClass.getMethod("appCanBePromoted", String::class.java, intClass)
                    .apply { isAccessible = true }.invoke(service, target.packageName, target.uid) as Boolean) 1 else 0
            }
        }.also { require(NotificationBatchEngine.validValue(kind, it)) }

        override fun write(target: NotificationAppTarget, kind: NotificationPermissionKind, value: Int) {
            when (kind) {
                NotificationPermissionKind.Notifications -> vendor.javaClass.getMethod("updateNotificationAction", String::class.java, intClass, Bundle::class.java)
                    .invoke(vendor, target.packageName, target.uid, Bundle().apply {
                        putBoolean("enabled", value == 1)
                        putBoolean("importanceLockForPackage", false)
                        putInt("reasonCode", 115)
                    })
                NotificationPermissionKind.Banner -> vendor.javaClass.getMethod("setAppBanner", String::class.java, intClass, boolClass)
                    .invoke(vendor, target.packageName, target.uid, value == 1)
                NotificationPermissionKind.Silent -> vendor.javaClass.getMethod("setStowOption", String::class.java, intClass, intClass)
                    .invoke(vendor, target.packageName, target.uid, value)
                NotificationPermissionKind.LiveActivity -> {
                    requireLiveActivitySupport(target)
                    service.javaClass.getMethod("setCanBePromoted", String::class.java, intClass, boolClass, boolClass)
                        .apply { isAccessible = true }.invoke(service, target.packageName, target.uid, value == 1, true)
                }
                NotificationPermissionKind.LockScreen -> vendor.javaClass.getMethod("setAppVisibility", String::class.java, intClass, intClass)
                    .invoke(vendor, target.packageName, target.uid, value)
            }
        }

        private fun requireLiveActivitySupport(target: NotificationAppTarget) {
            val info = packageManager.javaClass.getMethod("getPackageInfoAsUser", String::class.java, intClass, intClass)
                .invoke(packageManager, target.packageName, PackageManager.GET_PERMISSIONS, target.uid / 100000)
                as android.content.pm.PackageInfo
            check(info.requestedPermissions?.contains("android.permission.POST_PROMOTED_NOTIFICATIONS") == true)
        }

        private fun callVendor(name: String, target: NotificationAppTarget): Any = vendor.javaClass
            .getMethod(name, String::class.java, intClass).invoke(vendor, target.packageName, target.uid)!!
    }
}
