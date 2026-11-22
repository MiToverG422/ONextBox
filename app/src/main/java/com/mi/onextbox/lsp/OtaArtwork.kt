package com.mi.onextbox.lsp

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.Process
import com.mi.onextbox.BuildConfig
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

internal enum class ArtworkTarget(val key: String, val directory: String, val authority: String, val reader: String) {
    OTA("settings_ota_artwork", "ota-artwork", ".otaartwork", "com.android.settings"),
    WALLET("wallet_card_artwork", "wallet-artwork", ".walletartwork", "com.finshell.wallet"),
}

internal data class OtaCrop(val x: Float = .5f, val y: Float = .5f, val zoom: Float = 1f)

internal data class OtaArtwork(val id: String, val crop: OtaCrop) {
    fun encode() = listOf(id, crop.x, crop.y, crop.zoom).joinToString("|")

    companion object {
        const val KEY = "settings_ota_artwork"
        const val HIDE = "settings_ota_hide_info"
        private const val MAX_BYTES = 24 * 1024 * 1024
        private val idPattern = Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
        fun parse(value: String?): OtaArtwork? {
            val parts = value?.split('|') ?: return null
            if (parts.size != 4 || !idPattern.matches(parts.first())) return null
            val coords = parts.drop(1).map { it.toFloatOrNull()?.takeIf(Float::isFinite) ?: return null }
            return OtaArtwork(parts.first(), OtaCrop(coords[0].coerceIn(0f, 1f), coords[1].coerceIn(0f, 1f), coords[2].coerceIn(1f, 4f)))
        }

        fun file(context: Context, id: String, target: ArtworkTarget = ArtworkTarget.OTA): File {
            require(idPattern.matches(id))
            return File(File(context.filesDir, target.directory), id)
        }

        fun uri(id: String, target: ArtworkTarget = ArtworkTarget.OTA): Uri = Uri.Builder().scheme("content")
            .authority(BuildConfig.APPLICATION_ID + target.authority).appendPath(id).build()

        fun stage(context: Context, uri: Uri): File {
            val expiration = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            context.cacheDir.listFiles { file -> file.name.startsWith("ota-art-") && file.name.endsWith(".image") &&
                file.lastModified() < expiration }?.forEach(File::delete)
            val temp = File.createTempFile("ota-art-", ".image", context.cacheDir)
            try {
                requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(32768)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_BYTES)
                            output.write(buffer, 0, count)
                        }
                    }
                }
                decode(ImageDecoder.createSource(temp), 512)
                return temp
            } catch (e: Exception) {
                temp.delete()
                throw e
            }
        }

        fun decode(source: ImageDecoder.Source, maxEdge: Int): Drawable =
            ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                require(info.mimeType in setOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/heif", "image/heic", "image/avif"))
                val size = info.size
                require(size.width > 0 && size.height > 0 && size.width.toLong() * size.height <= 80_000_000L)
                val scale = minOf(1.0, maxEdge.toDouble() / maxOf(size.width, size.height))
                decoder.setTargetSize(maxOf(1, (size.width * scale).roundToInt()), maxOf(1, (size.height * scale).roundToInt()))
            }

        @Synchronized
        fun save(context: Context, input: File?, existing: OtaArtwork?, crop: OtaCrop, artworkTarget: ArtworkTarget = ArtworkTarget.OTA,
                 preferenceKey: String = artworkTarget.key): OtaArtwork? {
            val prefs = LspPreferenceStore.prefs(context)
            val old = parse(prefs.getString(preferenceKey, null))
            val id = if (input == null) existing?.id else UUID.randomUUID().toString()
            val next = id?.let { OtaArtwork(it, crop) }
            val target = if (input != null && next != null) file(context, next.id, artworkTarget) else null
            try {
                if (target != null) {
                    target.parentFile?.mkdirs()
                    input!!.copyTo(target)
                } else if (next != null) {
                    require(file(context, next.id, artworkTarget).isFile)
                }
                ModernXposedPreferenceSync.setStringAndSync(context, preferenceKey, next?.encode().orEmpty())
                if (next != null && artworkTarget == ArtworkTarget.WALLET) LspFrameworkService.requestScopes(setOf(WalletOptions.PACKAGE))
            } catch (e: Exception) {
                target?.delete()
                throw e
            }
            if (old != null && old.id != next?.id) file(context, old.id, artworkTarget).delete()
            return next
        }
    }
}

class WalletArtworkProvider : OtaArtworkProvider() {
    internal override val target = ArtworkTarget.WALLET

    override fun call(method: String, arg: String?, extras: android.os.Bundle?): android.os.Bundle? {
        val app = requireNotNull(context)
        if (Binder.getCallingUid() != app.packageManager.getPackageUid(target.reader, 0))
            throw SecurityException("Only Wallet may use a swipe session")
        if (method == "cardSeen") {
            return WalletCardCatalog.record(app, arg, extras)
        }
        val token = extras?.getBinder("session") ?: return null
        when (method) {
            "enterSwipe" -> if (LspPreferenceStore.prefs(app).getBoolean(WalletOptions.NFC.key, false)) WalletNfcLease.enter(token)
            "leaveSwipe" -> WalletNfcLease.leave(token)
            else -> return null
        }
        return android.os.Bundle.EMPTY
    }
}

open class OtaArtworkProvider : ContentProvider() {
    internal open val target = ArtworkTarget.OTA
    override fun onCreate() = true
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val app = requireNotNull(context)
        val uid = Binder.getCallingUid()
        if (uid != Process.myUid() && app.packageManager.getPackageUid(target.reader, 0) != uid)
            throw SecurityException("Caller cannot read this artwork")
        require(mode == "r" && uri.pathSegments.size == 1)
        val id = uri.pathSegments.single()
        if (target == ArtworkTarget.WALLET && LspPreferenceStore.prefs(app).all.none { (key, value) ->
                key.startsWith(WalletCardCatalog.PREFIX) && OtaArtwork.parse(value as? String)?.id == id
            })
            throw java.io.FileNotFoundException()
        val file = OtaArtwork.file(app, id, target)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String = "image/*"
    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
}
