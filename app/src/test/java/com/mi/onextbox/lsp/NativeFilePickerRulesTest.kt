package com.mi.onextbox.lsp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeFilePickerRulesTest {
    private val standardActions = listOf(
        "android.intent.action.OPEN_DOCUMENT",
        "android.intent.action.CREATE_DOCUMENT",
        "android.intent.action.OPEN_DOCUMENT_TREE",
        "android.intent.action.GET_CONTENT",
    )

    private fun restore(
        enabled: Boolean = true,
        action: String? = standardActions.first(),
        explicitlyTargeted: Boolean = false,
        selectedPackage: String? = NativeFilePickerRules.FILE_MANAGER_PACKAGE,
        selectedActivity: String? = NativeFilePickerRules.FILE_MANAGER_PICKER,
        firstPackage: String? = "com.android.documentsui",
        firstAvailable: Boolean = true,
    ) = NativeFilePickerRules.shouldRestore(
        enabled, action, explicitlyTargeted, selectedPackage, selectedActivity, firstPackage, firstAvailable,
    )

    @Test
    fun standardOpenSaveTreeAndContentRequestsCanUseNativePicker() {
        standardActions.forEach { action -> assertTrue(action, restore(action = action)) }
    }

    @Test
    fun disabledFeatureAlwaysRetainsSystemRouting() {
        standardActions.forEach { action -> assertFalse(action, restore(enabled = false, action = action)) }
    }

    @Test
    fun explicitComponentsPackagesAndSelectorsAreNotOverridden() {
        standardActions.forEach { action ->
            assertFalse(action, restore(action = action, explicitlyTargeted = true))
        }
    }

    @Test
    fun photoPickerCameraSharingCalendarAndFileManagerHomeAreUntouched() {
        listOf(
            null, "", "android.intent.action.MAIN", "android.intent.action.VIEW",
            "android.intent.action.PICK", "android.intent.action.SEND", "android.intent.action.SEND_MULTIPLE",
            "android.intent.action.EDIT", "android.intent.action.INSERT",
            "android.provider.action.PICK_IMAGES", "android.provider.action.PICK_IMAGES_MULTI",
            "android.media.action.IMAGE_CAPTURE", "oplus.intent.action.filemanager.BROWSER_FILE",
        ).forEach { action -> assertFalse(action.toString(), restore(action = action)) }
    }

    @Test
    fun onlyTheColorOsPickerRedirectIsCancelled() {
        listOf(null, "", "other.picker", "com.android.documentsui", "com.oplus.filemanager")
            .forEach { packageName -> assertFalse(restore(selectedPackage = packageName)) }
        listOf(
            null, "", "com.oplus.filemanager.main.ui.MainActivity",
            "com.oplus.filemanager.filechoose.ui.singlepicker.SinglePickerActivity",
            "com.oplus.filemanager.picker.OtherActivity",
        ).forEach { activity -> assertFalse(restore(selectedActivity = activity)) }
    }

    @Test
    fun bothSystemDocumentsUiPackagesAreRecognized() {
        listOf("com.android.documentsui", "com.google.android.documentsui")
            .forEach { packageName -> assertTrue(restore(firstPackage = packageName)) }
    }

    @Test
    fun aMissingDisabledOrUnexportedNativePickerKeepsTheOriginalResult() {
        assertFalse(restore(firstAvailable = false))
        assertFalse(restore(firstPackage = null))
    }

    @Test
    fun aDifferentFirstCandidateIsNotForcedToNativePicker() {
        listOf("", "other.picker", "com.coloros.filemanager", "com.android.externalstorage")
            .forEach { packageName -> assertFalse(restore(firstPackage = packageName)) }
    }

    @Test
    fun exactRedirectMethodSignatureMatches() {
        assertTrue(NativeFilePickerRules.isRedirectMethod(
            "interceptPickerIntent", "android.content.pm.ResolveInfo",
            listOf("android.content.Intent", "java.util.List"),
        ))
    }

    @Test
    fun changedMethodNamesOrReturnTypesAreRejected() {
        listOf("", "interceptIntent", "interceptPickerIntentToOplus").forEach { name ->
            assertFalse(NativeFilePickerRules.isRedirectMethod(
                name, "android.content.pm.ResolveInfo", listOf("android.content.Intent", "java.util.List"),
            ))
        }
        listOf("void", "java.lang.Object", "android.content.pm.ActivityInfo").forEach { returnType ->
            assertFalse(NativeFilePickerRules.isRedirectMethod(
                "interceptPickerIntent", returnType, listOf("android.content.Intent", "java.util.List"),
            ))
        }
    }

    @Test
    fun missingExtraReorderedOrChangedParametersAreRejected() {
        listOf(
            emptyList(), listOf("android.content.Intent"),
            listOf("java.util.List", "android.content.Intent"),
            listOf("android.content.Intent", "java.util.List", "int"),
            listOf("android.content.Intent", "java.util.ArrayList"),
        ).forEach { parameters ->
            assertFalse(NativeFilePickerRules.isRedirectMethod(
                "interceptPickerIntent", "android.content.pm.ResolveInfo", parameters,
            ))
        }
    }
}
