package com.mi.onextbox.lsp

import android.app.Notification
import android.app.PendingIntent
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import com.mi.onextbox.lsp.compat.ModernHookBridge
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Google Messages feature hooks. */
internal object GoogleMessagesHooker {
    private const val TAG = "ONextBox-GoogleMessages"
    private val hookedLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val geminiLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val copyReceivers = ConcurrentHashMap.newKeySet<Class<*>>()
    private val codeAfterCue = Regex(
        "(?:验证码|校验码|动态码|登录码|驗證碼|校驗碼|動態碼|登入碼|verification\\s*code|security\\s*code|passcode|otp|code)" +
            "[^0-9]{0,30}([0-9]{4,8})(?![0-9])",
        RegexOption.IGNORE_CASE,
    )
    private val codeBeforeCue = Regex(
        "(?<![0-9])([0-9]{4,8})(?![0-9])[^0-9]{0,30}" +
            "(?:验证码|校验码|动态码|登录码|驗證碼|校驗碼|動態碼|登入碼|verification\\s*code|security\\s*code|passcode|otp|code)",
        RegexOption.IGNORE_CASE,
    )

    fun hook(classLoader: ClassLoader) {
        if (!hookedLoaders.add(System.identityHashCode(classLoader))) return
        installCopyOtpHook()
        installAppHooks(classLoader)
        runCatching {
            val method = Application::class.java.getDeclaredMethod("attach", Context::class.java)
            ModernHookBridge.hookMethod(method, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = param.args.firstOrNull() as? Context ?: return
                    installAppHooks(context.classLoader)
                }
            })
        }.onFailure { HookLog.w(TAG, "Failed to defer hooks until application attachment", it) }
    }

    @Synchronized
    private fun installAppHooks(loader: ClassLoader) {
        val loaderId = System.identityHashCode(loader)
        val receiver = ModernReflect.findClassIfExists(GoogleMessagesOtpAction.RECEIVER, loader)
        if (receiver != null && receiver !in copyReceivers) {
            installCopyOtpReceiverHook(loader)
        }
        if (loaderId !in geminiLoaders && ModernReflect.findClassIfExists("dbat", loader) != null) {
            if (installGeminiHook(loader)) geminiLoaders.add(loaderId)
        }
    }

    private fun installCopyOtpReceiverHook(loader: ClassLoader) {
        val receiver = ModernReflect.findClassIfExists(GoogleMessagesOtpAction.RECEIVER, loader) ?: return
        runCatching {
            // onReceive is inherited. Guard the receiver instance as well as our action,
            // because the declaring superclass also handles unrelated Google broadcasts.
            val method = ModernReflect.findMethodExact(
                receiver, "onReceive", arrayOf(Context::class.java, Intent::class.java),
            )
            ModernHookBridge.hookMethod(method, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!receiver.isInstance(param.thisObject)) return
                    val intent = param.args.getOrNull(1) as? Intent ?: return
                    if (!GoogleMessagesOtpAction.ownsAction(
                            intent.action, intent.data?.scheme,
                            intent.hasExtra("message_id") || intent.hasExtra("conversation_id"),
                        )) return
                    // Always consume our action, even if disabled/invalid: it must never
                    // fall through into Google's message bookkeeping without real IDs.
                    param.result = null
                    runCatching {
                        if (!GoogleMessagesConfig.isEnabledInHook(GoogleMessagesConfig.Switch.CopyOtp)) return@runCatching
                        val context = param.args.firstOrNull() as? Context ?: return@runCatching
                        val code = intent.getStringExtra("otp_code")
                        if (!GoogleMessagesOtpAction.isValidCode(code)) return@runCatching
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                            ?: error("Clipboard service unavailable")
                        val clip = ClipData.newPlainText("Verification code", code)
                        clip.description.extras = PersistableBundle().apply {
                            putBoolean("android.content.extra.IS_SENSITIVE", true)
                        }
                        clipboard.setPrimaryClip(clip)
                        if (Build.VERSION.SDK_INT <= 32) {
                            val text = if (Locale.getDefault().language == "zh") "验证码已复制" else "Code copied"
                            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                        }
                        // Never log the OTP, sender, message IDs, or clipboard contents.
                        HookLog.i(TAG, "Module OTP copy completed")
                    }.onFailure { HookLog.w(TAG, "Module OTP copy failed") }
                }
            })
            copyReceivers.add(receiver)
            HookLog.i(TAG, "Module OTP copy receiver hook installed")
        }.onFailure { HookLog.w(TAG, "Failed to hook module OTP copy receiver", it) }
    }

    private fun installGeminiHook(loader: ClassLoader): Boolean {
        return hookMethod(loader, "dbat", "e") { param ->
            if (!GoogleMessagesConfig.isEnabledInHook(GoogleMessagesConfig.Switch.Gemini)) return@hookMethod
            if (param.result != false) return@hookMethod
            val flagName = runCatching {
                ModernReflect.callMethod(param.thisObject, "j") as? String
            }.getOrNull() ?: return@hookMethod
            if (flagName.endsWith("enable_penpal_conversation")) {
                param.result = true
            }
        }
    }

    private fun installCopyOtpHook() {
        runCatching {
            val method = Notification.Builder::class.java.getDeclaredMethod("build")
            ModernHookBridge.hookMethod(method, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!GoogleMessagesConfig.isEnabledInHook(GoogleMessagesConfig.Switch.CopyOtp)) return
                    val notification = param.result as? Notification ?: return
                    if (notification.category != Notification.CATEGORY_MESSAGE) return
                    val actions = notification.actions.orEmpty()
                    if (actions.any { it.title?.toString()?.let(::isCopyLabel) == true }) return
                    val context = ModernReflect.getObjectField(param.thisObject ?: return, "mContext") as? Context
                        ?: return
                    val code = findOtp(notification.extras) ?: return
                    val receiver = ModernReflect.findClassIfExists(
                        GoogleMessagesOtpAction.RECEIVER,
                        context.classLoader,
                    ) ?: return
                    // Do not offer a button if the compatible receiver hook isn't ready.
                    if (receiver !in copyReceivers) return
                    val intent = Intent(GoogleMessagesOtpAction.ACTION).apply {
                        setClass(context, receiver)
                        // Unique immutable actions prevent codes from different notifications
                        // overwriting each other; the URI must not contain an OTP/fingerprint.
                        data = Uri.parse("onextbox-otp://${UUID.randomUUID()}")
                        putExtra("otp_code", code)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    val label = if (Locale.getDefault().language == "zh") "复制" else "Copy"
                    val action = Notification.Action.Builder(
                        Icon.createWithResource(context, android.R.drawable.ic_menu_edit),
                        label,
                        pendingIntent,
                    ).build()
                    notification.actions = (actions.toList() + action).toTypedArray()
                }
            })
            HookLog.i(TAG, "SMS OTP notification action hook installed")
        }.onFailure { HookLog.w(TAG, "Failed to hook SMS OTP notification action", it) }
    }

    private fun findOtp(extras: Bundle?): String? {
        if (extras == null) return null
        val texts = mutableListOf<String>()
        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.let(texts::add)
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.let(texts::add)
        @Suppress("DEPRECATION")
        extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.forEach { message ->
            (message as? Bundle)?.getCharSequence("text")?.toString()?.let(texts::add)
        }
        return texts.asReversed().firstNotNullOfOrNull { text ->
            codeAfterCue.find(text)?.groupValues?.get(1)
                ?: codeBeforeCue.find(text)?.groupValues?.get(1)
        }
    }

    private fun isCopyLabel(label: String): Boolean =
        label.contains("复制", ignoreCase = true) ||
            label.contains("複製", ignoreCase = true) ||
            label.contains("copy", ignoreCase = true)

    private fun hookMethod(
        loader: ClassLoader,
        className: String,
        methodName: String,
        before: ((ModernMethodHook.MethodHookParam) -> Unit)? = null,
        after: (ModernMethodHook.MethodHookParam) -> Unit,
    ): Boolean {
        val type = ModernReflect.findClassIfExists(className, loader)
        if (type == null) {
            HookLog.w(TAG, "$className unavailable")
            return false
        }
        return runCatching {
            val handles = ModernHookBridge.hookAllMethods(type, methodName, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) { before?.invoke(param) }
                override fun afterHookedMethod(param: MethodHookParam) = after(param)
            })
            check(handles.isNotEmpty()) { "$className#$methodName unavailable" }
            HookLog.i(TAG, "$className#$methodName hooked")
            true
        }.onFailure { HookLog.w(TAG, "Failed to hook $className#$methodName", it) }.getOrDefault(false)
    }
}
