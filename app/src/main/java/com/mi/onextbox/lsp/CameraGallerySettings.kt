package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object CameraGallerySettings {
    const val PACKAGE_NAME = "com.oplus.camera"
    const val FOLLOW_DEFAULT = "camera_follow_default_gallery"
    val keys = setOf(FOLLOW_DEFAULT)
    fun enabled(context: Context) = LspPreferenceStore.prefs(context).getBoolean(FOLLOW_DEFAULT, false)
    fun state(context: Context) = mapOf(FOLLOW_DEFAULT to enabled(context))
    fun active() = HookConfigSnapshot.boolean(FOLLOW_DEFAULT, false) == true
    fun set(context: Context, enabled: Boolean) =
        ModernXposedPreferenceSync.setBooleanAndSync(context, FOLLOW_DEFAULT, enabled)
}

internal object CameraGalleryRules {
    val nativeGalleries = setOf("com.coloros.gallery3d", "com.coloros.gallery", "com.oneplus.gallery")

    fun packageFromDefault(value: String?): String? = value?.trim()?.substringBefore('/')?.takeIf {
        it.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))
    }

    fun externalGallery(packageName: String?): Boolean = !packageName.isNullOrBlank() &&
        packageName !in nativeGalleries && packageName != "android" && packageName != CameraGallerySettings.PACKAGE_NAME

    fun ordinaryCamera(action: String?, secure: Boolean, locked: Boolean): Boolean = !secure && !locked &&
        action in setOf(null, "android.intent.action.MAIN", "android.media.action.STILL_IMAGE_CAMERA",
            "android.media.action.VIDEO_CAMERA")

    fun publishedMedia(scheme: String?, authority: String?, mime: String?, pending: Int, trashed: Int): Boolean =
        scheme == "content" && authority == "media" &&
            (mime?.startsWith("image/") == true || mime?.startsWith("video/") == true) && pending == 0 && trashed == 0
}
