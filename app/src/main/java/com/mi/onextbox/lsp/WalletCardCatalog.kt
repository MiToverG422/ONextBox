package com.mi.onextbox.lsp

import android.content.Context
import android.os.Bundle
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

internal object WalletCardCatalog {
    const val PREFIX = "wallet_art_card_"
    private val validId = Regex("[0-9a-f]{64}")
    fun prefs(context: Context) = context.getSharedPreferences("wallet_card_catalog", Context.MODE_PRIVATE)

    @Synchronized
    fun record(context: Context, group: String?, data: Bundle?): Bundle? {
        if (group == null || !validId.matches(group) || data == null) return null
        val label = data.getString("label")?.take(80)?.takeIf { it.isNotBlank() } ?: return null
        val source = data.getString("source")?.takeIf { it == "bag" || it == "swipe" } ?: return null
        val member = data.getString("member")?.takeIf(validId::matches) ?: return null
        val store = prefs(context)
        val aliases = context.getSharedPreferences("wallet_card_aliases", Context.MODE_PRIVATE)
        val prefix = "$group/"
        val route = "$prefix$source/$member"
        val rows = aliases.all.filterKeys { it.startsWith(prefix) }
        val sameSurface = rows.filterKeys { it.startsWith("$prefix$source/") }.values.toSet()
        val other = rows.filterKeys { !it.startsWith("$prefix$source/") }
        val sharedMember = other.entries.firstOrNull { it.key.endsWith("/$member") }?.value as? String
        val matchingNames = other.values.filterIsInstance<String>().distinct()
            .filter { it !in sameSurface && store.getString(it, null) == label }
        val existing = aliases.getString(route, null) ?: sharedMember ?: matchingNames.singleOrNull()
        val first = if (existing == null && rows.isEmpty()) preferredSavedKey(context, group, member) else null
        val id = existing ?: first ?: member
        if (!store.contains(id) && store.all.size >= 256) return null
        val newRoute = aliases.getString(route, null) == null
        if (newRoute && id != member && store.getString(member, null) == label &&
            member !in rows.values && preferredSavedKey(context, id, member) == member) {
            // Keep both existing images and make the more recently picked one visible on both screens.
            val saved = LspPreferenceStore.prefs(context)
            val newer = saved.getString(PREFIX + member, null)
            val older = saved.getString(PREFIX + id, null)
            if (!newer.isNullOrEmpty()) {
                saved.edit().putString(PREFIX + member, older.orEmpty()).putString(PREFIX + id, newer).apply()
                ModernXposedPreferenceSync.syncNow(context)
            }
        }
        if (newRoute) aliases.edit().putString(route, id).apply()
        val changes = store.edit().putString(id, label)
        // The earlier picker listed each presentation model as a separate card.
        for (old in listOf(group, member)) {
            if (old != id && old !in rows.values && store.getString(old, null) == label) changes.remove(old)
        }
        changes.apply()
        return Bundle().apply { putString("id", id) }
    }

    private fun preferredSavedKey(context: Context, group: String, member: String): String {
        val saved = LspPreferenceStore.prefs(context)
        return listOf(group, member).mapNotNull { key ->
            OtaArtwork.parse(saved.getString(PREFIX + key, null))?.let { art ->
                key to OtaArtwork.file(context, art.id, ArtworkTarget.WALLET).lastModified()
            }
        }.maxByOrNull { it.second }?.first ?: group
    }

    fun identity(type: String, biz: String, aid: String, digest: String, fallback: String, instance: String = ""): String? {
        val suffix = if (type == "1") digest.trim().removeSurrounding("(", ")") else ""
        val parts = if (instance.isNotBlank()) listOf("instance", type, instance)
        else if (biz.isNotBlank() || aid.isNotBlank() || suffix.isNotBlank())
            listOf("shared", type, biz, aid, suffix)
        else if (fallback.isNotBlank()) listOf("local", type, fallback) else return null
        val bytes = parts.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            (it.toInt() and 255).toString(16).padStart(2, '0')
        }
    }
}

internal class WalletCardReader(private val model: Class<*>, nfc: Boolean) {
    private fun accessor(field: String, getter: String): (Any) -> String {
        if (useGetters) {
            val method = runCatching { model.getMethod(getter) }.getOrNull()
            return { method?.invoke(it)?.toString().orEmpty() }
        }
        val prop = runCatching { model.getField(field) }.getOrNull()
        return { prop?.get(it)?.toString().orEmpty() }
    }
    private val useGetters = nfc
    private val type = accessor("cardType", "getCardType")
    private val biz = accessor("bizId", "getBizId")
    private val aid = accessor("aid", "getAid")
    private val digest = accessor("lastDigestText", "getLastDigest")
    private val label = accessor("nameText", "getDisplayName")
    private val fallback = accessor("flowNo", "getUniqueCardId")
    private val localId = if (nfc) accessor("", "getId") else null
    private val clickAction = if (!nfc) runCatching { model.getField("clickItemRunnable") }.getOrNull() else null
    private val payInfo = if (!nfc) runCatching {
        Class.forName("com.nearme.common.domain.rsp.PayCardInfo", false, model.classLoader)
    }.getOrNull() else null
    private val uniqueId = payInfo?.let { runCatching { it.getMethod("getUniqueCardId") }.getOrNull() }
    private val capturedInfo = mutableMapOf<Class<*>, java.lang.reflect.Field?>()

    private fun instance(card: Any): String {
        if (useGetters) return fallback(card)
        val owner = clickAction?.get(card) ?: return ""
        val field = capturedInfo.getOrPut(owner.javaClass) {
            owner.javaClass.declaredFields.singleOrNull { it.type == payInfo }?.apply { isAccessible = true }
        } ?: return ""
        return field.get(owner)?.let { uniqueId?.invoke(it) as? String }.orEmpty()
    }

    fun identify(image: android.widget.ImageView, value: Any?): String? {
        if (value == null || !model.isInstance(value)) return null
        val cardType = type(value)
        val local = fallback(value).ifBlank { localId?.invoke(value).orEmpty() }
        val group = WalletCardCatalog.identity(cardType, biz(value), aid(value), digest(value), local) ?: return null
        val title = label(value).trim().take(80).ifBlank { "#${group.take(6)}" }
        val instance = instance(value)
        val member = if (instance.isNotBlank()) WalletCardCatalog.identity(cardType, "", "", "", "", instance)!!
            else WalletCardCatalog.identity(cardType, "", "", "", local.ifBlank { title })!!
        val source = if (useGetters) "swipe" else "bag"
        val token = "$group/$source/$member"
        val resolver = image.context.applicationContext.contentResolver
        if (reported.put(token, title) != title) {
            val view = java.lang.ref.WeakReference(image)
            worker.execute {
                runCatching {
                    val data = Bundle().apply {
                        putString("source", source)
                        putString("member", member)
                        putString("label", title)
                    }
                    val id = resolver.call(OtaArtwork.uri(group, ArtworkTarget.WALLET), "cardSeen", group, data)
                        ?.getString("id") ?: error("Catalog unavailable")
                    resolved[token] = WalletCardCatalog.PREFIX + id
                    view.get()?.postInvalidateOnAnimation()
                }.onFailure { reported.remove(token, title) }
            }
        }
        return token
    }

    companion object {
        private val reported = ConcurrentHashMap<String, String>()
        private val resolved = ConcurrentHashMap<String, String>()
        private val worker = Executors.newSingleThreadExecutor()
        fun preference(token: String): String? = resolved[token]
    }
}
