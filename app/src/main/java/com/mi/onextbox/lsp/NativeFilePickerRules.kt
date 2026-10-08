package com.mi.onextbox.lsp

internal object NativeFilePickerRules {
    const val FILE_MANAGER_PACKAGE = "com.coloros.filemanager"
    const val FILE_MANAGER_PICKER = "com.oplus.filemanager.picker.PickerActivity"

    private val actions = setOf(
        "android.intent.action.OPEN_DOCUMENT",
        "android.intent.action.CREATE_DOCUMENT",
        "android.intent.action.OPEN_DOCUMENT_TREE",
        "android.intent.action.GET_CONTENT",
    )
    private val nativePackages = setOf("com.android.documentsui", "com.google.android.documentsui")

    fun shouldRestore(
        enabled: Boolean,
        action: String?,
        explicitlyTargeted: Boolean,
        selectedPackage: String?,
        selectedActivity: String?,
        firstPackage: String?,
        firstAvailable: Boolean,
    ): Boolean = enabled && action in actions && !explicitlyTargeted && firstAvailable &&
        firstPackage in nativePackages && selectedPackage == FILE_MANAGER_PACKAGE &&
        selectedActivity == FILE_MANAGER_PICKER

    fun isRedirectMethod(name: String, returnType: String, parameterTypes: List<String>): Boolean =
        name == "interceptPickerIntent" && returnType == "android.content.pm.ResolveInfo" &&
            parameterTypes == listOf("android.content.Intent", "java.util.List")
}
