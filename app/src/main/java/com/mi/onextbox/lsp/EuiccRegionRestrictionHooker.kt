package com.mi.onextbox.lsp

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ScrollView
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ColorOS 17 eSIM compatibility hooks.
 *
 * DownloadChecker.c(String) performs the complete check. Its first step, d(String), contains the
 * network/location/server-region gate; c(String) then keeps enforcing the profile-count limit and
 * the device/eSIM binding result. Hooking d instead of c preserves those later safeguards by
 * default; the separate profile-limit option only overrides q(), leaving binding untouched.
 *
 * The C17 CN LPA also receives the server's confirmation-code request but its ViewModel only logs
 * the callback. AgentService consequently waits forever on a CountDownLatch. The companion hook
 * restores that missing UI and writes the user's response back to the existing OEM transaction.
 */
internal object EuiccRegionRestrictionHooker {
    private const val TAG = "ONextBox-Euicc"
    private const val DOWNLOAD_CHECKER_CLASS = "com.oplus.euicc.DownloadChecker"
    private const val COUNTRY_PRECHECK_METHOD = "d"
    private const val PROFILE_LIMIT_CHECK_METHOD = "q"
    private const val DOWNLOAD_ACTIVITY_CLASS =
        "com.oplus.euicc.lui.activity.EsimDownloadActivity"
    private const val DOWNLOAD_VIEW_MODEL_CLASS =
        "com.oplus.euicc.lui.viewmodel.EsimDownloadViewModel"
    private const val AGENT_IMPLEMENTATION_CLASS =
        "com.oplus.euicc.sdk.profileassistant.internal.b"
    private const val CONFIRMATION_CALLBACK_CLASS =
        "com.oplus.euicc.sdk.profileassistant.Agent\$ConfirmationCodeResponseCallback"
    private const val AGENT_SERVICE_CLASS =
        "com.oplus.euicc.sdk.profileassistant.internal.AgentService"
    private const val COUI_DIALOG_BUILDER_CLASS =
        "com.coui.appcompat.dialog.COUIAlertDialogBuilder"
    private const val COUI_INPUT_VIEW_CLASS =
        "com.coui.appcompat.edittext.COUIInputView"
    private const val COUI_EDIT_TEXT_CLASS =
        "com.coui.appcompat.edittext.COUIEditText"

    private val loggedBypass = AtomicBoolean(false)
    private val loggedCountryBlock = AtomicBoolean(false)
    private val loggedProfileLimitBypass = AtomicBoolean(false)
    private val stateLock = Any()

    @Volatile
    private var activityReference = WeakReference<Activity>(null)

    @Volatile
    private var dialogReference = WeakReference<Dialog>(null)

    private var pendingConfirmation: PendingConfirmation? = null
    private var pendingDownload: PendingDownload? = null
    private val replayingDownload = ThreadLocal<ReplayDownload?>()

    fun hook(classLoader: ClassLoader?): Boolean {
        val countryHooked = hookCountryPrecheck(classLoader)
        val profileLimitHooked = hookProfileLimitCheck(classLoader)
        val confirmationHooked = hookConfirmationCodeFlow(classLoader)
        return countryHooked || profileLimitHooked || confirmationHooked
    }

    private fun hookCountryPrecheck(classLoader: ClassLoader?): Boolean {
        val checkerClass = findClass(DOWNLOAD_CHECKER_CLASS, classLoader)
        if (checkerClass == null) {
            HookLog.w(TAG, "ColorOS 17 DownloadChecker was not found")
            return false
        }

        val method = checkerClass.declaredMethods.singleOrNull { candidate ->
            candidate.name == COUNTRY_PRECHECK_METHOD &&
                !Modifier.isStatic(candidate.modifiers) &&
                candidate.returnType == Int::class.javaPrimitiveType &&
                candidate.parameterTypes.contentEquals(arrayOf(String::class.java))
        }
        if (method == null) {
            HookLog.w(TAG, "Compatible DownloadChecker.d(String) target was not found")
            return false
        }

        return runCatching {
            ModernHookRegistry.installFast(
                key = "euicc:download-country-precheck:${method.toGenericString()}",
                executable = method,
                hooker = XposedInterface.Hooker { chain ->
                    if (!LspConfig.isEsimRegionRestrictionBypassEnabledXposed()) {
                        return@Hooker chain.proceed()
                    }
                    if (
                        CurrentNetworkCountryGuard.isChinaInCurrentProcess() &&
                        !LspConfig.isEsimRegionRestrictionOverrideEnabledXposed()
                    ) {
                        if (loggedCountryBlock.compareAndSet(false, true)) {
                            HookLog.w(TAG, "Region bypass denied on a CN mobile or Wi-Fi network")
                        }
                        return@Hooker chain.proceed()
                    }
                    if (loggedBypass.compareAndSet(false, true)) {
                        HookLog.i(TAG, "Bypassing the ColorOS eSIM download region precheck")
                    }
                    0
                },
            )
            HookLog.i(TAG, "Installed ColorOS 17 eSIM region-precheck hook")
            true
        }.onFailure { throwable ->
            HookLog.e(TAG, "Failed to install the eSIM region-precheck hook", throwable)
        }.getOrDefault(false)
    }

