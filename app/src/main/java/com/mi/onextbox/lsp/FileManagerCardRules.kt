package com.mi.onextbox.lsp

internal object FileManagerCardRules {
    const val PACKAGE_NAME = "com.coloros.filemanager"

    fun shouldHide(hideRequested: Boolean, packageName: String): Boolean =
        hideRequested && packageName == PACKAGE_NAME

    fun isDisplayPredicate(name: String, returnType: String, parameterTypes: List<String>): Boolean =
        name == "l" && returnType == "boolean" && parameterTypes == listOf(
            "androidx.fragment.app.FragmentActivity", "boolean",
        )

    fun isCardFactory(name: String, returnType: String, parameterTypes: List<String>): Boolean =
        name == "c" && returnType == "jn.d" && parameterTypes == listOf(
            "android.view.ViewGroup", "androidx.fragment.app.FragmentActivity", "boolean", "bz.l",
        )
}
