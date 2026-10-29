package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class SystemExtrasRulesTest {
    @Test fun engineerChoicesUseNativeDisplayMessagesOnly() {
        assertNull(EngineerDisplayRules.message(0))
        assertEquals(1, EngineerDisplayRules.message(1))
        assertEquals(0, EngineerDisplayRules.message(2))
        assertEquals(2, EngineerDisplayRules.message(3))
        assertNull(EngineerDisplayRules.message(99))
    }
    @Test fun photoPickerOnlyRoutesSupportedActions() {
        assertEquals("PhotoPickerActivity", NativePhotoPickerRules.target("android.provider.action.PICK_IMAGES"))
        assertEquals("PhotoPickerGetContentActivity", NativePhotoPickerRules.target("android.intent.action.GET_CONTENT"))
        assertEquals("PhotoPickerUserSelectActivity", NativePhotoPickerRules.target("android.provider.action.USER_SELECT_IMAGES_FOR_APP"))
        assertNull(NativePhotoPickerRules.target("android.intent.action.OPEN_DOCUMENT"))
        assertNull(NativePhotoPickerRules.target(null))
    }
}