    private fun hookProfileLimitCheck(classLoader: ClassLoader?): Boolean {
        val checkerClass = findClass(DOWNLOAD_CHECKER_CLASS, classLoader)
        if (checkerClass == null) {
            HookLog.w(TAG, "ColorOS 17 DownloadChecker was not found for profile-limit bypass")
            return false
        }
        val method = checkerClass.declaredMethods.singleOrNull { candidate ->
            candidate.name == PROFILE_LIMIT_CHECK_METHOD &&
                !Modifier.isStatic(candidate.modifiers) &&
                candidate.returnType == Boolean::class.javaPrimitiveType &&
                candidate.parameterCount == 0
        }
        if (method == null) {
            HookLog.w(TAG, "Compatible DownloadChecker.q() profile-limit target was not found")
            return false
        }

        return runCatching {
            ModernHookRegistry.installFast(
                key = "euicc:profile-limit-check:${method.toGenericString()}",
                executable = method,
                hooker = XposedInterface.Hooker { chain ->
                    if (!LspConfig.isEsimProfileLimitBypassEnabledXposed()) {
                        return@Hooker chain.proceed()
                    }
                    if (loggedProfileLimitBypass.compareAndSet(false, true)) {
                        HookLog.i(TAG, "Bypassing the OPlus eSIM operational-profile count limit")
                    }
                    false
                },
            )
            HookLog.i(TAG, "Installed ColorOS 17 eSIM profile-limit hook")
            true
        }.onFailure { throwable ->
            HookLog.e(TAG, "Failed to install the eSIM profile-limit hook", throwable)
        }.getOrDefault(false)
    }

    private fun hookConfirmationCodeFlow(classLoader: ClassLoader?): Boolean {
        val activityClass = findClass(DOWNLOAD_ACTIVITY_CLASS, classLoader)
        val viewModelClass = findClass(DOWNLOAD_VIEW_MODEL_CLASS, classLoader)
        val callbackClass = findClass(CONFIRMATION_CALLBACK_CLASS, classLoader)
        val agentClass = findClass(AGENT_IMPLEMENTATION_CLASS, classLoader)
        if (activityClass == null || viewModelClass == null || callbackClass == null || agentClass == null) {
            HookLog.w(TAG, "C17 eSIM confirmation-code classes were not found")
            return false
        }

        val onResume = activityClass.declaredMethods.singleOrNull { method ->
            method.name == "onResume" && method.parameterCount == 0 &&
                method.returnType == Void.TYPE
        }
        val onDestroy = activityClass.declaredMethods.singleOrNull { method ->
            method.name == "onDestroy" && method.parameterCount == 0 &&
                method.returnType == Void.TYPE
        }
        val confirmationMethod = viewModelClass.declaredClasses
            .asSequence()
            .flatMap { nestedClass -> nestedClass.declaredMethods.asSequence() }
            .singleOrNull { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.contentEquals(
                        arrayOf(Boolean::class.javaPrimitiveType, callbackClass),
                    )
            }
        val downloadMethod = viewModelClass.declaredMethods.singleOrNull { method ->
            method.name == "m" &&
                !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(arrayOf(String::class.java))
        }
        val agentDownloadMethod = agentClass.declaredMethods.singleOrNull { method ->
            method.name == "a" &&
                !Modifier.isStatic(method.modifiers) &&
                method.returnType == Long::class.javaPrimitiveType &&
                method.parameterCount == 3 &&
                method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1] == String::class.java
        }

