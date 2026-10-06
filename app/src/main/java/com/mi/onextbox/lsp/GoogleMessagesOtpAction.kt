package com.mi.onextbox.lsp

/** Routing/validation for module-owned copy actions; never intercept native actions. */
internal object GoogleMessagesOtpAction {
    const val ACTION = "com.mi.onextbox.action.COPY_GOOGLE_MESSAGES_OTP"
    const val RECEIVER = "com.google.android.apps.messaging.shared.receiver.CopyOtpReceiver"
    private const val LEGACY_ACTION = "com.google.android.apps.messaging.copy_otp"

    fun ownsAction(action: String?, scheme: String?, hasNativeIds: Boolean): Boolean =
        action == ACTION ||
            (action == LEGACY_ACTION && scheme == "onextbox-otp" && !hasNativeIds)

    fun isValidCode(code: String?): Boolean =
        code != null && code.length in 4..8 && code.all { it in '0'..'9' }
}
