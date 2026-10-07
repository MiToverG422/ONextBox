package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileManagerCardRulesTest {
    private val displayParameters = listOf("androidx.fragment.app.FragmentActivity", "boolean")
    private val factoryParameters = listOf(
        "android.view.ViewGroup", "androidx.fragment.app.FragmentActivity", "boolean", "bz.l",
    )

    @Test
    fun hideRequiresTheVerifiedPackageAndEnabledSetting() {
        assertEquals("com.coloros.filemanager", FileManagerCardRules.PACKAGE_NAME)
        assertTrue(FileManagerCardRules.shouldHide(true, "com.coloros.filemanager"))
    }

    @Test
    fun disabledSettingKeepsTheCard() {
        assertFalse(FileManagerCardRules.shouldHide(false, "com.coloros.filemanager"))
    }

    @Test
    fun otherPackagesCannotInheritTheHideSetting() {
        listOf(
            "", "com.oplus.filemanager", "com.android.documentsui", "com.google.android.documentsui",
            "com.coloros.filemanager.debug", "com.coloros.filemanager ", "Com.coloros.filemanager",
        ).forEach { packageName ->
            assertFalse(packageName, FileManagerCardRules.shouldHide(true, packageName))
        }
    }

    @Test
    fun displayPredicateMatchesOnlyTheVerifiedSignature() {
        assertTrue(FileManagerCardRules.isDisplayPredicate("l", "boolean", displayParameters))
    }

    @Test
    fun displayPredicateRejectsOtherNamesAndBoxedReturnTypes() {
        listOf("", "L", "i", "shouldDisplay").forEach { name ->
            assertFalse(name, FileManagerCardRules.isDisplayPredicate(name, "boolean", displayParameters))
        }
        listOf("java.lang.Boolean", "Boolean", "void", "int").forEach { result ->
            assertFalse(result, FileManagerCardRules.isDisplayPredicate("l", result, displayParameters))
        }
    }

    @Test
    fun displayPredicateRejectsChangedArgumentShapes() {
        listOf(
            emptyList(),
            displayParameters.dropLast(1),
            displayParameters + "boolean",
            displayParameters.reversed(),
            listOf("android.app.Activity", "boolean"),
            listOf("androidx.fragment.app.FragmentActivity", "java.lang.Boolean"),
        ).forEach { parameters ->
            assertFalse(parameters.toString(), FileManagerCardRules.isDisplayPredicate("l", "boolean", parameters))
        }
    }

    @Test
    fun factoryMatchesOnlyTheVerifiedSignature() {
        assertTrue(FileManagerCardRules.isCardFactory("c", "jn.d", factoryParameters))
    }

    @Test
    fun factoryRejectsOtherNamesAndReturnTypes() {
        listOf("", "C", "create", "l").forEach { name ->
            assertFalse(name, FileManagerCardRules.isCardFactory(name, "jn.d", factoryParameters))
        }
        listOf("android.view.View", "java.lang.Object", "jn.D", "jn.d.Inner", "boolean").forEach { result ->
            assertFalse(result, FileManagerCardRules.isCardFactory("c", result, factoryParameters))
        }
    }

    @Test
    fun factoryRejectsMissingExtraReorderedAndChangedArguments() {
        listOf(
            emptyList(),
            factoryParameters.dropLast(1),
            factoryParameters + "boolean",
            factoryParameters.reversed(),
            listOf("android.view.View", "androidx.fragment.app.FragmentActivity", "boolean", "bz.l"),
            listOf("android.view.ViewGroup", "android.app.Activity", "boolean", "bz.l"),
            listOf("android.view.ViewGroup", "androidx.fragment.app.FragmentActivity", "java.lang.Boolean", "bz.l"),
            listOf("android.view.ViewGroup", "androidx.fragment.app.FragmentActivity", "boolean", "bz.L"),
        ).forEach { parameters ->
            assertFalse(parameters.toString(), FileManagerCardRules.isCardFactory("c", "jn.d", parameters))
        }
    }
}