        if (
            onResume == null || onDestroy == null || downloadMethod == null ||
            agentDownloadMethod == null
        ) {
            val missing = buildList {
                if (onResume == null) add("activity.onResume")
                if (onDestroy == null) add("activity.onDestroy")
                if (downloadMethod == null) add("view-model download")
                if (agentDownloadMethod == null) add("agent download")
            }
            HookLog.w(TAG, "Compatible C17 eSIM targets were not found: ${missing.joinToString()}")
            return false
        }

        return runCatching {
            installAfterHook("euicc:download-activity-resume", onResume) { param ->
                val activity = param.thisObject as? Activity ?: return@installAfterHook
                activityReference = WeakReference(activity)
                showPendingPrompts(activity)
            }
            installAfterHook("euicc:download-activity-destroy", onDestroy) { param ->
                val activity = param.thisObject as? Activity ?: return@installAfterHook
                if (activityReference.get() === activity) {
                    activityReference = WeakReference(null)
                    // Preserve a pending server request across Activity recreation. Dismissing a
                    // dialog directly does not invoke its cancel listener.
                    dialogReference.get()?.dismiss()
                    dialogReference = WeakReference(null)
                }
            }
            if (confirmationMethod != null) {
                installAfterHook(
                    key = "euicc:confirmation-code-required:${confirmationMethod.toGenericString()}",
                    method = confirmationMethod,
                ) { param ->
                    if (!LspConfig.isEsimConfirmationCodePromptEnabledXposed()) {
                        return@installAfterHook
                    }
                    val retry = param.args.getOrNull(0) as? Boolean ?: false
                    val callback = param.args.getOrNull(1) ?: return@installAfterHook
                    onConfirmationCodeRequired(callback, retry)
                }
            } else {
                HookLog.w(TAG, "Server-requested confirmation callback is unavailable; pre-download entry remains active")
            }
            installBeforeHook(
                key = "euicc:prompt-before-download:${downloadMethod.toGenericString()}",
                method = downloadMethod,
            ) { param ->
                if (!LspConfig.isEsimConfirmationCodePromptEnabledXposed()) {
                    return@installBeforeHook
                }
                val viewModel = param.thisObject ?: return@installBeforeHook
                if (replayingDownload.get()?.viewModel === viewModel) {
                    return@installBeforeHook
                }
                val activationCode = param.args.getOrNull(0) as? String ?: return@installBeforeHook
                param.result = null
                onDownloadRequested(viewModel, downloadMethod, activationCode)
            }
            installBeforeHook(
                key = "euicc:inject-confirmation-code:${agentDownloadMethod.toGenericString()}",
                method = agentDownloadMethod,
            ) { param ->
                val replay = replayingDownload.get() ?: return@installBeforeHook
                param.args[1] = replay.confirmationCode
                HookLog.i(TAG, "Passing the carrier confirmation code to the OEM eSIM agent")
            }
            HookLog.i(TAG, "Installed ColorOS 17 eSIM confirmation-code UI repair")
            true
        }.onFailure { throwable ->
            HookLog.e(TAG, "Failed to install the eSIM confirmation-code UI repair", throwable)
        }.getOrDefault(false)
    }

    private fun installAfterHook(
        key: String,
        method: Method,
        after: (ModernMethodHook.MethodHookParam) -> Unit,
    ) {
        ModernHookRegistry.installCompat(
            key = key,
            executable = method,
            callback = object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) = after(param)
            },
        )
    }

    private fun installBeforeHook(
        key: String,
        method: Method,
        before: (ModernMethodHook.MethodHookParam) -> Unit,
    ) {
        ModernHookRegistry.installCompat(
            key = key,
            executable = method,
            callback = object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) = before(param)
            },
        )
    }

    private fun onDownloadRequested(viewModel: Any, method: Method, activationCode: String) {
        val request = synchronized(stateLock) {
            val current = pendingDownload
            if (
                current != null && current.viewModel === viewModel &&
                current.activationCode == activationCode && !current.completed.get()
            ) {
                current
            } else {
                PendingDownload(viewModel, method, activationCode).also { pendingDownload = it }
            }
        }
        HookLog.i(TAG, "Waiting for optional carrier confirmation-code entry before download")
        val activity = activityReference.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed) return
        activity.runOnUiThread { showPreDownloadDialog(activity, request) }
    }

    private fun onConfirmationCodeRequired(callback: Any, retry: Boolean) {
        val request = synchronized(stateLock) {
            val current = pendingConfirmation
            if (current != null && current.callback === callback && !current.completed.get()) {
                current.retry = current.retry || retry
                current
            } else {
                PendingConfirmation(callback = callback, retry = retry).also {
                    pendingConfirmation = it
                }
            }
        }

        HookLog.i(TAG, "Carrier confirmation code requested${if (retry) " again" else ""}")
        val activity = activityReference.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            HookLog.w(TAG, "Confirmation-code request is waiting for the download Activity")
            return
        }
        activity.runOnUiThread { showConfirmationDialog(activity, request) }
    }

    private fun showPendingPrompts(activity: Activity) {
        val downloadRequest = synchronized(stateLock) { pendingDownload }
        if (downloadRequest != null && !downloadRequest.completed.get()) {
            activity.runOnUiThread { showPreDownloadDialog(activity, downloadRequest) }
            return
        }
        val request = synchronized(stateLock) { pendingConfirmation } ?: return
        if (request.completed.get()) return
        activity.runOnUiThread { showConfirmationDialog(activity, request) }
    }

    private fun showPreDownloadDialog(activity: Activity, request: PendingDownload) {
        if (activity.isFinishing || activity.isDestroyed || request.completed.get()) return
        synchronized(stateLock) {
            if (pendingDownload !== request) return
            if (dialogReference.get()?.isShowing == true) return
        }

        val strings = PromptStrings.preDownload()
        val input = createConfirmationInput(activity, strings)
        val dialogResult = createPromptDialog(activity, input.container, strings)
        val dialog = dialogResult.dialog
        dialog.setOnDismissListener {
            synchronized(stateLock) {
                if (dialogReference.get() === dialog) dialogReference = WeakReference(null)
            }
        }
        dialog.setOnShowListener {
            dialog.findViewById<View>(android.R.id.button1)?.setOnClickListener {
                val code = input.editText.text?.toString()?.trim().orEmpty()
                if (resumeDownload(request, code)) {
                    dialog.dismiss()
                } else {
                    input.editText.error = strings.deliveryError
                }
            }
            dialog.findViewById<View>(android.R.id.button2)?.setOnClickListener {
                if (cancelDownload(request)) {
                    dialog.dismiss()
                    activity.finish()
                }
            }
            input.editText.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        synchronized(stateLock) {
            if (pendingDownload !== request || request.completed.get()) return
            dialogReference = WeakReference(dialog)
        }
        if (!showDialog(dialog, dialogResult.couiBuilder)) return
    }

    private fun showConfirmationDialog(activity: Activity, request: PendingConfirmation) {
        if (activity.isFinishing || activity.isDestroyed || request.completed.get()) return
        synchronized(stateLock) {
            if (pendingConfirmation !== request) return
            if (dialogReference.get()?.isShowing == true) return
        }

        val strings = PromptStrings.confirmation(request.retry)
        val input = createConfirmationInput(activity, strings)
        val dialogResult = createPromptDialog(activity, input.container, strings)
        val dialog = dialogResult.dialog

        dialog.setOnDismissListener {
            synchronized(stateLock) {
                if (dialogReference.get() === dialog) {
                    dialogReference = WeakReference(null)
                }
            }
        }
        dialog.setOnShowListener {
            dialog.findViewById<View>(android.R.id.button1)?.setOnClickListener {
                val code = input.editText.text?.toString()?.trim().orEmpty()
                if (code.isEmpty()) {
                    input.editText.error = strings.emptyError
                    return@setOnClickListener
                }
                if (completeConfirmation(request, code)) {
                    dialog.dismiss()
                } else {
                    input.editText.error = strings.deliveryError
                }
            }
            dialog.findViewById<View>(android.R.id.button2)?.setOnClickListener {
                if (completeConfirmation(request, null)) {
                    dialog.dismiss()
                } else {
                    input.editText.error = strings.deliveryError
                }
            }
            input.editText.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }

        synchronized(stateLock) {
            if (pendingConfirmation !== request || request.completed.get()) return
            dialogReference = WeakReference(dialog)
        }
        showDialog(dialog, dialogResult.couiBuilder)
    }

    private fun showDialog(dialog: Dialog, couiBuilder: Any?): Boolean {
        val shown = runCatching { dialog.show() }
            .onFailure { error ->
                HookLog.e(TAG, "Could not show the eSIM confirmation-code dialog", error)
            }
            .isSuccess
        if (!shown) {
            synchronized(stateLock) {
                if (dialogReference.get() === dialog) dialogReference = WeakReference(null)
            }
            return false
        }
        runCatching {
            couiBuilder?.let { builder ->
                ModernReflect.callMethod(builder, "updateViewAfterShown")
            }
        }
        return true
    }

    private fun createPromptDialog(
        activity: Activity,
        inputView: View,
        strings: PromptStrings,
    ): DialogResult {
        return runCatching {
            createCouiDialog(activity, inputView, strings)
        }.onFailure { error ->
            HookLog.w(TAG, "COUI confirmation dialog unavailable; using platform dialog", error)
        }.getOrElse {
            createPlatformDialog(activity, inputView, strings)
        }
    }

    private fun createCouiDialog(
        activity: Activity,
        inputView: View,
        strings: PromptStrings,
    ): DialogResult {
        val builderClass = findClass(COUI_DIALOG_BUILDER_CLASS, activity.classLoader)
            ?: throw ClassNotFoundException(COUI_DIALOG_BUILDER_CLASS)
        val builder = ModernReflect.newInstance(builderClass, activity)
        runCatching { ModernReflect.callMethod(builder, "setBlurBackgroundDrawable", true) }
        ModernReflect.callMethod(builder, "setTitle", strings.title)
        ModernReflect.callMethod(builder, "setMessage", strings.message)
        ModernReflect.callMethod(builder, "setView", inputView)
        ModernReflect.callMethod(builder, "setPositiveButton", strings.continueLabel, null)
        ModernReflect.callMethod(builder, "setNegativeButton", strings.cancelLabel, null)
        ModernReflect.callMethod(builder, "setCancelable", false)
        val dialog = ModernReflect.callMethod(builder, "create") as? Dialog
            ?: error("COUIAlertDialogBuilder.create() returned no Dialog")
        return DialogResult(dialog = dialog, couiBuilder = builder)
    }

    private fun createPlatformDialog(
        activity: Activity,
        inputView: View,
        strings: PromptStrings,
    ): DialogResult {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(strings.title)
            .setMessage(strings.message)
            .setView(inputView)
            .setPositiveButton(strings.continueLabel, null)
            .setNegativeButton(strings.cancelLabel, null)
            .setCancelable(false)
            .create()
        return DialogResult(dialog = dialog, couiBuilder = null)
    }

    private fun createConfirmationInput(
        activity: Activity,
        strings: PromptStrings,
    ): ConfirmationInput {
        val inputViewClass = findClass(COUI_INPUT_VIEW_CLASS, activity.classLoader)
        if (inputViewClass != null) {
            runCatching {
                val container = ModernReflect.newInstance(inputViewClass, activity) as View
                ModernReflect.callMethod(container, "setTitle", strings.fieldTitle)
                ModernReflect.callMethod(container, "setHint", strings.hint)
                val editText = ModernReflect.callMethod(container, "getEditText") as EditText
                configureConfirmationEditText(editText, strings)
                return ConfirmationInput(wrapLikeC17InputLayout(activity, container), editText)
            }.onFailure { error ->
                HookLog.w(TAG, "COUI input view unavailable; using a plain input", error)
            }
        }

        val editText = findClass(COUI_EDIT_TEXT_CLASS, activity.classLoader)
            ?.let { editTextClass ->
                runCatching {
                    ModernReflect.newInstance(editTextClass, activity) as EditText
                }.getOrNull()
            }
            ?: EditText(activity)
        configureConfirmationEditText(editText, strings)
        return ConfirmationInput(wrapLikeC17InputLayout(activity, editText), editText)
    }

    /** Matches coui_single_edit_bottom_alert_dialog_layout.xml from the C17 eSIM app. */
    private fun wrapLikeC17InputLayout(activity: Activity, inputView: View): View {
        val density = activity.resources.displayMetrics.density
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            marginStart = (24 * density).toInt()
            marginEnd = (24 * density).toInt()
            topMargin = (8 * density).toInt()
            bottomMargin = (16 * density).toInt()
        }
        return ScrollView(activity).apply {
            isFillViewport = true
            addView(inputView, layoutParams)
        }
    }

    private fun configureConfirmationEditText(
        editText: EditText,
        strings: PromptStrings,
    ) {
        editText.hint = strings.hint
        editText.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        editText.isSingleLine = true
        editText.maxLines = 1
    }

    private fun resumeDownload(request: PendingDownload, confirmationCode: String): Boolean {
        if (!request.completed.compareAndSet(false, true)) return true
        val replay = ReplayDownload(request.viewModel, confirmationCode)
        val resumed = runCatching {
            replayingDownload.set(replay)
            request.method.invoke(request.viewModel, request.activationCode)
        }.onFailure { throwable ->
            HookLog.e(TAG, "Could not resume the OEM eSIM download", throwable)
        }.isSuccess
        replayingDownload.remove()
        if (!resumed) {
            request.completed.set(false)
            return false
        }
        synchronized(stateLock) {
            if (pendingDownload === request) pendingDownload = null
        }
        return true
    }

    private fun cancelDownload(request: PendingDownload): Boolean {
        if (!request.completed.compareAndSet(false, true)) return true
        synchronized(stateLock) {
            if (pendingDownload === request) pendingDownload = null
        }
        HookLog.i(TAG, "eSIM download cancelled before starting")
        return true
    }

    private fun completeConfirmation(request: PendingConfirmation, code: String?): Boolean {
        if (!request.completed.compareAndSet(false, true)) return true
        val delivered = deliverConfirmationCode(request.callback, code)
        if (!delivered) {
            request.completed.set(false)
            return false
        }
        synchronized(stateLock) {
            if (pendingConfirmation === request) pendingConfirmation = null
        }
        HookLog.i(TAG, if (code == null) "Confirmation-code entry cancelled" else "Confirmation code submitted")
        return true
    }

    /**
     * C17's marker callback has no methods. It retains ProgressionCallback -> AgentService, where
     * the OEM implementation expects its String field to be set before its latch is released.
     */
    private fun deliverConfirmationCode(callback: Any, code: String?): Boolean {
        return runCatching {
            val progressionField = callback.javaClass.declaredFields.singleOrNull { field ->
                field.type.name.endsWith("AgentService\$ProgressionCallback")
            } ?: callback.javaClass.getDeclaredField("this\$1")
            progressionField.isAccessible = true
            val progression = requireNotNull(progressionField.get(callback))

            val serviceField = progression.javaClass.declaredFields.singleOrNull { field ->
                field.type.name == AGENT_SERVICE_CLASS
            } ?: progression.javaClass.getDeclaredField("b")
            serviceField.isAccessible = true
            val service = requireNotNull(serviceField.get(progression))

            val codeField = service.javaClass.declaredFields.singleOrNull { field ->
                field.type == String::class.java
            } ?: service.javaClass.getDeclaredField("f")
            val latchField = service.javaClass.declaredFields.singleOrNull { field ->
                field.type == CountDownLatch::class.java
            } ?: service.javaClass.getDeclaredField("g")
            codeField.isAccessible = true
            latchField.isAccessible = true
            val latch = latchField.get(service) as? CountDownLatch
                ?: error("AgentService confirmation latch is unavailable")
            codeField.set(service, code)
            latch.countDown()
        }.onFailure { throwable ->
            HookLog.e(TAG, "Could not resume the OEM eSIM confirmation-code transaction", throwable)
        }.isSuccess
    }

    private fun findClass(className: String, classLoader: ClassLoader?): Class<*>? {
        listOf(classLoader, null, ClassLoader.getSystemClassLoader())
            .distinct()
            .forEach { loader ->
                ModernReflect.findClassIfExists(className, loader)?.let { return it }
            }
        return null
    }

    private data class PendingConfirmation(
        val callback: Any,
        var retry: Boolean,
        val completed: AtomicBoolean = AtomicBoolean(false),
    )

    private data class PendingDownload(
        val viewModel: Any,
        val method: Method,
        val activationCode: String,
        val completed: AtomicBoolean = AtomicBoolean(false),
    )

    private data class ReplayDownload(
        val viewModel: Any,
        val confirmationCode: String,
    )

    private data class ConfirmationInput(
        val container: View,
        val editText: EditText,
    )

    private data class DialogResult(
        val dialog: Dialog,
        val couiBuilder: Any?,
    )

    private data class PromptStrings(
        val title: String,
        val message: String,
        val fieldTitle: String,
        val hint: String,
        val emptyError: String,
        val deliveryError: String,
        val continueLabel: String,
        val cancelLabel: String,
    ) {
        companion object {
            fun confirmation(retry: Boolean): PromptStrings {
                val locale = Locale.getDefault()
                val isTraditional = locale.language == "zh" &&
                    locale.country.uppercase(Locale.ROOT) in setOf("TW", "HK", "MO")
                return when {
                    isTraditional -> PromptStrings(
                        title = if (retry) "確認碼不正確" else "輸入確認碼",
                        message = if (retry) {
                            "請重新輸入電信業者提供的 eSIM 確認碼"
                        } else {
                            "此 eSIM 需要電信業者提供的確認碼"
                        },
                        fieldTitle = "確認碼",
                        hint = "輸入電信業者確認碼",
                        emptyError = "請輸入確認碼",
                        deliveryError = "暫時無法提交，請重試",
                        continueLabel = "繼續",
                        cancelLabel = "取消",
                    )
                    locale.language == "zh" -> PromptStrings(
                        title = if (retry) "验证码不正确" else "输入验证码",
                        message = if (retry) {
                            "请重新输入运营商提供的 eSIM 验证码"
                        } else {
                            "此 eSIM 需要运营商提供的验证码"
                        },
                        fieldTitle = "验证码",
                        hint = "输入运营商验证码",
                        emptyError = "请输入验证码",
                        deliveryError = "暂时无法提交，请重试",
                        continueLabel = "继续",
                        cancelLabel = "取消",
                    )
                    else -> PromptStrings(
                        title = if (retry) "Incorrect confirmation code" else "Enter confirmation code",
                        message = if (retry) {
                            "Enter the eSIM confirmation code from your carrier again"
                        } else {
                            "This eSIM requires a confirmation code from your carrier"
                        },
                        fieldTitle = "Confirmation code",
                        hint = "Enter carrier confirmation code",
                        emptyError = "Enter the confirmation code",
                        deliveryError = "Unable to submit. Try again.",
                        continueLabel = "Continue",
                        cancelLabel = "Cancel",
                    )
                }
            }

            fun preDownload(): PromptStrings {
                val locale = Locale.getDefault()
                val isTraditional = locale.language == "zh" &&
                    locale.country.uppercase(Locale.ROOT) in setOf("TW", "HK", "MO")
                return when {
                    isTraditional -> PromptStrings(
                        title = "輸入電信業者確認碼",
                        message = "如電信業者另外提供了 eSIM 確認碼，請在下載前輸入；沒有則留空繼續",
                        fieldTitle = "確認碼（選填）",
                        hint = "輸入電信業者確認碼",
                        emptyError = "",
                        deliveryError = "暫時無法開始下載，請重試",
                        continueLabel = "繼續",
                        cancelLabel = "取消",
                    )
                    locale.language == "zh" -> PromptStrings(
                        title = "输入运营商验证码",
                        message = "如运营商另外提供了 eSIM 验证码，请在下载前输入；没有则留空继续",
                        fieldTitle = "验证码（选填）",
                        hint = "输入运营商验证码",
                        emptyError = "",
                        deliveryError = "暂时无法开始下载，请重试",
                        continueLabel = "继续",
                        cancelLabel = "取消",
                    )
                    else -> PromptStrings(
                        title = "Enter carrier confirmation code",
                        message = "If your carrier provided a separate eSIM confirmation code, enter it before downloading. Otherwise, leave it blank.",
                        fieldTitle = "Confirmation code (optional)",
                        hint = "Enter carrier confirmation code",
                        emptyError = "",
                        deliveryError = "Unable to start the download. Try again.",
                        continueLabel = "Continue",
                        cancelLabel = "Cancel",
                    )
                }
            }
        }
    }
}
