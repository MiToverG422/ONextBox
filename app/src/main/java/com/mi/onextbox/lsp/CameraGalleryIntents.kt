package com.mi.onextbox.lsp

import android.app.Activity
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.concurrent.ConcurrentHashMap

internal object CameraGalleryIntents {
    private val reported = ConcurrentHashMap.newKeySet<String>()
    private fun report(source: String, decision: String) {
        val message = "$source preview: $decision"
        if (reported.size < 16 && reported.add(message)) HookLog.i("ONextBox-Camera", message)
    }
    fun customizedGallery(context: Context): String? = runCatching {
        val owner = Class.forName("android.content.pm.OplusPackageManager")
        val manager = HiddenApiBypass.invoke(owner, null, "getOplusPackageManager", context.applicationContext)
            ?: return@runCatching null
        CameraGalleryRules.packageFromDefault(HiddenApiBypass.invoke(owner, manager,
            "getCustomizeDefaultApp", "gallery") as? String)
    }.getOrNull()

    fun galleryPreferenceIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        setDataAndType(Uri.parse("content://media"), "image/*")
    }

    fun selectedGallery(context: Context): String? {
        customizedGallery(context)?.let { return it }
        val preferred = context.packageManager.resolveActivity(galleryPreferenceIntent(), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
        if (preferred != null && preferred.packageName != "android" &&
            preferred.packageName != "com.android.intentresolver") return preferred.packageName
        return context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName?.takeUnless { it in setOf("android", "com.android.intentresolver") }
    }

    fun viewIntent(uri: Uri, mime: String, target: String): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        setPackage(target)
        clipData = ClipData.newRawUri("", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun tryOpen(activity: Activity, uri: Uri?, secure: Boolean, source: String = "ordinary"): Boolean {
        fun retain(reason: String): Boolean { report(source, "native retained ($reason)"); return false }
        if (uri == null || activity.isFinishing || activity.isDestroyed) return retain("media or activity unavailable")
        return runCatching {
            val keyguard = activity.getSystemService(KeyguardManager::class.java) ?: return retain("keyguard unavailable")
            if (!CameraGalleryRules.ordinaryCamera(activity.intent?.action, secure, keyguard.isKeyguardLocked))
                return retain("camera entry action=${activity.intent?.action}, secure=$secure, locked=${keyguard.isKeyguardLocked}")
            val target = selectedGallery(activity)?.takeIf(CameraGalleryRules::externalGallery) ?: return retain("default is native or unresolved")
            if (uri.scheme != "content" || uri.authority != MediaStore.AUTHORITY) return retain("non-public media URI")
            val mime = activity.contentResolver.getType(uri) ?: return retain("media type unavailable")
            val ready = activity.contentResolver.query(uri,
                arrayOf(MediaStore.MediaColumns.IS_PENDING, MediaStore.MediaColumns.IS_TRASHED),
                null, null, null)?.use { cursor ->
                cursor.moveToFirst() && CameraGalleryRules.publishedMedia(uri.scheme, uri.authority, mime,
                    cursor.getInt(0), cursor.getInt(1))
            } == true
            if (!ready) return retain("media is not published")
            val intent = viewIntent(uri, mime, target)
            val handler = activity.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
                ?: return retain("default gallery has no media viewer")
            if (handler.packageName != target || !handler.exported || !handler.enabled || !handler.applicationInfo.enabled)
                return retain("default gallery viewer unavailable")
            intent.setClassName(handler.packageName, handler.name)
            val options = ActivityOptions.makeBasic().apply {
                activity.display?.let { setLaunchDisplayId(it.displayId) }
            }
            activity.startActivity(intent, options.toBundle())
            report(source, "opened in $target")
            true
        }.onFailure { HookLog.w("ONextBox-Camera", "Default gallery unavailable; native preview retained", it) }
            .getOrDefault(false)
    }
}
