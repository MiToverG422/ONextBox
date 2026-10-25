package com.mi.onextbox.ui.common

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LspMissingScopesNoticeUiContractTest {
    private val source = listOf(
        File("src/main/java/com/mi/onextbox/ui/common/LspMissingScopesNotice.kt"),
        File("app/src/main/java/com/mi/onextbox/ui/common/LspMissingScopesNotice.kt"),
    ).first { it.isFile }.readText()

    @Test fun couiActionRetainsNativeButtonPaddingAndPressFeedback() {
        val action = source.substringAfter("Button(").substringBefore("if (openFailed)")
        assertTrue(action.contains("onClick = onOpen"))
        assertTrue(action.contains("enabled = !opening"))
        assertFalse(action.contains("contentPadding ="))
        assertFalse(action.contains("indication = null"))
        assertFalse(action.contains("pointerInput"))
    }

    @Test fun noticeUsesCouiCardAndTextColors() {
        assertTrue(source.contains("import io.github.suqi8.coui.kmp.basic.Button"))
        assertTrue(source.contains("cornerRadius = AppUiTokens.CardCornerRadius"))
        assertTrue(source.contains("color = COUITheme.colorScheme.onSurface"))
    }
}
